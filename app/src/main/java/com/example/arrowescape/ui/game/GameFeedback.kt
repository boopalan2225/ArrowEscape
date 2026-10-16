package com.example.arrowescape.ui.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationAttributes
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

    // Vibrating without telling the system *why* leaves it to classify the
    // call itself, and on at least one real device that classification
    // landed somewhere that gets silently scaled to nothing - the effect
    // dispatches, the OS reports it as finished, and you feel zero. Tagging
    // it USAGE_TOUCH puts it in the same bucket as keyboard taps and other
    // UI feedback, which is reliably audible^Wfeelable everywhere.
    // VibrationAttributes the class exists on-device from API 30, but the
    // Vibrator.vibrate(VibrationEffect, VibrationAttributes) overload isn't
    // added until API 33 - gating on R builds a valid attributes object on
    // API 30-32 and then throws NoSuchMethodError the moment it's actually
    // passed to vibrate(). Below 33 we fall back to the older
    // vibrate(VibrationEffect, AudioAttributes) overload (API 26+, our
    // minSdk) so taps still get classified instead of silently using the
    // no-attributes call.
    private val touchAttributes: VibrationAttributes? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_TOUCH)
                .build()
        } else null

    private val touchAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun vibrate(effect: VibrationEffect) {
        val vib = vibrator ?: return
        val attrs = touchAttributes
        if (attrs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vib.vibrate(effect, attrs)
        } else {
            vib.vibrate(effect, touchAudioAttributes)
        }
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
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrate(effect)
    }

    fun playErrorHaptic() {
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 30, 50, 30), -1)
        }
        vibrate(effect)
    }

    /** Call from the owning ViewModel's onCleared(). */
    fun release() {
        soundPool.release()
    }
}
