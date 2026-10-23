package com.clevercorner.arrowescape.ui.settings

import com.clevercorner.arrowescape.BuildConfig

/**
 * The handful of facts every "small print" screen needs to agree on.
 *
 * [VERSION] reads BuildConfig rather than a hand-typed string, so bumping
 * versionName in Gradle is the only move required - a stale "1.0.0" on the
 * About screen is the kind of bug nobody notices until a reviewer does.
 */
object AppInfo {
    const val NAME = "Arrow Escape"
    val VERSION: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_NUMBER})"
    val BUILD_NUMBER: Int = BuildConfig.BUILD_NUMBER
    const val SUPPORT_EMAIL = "support@arrowescape.app"
    const val COPYRIGHT = "\u00a9 2025 Arrow Escape. All rights reserved."

    /** When the Terms and the Privacy Policy were last reworded. */
    const val LEGAL_LAST_UPDATED = "October 2025"
}
