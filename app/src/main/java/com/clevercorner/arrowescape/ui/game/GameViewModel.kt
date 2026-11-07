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
import com.clevercorner.arrowescape.game.engine.CollisionChecker
import com.clevercorner.arrowescape.game.engine.GameEngine
import com.clevercorner.arrowescape.game.engine.GameSolver
import com.clevercorner.arrowescape.game.level.DifficultyConfig
import com.clevercorner.arrowescape.game.level.GenerationDispatchers
import com.clevercorner.arrowescape.game.level.GenerationGuard
import com.clevercorner.arrowescape.game.level.LevelGenerator
import com.clevercorner.arrowescape.game.model.Arrow
import com.clevercorner.arrowescape.game.model.Cell
import com.clevercorner.arrowescape.game.model.GameState
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

class GameViewModel(
    private val preferences: GamePreferences,
    private val adManager: AdManager,
    private val analytics: AnalyticsTracker,
    /** App-wide, and owned by the app - this ViewModel must not release it. */
    private val feedback: GameFeedback,
    private val context: Context
) : ViewModel() {

    companion object {
        /** This ViewModel owns the classic grid mode's progress, and only that. */
        private val MODE = GameMode.CLASSIC

        private const val INTERSTITIAL_FREQUENCY = 4 // Show ad every N completed levels
        private const val ESCAPE_ANIMATION_DURATION_MS = 530L
        private const val SHAKE_ANIMATION_DURATION_MS = 300L
        private const val HINTS_PER_LEVEL = 2

        /**
         * How long speculative neighbour generation waits before starting,
         * so it never competes with the board the player is waiting on.
         */
        private const val PREFETCH_GRACE_MS = 600L
    }

    private val gameEngine = GameEngine()

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    /** Arrow id -> the exact route it is sliding along (deflectors included). */
    private val _escapingArrows = MutableStateFlow<Map<Int, List<Cell>>>(emptyMap())
    val escapingArrows: StateFlow<Map<Int, List<Cell>>> = _escapingArrows.asStateFlow()

    private val _shakingArrows = MutableStateFlow<Set<Int>>(emptySet())
    val shakingArrows: StateFlow<Set<Int>> = _shakingArrows.asStateFlow()

    private val _levelShape = MutableStateFlow(com.clevercorner.arrowescape.game.level.LevelShape.SQUARE)
    val levelShape: StateFlow<com.clevercorner.arrowescape.game.level.LevelShape> = _levelShape.asStateFlow()

    private val _shapeName = MutableStateFlow("Square")
    val shapeName: StateFlow<String> = _shapeName.asStateFlow()

    /** Generator read-out for the dev bar: how chained this board came out. */
    private val _levelMetrics = MutableStateFlow(GameSolver.Solution(emptyList(), 0f, 0f, 0))
    val levelMetrics: StateFlow<GameSolver.Solution> = _levelMetrics.asStateFlow()

    private val _hintsRemaining = MutableStateFlow(HINTS_PER_LEVEL)
    val hintsRemaining: StateFlow<Int> = _hintsRemaining.asStateFlow()

    private val _combo = MutableStateFlow(0)
    val combo: StateFlow<Int> = _combo.asStateFlow()

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

    private var continueUsedThisLevel = false

    /**
     * The level currently on screen or on its way there.
     *
     * Null means "no board has ever been dealt in this ViewModel", which is
     * the one question [resumeLevel] needs answered.
     */
    private var loadedLevel: Int? = null

    // Haptics & sound - one app-wide instance, see GameFeedback.

    /** Speculative build of the neighbouring levels. See [prefetchNeighbours]. */
    private var prefetchJob: Job? = null

    /**
     * Deals [level] as a fresh board. Tapping Play means Play, so this
     * always re-deals - asking for the level already on screen is a restart,
     * not a no-op.
     */
    fun startLevel(level: Int) {
        // Whatever we were speculatively building, the player has now told us
        // what they actually want - stop competing with it for CPU.
        prefetchJob?.cancel()
        loadedLevel = level

        // Stamped before the first suspension point, so from this instant
        // the header names the level being built rather than whatever was
        // on screen - or, on a cold start, the default of 1. Everything in
        // a header is a pure function of the level number, so none of it
        // has to wait for a board. See [LevelGenerator.headerFor].
        val header = LevelGenerator.headerFor(level)
        _shapeName.value = header.shapeName
        _gameState.value = GameState(level = level, difficultyLabel = header.difficultyLabel)
        _isGenerating.value = true

        viewModelScope.launch {
            // Generate off the main thread to keep the UI smooth.
            val generated = withContext(Dispatchers.Default) {
                val guard = guardFor(coroutineContext.job)
                if (level == 1) {
                    LevelGenerator.generateTutorialLevel()
                } else {
                    LevelGenerator.generateLevel(level, guard)
                }
            }

            _levelShape.value = generated.shape
            _shapeName.value = generated.shapeName
            _levelMetrics.value = generated.solution
            _gameState.value = gameEngine.createLevel(
                level,
                generated.arrows,
                generated.board,
                generated.difficultyLabel
            )
            _escapingArrows.value = emptyMap()
            _shakingArrows.value = emptySet()
            _hintsRemaining.value = HINTS_PER_LEVEL
            _combo.value = 0
            continueUsedThisLevel = false
            _isGenerating.value = false
            prefetchNeighbours(level)

            analytics.log(
                Events.levelStarted(
                    level = level,
                    arrowCount = generated.arrows.size,
                    mode = "classic",
                    isOffline = NetworkUtils.isOffline(context)
                )
            )
        }
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
        if (loadedLevel == null) startLevel(level)
    }

    /**
     * Quietly builds the levels either side of [level] while the player is
     * busy with this one, so stepping between them is instant.
     *
     * The generator caches what it builds, so this only has to warm that
     * cache - there is no second copy of the result to keep in sync here.
     *
     * Two things keep this bet from ever costing the player: it runs on one
     * low-priority thread ([GenerationDispatchers.prefetch]) rather than
     * across every core, and it hands the generator a [GenerationGuard] so
     * cancelling the job actually stops the arithmetic - `cancel()` alone
     * does nothing to a long non-suspending loop.
     */
    private fun prefetchNeighbours(level: Int) {
        prefetchJob = viewModelScope.launch(GenerationDispatchers.prefetch) {
            delay(PREFETCH_GRACE_MS)
            val guard = guardFor(coroutineContext.job)
            for (neighbour in listOf(level + 1, level - 1)) {
                if (neighbour < 2) continue
                ensureActive()
                LevelGenerator.generateLevel(neighbour, guard)
            }
        }
    }

    /** Ties a generator's "is anybody still waiting?" to the job doing the waiting. */
    private fun guardFor(job: Job) = GenerationGuard { !job.isActive }

    /**
     * Developer mode: step to the previous (-1) or next (+1) level without
     * touching progress.
     *
     * Clamped to [DifficultyConfig.TOTAL_LEVELS] - that bound exists so the
     * dev bar can hide Next at the edge; stepping past it here would silently
     * reopen the dead button.
     */
    /**
     * Moves [delta] levels and loads that board.
     *
     * Deliberately does NOT write the level back to preferences: stepping
     * back to replay level 3 should not make Home offer level 3 forever
     * after. Navigation is a visit, not a move.
     */
    fun stepLevel(delta: Int) {
        val target = (_gameState.value.level + delta)
            .coerceIn(1, DifficultyConfig.TOTAL_LEVELS)
        startLevel(target)
    }

    /**
     * Reveals a hint by highlighting an escapable arrow.
     * Costs one hint from the available pool.
     */
    fun useHint() {
        if (_hintsRemaining.value <= 0) return
        val state = _gameState.value
        if (state.status != GameStatus.PLAYING) return

        val hintId = gameEngine.findHintArrow(state) ?: return
        _hintsRemaining.value = _hintsRemaining.value - 1
        _gameState.value = state.copy(hintArrowId = hintId)

        // Clear the hint highlight after a few seconds
        viewModelScope.launch {
            delay(2500)
            if (_gameState.value.hintArrowId == hintId) {
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

    // Debounce guard to prevent duplicate tap processing
    private var lastTapTimeMs = 0L
    private var lastTappedArrowId = -1
    private val TAP_DEBOUNCE_MS = 100L

    fun onArrowTap(arrow: Arrow) {
        val now = System.currentTimeMillis()
        
        // Debounce: ignore rapid duplicate taps on same arrow
        if (arrow.id == lastTappedArrowId && (now - lastTapTimeMs) < TAP_DEBOUNCE_MS) {
            return
        }
        lastTapTimeMs = now
        lastTappedArrowId = arrow.id
        
        val currentState = _gameState.value
        if (currentState.status != GameStatus.PLAYING) return

        // Prevent double-tapping an arrow that's already animating
        if (_escapingArrows.value.containsKey(arrow.id) || _shakingArrows.value.contains(arrow.id)) {
            return
        }

        // Clear any active hint on interaction
        if (currentState.hintArrowId != null) {
            _gameState.value = currentState.copy(hintArrowId = null)
        }

        val result = gameEngine.tapArrow(_gameState.value, arrow.id)

        val escape = result.escape
        if (escape != null) {
            handleValidMove(escape, result.state)
        } else {
            handleInvalidMove(arrow, result.state)
        }
    }

    private fun handleValidMove(escape: CollisionChecker.Escape, newState: GameState) {
        // Start the escape animation for everything that leaves - a
        // colour-locked pair flies out together.
        _escapingArrows.value = _escapingArrows.value + escape.routes

        // Combo: increment on each consecutive valid move
        _combo.value = _combo.value + 1

        // Play sound and haptic
        if (prefs.value.soundEnabled) feedback.playSuccessSound()
        if (prefs.value.hapticsEnabled) feedback.playSuccessHaptic()

//        analytics.log(Events.validMove(newState.level, newState.remainingCount))

        // Commit the arrow list RIGHT NOW so other arrows immediately know
        // this one is gone (fixes a bug where a blocked arrow tapped during
        // the escape animation would be wrongly rejected as a mistake).
        // We hold back a LEVEL_COMPLETE status flip until the animation
        // finishes so the completion overlay doesn't pop in over a
        // still-animating arrow. (Status here is guaranteed PLAYING - this
        // fn only runs when the tap started from PLAYING.)
        _gameState.value = newState.copy(status = _gameState.value.status)

        // Capture only the final status we might need to apply. Do NOT
        // reassign the full newState later - that would overwrite any
        // arrows tapped while this animation was in flight.
        val finalStatus = newState.status
        val departed = escape.arrowIds

        viewModelScope.launch {
            delay(ESCAPE_ANIMATION_DURATION_MS)
            _escapingArrows.value = _escapingArrows.value - departed

            // Only update status if this was the last arrow (level complete).
            // Read CURRENT state, don't clobber with stale captured state.
            if (finalStatus == GameStatus.LEVEL_COMPLETE) {
                _gameState.value = _gameState.value.copy(status = GameStatus.LEVEL_COMPLETE)
                handleLevelComplete(_gameState.value)
            }
        }
    }

    private fun handleInvalidMove(arrow: Arrow, newState: GameState) {
        // Start shake animation
        _shakingArrows.value = _shakingArrows.value + arrow.id

        // Reset combo on a mistake
        _combo.value = 0

        // Play error sound and haptic
        if (prefs.value.soundEnabled) feedback.playErrorSound()
        if (prefs.value.hapticsEnabled) feedback.playErrorHaptic()

        _gameState.value = newState

        viewModelScope.launch {
            delay(SHAKE_ANIMATION_DURATION_MS)
            _shakingArrows.value = _shakingArrows.value - arrow.id

            // Check for out of lives
            if (newState.status == GameStatus.OUT_OF_LIVES) {
                analytics.log(
                    Events.livesExhausted(
                        level = newState.level,
                        clearedPercentage = newState.clearedPercentage,
                        isOffline = NetworkUtils.isOffline(context)
                    )
                )
                analytics.log(Events.rewardedAdOffered(newState.level, purpose = "continue"))
                reviewPrompter.onLevelFailed(prefs.value.review)
            }
        }
    }

    private fun handleLevelComplete(state: GameState) {
        viewModelScope.launch {
            analytics.log(
                Events.levelCompleted(
                    level = state.level,
                    mistakes = state.mistakes,
                    continueUsed = continueUsedThisLevel,
                    mode = "classic",
                    isOffline = NetworkUtils.isOffline(context)
                )
            )

            val stars = Scoring.starsFor(state.mistakes)
            preferences.saveLevelStars(MODE, state.level, stars)

            preferences.setCurrentLevel(MODE, state.level + 1)
            preferences.incrementTotalLevelsCompleted(MODE)
            preferences.incrementLevelsSinceInterstitial()
            preferences.clearSavedGame()

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

    fun onContinueWithAd(activity: Activity) {
        val currentLevel = _gameState.value.level
        analytics.log(Events.rewardedAdStarted(currentLevel, purpose = "continue"))

        adManager.showRewardedAd(
            activity = activity,
            onRewarded = {
                analytics.log(Events.rewardedAdCompleted(currentLevel, purpose = "continue", rewardAmount = 3))
                continueUsedThisLevel = true
                _gameState.value = gameEngine.restoreLives(_gameState.value)
                adManager.loadRewardedAd(context) // Preload next
            },
            onFailed = {
                analytics.log(Events.rewardedAdFailed(currentLevel, purpose = "continue"))
            }
        )
    }

    fun onRestartLevel() {
        val currentLevel = _gameState.value.level
        analytics.log(Events.levelRestarted(currentLevel))
        startLevel(currentLevel)
    }

    fun onNextLevel() {
        viewModelScope.launch {
            val nextLevel = _gameState.value.level + 1

            // Check if we should show interstitial
            if (prefs.value.levelsSinceInterstitial >= INTERSTITIAL_FREQUENCY) {
                // Don't show interstitial right after a rewarded ad
                if (!continueUsedThisLevel && adManager.isInterstitialAdReady()) {
                    analytics.log(Events.interstitialShown(nextLevel))
                    // We'd need Activity reference here - for now just reset counter
                    preferences.resetLevelsSinceInterstitial()
                }
            }

            startLevel(nextLevel)
        }
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
        viewModelScope.launch {
            val state = _gameState.value
            if (state.status == GameStatus.PLAYING && state.remainingCount > 0) {
                val activeIds = state.activeArrows.map { it.id }.toSet()
                preferences.saveGameState(state.level, state.lives, activeIds)
            }
        }
    }

    // Settings
    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setSoundEnabled(enabled)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setHapticsEnabled(enabled)
        }
    }
}

class GameViewModelFactory(
    private val preferences: GamePreferences,
    private val adManager: AdManager,
    private val analytics: AnalyticsTracker,
    private val feedback: GameFeedback,
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GameViewModel(preferences, adManager, analytics, feedback, context) as T
    }
}
