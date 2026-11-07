package com.clevercorner.arrowescape.ui.game

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clevercorner.arrowescape.ads.AdManager
import com.clevercorner.arrowescape.analytics.AnalyticsTracker
import com.clevercorner.arrowescape.analytics.Events
import com.clevercorner.arrowescape.data.GameMode
import com.clevercorner.arrowescape.data.GamePreferences
import com.clevercorner.arrowescape.game.Scoring
import com.clevercorner.arrowescape.game.curve.*
import com.clevercorner.arrowescape.game.daily.DailyBoardLayout
import com.clevercorner.arrowescape.game.daily.DailyPuzzle
import com.clevercorner.arrowescape.game.daily.DailyShuffle
import com.clevercorner.arrowescape.game.level.GenerationDispatchers
import com.clevercorner.arrowescape.game.level.GenerationGuard
import com.clevercorner.arrowescape.game.model.GameStatus
import com.clevercorner.arrowescape.review.ReviewPrompter
import com.clevercorner.arrowescape.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * Game state for curved arrow puzzles.
 */
data class CurvedGameState(
    val level: Int = 1,
    val arrows: List<CurvedArrow> = emptyList(),
    val shape: ShapePath? = null,
    val shapeName: String = "",
    val difficulty: String = "Easy",
    val lives: Int = 3,
    val mistakes: Int = 0,
    val status: GameStatus = GameStatus.PLAYING,
    val hintArrowId: Int? = null,
    val colorPalette: List<Long> = emptyList(),
    /** No remaining arrow can ever escape - the level cannot be finished. */
    val isStuck: Boolean = false
) {
    /**
     * Computed once per state, not once per read.
     *
     * These used to be `get()` properties, and a dense board is hundreds of
     * arrows: the header, the stats row, the progress track and the
     * completion overlay each triggered their own filter pass, on every
     * recomposition, several times a frame while arrows are flying. The
     * state is immutable, so the answer cannot change between reads - which
     * makes recomputing it pure waste.
     */
    val activeArrows: List<CurvedArrow> = arrows.filter { it.isActive }

    val remainingCount: Int get() = activeArrows.size

    val clearedPercentage: Int
        get() = if (arrows.isEmpty()) 0 else ((arrows.size - remainingCount) * 100) / arrows.size
}

/**
 * Which kind of board is on screen.
 *
 * The tapping, animating and life-losing are identical either way - only the
 * bookkeeping at the end differs - so one ViewModel serves both rather than a
 * near-copy serving the daily.
 */
enum class CurvedSession { JOURNEY, DAILY }

/**
 * ViewModel for curved arrow puzzle gameplay.
 * Uses bezier curves instead of grid-based arrows.
 */
class CurvedGameViewModel(
    private val preferences: GamePreferences,
    private val adManager: AdManager,
    private val analytics: AnalyticsTracker,
    /** App-wide, and owned by the app - this ViewModel must not release it. */
    private val feedback: GameFeedback,
    private val context: Context
) : ViewModel() {

    companion object {
        /** This ViewModel owns the curved mode's progress, and only that. */
        private val MODE = GameMode.CURVED

        private const val INTERSTITIAL_FREQUENCY = 4
        private const val ANIMATION_FRAME_MS = 16L
        private const val DEFAULT_LIVES = 3
        private const val HINTS_PER_LEVEL = 2
        /** The journey's board: a tall card that fills a phone screen. */
private const val JOURNEY_BOARD_WIDTH = 400f
        private const val JOURNEY_BOARD_HEIGHT = 600f

        /** Half a shuffle: fold away, then unfold into the new arrangement. */
        private const val SHUFFLE_FOLD_MS = 420L

        /**
         * How long speculative neighbour generation waits before starting.
         * Long enough for the board the player is waiting on to compose,
         * measure and draw first. See [prefetchNeighbours].
         */
        private const val PREFETCH_GRACE_MS = 600L
    }

    // ─────────────────────────────────────────────────────────────────────
    // State flows
    // ─────────────────────────────────────────────────────────────────────
    
    private val _gameState = MutableStateFlow(CurvedGameState())
    val gameState: StateFlow<CurvedGameState> = _gameState.asStateFlow()

    // Escape progress: arrow ID -> animation progress (0 to 1)
    private val _escapingArrows = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val escapingArrows: StateFlow<Map<Int, Float>> = _escapingArrows.asStateFlow()

    // Blocked taps: arrow ID -> how far it lunges and how far through the bump it is
    private val _bumpingArrows = MutableStateFlow<Map<Int, ArrowMotion.Bump>>(emptyMap())
    val bumpingArrows: StateFlow<Map<Int, ArrowMotion.Bump>> = _bumpingArrows.asStateFlow()

    private val _hintsRemaining = MutableStateFlow(HINTS_PER_LEVEL)
    val hintsRemaining: StateFlow<Int> = _hintsRemaining.asStateFlow()

    private val _combo = MutableStateFlow(0)
    val combo: StateFlow<Int> = _combo.asStateFlow()

    /**
     * Boards the player can rewind to, newest last - each is the arrow list as
     * it stood before an escape. Cleared whenever the board is re-dealt, since
     * an older arrangement is not one a shuffle could have produced.
     */
    private val undoHistory = UndoHistory<List<CurvedArrow>>()
    val canUndo: StateFlow<Boolean> = undoHistory.canUndo

    /** The longest run on record, so only a genuine new record gets written. */
    private var bestRunKnown = 0

    /** True while the next board is being built. The screen shows a spinner. */
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    /**
     * Shared with every other reader in the app - see [GamePreferences.prefs].
     * Defaulted rather than nullable because gameplay cannot pause mid-tap to
     * ask whether disk has answered yet.
     */
    val prefs = preferences.prefsOrDefault

    /** Decides when a happy player gets asked for a store review. */
    val reviewPrompter = ReviewPrompter(preferences, analytics)

    // Board dimensions for rendering
    private val _boardWidth = MutableStateFlow(JOURNEY_BOARD_WIDTH)
    val boardWidth: StateFlow<Float> = _boardWidth.asStateFlow()

    private val _boardHeight = MutableStateFlow(JOURNEY_BOARD_HEIGHT)
    val boardHeight: StateFlow<Float> = _boardHeight.asStateFlow()

    /**
     * Switches the playfield every later step reads from - generation,
     * blocking graph, exit rays and the on-screen scale alike. Call before
     * [loadBoard]; a board half-built against the previous size would judge
     * escapes against walls the player cannot see.
     */
    private fun useBoard(width: Float, height: Float) {
        _boardWidth.value = width
        _boardHeight.value = height
    }

    private var continueUsedThisLevel = false

    /**
     * The journey level currently on screen or on its way there.
     *
     * Null means "no board has ever been dealt in this ViewModel", which is
     * the one question [resumeLevel] needs answered.
     */
    private var journeyLevel: Int? = null

    private var session = CurvedSession.JOURNEY

    /** Non-null only while a daily board is being played. */
    private var dailyPuzzle: DailyPuzzle? = null
    private var dailyLayout: DailyBoardLayout = DailyBoardLayout.PLAQUE

    /**
     * Precomputed blocking graph for the level. Built once - arrow geometry
     * never changes during play, only which arrows are still alive, so every
     * tap is a set lookup rather than a geometric sweep.
     */
    private var blockingGraph: CurvedGameEngine.BlockingGraph? = null

    /** The job building the board on screen. Superseded loads are cancelled. */
    private var boardJob: Job? = null

    /** Speculative build of the neighbouring levels. See [prefetchNeighbours]. */
    private var prefetchJob: Job? = null

    private fun requireGraph(state: CurvedGameState): CurvedGameEngine.BlockingGraph =
        blockingGraph ?: CurvedGameEngine.BlockingGraph(state.arrows, board()).also {
            blockingGraph = it
        }

    private fun board() = CurvedGameEngine.Board(_boardWidth.value, _boardHeight.value)

    // ─────────────────────────────────────────────────────────────────────
    // Haptics & Sound - one app-wide instance, see GameFeedback.
    // ─────────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────
    // Level Navigation
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Deals [level] as a fresh journey board. Tapping Play means Play, so
     * this always re-deals - asking for the level already on screen is a
     * restart, not a no-op.
     */
    fun startLevel(level: Int) {
        session = CurvedSession.JOURNEY
        journeyLevel = level
        dailyPuzzle = null
        stopShuffleClock()
        useBoard(JOURNEY_BOARD_WIDTH, JOURNEY_BOARD_HEIGHT)
        loadBoard(level)
    }

    /**
     * Deals [level] only if there is no board yet.
     *
     * For the game route, which is reached by a caller that has already
     * started the level - so normally this does nothing. It earns its keep
     * after process death, when the back stack is restored straight onto the
     * game screen with a brand new ViewModel and nobody to deal the board.
     */
    fun resumeLevel(level: Int) {
        if (journeyLevel == null) startLevel(level)
    }

    /**
     * Plays today's (or any day's) puzzle. Same engine, same board, different
     * ending: a daily marks a date solved instead of advancing the journey.
     *
     * The daily also gets the shuffle clock - see [runShuffleClock].
     */
    fun startDaily(puzzle: DailyPuzzle, layout: DailyBoardLayout = DailyBoardLayout.PLAQUE) {
        session = CurvedSession.DAILY
        dailyPuzzle = puzzle
        dailyLayout = layout
        journeyLevel = null
        analytics.log(Events.dailyChallengeStarted(puzzle.date.toString()))
        viewModelScope.launch { bestRunKnown = preferences.prefs.filterNotNull().first().daily.bestRun }
        useBoard(layout.width, layout.height)
        loadBoard(puzzle.levelSeed)
        runShuffleClock()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Daily shuffle
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Counts down to the next board re-arrangement. Null outside the daily
     * challenge, which is also how the UI knows not to draw the countdown.
     */
    private val _shuffleSecondsLeft = MutableStateFlow<Int?>(null)
    val shuffleSecondsLeft: StateFlow<Int?> = _shuffleSecondsLeft.asStateFlow()

    /** How many times today's board has turned over. */
    private val _shuffleCount = MutableStateFlow(0)
    val shuffleCount: StateFlow<Int> = _shuffleCount.asStateFlow()

    /**
     * 1 while the board is settled, dipping to 0 as it re-arranges.
     *
     * The board folds every arrow flat, swaps in the new arrangement at the
     * instant nothing is visible, then unfolds. Done this way the change is
     * a *movement* rather than a jump cut - and crucially the player never
     * sees an arrowhead teleport from one end of its curve to the other,
     * which is what made the first version look like a rendering glitch.
     */
    private val _shufflePhase = MutableStateFlow(1f)
    val shufflePhase: StateFlow<Float> = _shufflePhase.asStateFlow()

    private var shuffleJob: Job? = null

    /**
     * The daily's heartbeat: wait out an interval, re-arrange, repeat.
     *
     * The countdown only ticks while the level is actually being played, so
     * reading the stuck-modal or the win card does not eat the clock. One
     * job for the whole session rather than one per cycle - restarting the
     * daily cancels it, and nothing else needs to know it exists.
     */
    private fun runShuffleClock() {
        shuffleJob?.cancel()
        _shuffleCount.value = 0
        shuffleJob = viewModelScope.launch {
            while (true) {
                var remaining = DailyShuffle.INTERVAL_SECONDS
                while (remaining > 0) {
                    _shuffleSecondsLeft.value = remaining
                    delay(1000)
                    if (_gameState.value.status == GameStatus.PLAYING) remaining--
                }
                _shuffleSecondsLeft.value = 0
                shuffleBoard()
            }
        }
    }

    /**
     * Re-arranges the arrows still on the board, keeping every one of them.
     *
     * Only the survivors are shuffled: arrows that already escaped stay gone,
     * so the count the player is working against never jumps. If the search
     * cannot find a solvable arrangement the shuffle is skipped outright -
     * dropping an arrow to force one through would be a worse lie than a
     * board that occasionally stays put.
     *
     * @return whether the board actually changed.
     */
    private suspend fun shuffleBoard(): Boolean {
        val state = _gameState.value
        if (state.status != GameStatus.PLAYING) return false

        // Never re-arrange the board out from under an arrow that is already
        // flying. Waiting one cycle is invisible; teleporting a mid-flight
        // arrow is not.
        if (_escapingArrows.value.isNotEmpty() || _bumpingArrows.value.isNotEmpty()) return false

        val survivors = state.arrows.filter { it.isActive }
        if (survivors.size < 2) return false

        // Searched with the same tuning the level was generated with, so a
        // shuffled daily is as hard as the board it came from - not a second,
        // easier target that only the daily ever sees.
        val puzzle = dailyPuzzle ?: return false
        val tuning = CurvedLevelGenerator.tuningFor(puzzle.levelSeed)
        val seed = puzzle.stamp.toLong() * 31 + _shuffleCount.value
        val reshuffled = withContext(Dispatchers.Default) {
            CurvedLevelGenerator.reshuffle(survivors, board(), Random(seed), tuning)
        } ?: return false

        val byId = reshuffled.arrows.associateBy { it.id }
        val arrows = state.arrows.map { byId[it.id] ?: it }

        playShuffle(arrows, reshuffled.blockingGraph)
        _shuffleCount.value++
        undoHistory.clear()
        if (prefs.value.hapticsEnabled) feedback.playErrorHaptic()
        return true
    }

    /**
     * Folds the board away, swaps in [arrows], and unfolds it again.
     *
     * The swap happens at the pinch point, when every arrow is drawn at zero
     * width, so there is no frame in which old and new geometry are both
     * visible. The board decides how to stagger the fold across the canvas -
     * see `shuffleScaleFor` - this only owns the timing.
     *
     * Driven by [animateProgress] rather than a Compose `Animatable`: an
     * Animatable needs a MonotonicFrameClock, which a viewModelScope does
     * not have, and asking for one crashed the moment the first shuffle
     * fired. Every other animation in this class already uses this helper.
     */
    private suspend fun playShuffle(arrows: List<CurvedArrow>, graph: CurvedGameEngine.BlockingGraph) {
        animateProgress(SHUFFLE_FOLD_MS) { t -> _shufflePhase.value = 1f - smoothStep(t) }

        // The blocking graph is geometry-derived and the geometry just
        // changed; a stale graph would happily let blocked arrows fly. The
        // shuffle measured the new one while it searched, so swapping it in
        // here costs nothing - it used to be re-derived on the main thread,
        // mid-animation, which is the worst possible moment for a ray cast.
        blockingGraph = graph
        _gameState.value = _gameState.value.copy(arrows = arrows, hintArrowId = null)

        animateProgress(SHUFFLE_FOLD_MS) { t -> _shufflePhase.value = smoothStep(t) }
        _shufflePhase.value = 1f
    }

    /** Classic smoothstep - eases both ends without an easing curve object. */
    private fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)

    /** Stops the daily clock - on leaving the screen, or on solving the day. */
    fun stopShuffleClock() {
        shuffleJob?.cancel()
        shuffleJob = null
        _shuffleSecondsLeft.value = null
        _shufflePhase.value = 1f
    }

    private fun loadBoard(level: Int) {
        // Whatever we were speculatively building, the player has now told us
        // what they actually want - stop competing with it for CPU.
        prefetchJob?.cancel()
        boardJob?.cancel()
        undoHistory.clear()

        // Set before the first suspension point, so from this instant the
        // header names the requested level and the board area shows only the
        // spinner - never the previous level's arrows or name.
        _gameState.value = headerOf(level)
        _isGenerating.value = true

        boardJob = viewModelScope.launch {
            // Generate the level off the main thread. Dense levels have
            // hundreds of arrows, and the generator settles their blocking
            // relationships as part of the orientation search - so the board
            // arrives with its graph already measured.
            val curvedLevel = withContext(Dispatchers.Default) {
                CurvedLevelGenerator.generateLevel(
                    level,
                    _boardWidth.value,
                    _boardHeight.value,
                    guardFor(coroutineContext.job)
                )
            }
            val graph = curvedLevel.blockingGraph

            _gameState.value = CurvedGameState(
                level = level,
                arrows = curvedLevel.arrows,
                shape = curvedLevel.shape,
                shapeName = curvedLevel.shapeName,
                difficulty = curvedLevel.difficulty,
                lives = DEFAULT_LIVES,
                mistakes = 0,
                status = GameStatus.PLAYING,
                hintArrowId = null,
                colorPalette = curvedLevel.colorPalette,
                // The generator proves solvability before it returns, and
                // drops arrows until it holds - so the clear order it found
                // covering every arrow IS the proof. Re-deriving that here
                // cost a full sweep on the player's critical path to answer
                // "yes" every single time; comparing two sizes does not.
                isStuck = curvedLevel.solutionOrder.size < curvedLevel.arrows.size
            )

            if (_gameState.value.isStuck) {
                android.util.Log.e("CurvedGame", "Level $level was generated unsolvable")
            }

            _escapingArrows.value = emptyMap()
            _bumpingArrows.value = emptyMap()
            _hintsRemaining.value = HINTS_PER_LEVEL
            _combo.value = 0
            continueUsedThisLevel = false
            blockingGraph = graph
            _isGenerating.value = false
            prefetchNeighbours(level)
            // Developer sessions are for looking at levels, not for metrics.
            if (!prefs.value.devMode) {
                analytics.log(
                    Events.levelStarted(
                        level = level,
                        arrowCount = curvedLevel.arrows.size,
                        mode = "curved",
                        isOffline = NetworkUtils.isOffline(context)
                    )
                )
            }
        }
    }

    /**
     * Quietly builds the levels either side of [level] while the player is
     * busy with this one, so stepping between them is instant.
     *
     * The generator caches what it builds, so this only has to warm that
     * cache - there is no second copy of the result to keep in sync here.
     * Cancelled the moment a real load starts, so speculative work never
     * delays the level actually being waited on.
     */
    /**
     * The part of a level's state that comes from its number alone - the
     * name and difficulty the header shows. Read from the same sources the
     * generator uses, so it cannot disagree with the finished board, and it
     * is available instantly where the board is not.
     */
    private fun headerOf(level: Int): CurvedGameState {
        val header = CurvedLevelGenerator.headerFor(level)
        return CurvedGameState(
            level = level,
            shapeName = header.shapeName,
            difficulty = header.difficultyLabel
        )
    }

    /**
     * Quietly builds the levels either side of [level] while the player is
     * busy with this one, so stepping between them is instant.
     *
     * The generator caches what it builds, so this only has to warm that
     * cache - there is no second copy of the result to keep in sync here.
     * Cancelled the moment a real load starts, so speculative work never
     * delays the level actually being waited on.
     *
     * The grace period matters as much as the cancellation: the board that
     * just finished generating still has to compose, measure and draw a few
     * hundred curves, and starting two more searches in that exact window
     * means the speculative work competes with the frame the player is
     * actually waiting on.
     *
     * It runs on one low-priority thread ([GenerationDispatchers.prefetch])
     * rather than across every core, and hands the generator a
     * [GenerationGuard] so that cancelling the job actually stops the
     * arithmetic - `cancel()` alone does nothing to a long non-suspending
     * loop, which is why a "cancelled" prefetch used to run to completion.
     */
    private fun prefetchNeighbours(level: Int) {
        prefetchJob = viewModelScope.launch(GenerationDispatchers.prefetch) {
            delay(PREFETCH_GRACE_MS)
            val guard = guardFor(coroutineContext.job)
            for (neighbour in listOf(level + 1, level - 1)) {
                if (neighbour < 1) continue
                ensureActive()
                CurvedLevelGenerator.generateLevel(
                    neighbour,
                    _boardWidth.value,
                    _boardHeight.value,
                    guard
                )
            }
        }
    }

    /** Ties a generator's "is anybody still waiting?" to the job doing the waiting. */
    private fun guardFor(job: Job) = GenerationGuard { !job.isActive }

    // ─────────────────────────────────────────────────────────────────────
    // Gameplay
    // ─────────────────────────────────────────────────────────────────────

    // Debounce guard
    private var lastTapTimeMs = 0L
    private var lastTappedArrowId = -1
    private val TAP_DEBOUNCE_MS = 100L

    fun onArrowTap(arrow: CurvedArrow) {
        val now = System.currentTimeMillis()

        // Debounce duplicate taps
        if (arrow.id == lastTappedArrowId && (now - lastTapTimeMs) < TAP_DEBOUNCE_MS) {
            return
        }
        lastTapTimeMs = now
        lastTappedArrowId = arrow.id

        val currentState = _gameState.value
        if (currentState.status != GameStatus.PLAYING || currentState.isStuck) return

        // Mid-shuffle the board is folded away; a tap now would fire an
        // arrow the player cannot even see.
        if (_shufflePhase.value < 1f) return

        // Skip if already animating
        if (_escapingArrows.value.containsKey(arrow.id) || _bumpingArrows.value.containsKey(arrow.id)) {
            return
        }

        // Clear hint highlight
        if (currentState.hintArrowId != null) {
            _gameState.value = currentState.copy(hintArrowId = null)
        }

        // Judged against the BOARD, not the shape - arrows escape off-screen
        // and the shape is only an art guide.
        val liveIds = currentState.arrows.filter { it.isActive }.map { it.id }.toSet()
        val canEscape = requireGraph(currentState).isFree(arrow.id, liveIds)

        if (canEscape) {
            handleValidMove(arrow)
        } else {
            handleInvalidMove(arrow)
        }
    }

    private fun handleValidMove(arrow: CurvedArrow) {
        val currentState = _gameState.value

        // Start escape animation
        _escapingArrows.value = _escapingArrows.value + (arrow.id to 0f)

        // Increment combo
        _combo.value = _combo.value + 1
        if (session == CurvedSession.DAILY) recordRun(_combo.value)

        // Sounds & haptics
        if (prefs.value.soundEnabled) feedback.playSuccessSound()
        if (prefs.value.hapticsEnabled) feedback.playSuccessHaptic()

//        analytics.log(Events.validMove(currentState.level, currentState.remainingCount - 1))

        undoHistory.remember(currentState.arrows)

        // Mark arrow as inactive immediately (so other taps see it as gone)
        val updatedArrows = currentState.arrows.map {
            if (it.id == arrow.id) it.withActive(false) else it
        }
        _gameState.value = checkSolvable(currentState.copy(arrows = updatedArrows))

        val arrowId = arrow.id

        // Animate escape - longer arrows take longer to pull through
        viewModelScope.launch {
            animateProgress(ArrowMotion.escapeDurationMs(arrow.length)) { progress ->
                _escapingArrows.value = _escapingArrows.value + (arrowId to progress)
            }

            // Animation complete
            _escapingArrows.value = _escapingArrows.value - arrowId

            // Check for level complete
            val state = _gameState.value
            if (state.activeArrows.isEmpty()) {
                _gameState.value = state.copy(status = GameStatus.LEVEL_COMPLETE)
                handleLevelComplete(state)
            }
        }
    }

    private fun handleInvalidMove(arrow: CurvedArrow) {
        val currentState = _gameState.value

        // Reset combo
        _combo.value = 0

        // Sounds & haptics
        if (prefs.value.soundEnabled) feedback.playErrorSound()
        if (prefs.value.hapticsEnabled) feedback.playErrorHaptic()

        // Lose a life
        val newLives = currentState.lives - 1
        val newStatus = if (newLives <= 0) GameStatus.OUT_OF_LIVES else GameStatus.PLAYING

        _gameState.value = currentState.copy(
            lives = newLives,
            mistakes = currentState.mistakes + 1,
            status = newStatus
        )

        // Lunge toward whatever is in the way, touch it, and pull back.
        val distance = ArrowMotion.bumpDistance(
            runwayAhead(arrow, currentState),
            arrow.strokeWidth
        )
        viewModelScope.launch {
            animateProgress(ArrowMotion.bumpDurationMs(distance)) { progress ->
                _bumpingArrows.value = _bumpingArrows.value + (arrow.id to ArrowMotion.Bump(distance, progress))
            }
            _bumpingArrows.value = _bumpingArrows.value - arrow.id

            if (newStatus == GameStatus.OUT_OF_LIVES) {
                val daily = dailyPuzzle.takeIf { session == CurvedSession.DAILY }
                if (daily != null) {
                    analytics.log(
                        Events.dailyChallengeFailed(
                            date = daily.date.toString(),
                            clearedPercentage = currentState.clearedPercentage
                        )
                    )
                }
                analytics.log(
                    Events.livesExhausted(
                        level = currentState.level,
                        clearedPercentage = currentState.clearedPercentage,
                        isOffline = NetworkUtils.isOffline(context)
                    )
                )
                analytics.log(Events.rewardedAdOffered(currentState.level, purpose = "continue"))
                reviewPrompter.onLevelFailed(prefs.value.review)
            }
        }
    }

    /** Free space between [arrow]'s tip and the first live arrow in its way. */
    private fun runwayAhead(arrow: CurvedArrow, state: CurvedGameState): Float {
        val liveIds = state.activeArrows.map { it.id }.toSet()
        val blockerIds = requireGraph(state).blockersOf(arrow.id).filter { it in liveIds }.toSet()
        val blockers = state.arrows.filter { it.id in blockerIds }
        return CurvedGameEngine.runwayBeforeContact(arrow, blockers, board())
    }

    /** True when [arrow] would escape if tapped right now. Drives the long-press preview. */
    fun isArrowFree(arrow: CurvedArrow): Boolean {
        val state = _gameState.value
        val liveIds = state.activeArrows.map { it.id }.toSet()
        return requireGraph(state).isFree(arrow.id, liveIds)
    }

    /** Where [arrow]'s straight exit ray leaves the board, for the preview line. */
    fun exitPointOf(arrow: CurvedArrow): Point2D = board().rayExit(arrow.endPoint, arrow.direction)

    /**
     * Drive [onFrame] from 0 to 1 over [durationMs] of real time.
     * Progress comes from the clock rather than counting frames, so a dropped
     * frame makes the animation skip ahead instead of running long.
     */
    private suspend fun animateProgress(durationMs: Long, onFrame: (Float) -> Unit) {
        val start = System.nanoTime()
        while (true) {
            val elapsedMs = (System.nanoTime() - start) / 1_000_000f
            val progress = (elapsedMs / durationMs).coerceIn(0f, 1f)
            onFrame(progress)
            if (progress >= 1f) return
            delay(ANIMATION_FRAME_MS)
        }
    }

    private fun handleLevelComplete(state: CurvedGameState) {
        val daily = dailyPuzzle.takeIf { session == CurvedSession.DAILY }
        val stars = Scoring.starsFor(state.mistakes)

        // One puzzle a day is a RULE, not a progress stat, so it is recorded
        // even in developer mode. Letting the dev flag skip this write left
        // the door open all afternoon: the same board could be solved, left
        // and re-entered forever, which is the one thing a daily must never
        // allow. Everything below this line is progress, and dev mode may
        // still skip all of it.
        if (daily != null) {
            stopShuffleClock()
            viewModelScope.launch { preferences.markDailyCleared(daily.date, stars) }
            analytics.log(
                Events.dailyChallengeCompleted(
                    date = daily.date.toString(),
                    mistakes = state.mistakes,
                    streak = prefs.value.daily.streak
                )
            )
        }

        // Developer mode never changes saved progress, stars or ad counters.
        if (prefs.value.devMode) return

        viewModelScope.launch {
            analytics.log(
                Events.levelCompleted(
                    level = state.level,
                    mistakes = state.mistakes,
                    continueUsed = continueUsedThisLevel,
                    mode = "curved",
                    isOffline = NetworkUtils.isOffline(context)
                )
            )

            // A daily owes the journey nothing: no level bump, no stars on
            // the map, no interstitial counter. Its own record was written
            // above, before dev mode had a chance to veto it.
            if (daily == null) {
                preferences.saveLevelStars(MODE, state.level, stars)
                // Clamped so the save file itself can never claim a level
                // past the authored catalogue - see [CurvedJourney.authoredLevelCount].
                // A player on the last authored level who presses on simply
                // stays recorded on it; [onNextLevel] is what decides what
                // that moment actually looks like on screen.
                preferences.setCurrentLevel(MODE, (state.level + 1).coerceAtMost(CurvedJourney.authoredLevelCount))
                preferences.incrementTotalLevelsCompleted(MODE)
                preferences.incrementLevelsSinceInterstitial()
                preferences.clearSavedGame()
            }

            // The streak is judged against the total that includes this very
            // win, which the flow above has not emitted yet - hence the +1.
            reviewPrompter.onLevelCleared(
                prefs.value.review,
                prefs.value.totalLevelsCompleted + 1
            )

            if (prefs.value.soundEnabled) feedback.playCompleteSound()
            if (prefs.value.hapticsEnabled) feedback.playSuccessHaptic()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Hints
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Takes back the last escape. Only the board rewinds: a life lost to a
     * blocked tap stays lost, so undo cannot be used to dodge a mistake.
     *
     * The run is broken too. Otherwise escaping the same arrow again would
     * count twice towards the best run.
     */
    fun undoMove() {
        val state = _gameState.value
        if (state.status != GameStatus.PLAYING) return
        // Never rewind under an arrow that is still on its way out. Checked
        // before popping, so a refused undo keeps its snapshot.
        if (_escapingArrows.value.isNotEmpty() || _bumpingArrows.value.isNotEmpty()) return

        val previous = undoHistory.pop() ?: return
        _combo.value = 0
        _gameState.value = state.copy(arrows = previous, hintArrowId = null)
    }

    /** Writes a best run only when this run has actually beaten the record. */
    private fun recordRun(run: Int) {
        if (run <= bestRunKnown) return
        bestRunKnown = run
        viewModelScope.launch { preferences.recordDailyRun(run) }
    }

    fun useHint() {
        if (_hintsRemaining.value <= 0) return
        val state = _gameState.value
        if (state.status != GameStatus.PLAYING) return

        // Prefer the arrow that frees the most neighbours, so the hint
        // actually teaches the solution instead of naming any legal move.
        val graph = requireGraph(state)
        val liveIds = state.activeArrows.map { it.id }.toSet()
        val free = state.activeArrows.filter { graph.isFree(it.id, liveIds) }
        val unblockCount = liveIds.flatMap { graph.blockersOf(it) }.groupingBy { it }.eachCount()
        val hintArrow = free.maxByOrNull { unblockCount[it.id] ?: 0 } ?: return

        _hintsRemaining.value = _hintsRemaining.value - 1
        _gameState.value = state.copy(hintArrowId = hintArrow.id)

        viewModelScope.launch {
            delay(2500)
            if (_gameState.value.hintArrowId == hintArrow.id) {
                _gameState.value = _gameState.value.copy(hintArrowId = null)
            }
        }
    }

    /**
     * Hints work like lives: run out, watch an ad, get the full allowance
     * back. One rewarded-ad pool covers both - a player who just watched one
     * for lives shouldn't be asked to sit through a second to keep hinting.
     */
    fun onWatchAdForHints(activity: Activity) {
        val currentLevel = _gameState.value.level
        analytics.log(Events.rewardedAdStarted(currentLevel, purpose = "hint"))

        adManager.showRewardedAd(
            activity = activity,
            onRewarded = {
                analytics.log(Events.rewardedAdCompleted(currentLevel, purpose = "hint", rewardAmount = HINTS_PER_LEVEL))
                _hintsRemaining.value = HINTS_PER_LEVEL
                adManager.loadRewardedAd(context)
            },
            onFailed = {
                analytics.log(Events.rewardedAdFailed(currentLevel, purpose = "hint"))
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Continue / Restart / Next Level
    // ─────────────────────────────────────────────────────────────────────

    fun onContinueWithAd(activity: Activity) {
        val currentLevel = _gameState.value.level
        analytics.log(Events.rewardedAdStarted(currentLevel, purpose = "continue"))

        adManager.showRewardedAd(
            activity = activity,
            onRewarded = {
                analytics.log(Events.rewardedAdCompleted(currentLevel, purpose = "continue", rewardAmount = DEFAULT_LIVES))
                continueUsedThisLevel = true
                _gameState.value = _gameState.value.copy(
                    lives = DEFAULT_LIVES,
                    status = GameStatus.PLAYING
                )
                adManager.loadRewardedAd(context)
            },
            onFailed = {
                analytics.log(Events.rewardedAdFailed(currentLevel, purpose = "continue"))
            }
        )
    }

    fun onRestartLevel() {
        val currentLevel = _gameState.value.level
        analytics.log(Events.levelRestarted(currentLevel))

        // Retrying a daily must stay a daily - restarting through the journey
        // path would quietly turn today's puzzle into a journey level.
        val daily = dailyPuzzle
        if (session == CurvedSession.DAILY && daily != null) startDaily(daily, dailyLayout)
        else startLevel(currentLevel)
    }

    /**
     * Deals the next level - unless the authored catalogue has already run
     * out, in which case there is nothing further to deal. See
     * [CurvedJourney.authoredLevelCount].
     *
     * A screen-level caller can offer something better for that moment (the
     * Journey Map's own "more is coming" card is the natural fit - see
     * `ComingSoonCard`); this guard exists underneath that so a missed or
     * future screen change can never generate or save a level past the
     * catalogue by calling straight through here.
     */
    fun onNextLevel() {
        val nextLevel = _gameState.value.level + 1
        if (nextLevel > CurvedJourney.authoredLevelCount) return
        startLevel(nextLevel)
    }

    /**
     * Way out of a board that cannot be finished. It counts as passed - the
     * player did nothing wrong, so they must not lose their place - except in
     * developer mode, which never touches saved progress.
     *
     * Clamped at [CurvedJourney.authoredLevelCount] for the same reason
     * [onNextLevel] is. If the stuck board *is* the last authored level,
     * this re-deals that same level rather than reaching past it - a level
     * stuck at the very end of the catalogue is a generator bug worth fixing
     * on its own merits, not something this guard should paper over by
     * handing out unauthored content instead.
     */
    fun onSkipStuckLevel() {
        val nextLevel = (_gameState.value.level + 1).coerceAtMost(CurvedJourney.authoredLevelCount)
        if (!prefs.value.devMode) {
            viewModelScope.launch { preferences.setCurrentLevel(MODE, nextLevel) }
        }
        startLevel(nextLevel)
    }

    /**
     * Re-verifies that the arrows still on the board can all be cleared.
     *
     * Removing an arrow can only unblock others, so a solvable board stays
     * solvable under any legal move and this should never fire mid-game. It is
     * a safety net against a bad level (or a future engine bug) leaving the
     * player in a dead end with no explanation. Cheap: one pass over the
     * cached blocking graph.
     */
    private fun checkSolvable(state: CurvedGameState): CurvedGameState {
        val live = state.activeArrows.map { it.id }.toSet()
        val stuck = live.isNotEmpty() && !CurvedGameEngine.isSolvable(requireGraph(state), live)
        if (stuck) android.util.Log.e("CurvedGame", "Level ${state.level} reached an unsolvable state")
        return if (stuck == state.isStuck) state else state.copy(isStuck = stuck)
    }

    /**
     * Developer mode: step to the previous (-1) or next (+1) level without
     * touching progress.
     *
     * Clamped to [CurvedJourney.authoredLevelCount] - the same ceiling
     * [onNextLevel] and real progression obey. This used to be unbounded on
     * the reasoning that curved content never ends (the generator still
     * doesn't, see `ShapeSynthesis`), but a dev tool exists to walk the
     * app's *shipped* roadblocks, and nothing past the authored catalogue is
     * one. A visit, not a move either way: preferences stay where the
     * player left them, same as the classic twin.
     */
    fun stepLevel(delta: Int) {
        val target = (_gameState.value.level + delta).coerceIn(1, CurvedJourney.authoredLevelCount)
        startLevel(target)
    }

    fun showInterstitialIfNeeded(activity: Activity, onComplete: () -> Unit) {
        viewModelScope.launch {
            if (prefs.value.levelsSinceInterstitial >= INTERSTITIAL_FREQUENCY &&
                !continueUsedThisLevel &&
                adManager.isInterstitialAdReady()
            ) {
                analytics.log(Events.interstitialShown(_gameState.value.level))
                preferences.resetLevelsSinceInterstitial()
                adManager.showInterstitialAd(activity, onComplete)
            } else {
                onComplete()
            }
        }
    }

    fun isRewardedAdAvailable(): Boolean = adManager.isRewardedAdReady()

    fun saveGameState() {
        // For now, curved mode doesn't persist mid-level state
        // Could be added later if needed
    }

    // ─────────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────
    // Settings
    // ─────────────────────────────────────────────────────────────────────

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setSoundEnabled(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setHapticsEnabled(enabled) }
    }
}

class CurvedGameViewModelFactory(
    private val preferences: GamePreferences,
    private val adManager: AdManager,
    private val analytics: AnalyticsTracker,
    private val feedback: GameFeedback,
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CurvedGameViewModel(preferences, adManager, analytics, feedback, context) as T
    }
}
