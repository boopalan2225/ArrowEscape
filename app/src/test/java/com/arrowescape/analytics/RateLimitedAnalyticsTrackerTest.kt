package com.arrowescape.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class RateLimitedAnalyticsTrackerTest {

    /** Captures what actually made it through the limiter. */
    private class RecordingTracker : AnalyticsTracker {
        val received = mutableListOf<AnalyticsEvent>()
        override fun log(event: AnalyticsEvent) {
            received += event
        }

        fun countOf(name: String) = received.count { it.name == name }
    }

    private val sink = RecordingTracker()
    private var now = 0L

    private fun limiter(limits: Map<String, LogLimit>) =
        RateLimitedAnalyticsTracker(sink, limits) { now }

    @Test
    fun `unlisted events are never throttled`() {
        val tracker = limiter(mapOf("throttled" to LogLimit(maxPerSession = 1)))

        repeat(50) { tracker.log(AnalyticsEvent("free")) }

        assertEquals(50, sink.countOf("free"))
    }

    @Test
    fun `session cap stops sending once reached`() {
        val tracker = limiter(mapOf("spam" to LogLimit(maxPerSession = 3)))

        repeat(10) { tracker.log(AnalyticsEvent("spam")) }

        assertEquals(3, sink.countOf("spam"))
    }

    @Test
    fun `minimum interval drops events that arrive too soon`() {
        val tracker = limiter(mapOf("burst" to LogLimit(minIntervalMs = 1_000)))

        tracker.log(AnalyticsEvent("burst")) // t=0, sent
        now = 999
        tracker.log(AnalyticsEvent("burst")) // too soon, dropped
        now = 1_000
        tracker.log(AnalyticsEvent("burst")) // exactly on the boundary, sent

        assertEquals(2, sink.countOf("burst"))
    }

    @Test
    fun `interval is measured from the last sent event not the last attempt`() {
        val tracker = limiter(mapOf("burst" to LogLimit(minIntervalMs = 100)))

        tracker.log(AnalyticsEvent("burst")) // t=0, sent
        now = 50
        tracker.log(AnalyticsEvent("burst")) // dropped, must not reset the clock
        now = 100
        tracker.log(AnalyticsEvent("burst")) // 100ms since the *sent* one, sent

        assertEquals(2, sink.countOf("burst"))
    }

    @Test
    fun `limits are tracked per event name`() {
        val tracker = limiter(
            mapOf("a" to LogLimit(maxPerSession = 1), "b" to LogLimit(maxPerSession = 1))
        )

        repeat(5) {
            tracker.log(AnalyticsEvent("a"))
            tracker.log(AnalyticsEvent("b"))
        }

        assertEquals(1, sink.countOf("a"))
        assertEquals(1, sink.countOf("b"))
    }

    @Test
    fun `event payload passes through untouched`() {
        val tracker = limiter(emptyMap())
        val event = Events.adLoadFailed(AdFormat.REWARDED, errorCode = 3, message = "no fill")

        tracker.log(event)

        assertEquals(listOf(event), sink.received)
    }

    @Test
    fun `revenue and impressions are deliberately left unlimited`() {
        val tracker = RateLimitedAnalyticsTracker(sink, AnalyticsLimits.DEFAULTS) { now }

        repeat(500) {
            tracker.log(Events.adImpression(AdFormat.REWARDED))
            tracker.log(Events.adRevenue(AdFormat.REWARDED, 1_000L, "USD", 1))
        }

        assertEquals(500, sink.countOf("ad_impression"))
        assertEquals(500, sink.countOf("ad_revenue"))
    }

    @Test
    fun `default limits muzzle both ad failure events`() {
        val tracker = RateLimitedAnalyticsTracker(sink, AnalyticsLimits.DEFAULTS) { now }

        // A tight retry loop: same millisecond, over and over.
        repeat(100) {
            tracker.log(Events.adLoadFailed(AdFormat.INTERSTITIAL, 0, "network error"))
            tracker.log(Events.adNotReady(AdFormat.REWARDED))
        }

        assertEquals(1, sink.countOf("ad_load_failed"))
        assertEquals(1, sink.countOf("ad_not_ready"))
    }
}

class CompositeAnalyticsTrackerTest {

    @Test
    fun `a throwing sink does not stop the others`() {
        val healthy = mutableListOf<AnalyticsEvent>()
        val composite = CompositeAnalyticsTracker(
            AnalyticsTracker { error("ad SDK callback on a bad day") },
            AnalyticsTracker { healthy += it }
        )

        composite.log(Events.appOpen())

        assertEquals(1, healthy.size)
    }
}
