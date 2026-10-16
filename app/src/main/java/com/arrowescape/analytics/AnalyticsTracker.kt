package com.arrowescape.analytics

/**
 * A sink for [AnalyticsEvent]s.
 *
 * Deliberately one method. The event vocabulary lives in [Events], so adding
 * an event never forces every implementation to grow another override - which
 * is exactly what the previous one-method-per-event interface did.
 *
 * Implementations must be cheap and must never throw: callers log from hot
 * paths and from ad SDK callbacks.
 */
fun interface AnalyticsTracker {
    fun log(event: AnalyticsEvent)
}

/** Drops everything. Used in tests, previews, and ad-free builds. */
object NoOpAnalyticsTracker : AnalyticsTracker {
    override fun log(event: AnalyticsEvent) = Unit
}

/** Prints to Logcat. Debug builds only - it is noisy by design. */
class DebugAnalyticsTracker(private val tag: String = "Analytics") : AnalyticsTracker {
    override fun log(event: AnalyticsEvent) {
        val params = if (event.params.isEmpty()) "" else " | ${event.params}"
        android.util.Log.d(tag, "EVENT: ${event.name}$params")
    }
}

/**
 * Fans one event out to several sinks.
 *
 * A failing sink must not silence the others, so each delivery is isolated.
 */
class CompositeAnalyticsTracker(
    private val sinks: List<AnalyticsTracker>
) : AnalyticsTracker {

    constructor(vararg sinks: AnalyticsTracker) : this(sinks.toList())

    override fun log(event: AnalyticsEvent) {
        sinks.forEach { sink ->
            runCatching { sink.log(event) }
                .onFailure { android.util.Log.w("Analytics", "Sink ${sink.javaClass.simpleName} failed", it) }
        }
    }
}
