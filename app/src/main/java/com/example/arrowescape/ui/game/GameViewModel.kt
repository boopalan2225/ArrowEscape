package com.example.arrowescape.ui.game

import android.app.Activity
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.arrowescape.ads.AdManager
import com.example.arrowescape.analytics.AnalyticsTracker
import com.example.arrowescape.data.GamePreferences
import com.example.arrowescape.game.engine.GameEngine
import com.example.arrowescape.game.level.LevelGenerator
import com.example.arrowescape.game.model.Arrow
import com.example.arrowescape.game.model.Direction
import com.example.arrowescape.game.model.GameState
import com.example.arrowescape.game.model.GameStatus
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
        private const val INTERSTITIAL_FREQUENCY = 4 // Show ad every N completed levels
        private const val ESCAPE_ANIMATION_DURATION_MS = 350L
        private const val SHAKE_ANIMATION_DURATION_MS = 300L
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

    private val _levelShape = MutableStateFlow(com.example.arrowescape.game.level.LevelShape.SQUARE)
    val levelShape: StateFlow<com.example.arrowescape.game.level.LevelShape> = _levelShape.asStateFlow()

    private val _hintsRemaining = MutableStateFlow(3)
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
        viewModelScope.launch { preferences.setCurrentLevel(level) }
    }

    /**
     * Returns and clears any pending level request.
     */
    fun consumePendingLevel(): Int? = pendingLevel.also { pendingLevel = null }

    // Haptics
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    // Sound pool for effects
    private var soundPool: SoundPool? = null
    private var successSoundId: Int = 0
    private var errorSoundId: Int = 0
    private var completeSoundId: Int = 0

    init {
        initSounds()
        preloadAds()
    }

    private fun initSounds() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()

        // We'll use programmatic sounds for now (tone generation would need more setup)
        // In production, load actual .ogg files from res/raw
    }

    private fun preloadAds() {
        adManager.loadRewardedAd(context)
        adManager.loadInterstitialAd(context)
    }

    fun startLevel(leve: Int) {
        val level = 45
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
            _gameState.value = gameEngine.createLevel(
                level,
                generated.arrows,
                generated.gridRows,
                generated.gridCols,
                generated.difficultyLabel
            )
            _escapingArrows.value = emptyMap()
            _shakingArrows.value = emptySet()
            _hintsRemaining.value = 3
            _combo.value = 0
            continueUsedThisLevel = false

            analytics.trackLevelStarted(level, generated.arrows.size)
        }
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

    fun onArrowTap(arrow: Arrow) {
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
        if (prefs.value.soundEnabled) playSuccessSound()
        if (prefs.value.hapticsEnabled) playSuccessHaptic()

        analytics.trackValidMove(newState.level, newState.remainingCount)

        viewModelScope.launch {
            delay(ESCAPE_ANIMATION_DURATION_MS)
            _escapingArrows.value = _escapingArrows.value - arrow.id
            _gameState.value = newState

            // Check for level complete
            if (newState.status == GameStatus.LEVEL_COMPLETE) {
                handleLevelComplete(newState)
            }
        }
    }

    private fun handleInvalidMove(arrow: Arrow, newState: GameState) {
        // Start shake animation
        _shakingArrows.value = _shakingArrows.value + arrow.id

        // Reset combo on a mistake
        _combo.value = 0

        // Play error sound and haptic
        if (prefs.value.soundEnabled) playErrorSound()
        if (prefs.value.hapticsEnabled) playErrorHaptic()

        _gameState.value = newState
        analytics.trackInvalidMove(newState.level, newState.lives)

        viewModelScope.launch {
            delay(SHAKE_ANIMATION_DURATION_MS)
            _shakingArrows.value = _shakingArrows.value - arrow.id

            // Check for out of lives
            if (newState.status == GameStatus.OUT_OF_LIVES) {
                analytics.trackLivesExhausted(newState.level, newState.clearedPercentage)
                analytics.trackRewardedAdOffered(newState.level)
            }
        }
    }

    private fun handleLevelComplete(state: GameState) {
        viewModelScope.launch {
            val timeSeconds = gameEngine.getCompletionTimeSeconds(state)
            analytics.trackLevelCompleted(
                state.level,
                timeSeconds,
                state.mistakes,
                continueUsedThisLevel
            )

            preferences.setCurrentLevel(state.level + 1)
            preferences.incrementTotalLevelsCompleted()
            preferences.incrementLevelsSinceInterstitial()
            preferences.clearSavedGame()

            if (prefs.value.soundEnabled) playCompleteSound()
            if (prefs.value.hapticsEnabled) playSuccessHaptic()
        }
    }

    fun onContinueWithAd(activity: Activity) {
        val currentLevel = _gameState.value.level
        analytics.trackRewardedAdStarted(currentLevel)

        adManager.showRewardedAd(
            activity = activity,
            onRewarded = {
                analytics.trackRewardedAdCompleted(currentLevel)
                continueUsedThisLevel = true
                _gameState.value = gameEngine.restoreLives(_gameState.value)
                adManager.loadRewardedAd(context) // Preload next
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
        viewModelScope.launch {
            val nextLevel = _gameState.value.level + 1

            // Check if we should show interstitial
            if (prefs.value.levelsSinceInterstitial >= INTERSTITIAL_FREQUENCY) {
                // Don't show interstitial right after a rewarded ad
                if (!continueUsedThisLevel && adManager.isInterstitialAdReady()) {
                    analytics.trackInterstitialShown(nextLevel)
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
        viewModelScope.launch {
            val state = _gameState.value
            if (state.status == GameStatus.PLAYING && state.remainingCount > 0) {
                val activeIds = state.activeArrows.map { it.id }.toSet()
                preferences.saveGameState(state.level, state.lives, activeIds)
            }
        }
    }

    // Sound effects (simplified - in production, use actual audio files)
    private fun playSuccessSound() {
        // Would play a short "whoosh" sound
    }

    private fun playErrorSound() {
        // Would play a short "bonk" sound
    }

    private fun playCompleteSound() {
        // Would play a celebratory chime
    }

    // Haptic feedback
    private fun playSuccessHaptic() {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(10)
            }
        }
    }

    private fun playErrorHaptic() {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0, 30, 50, 30), -1)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundPool?.release()
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
