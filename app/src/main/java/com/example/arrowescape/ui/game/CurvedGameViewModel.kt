package com.example.arrowescape.ui.game

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.arrowescape.ads.AdManager
import com.example.arrowescape.analytics.AnalyticsTracker
import com.example.arrowescape.data.GameMode
import com.example.arrowescape.data.GamePreferences
import com.example.arrowescape.game.curve.*
import com.example.arrowescape.game.model.GameStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val startTimeMillis: Long = System.currentTimeMillis(),
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
        private const val BOARD_WIDTH = 400f
        private const val BOARD_HEIGHT = 600f
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

    private val _hintsRemaining = MutableStateFlow(2)
    val hintsRemaining: StateFlow<Int> = _hintsRemaining.asStateFlow()

    private val _combo = MutableStateFlow(0)
    val combo: StateFlow<Int> = _combo.asStateFlow()

    val prefs = preferences.prefsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        GamePreferences.Prefs()
    )

    // Board dimensions for rendering
    private val _boardWidth = MutableStateFlow(BOARD_WIDTH)
    val boardWidth: StateFlow<Float> = _boardWidth.asStateFlow()

    private val _boardHeight = MutableStateFlow(BOARD_HEIGHT)
    val boardHeight: StateFlow<Float> = _boardHeight.asStateFlow()

    private var continueUsedThisLevel = false
    private var pendingLevel: Int? = null

    /**
     * Precomputed blocking graph for the level. Built once - arrow geometry
     * never changes during play, only which arrows are still alive, so every
     * tap is a set lookup rather than a geometric sweep.
     */
    private var blockingGraph: CurvedGameEngine.BlockingGraph? = null

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
        viewModelScope.launch {
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
                startTimeMillis = System.currentTimeMillis(),
                colorPalette = curvedLevel.colorPalette,
                // The generator promises a solvable level; verify rather than trust.
                isStuck = !CurvedGameEngine.isSolvable(graph, curvedLevel.arrows.map { it.id }.toSet())
            )

            if (_gameState.value.isStuck) {
                android.util.Log.e("CurvedGame", "Level $level was generated unsolvable")
            }

            _escapingArrows.value = emptyMap()
            _bumpingArrows.value = emptyMap()
            _hintsRemaining.value = 2
            _combo.value = 0
            continueUsedThisLevel = false
            blockingGraph = graph
            // Developer sessions are for looking at levels, not for metrics.
            if (!prefs.value.devMode) {
                analytics.trackLevelStarted(level, curvedLevel.arrows.size)
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

        analytics.trackValidMove(currentState.level, currentState.remainingCount - 1)

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

        analytics.trackInvalidMove(currentState.level, newLives)

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
                analytics.trackLivesExhausted(currentState.level, currentState.clearedPercentage)
                analytics.trackRewardedAdOffered(currentState.level)
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
            val timeSeconds = ((System.currentTimeMillis() - state.startTimeMillis) / 1000).toInt()
            analytics.trackLevelCompleted(
                state.level,
                timeSeconds,
                state.mistakes,
                continueUsedThisLevel
            )

            // Calculate stars
            val stars = when {
                state.mistakes == 0 -> 3
                state.mistakes <= 2 -> 2
                else -> 1
            }
            preferences.saveLevelStars(MODE, state.level, stars)
            preferences.setCurrentLevel(MODE, state.level + 1)
            preferences.incrementTotalLevelsCompleted(MODE)
            preferences.incrementLevelsSinceInterstitial()
            preferences.clearSavedGame()

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

    // ─────────────────────────────────────────────────────────────────────
    // Continue / Restart / Next Level
    // ─────────────────────────────────────────────────────────────────────

    fun onContinueWithAd(activity: Activity) {
        val currentLevel = _gameState.value.level
        analytics.trackRewardedAdStarted(currentLevel)

        adManager.showRewardedAd(
            activity = activity,
            onRewarded = {
                analytics.trackRewardedAdCompleted(currentLevel)
                continueUsedThisLevel = true
                _gameState.value = _gameState.value.copy(
                    lives = DEFAULT_LIVES,
                    status = GameStatus.PLAYING
                )
                adManager.loadRewardedAd(context)
            },
            onFailed = {
                analytics.trackRewardedAdFailed(currentLevel)
            }
        )
    }

    fun onRestartLevel() {
        val currentLevel = _gameState.value.level
        analytics.trackLevelRestarted(currentLevel)
        startLevel(currentLevel)
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
    fun devStepLevel(delta: Int) {
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
                analytics.trackInterstitialShown(_gameState.value.level)
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
