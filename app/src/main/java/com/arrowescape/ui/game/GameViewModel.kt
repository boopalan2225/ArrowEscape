package com.arrowescape.ui.game

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arrowescape.ads.AdManager
import com.arrowescape.analytics.AnalyticsTracker
import com.arrowescape.analytics.Events
import com.arrowescape.data.GameMode
import com.arrowescape.data.GamePreferences
import com.arrowescape.game.engine.GameEngine
import com.arrowescape.game.level.DifficultyConfig
import com.arrowescape.game.level.LevelGenerator
import com.arrowescape.game.model.Arrow
import com.arrowescape.game.model.Direction
import com.arrowescape.game.model.GameState
import com.arrowescape.game.model.GameStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameViewModel(
    private val preferences: GamePreferences,
    private val adManager: AdManager,
    private val analytics: AnalyticsTracker,
    private val context: Context
) : ViewModel() {

    companion object {
        /** This ViewModel owns the classic grid mode's progress, and only that. */
        private val MODE = GameMode.CLASSIC

        private const val INTERSTITIAL_FREQUENCY = 4 // Show ad every N completed levels
        private const val ESCAPE_ANIMATION_DURATION_MS = 530L
        private const val SHAKE_ANIMATION_DURATION_MS = 300L
        private const val HINTS_PER_LEVEL = 2
    }

    private val gameEngine = GameEngine()

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _escapingArrows = MutableStateFlow<Map<Int, Direction>>(emptyMap())
    val escapingArrows: StateFlow<Map<Int, Direction>> = _escapingArrows.asStateFlow()

    private val _shakingArrows = MutableStateFlow<Set<Int>>(emptySet())
    val shakingArrows: StateFlow<Set<Int>> = _shakingArrows.asStateFlow()

    private val _gridRows = MutableStateFlow(GameEngine.DEFAULT_GRID_ROWS)
    val gridRows: StateFlow<Int> = _gridRows.asStateFlow()

    private val _gridCols = MutableStateFlow(GameEngine.DEFAULT_GRID_COLS)
    val gridCols: StateFlow<Int> = _gridCols.asStateFlow()

    private val _levelShape = MutableStateFlow(com.arrowescape.game.level.LevelShape.SQUARE)
    val levelShape: StateFlow<com.arrowescape.game.level.LevelShape> = _levelShape.asStateFlow()

    private val _shapeName = MutableStateFlow("Square")
    val shapeName: StateFlow<String> = _shapeName.asStateFlow()

    private val _hintsRemaining = MutableStateFlow(HINTS_PER_LEVEL)
    val hintsRemaining: StateFlow<Int> = _hintsRemaining.asStateFlow()

    private val _combo = MutableStateFlow(0)
    val combo: StateFlow<Int> = _combo.asStateFlow()

    val prefs = preferences.prefsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        GamePreferences.Prefs()
    )

    private var currentLevelArrows: List<Arrow> = emptyList()
    private var continueUsedThisLevel = false
    private var pendingLevel: Int? = null

    /**
     * Requests that the game start at a specific level (e.g. from the
     * milestone journey screen). Persists it as the current level.
     */
    fun setPendingLevel(level: Int) {
        pendingLevel = level
        viewModelScope.launch { preferences.setCurrentLevel(MODE, level) }
    }

    /**
     * Returns and clears any pending level request.
     */
    fun consumePendingLevel(): Int? = pendingLevel.also { pendingLevel = null }

    // Haptics & sound - shared implementation, see GameFeedback.
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

    fun startLevel(level: Int) {
        viewModelScope.launch {
            // Generate off the main thread to keep the UI smooth.
            val generated = withContext(Dispatchers.Default) {
                if (level == 1) {
                    LevelGenerator.generateTutorialLevel()
                } else {
                    LevelGenerator.generateLevel(level)
                }
            }

            currentLevelArrows = generated.arrows
            _gridRows.value = generated.gridRows
            _gridCols.value = generated.gridCols
            _levelShape.value = generated.shape
            _shapeName.value = generated.shapeName
            _gameState.value = gameEngine.createLevel(
                level,
                generated.arrows,
                generated.gridRows,
                generated.gridCols,
                generated.difficultyLabel
            )
            _escapingArrows.value = emptyMap()
            _shakingArrows.value = emptySet()
            _hintsRemaining.value = HINTS_PER_LEVEL
            _combo.value = 0
            continueUsedThisLevel = false

            analytics.log(Events.levelStarted(level, generated.arrows.size))
        }
    }

    /**
     * Developer mode: step to the previous (-1) or next (+1) level without
     * touching progress.
     *
     * Clamped to [DifficultyConfig.TOTAL_LEVELS] - that bound exists so the
     * dev bar can hide Next at the edge; stepping past it here would silently
     * reopen the dead button.
     */
    fun devStepLevel(delta: Int) {
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

        val (newState, wasValid) = gameEngine.tapArrow(_gameState.value, arrow.id)

        if (wasValid) {
            handleValidMove(arrow, newState)
        } else {
            handleInvalidMove(arrow, newState)
        }
    }

    private fun handleValidMove(arrow: Arrow, newState: GameState) {
        // Start escape animation
        _escapingArrows.value = _escapingArrows.value + (arrow.id to arrow.direction)

        // Combo: increment on each consecutive valid move
        _combo.value = _combo.value + 1

        // Play sound and haptic
        if (prefs.value.soundEnabled) feedback.playSuccessSound()
        if (prefs.value.hapticsEnabled) feedback.playSuccessHaptic()

        analytics.log(Events.validMove(newState.level, newState.remainingCount))

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
        val arrowId = arrow.id

        viewModelScope.launch {
            delay(ESCAPE_ANIMATION_DURATION_MS)
            _escapingArrows.value = _escapingArrows.value - arrowId

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
        analytics.log(Events.invalidMove(newState.level, newState.lives))

        viewModelScope.launch {
            delay(SHAKE_ANIMATION_DURATION_MS)
            _shakingArrows.value = _shakingArrows.value - arrow.id

            // Check for out of lives
            if (newState.status == GameStatus.OUT_OF_LIVES) {
                analytics.log(Events.livesExhausted(newState.level, newState.clearedPercentage))
                analytics.log(Events.rewardedAdOffered(newState.level))
            }
        }
    }

    private fun handleLevelComplete(state: GameState) {
        viewModelScope.launch {
            val timeSeconds = gameEngine.getCompletionTimeSeconds(state)
            analytics.log(Events.levelCompleted(
                state.level,
                timeSeconds,
                state.mistakes,
                continueUsedThisLevel
            ))

            // Calculate and save stars
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

    fun onContinueWithAd(activity: Activity) {
        val currentLevel = _gameState.value.level
        analytics.log(Events.rewardedAdStarted(currentLevel))

        adManager.showRewardedAd(
            activity = activity,
            onRewarded = {
                analytics.log(Events.rewardedAdCompleted(currentLevel))
                continueUsedThisLevel = true
                _gameState.value = gameEngine.restoreLives(_gameState.value)
                adManager.loadRewardedAd(context) // Preload next
            },
            onFailed = {
                analytics.log(Events.rewardedAdFailed(currentLevel))
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

    override fun onCleared() {
        super.onCleared()
        feedback.release()
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
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GameViewModel(preferences, adManager, analytics, context) as T
    }
}
