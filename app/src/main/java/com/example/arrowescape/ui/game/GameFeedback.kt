package com.example.arrowescape.ui.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.arrowescape.R

/**
 * Sound and haptic feedback for a tap. Shared by both game engines - the grid
 * and the curved board feel the same when you touch them, so there is no
 * reason for each ViewModel to carry its own copy of this plumbing.
 */
class GameFeedback(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // SoundPool.load() is asynchronous - playing an id before it finishes is
    // a silent no-op on the platform, not an error. A handful of short clips
    // load in a few milliseconds, but tracking completion properly means a
    // tap right as the screen opens never loses its sound to a race.
    private val loadedSoundIds = mutableSetOf<Int>()
    private val successSoundId = soundPool.load(context, R.raw.tap_success, 1)
    private val errorSoundId = soundPool.load(context, R.raw.tap_error, 1)
    private val completeSoundId = soundPool.load(context, R.raw.level_complete, 1)

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedSoundIds += sampleId
        }
    }

    fun playSuccessSound() = play(successSoundId)
    fun playErrorSound() = play(errorSoundId)
    fun playCompleteSound() = play(completeSoundId)

    private fun play(soundId: Int) {
        if (soundId in loadedSoundIds) {
            soundPool.play(soundId, 1f, 1f, /* priority = */ 1, /* loop = */ 0, /* rate = */ 1f)
        }
    }

    fun playSuccessHaptic() {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(10)
            }
        }
    }

    fun playErrorHaptic() {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0, 30, 50, 30), -1)
            }
        }
    }

    /** Call from the owning ViewModel's onCleared(). */
    fun release() {
        soundPool.release()
    }
}
