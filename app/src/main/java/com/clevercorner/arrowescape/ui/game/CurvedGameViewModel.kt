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
import com.clevercorner.arrowescape.game.curve.*
import com.clevercorner.arrowescape.game.daily.DailyPuzzle
import com.clevercorner.arrowescape.game.daily.DailyShuffle
import com.clevercorner.arrowescape.game.model.GameStatus
import com.clevercorner.arrowescape.review.ReviewPrompter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
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
    val activeArrows: List<CurvedArrow> get() = arrows.filter { it.isActive }
    val remainingCount: Int get() = activeArrows.size
    val clearedPercentage: Int get() {
        val total = arrows.size
        if (total == 0) return 0
        return ((total - remainingCount) * 100) / total
    }
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
    private val context: Context
) : ViewModel() {

    companion object {
        /** This ViewModel owns the curved mode's progress, and only that. */
        private val MODE = GameMode.CURVED

        private const val INTERSTITIAL_FREQUENCY = 4
        private const val ANIMATION_FRAME_MS = 16L
        private const val DEFAULT_LIVES = 3
        private const val HINTS_PER_LEVEL = 2
        private const val BOARD_WIDTH = 400f
        private const val BOARD_HEIGHT = 600f

        /** Half a shuffle: fold away, then unfold into the new arrangement. */
        private const val SHUFFLE_FOLD_MS = 420L
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

    /** True while the next board is being built. The screen shows a spinner. */
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    val prefs = preferences.prefsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        GamePreferences.Prefs()
    )

    /** Decides when a happy player gets asked for a store review. */
    val reviewPrompter = ReviewPrompter(preferences, analytics)

    // Board dimensions for rendering
    private val _boardWidth = MutableStateFlow(BOARD_WIDTH)
    val boardWidth: StateFlow<Float> = _boardWidth.asStateFlow()

    private val _boardHeight = MutableStateFlow(BOARD_HEIGHT)
    val boardHeight: StateFlow<Float> = _boardHeight.asStateFlow()

    private var continueUsedThisLevel = false
    private var pendingLevel: Int? = null

    /** Set by [prepareLevel]; cleared by the [startLevel] that consumes it. */
    private var preparedLevel: Int? = null

    private var session = CurvedSession.JOURNEY

    /** Non-null only while a daily board is being played. */
    private var dailyPuzzle: DailyPuzzle? = null

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

    private fun board() = CurvedGameEngine.Board(BOARD_WIDTH, BOARD_HEIGHT)

    // ─────────────────────────────────────────────────────────────────────
    // Haptics & Sound
    // ─────────────────────────────────────────────────────────────────────

    private val feedback = GameFeedback(context)

    init {
        preloadAds()
        // Every theme has its own voice; keep the board speaking the right one.
        feedback.followTheme(prefs, viewModelScope)
    }

    private fun preloadAds() {
        adManager.loadRewardedAd(context)
        adManager.loadInterstitialAd(context)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Level Navigation
    // ─────────────────────────────────────────────────────────────────────

    fun setPendingLevel(level: Int) {
        pendingLevel = level
        viewModelScope.launch { preferences.setCurrentLevel(MODE, level) }
    }

    fun consumePendingLevel(): Int? = pendingLevel.also { pendingLevel = null }

    fun startLevel(level: Int) {
        val alreadyPrepared = preparedLevel == level
        preparedLevel = null
        if (!alreadyPrepared) beginJourneyLevel(level)
    }

    /**
     * Starts building [level] before its screen exists. The game screen is
     * opened straight after, and [startLevel] then finds the board already
     * on its way rather than starting it over.
     *
     * The player already chose this level on the previous screen, so there
     * is no reason to wait for the game screen to ask for it.
     */
    fun prepareLevel(level: Int) {
        preparedLevel = level
        beginJourneyLevel(level)
    }

    private fun beginJourneyLevel(level: Int) {
        session = CurvedSession.JOURNEY
        dailyPuzzle = null
        stopShuffleClock()
        loadBoard(level)
    }

    /**
     * Plays today's (or any day's) puzzle. Same engine, same board, different
     * ending: a daily marks a date solved instead of advancing the journey.
     *
     * The daily also gets the shuffle clock - see [runShuffleClock].
     */
    fun startDaily(puzzle: DailyPuzzle) {
        session = CurvedSession.DAILY
        dailyPuzzle = puzzle
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

        val seed = (dailyPuzzle?.stamp ?: 0).toLong() * 31 + _shuffleCount.value
        val reshuffled = withContext(Dispatchers.Default) {
            CurvedLevelGenerator.reshuffle(survivors, board(), Random(seed))
        } ?: return false

        val byId = reshuffled.associateBy { it.id }
        val arrows = state.arrows.map { byId[it.id] ?: it }

        playShuffle(arrows)
        _shuffleCount.value++
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
    private suspend fun playShuffle(arrows: List<CurvedArrow>) {
        animateProgress(SHUFFLE_FOLD_MS) { t -> _shufflePhase.value = 1f - smoothStep(t) }

        // The blocking graph is geometry-derived and the geometry just
        // changed; a stale graph would happily let blocked arrows fly.
        blockingGraph = CurvedGameEngine.BlockingGraph(arrows, board())
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

        // Set before the first suspension point, so from this instant the
        // header names the requested level and the board area shows only the
        // spinner - never the previous level's arrows or name.
        _gameState.value = headerOf(level)
        _isGenerating.value = true

        boardJob = viewModelScope.launch {
            // Generate the level - and its blocking graph - off the main thread.
            // Dense levels have hundreds of arrows; building the graph lazily on
            // the first tap would stall the UI right when the player touches it.
            val (curvedLevel, graph) = withContext(Dispatchers.Default) {
                val generated = CurvedLevelGenerator.generateLevel(level, BOARD_WIDTH, BOARD_HEIGHT)
                generated to CurvedGameEngine.BlockingGraph(generated.arrows, board())
            }

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
                // The generator promises a solvable level; verify rather than trust.
                isStuck = !CurvedGameEngine.isSolvable(graph, curvedLevel.arrows.map { it.id }.toSet())
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
                analytics.log(Events.levelStarted(level, curvedLevel.arrows.size))
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
    private fun headerOf(level: Int) = CurvedGameState(
        level = level,
        shapeName = CurvedJourney.designFor(level).name,
        difficulty = DifficultyTuning.tierOf(level)
    )

    private fun prefetchNeighbours(level: Int) {
        prefetchJob = viewModelScope.launch(Dispatchers.Default) {
            for (neighbour in listOf(level + 1, level - 1)) {
                if (neighbour < 1) continue
                ensureActive()
                CurvedLevelGenerator.generateLevel(neighbour, BOARD_WIDTH, BOARD_HEIGHT)
            }
        }
    }

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

        // Sounds & haptics
        if (prefs.value.soundEnabled) feedback.playSuccessSound()
        if (prefs.value.hapticsEnabled) feedback.playSuccessHaptic()

//        analytics.log(Events.validMove(currentState.level, currentState.remainingCount - 1))

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

        analytics.log(Events.invalidMove(currentState.level, newLives))

        // Lunge toward whatever is in the way, touch it, and pull back.
        val distance = ArrowMotion.bumpDistance(
            runwayAhead(arrow, currentState),
            arrow.strokeWidth,
            arrow.length
        )
        viewModelScope.launch {
            animateProgress(ArrowMotion.bumpDurationMs(distance)) { progress ->
                _bumpingArrows.value = _bumpingArrows.value + (arrow.id to ArrowMotion.Bump(distance, progress))
            }
            _bumpingArrows.value = _bumpingArrows.value - arrow.id

            if (newStatus == GameStatus.OUT_OF_LIVES) {
                analytics.log(Events.livesExhausted(currentState.level, currentState.clearedPercentage))
                analytics.log(Events.rewardedAdOffered(currentState.level))
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
        // Developer mode never changes saved progress, stars or ad counters.
        if (prefs.value.devMode) return

        viewModelScope.launch {
            analytics.log(Events.levelCompleted(
                state.level,
                state.mistakes,
                continueUsedThisLevel
            ))

            // Calculate stars
            val stars = when {
                state.mistakes == 0 -> 3
                state.mistakes <= 2 -> 2
                else -> 1
            }
            val daily = dailyPuzzle
            if (session == CurvedSession.DAILY && daily != null) {
                // A daily owes the journey nothing: no stars, no level bump,
                // no interstitial counter. One date goes from open to solved.
                stopShuffleClock()
                preferences.markDailyCleared(daily.date)
            } else {
                preferences.saveLevelStars(MODE, state.level, stars)
                preferences.setCurrentLevel(MODE, state.level + 1)
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
        analytics.log(Events.rewardedAdStarted(currentLevel))

        adManager.showRewardedAd(
            activity = activity,
            onRewarded = {
                analytics.log(Events.rewardedAdCompleted(currentLevel))
                _hintsRemaining.value = HINTS_PER_LEVEL
                adManager.loadRewardedAd(context)
            },
            onFailed = {
                analytics.log(Events.rewardedAdFailed(currentLevel))
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Continue / Restart / Next Level
    // ─────────────────────────────────────────────────────────────────────

    fun onContinueWithAd(activity: Activity) {
        val currentLevel = _gameState.value.level
        analytics.log(Events.rewardedAdStarted(currentLevel))

        adManager.showRewardedAd(
            activity = activity,
            onRewarded = {
                analytics.log(Events.rewardedAdCompleted(currentLevel))
                continueUsedThisLevel = true
                _gameState.value = _gameState.value.copy(
                    lives = DEFAULT_LIVES,
                    status = GameStatus.PLAYING
                )
                adManager.loadRewardedAd(context)
            },
            onFailed = {
                analytics.log(Events.rewardedAdFailed(currentLevel))
            }
        )
    }

    fun onRestartLevel() {
        val currentLevel = _gameState.value.level
        analytics.log(Events.levelRestarted(currentLevel))

        // Retrying a daily must stay a daily - restarting through the journey
        // path would quietly turn today's puzzle into a journey level.
        val daily = dailyPuzzle
        if (session == CurvedSession.DAILY && daily != null) startDaily(daily)
        else startLevel(currentLevel)
    }

    fun onNextLevel() {
        val nextLevel = _gameState.value.level + 1
        startLevel(nextLevel)
    }

    /**
     * Way out of a board that cannot be finished. It counts as passed - the
     * player did nothing wrong, so they must not lose their place - except in
     * developer mode, which never touches saved progress.
     */
    fun onSkipStuckLevel() {
        val nextLevel = _gameState.value.level + 1
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
     * Clamped to [CurvedJourney.totalLevels] - that bound exists so the dev
     * bar can hide Next at the edge; stepping past it here would silently
     * reopen the dead button.
     */
    /**
     * Moves [delta] levels and loads that board. See the classic twin: a
     * visit, not a move, so preferences stay where the player left them.
     */
    fun stepLevel(delta: Int) {
        val target = (_gameState.value.level + delta)
            .coerceIn(1, CurvedJourney.totalLevels)
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

    override fun onCleared() {
        super.onCleared()
        feedback.release()
    }

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
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CurvedGameViewModel(preferences, adManager, analytics, context) as T
    }
}
