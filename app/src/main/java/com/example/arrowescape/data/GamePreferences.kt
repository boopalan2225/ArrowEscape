package com.example.arrowescape.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "arrow_escape_prefs")

/**
 * Handles all persistent game data using DataStore.
 *
 * Progress is stored *per [GameMode]*: each mode has its own level, best level,
 * completion count and stars, so a player can hop between modes and always pick
 * up where they left off. Settings (sound, haptics, theme, dev mode) stay global.
 */
class GamePreferences(private val context: Context) {

    companion object {
        // ── Global settings ──────────────────────────────────────────────
        private val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        private val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        private val KEY_LEVELS_SINCE_INTERSTITIAL = intPreferencesKey("levels_since_interstitial")
        private val KEY_DEV_MODE = booleanPreferencesKey("dev_mode") // Open any level, no progress changes
        private val KEY_THEME = stringPreferencesKey("theme") // AppTheme.id; absent = match the phone
        private val KEY_GAME_MODE = stringPreferencesKey("game_mode") // GameMode.id the player last chose
        private val KEY_MIGRATED = booleanPreferencesKey("progress_migrated_to_per_mode")

        // ── Per-mode progress (suffixed with GameMode.id) ────────────────
        private fun keyCurrentLevel(mode: GameMode) = intPreferencesKey("current_level_${mode.id}")
        private fun keyHighestLevel(mode: GameMode) = intPreferencesKey("highest_level_${mode.id}")
        private fun keyTotalCompleted(mode: GameMode) = intPreferencesKey("total_completed_${mode.id}")
        private fun keyLevelStars(mode: GameMode) = stringPreferencesKey("level_stars_${mode.id}")

        /**
         * Pre-per-mode keys. Progress used to be global - one set of numbers
         * shared by whichever mode was switched on - so on upgrade it is
         * inherited by the mode the player was last in, and only until that
         * mode's own keys get written. Read-only: nothing writes these now.
         */
        private val LEGACY_CURRENT_LEVEL = intPreferencesKey("current_level")
        private val LEGACY_HIGHEST_LEVEL = intPreferencesKey("highest_level")
        private val LEGACY_TOTAL_COMPLETED = intPreferencesKey("total_levels_completed")
        private val LEGACY_LEVEL_STARS = stringPreferencesKey("level_stars")
        private val LEGACY_CURVED_MODE = booleanPreferencesKey("curved_mode")

        // Saved game state
        private val KEY_SAVED_LEVEL = intPreferencesKey("saved_level")
        private val KEY_SAVED_LIVES = intPreferencesKey("saved_lives")
        private val KEY_SAVED_ARROWS = stringPreferencesKey("saved_arrows") // JSON encoded
    }

    /** One mode's standing: where the player is, and how well they did. */
    data class ModeProgress(
        val currentLevel: Int = 1,
        val highestLevel: Int = 1,
        val totalLevelsCompleted: Int = 0,
        val levelStars: Map<Int, Int> = emptyMap() // level -> stars (1-3)
    ) {
        /**
         * Levels cleared, never more than [totalLevels]. A journey can be
         * shorter than an old save (shapes get reworked, levels wrap), and
         * "370 of 150 cleared" helps nobody.
         */
        fun clearedOutOf(totalLevels: Int): Int =
            totalLevelsCompleted.coerceAtMost(totalLevels)
    }

    /**
     * Data class representing all preferences.
     *
     * [currentLevel] and friends are conveniences that read the *active* mode's
     * progress, so callers that do not care about modes need not know they exist.
     */
    data class Prefs(
        val mode: GameMode = GameMode.DEFAULT,
        val progress: Map<GameMode, ModeProgress> = emptyMap(),
        val soundEnabled: Boolean = true,
        val hapticsEnabled: Boolean = true,
        val levelsSinceInterstitial: Int = 0,
        /** Developer mode: level picker, prev/next, and play never touches saved progress. */
        val devMode: Boolean = false,
        /** [com.example.arrowescape.ui.theme.AppTheme.id] the player picked; null = never chose. */
        val themeId: String? = null
    ) {
        fun progressOf(mode: GameMode): ModeProgress = progress[mode] ?: ModeProgress()

        val activeProgress: ModeProgress get() = progressOf(mode)
        val currentLevel: Int get() = activeProgress.currentLevel
        val highestLevel: Int get() = activeProgress.highestLevel
        val totalLevelsCompleted: Int get() = activeProgress.totalLevelsCompleted
        val levelStars: Map<Int, Int> get() = activeProgress.levelStars
    }

    /**
     * Flow of all preferences.
     */
    val prefsFlow: Flow<Prefs> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { stored ->
            Prefs(
                mode = resolveMode(stored),
                progress = GameMode.entries.associateWith { stored.progressOf(it) },
                soundEnabled = stored[KEY_SOUND_ENABLED] ?: true,
                hapticsEnabled = stored[KEY_HAPTICS_ENABLED] ?: true,
                levelsSinceInterstitial = stored[KEY_LEVELS_SINCE_INTERSTITIAL] ?: 0,
                devMode = stored[KEY_DEV_MODE] ?: false,
                themeId = stored[KEY_THEME]
            )
        }

    /** The chosen mode, honouring the legacy `curved_mode` flag for upgraders. */
    private fun resolveMode(stored: Preferences): GameMode {
        stored[KEY_GAME_MODE]?.let { return GameMode.fromId(it) }
        val legacyCurved = stored[LEGACY_CURVED_MODE] ?: return GameMode.DEFAULT
        return if (legacyCurved) GameMode.CURVED else GameMode.CLASSIC
    }

    private fun Preferences.progressOf(mode: GameMode): ModeProgress = ModeProgress(
        currentLevel = this[keyCurrentLevel(mode)] ?: 1,
        highestLevel = this[keyHighestLevel(mode)] ?: 1,
        totalLevelsCompleted = this[keyTotalCompleted(mode)] ?: 0,
        levelStars = parseStars(this[keyLevelStars(mode)])
    )

    /**
     * Moves a pre-per-mode save into its owning mode, exactly once.
     *
     * Progress used to be global - one set of numbers shared by whichever mode
     * was switched on - so it belongs to the mode the player was last in.
     * Deciding this lazily at read time would be wrong: the answer would change
     * the moment they switched modes, dragging the old save along with them.
     */
    suspend fun migrateLegacyProgress() {
        context.dataStore.edit { prefs ->
            if (prefs[KEY_MIGRATED] == true) return@edit
            prefs[KEY_MIGRATED] = true

            val owner = resolveMode(prefs)
            prefs[keyCurrentLevel(owner)] = prefs[LEGACY_CURRENT_LEVEL] ?: return@edit
            prefs[keyHighestLevel(owner)] = prefs[LEGACY_HIGHEST_LEVEL] ?: 1
            prefs[keyTotalCompleted(owner)] = prefs[LEGACY_TOTAL_COMPLETED] ?: 0
            prefs[keyLevelStars(owner)] = prefs[LEGACY_LEVEL_STARS] ?: ""
            prefs[KEY_GAME_MODE] = owner.id
        }
    }

    /** Stars are stored compactly as "1:3,2:2,3:3". */
    private fun parseStars(encoded: String?): Map<Int, Int> =
        encoded.orEmpty().split(",")
            .mapNotNull { entry ->
                val parts = entry.split(":")
                val level = parts.getOrNull(0)?.toIntOrNull()
                val stars = parts.getOrNull(1)?.toIntOrNull()
                if (level != null && stars != null) level to stars else null
            }
            .toMap()

    private fun encodeStars(stars: Map<Int, Int>): String =
        stars.entries.joinToString(",") { "${it.key}:${it.value}" }

    suspend fun setCurrentLevel(mode: GameMode, level: Int) {
        context.dataStore.edit { prefs ->
            val best = prefs.progressOf(mode).highestLevel
            prefs[keyCurrentLevel(mode)] = level
            prefs[keyHighestLevel(mode)] = maxOf(level, best)
        }
    }

    suspend fun setGameMode(mode: GameMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GAME_MODE] = mode.id
        }
    }

    suspend fun incrementLevelsSinceInterstitial() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LEVELS_SINCE_INTERSTITIAL] = (prefs[KEY_LEVELS_SINCE_INTERSTITIAL] ?: 0) + 1
        }
    }

    suspend fun resetLevelsSinceInterstitial() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LEVELS_SINCE_INTERSTITIAL] = 0
        }
    }

    suspend fun incrementTotalLevelsCompleted(mode: GameMode) {
        context.dataStore.edit { prefs ->
            prefs[keyTotalCompleted(mode)] = prefs.progressOf(mode).totalLevelsCompleted + 1
        }
    }

    suspend fun saveLevelStars(mode: GameMode, level: Int, stars: Int) {
        context.dataStore.edit { prefs ->
            val starsMap = prefs.progressOf(mode).levelStars.toMutableMap()
            // Only update if new stars are better than existing
            if (stars > (starsMap[level] ?: 0)) {
                starsMap[level] = stars
            }
            prefs[keyLevelStars(mode)] = encodeStars(starsMap)
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SOUND_ENABLED] = enabled
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun setTheme(themeId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME] = themeId
        }
    }

    suspend fun setDevMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEV_MODE] = enabled
        }
    }

    /**
     * DEBUG: Unlock all levels of one mode for testing.
     */
    suspend fun unlockAllLevels(mode: GameMode, maxLevel: Int) {
        context.dataStore.edit { prefs ->
            prefs[keyHighestLevel(mode)] = maxLevel
            prefs[keyCurrentLevel(mode)] = maxLevel
            prefs[keyTotalCompleted(mode)] = maxLevel - 1
            // Give 2 stars to all levels for testing
            prefs[keyLevelStars(mode)] = encodeStars((1 until maxLevel).associateWith { 2 })
        }
    }

    /**
     * DEBUG: Reset one mode's progress to level 1.
     */
    suspend fun resetProgress(mode: GameMode) {
        context.dataStore.edit { prefs ->
            prefs[keyHighestLevel(mode)] = 1
            prefs[keyCurrentLevel(mode)] = 1
            prefs[keyTotalCompleted(mode)] = 0
            prefs[keyLevelStars(mode)] = ""
        }
    }

    // Saved game state for resuming after app kill
    data class SavedGameState(
        val level: Int,
        val lives: Int,
        val activeArrowIds: Set<Int> // Which arrows are still active
    )

    val savedGameFlow: Flow<SavedGameState?> = context.dataStore.data
        .map { prefs ->
            val level = prefs[KEY_SAVED_LEVEL] ?: return@map null
            val lives = prefs[KEY_SAVED_LIVES] ?: return@map null
            val arrowsJson = prefs[KEY_SAVED_ARROWS] ?: return@map null

            // Parse simple comma-separated IDs
            val activeIds = arrowsJson.split(",")
                .filter { it.isNotBlank() }
                .mapNotNull { it.toIntOrNull() }
                .toSet()

            SavedGameState(level, lives, activeIds)
        }

    suspend fun saveGameState(level: Int, lives: Int, activeArrowIds: Set<Int>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SAVED_LEVEL] = level
            prefs[KEY_SAVED_LIVES] = lives
            prefs[KEY_SAVED_ARROWS] = activeArrowIds.joinToString(",")
        }
    }

    suspend fun clearSavedGame() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SAVED_LEVEL)
            prefs.remove(KEY_SAVED_LIVES)
            prefs.remove(KEY_SAVED_ARROWS)
        }
    }
}
