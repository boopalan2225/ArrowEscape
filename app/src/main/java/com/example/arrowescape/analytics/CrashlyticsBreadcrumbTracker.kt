package com.example.arrowescape.analytics

import com.example.arrowescape.crash.CrashKeys
import com.example.arrowescape.crash.CrashReporter

/**
 * Replays analytics events as Crashlytics breadcrumbs.
 *
 * A crash report is far more useful with "the player failed an ad show on
 * level 47, then tapped restart" attached than without, and wiring it as a
 * sink means no call site has to log twice.
 */
class CrashlyticsBreadcrumbTracker(
    private val reporter: CrashReporter
) : AnalyticsTracker {

    override fun log(event: AnalyticsEvent) {
        reporter.log(event.asBreadcrumb())

        // Keys are the "state at crash time" view; breadcrumbs are history.
        event.params[Params.LEVEL]?.let { reporter.setKey(CrashKeys.CURRENT_LEVEL, it) }
        if (event.name.startsWith(AD_EVENT_PREFIX)) {
            reporter.setKey(CrashKeys.LAST_AD_EVENT, event.asBreadcrumb())
        }
    }

    private fun AnalyticsEvent.asBreadcrumb(): String =
        if (params.isEmpty()) name else "$name $params"

    private companion object {
        const val AD_EVENT_PREFIX = "ad_"
    }
}
