package com.clevercorner.arrowescape.cloud

import com.clevercorner.arrowescape.data.GameMode
import com.clevercorner.arrowescape.data.GamePreferences
import com.clevercorner.arrowescape.economy.Wallet
import com.clevercorner.arrowescape.game.daily.DailyProgress

/**
 * Everything worth carrying to a new phone.
 *
 * Deliberately a value object rather than "the preferences": settings are a
 * property of a *device* (this phone's volume, this phone's dark mode), while
 * progress, a streak and a purse are properties of a *player*. Only the
 * second kind belongs in the cloud, and naming the difference in a type
 * stops the two quietly merging over time.
 */
data class CloudState(
    val progress: Map<GameMode, GamePreferences.ModeProgress> = emptyMap(),
    val daily: DailyProgress = DailyProgress(),
    val wallet: Wallet = Wallet()
) {
    companion object {
        /** The cloud-worthy half of what is on this device right now. */
        fun of(prefs: GamePreferences.Prefs): CloudState =
            CloudState(progress = prefs.progress, daily = prefs.daily, wallet = prefs.wallet)
    }
}
