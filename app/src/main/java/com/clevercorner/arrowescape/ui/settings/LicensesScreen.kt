package com.clevercorner.arrowescape.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clevercorner.arrowescape.ui.theme.AppTheme
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme

/**
 * One third-party component we ship, and the licence that lets us.
 *
 * Grouped by licence text rather than per-artifact: five AndroidX libraries
 * under the same Apache 2.0 grant is one entry with five bullet points, not
 * five identical cards.
 */
data class ThirdPartyLicense(
    val name: String,
    val copyright: String,
    val license: String,
    val components: List<String>
)

/**
 * Everything we depend on at runtime.
 *
 * Keep this in step with the `dependencies` block in app/build.gradle.kts -
 * it is the one place the attribution lives, so adding a library means adding
 * a line here too.
 */
val THIRD_PARTY_LICENSES = listOf(
    ThirdPartyLicense(
        name = "AndroidX & Jetpack Compose",
        copyright = "Copyright \u00a9 The Android Open Source Project",
        license = "Apache License 2.0",
        components = listOf(
            "androidx.core:core-ktx",
            "androidx.lifecycle:lifecycle-runtime-ktx",
            "androidx.lifecycle:lifecycle-viewmodel-compose",
            "androidx.activity:activity-compose",
            "androidx.compose:compose-bom (ui, graphics, animation, material3)",
            "androidx.navigation:navigation-compose",
            "androidx.datastore:datastore-preferences"
        )
    ),
    ThirdPartyLicense(
        name = "Kotlin & kotlinx.coroutines",
        copyright = "Copyright \u00a9 JetBrains s.r.o. and Kotlin contributors",
        license = "Apache License 2.0",
        components = listOf(
            "org.jetbrains.kotlin:kotlin-stdlib",
            "org.jetbrains.kotlinx:kotlinx-coroutines-android",
            "org.jetbrains.kotlinx:kotlinx-coroutines-play-services"
        )
    ),
    ThirdPartyLicense(
        name = "Google Play services",
        copyright = "Copyright \u00a9 Google LLC",
        license = "Android Software Development Kit License",
        components = listOf(
            "com.google.android.gms:play-services-ads (AdMob)",
            "com.google.android.gms:play-services-games-v2",
            "com.google.android.play:review-ktx (In-App Review)"
        )
    ),
    ThirdPartyLicense(
        name = "Firebase for Android",
        copyright = "Copyright \u00a9 Google LLC",
        license = "Apache License 2.0",
        components = listOf(
            "com.google.firebase:firebase-analytics-ktx",
            "com.google.firebase:firebase-crashlytics-ktx"
        )
    ),
    ThirdPartyLicense(
        name = "Material Design Icons",
        copyright = "Copyright \u00a9 Google LLC",
        license = "Apache License 2.0",
        components = listOf("androidx.compose.material:material-icons-core")
    )
)

private const val APACHE_SUMMARY =
    "Licensed under the Apache License, Version 2.0. You may obtain a copy " +
        "at http://www.apache.org/licenses/LICENSE-2.0. Distributed on an " +
        "\"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND."

/** Who else's work is in here, and under what terms. */
@Composable
fun LicensesScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors

    DocumentScaffold(title = "Licenses", onBackClick = onBackClick, modifier = modifier) {
        Text(
            text = "Open source notices",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = "${AppInfo.NAME} stands on these shoulders.",
            style = MaterialTheme.typography.bodySmall,
            color = gameColors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        THIRD_PARTY_LICENSES.forEach { entry ->
            DocumentCard(title = entry.name) {
                DocumentParagraph("${entry.copyright}\n${entry.license}")
                Spacer(Modifier.height(8.dp))
                entry.components.forEach { component ->
                    DocumentBullet("\u25CF", component)
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        DocumentCard(title = "Apache License 2.0") {
            DocumentParagraph(APACHE_SUMMARY)
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "Everything else") {
            DocumentParagraph(
                "Level generation, artwork, sound packs and game code are " +
                    "original work. ${AppInfo.COPYRIGHT}"
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Preview(showBackground = true, name = "Licenses - Midnight")
@Composable
private fun LicensesScreenPreview() {
    ArrowEscapeTheme(AppTheme.MIDNIGHT) {
        LicensesScreen(onBackClick = {})
    }
}
