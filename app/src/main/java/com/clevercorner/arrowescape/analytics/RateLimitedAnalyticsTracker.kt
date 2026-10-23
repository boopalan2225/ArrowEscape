package com.clevercorner.arrowescape.analytics

import java.util.concurrent.ConcurrentHashMap

/**
 * How often one event name may reach the backend.
 *
 * @param maxPerSession hard cap for the process lifetime; [UNLIMITED] to disable.
 * @param minIntervalMs smallest gap between two sends; 0 to disable.
 */
data class LogLimit(
    val maxPerSession: Int = UNLIMITED,
    val minIntervalMs: Long = 0L
) {
    companion object {
        const val UNLIMITED = Int.MAX_VALUE
    }
}

/**
 * Throttles chatty events before they reach a real backend.
 *
 * Why this exists: Firebase enforces its own per-app event quotas and silently
 * drops the overflow. Dropping *predictably* here - the hundredth identical
 * `ad_load_failed` from a retry loop, or every tap in a 60-arrow level - keeps
 * the events that actually matter (impressions, rewards, completions) inside
 * the budget.
 *
 * Decorator, not a base class: any [AnalyticsTracker] can be wrapped, and the
 * limiter knows nothing about Firebase.
 */
class RateLimitedAnalyticsTracker(
    private val delegate: AnalyticsTracker,
    private val limits: Map<String, LogLimit> = AnalyticsLimits.DEFAULTS,
    private val clock: () -> Long = System::currentTimeMillis
) : AnalyticsTracker {

    private data class Usage(val count: Int, val lastSentAt: Long)

    private val usage = ConcurrentHashMap<String, Usage>()

    override fun log(event: AnalyticsEvent) {
        if (allows(event.name)) delegate.log(event)
    }

    /**
     * Returns true and records the send, atomically, so two threads cannot
     * both slip past the last slot of a cap.
     */
    private fun allows(name: String): Boolean {
        val limit = limits[name] ?: return true
        val now = clock()
        var permitted = false

        usage.compute(name) { _, previous ->
            val count = previous?.count ?: 0
            val sinceLast = now - (previous?.lastSentAt ?: Long.MIN_VALUE / 2)
            permitted = count < limit.maxPerSession && sinceLast >= limit.minIntervalMs
            if (permitted) Usage(count + 1, now) else previous ?: Usage(0, 0L)
        }

        return permitted
    }
}

/**
 * The events worth muzzling, and why. Everything absent is unlimited.
 */
object AnalyticsLimits {

    /** One per second is plenty to reconstruct pacing; taps can fire far faster. */
    private val GAMEPLAY_TAPS = LogLimit(maxPerSession = 200, minIntervalMs = 1_000)

    /**
     * Ad load/show failures arrive in retry bursts - the first few tell the
     * whole story, the next hundred just burn quota.
     */
    private val AD_FAILURES = LogLimit(maxPerSession = 20, minIntervalMs = 5_000)

    /** Requests are useful as a funnel denominator, but one per load attempt is enough. */
    private val AD_REQUESTS = LogLimit(maxPerSession = 100, minIntervalMs = 1_000)

    val DEFAULTS: Map<String, LogLimit> = mapOf(
        Events.validMove(0, 0).name to GAMEPLAY_TAPS,
        Events.invalidMove(0, 0).name to GAMEPLAY_TAPS,
        Events.adLoadRequested(AdFormat.REWARDED).name to AD_REQUESTS,
        Events.adLoadFailed(AdFormat.REWARDED, 0, "").name to AD_FAILURES,
        Events.adShowFailed(AdFormat.REWARDED, 0, "").name to AD_FAILURES,
        Events.adNotReady(AdFormat.REWARDED).name to AD_FAILURES
    )
}
