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
 */
class GamePreferences(private val context: Context) {

    companion object {
        private val KEY_CURRENT_LEVEL = intPreferencesKey("current_level")
        private val KEY_HIGHEST_LEVEL = intPreferencesKey("highest_level")
        private val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        private val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        private val KEY_LEVELS_SINCE_INTERSTITIAL = intPreferencesKey("levels_since_interstitial")
        private val KEY_TOTAL_LEVELS_COMPLETED = intPreferencesKey("total_levels_completed")
        private val KEY_LEVEL_STARS = stringPreferencesKey("level_stars") // JSON: "1:3,2:2,3:3"

        // Saved game state
        private val KEY_SAVED_LEVEL = intPreferencesKey("saved_level")
        private val KEY_SAVED_LIVES = intPreferencesKey("saved_lives")
        private val KEY_SAVED_ARROWS = stringPreferencesKey("saved_arrows") // JSON encoded
    }

    /**
     * Data class representing all preferences.
     */
    data class Prefs(
        val currentLevel: Int = 1,
        val highestLevel: Int = 1,
        val soundEnabled: Boolean = true,
        val hapticsEnabled: Boolean = true,
        val levelsSinceInterstitial: Int = 0,
        val totalLevelsCompleted: Int = 0,
        val levelStars: Map<Int, Int> = emptyMap() // level -> stars (1-3)
    )

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
        .map { prefs ->
            // Parse level stars from "1:3,2:2,3:3" format
            val starsString = prefs[KEY_LEVEL_STARS] ?: ""
            val levelStars = starsString.split(",")
                .filter { it.contains(":") }
                .mapNotNull { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) {
                        val level = parts[0].toIntOrNull()
                        val stars = parts[1].toIntOrNull()
                        if (level != null && stars != null) level to stars else null
                    } else null
                }
                .toMap()
            
            Prefs(
                currentLevel = prefs[KEY_CURRENT_LEVEL] ?: 1,
                highestLevel = prefs[KEY_HIGHEST_LEVEL] ?: 1,
                soundEnabled = prefs[KEY_SOUND_ENABLED] ?: true,
                hapticsEnabled = prefs[KEY_HAPTICS_ENABLED] ?: true,
                levelsSinceInterstitial = prefs[KEY_LEVELS_SINCE_INTERSTITIAL] ?: 0,
                totalLevelsCompleted = prefs[KEY_TOTAL_LEVELS_COMPLETED] ?: 0,
                levelStars = levelStars
            )
        }

    suspend fun setCurrentLevel(level: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CURRENT_LEVEL] = level
            if (level > (prefs[KEY_HIGHEST_LEVEL] ?: 1)) {
                prefs[KEY_HIGHEST_LEVEL] = level
            }
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

    suspend fun incrementTotalLevelsCompleted() {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOTAL_LEVELS_COMPLETED] = (prefs[KEY_TOTAL_LEVELS_COMPLETED] ?: 0) + 1
        }
    }

    suspend fun saveLevelStars(level: Int, stars: Int) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_LEVEL_STARS] ?: ""
            val starsMap = mutableMapOf<Int, Int>()
            
            // Parse existing stars
            if (existing.isNotBlank()) {
                existing.split(",").forEach { entry ->
                    if (entry.contains(":")) {
                        val parts = entry.split(":")
                        val l = parts[0].toIntOrNull()
                        val s = parts[1].toIntOrNull()
                        if (l != null && s != null) {
                            starsMap[l] = s
                        }
                    }
                }
            }
            
            // Only update if new stars are better than existing
            val currentStars = starsMap[level] ?: 0
            if (stars > currentStars) {
                starsMap[level] = stars
            }
            
            // Convert back to string
            val starsString = starsMap.entries.map { entry -> "${entry.key}:${entry.value}" }.joinToString(",")
            prefs[KEY_LEVEL_STARS] = starsString
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

    /**
     * DEBUG: Unlock all levels for testing.
     */
    suspend fun unlockAllLevels(maxLevel: Int = 150) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HIGHEST_LEVEL] = maxLevel
            prefs[KEY_CURRENT_LEVEL] = maxLevel
            prefs[KEY_TOTAL_LEVELS_COMPLETED] = maxLevel - 1
            // Give 2 stars to all levels for testing
            val starsString = (1 until maxLevel).joinToString(",") { "$it:2" }
            prefs[KEY_LEVEL_STARS] = starsString
        }
    }

    /**
     * DEBUG: Reset progress to level 1.
     */
    suspend fun resetProgress() {
        context.dataStore.edit { prefs ->
            prefs[KEY_HIGHEST_LEVEL] = 1
            prefs[KEY_CURRENT_LEVEL] = 1
            prefs[KEY_TOTAL_LEVELS_COMPLETED] = 0
            prefs[KEY_LEVEL_STARS] = ""
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
