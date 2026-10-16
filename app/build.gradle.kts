import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

/**
 * Release signing credentials.
 *
 * Secrets live in keystore.properties at the repo root, which is gitignored -
 * see keystore.properties.template for the expected keys. A missing or
 * incomplete file is not an error: the release build simply stays unsigned so
 * that cloning the repo and running `assembleRelease` still works.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

/** Null unless every credential is present and the keystore really exists. */
val releaseKeystore: File? = keystoreProperties.getProperty("storeFile")
    ?.let(rootProject::file)
    ?.takeIf { it.exists() }
    ?.takeIf {
        listOf("storePassword", "keyAlias", "keyPassword")
            .all { key -> !keystoreProperties.getProperty(key).isNullOrBlank() }
    }

android {
    // Code lives under com.arrowescape; the Play Store identity below adds the
    // .game suffix. Keeping them separate avoids a redundant com.arrowescape
    // .game.game.* tree, since the game logic already owns a `game` package.
    namespace = "com.arrowescape"
    compileSdk = 34

    testOptions {
        unitTests {
            // Analytics sinks fall back to android.util.Log, which is a stub on
            // the JVM. Returning defaults keeps that from throwing in tests.
            isReturnDefaultValues = true
        }
        unitTests.all {
            it.testLogging {
                showStandardStreams = true
            }
        }
    }

    defaultConfig {
        // Permanent once published - Play Store listings cannot change it.
        applicationId = "com.arrowescape.game"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // AdMob App ID - Replace with your production ID before release
        manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-3940256099942544~3347511713" // Test ID
        // Play Games Services App ID lives in res/values/strings.xml, not here -
        // see game_services_app_id for why.
    }

    signingConfigs {
        // Only declared when usable; referencing a half-configured signing
        // config fails the build in confusing ways at package time.
        releaseKeystore?.let { keystore ->
            create("release") {
                storeFile = keystore
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")

                // minSdk is 26, so v1 is dead weight. v2/v3 are what Android
                // verifies; v4 enables incremental install on API 30+.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Use test ad unit IDs in debug
            buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("Boolean", "USE_TEST_ADS", "true")
            // Placeholder google-services.json is checked in, so nothing is
            // sent anywhere until a real Firebase project replaces it. Flip
            // both of these to true once it does.
            buildConfigField("Boolean", "FIREBASE_ENABLED", "false")

            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                // Debug builds have no symbols worth uploading; skipping keeps
                // the edit-run loop fast.
                mappingFileUploadEnabled = false
                nativeSymbolUploadEnabled = false
            }
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Replace these with your production ad unit IDs
            buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"YOUR_PRODUCTION_REWARDED_AD_UNIT_ID\"")
            buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "\"YOUR_PRODUCTION_INTERSTITIAL_AD_UNIT_ID\"")
            buildConfigField("Boolean", "USE_TEST_ADS", "false")
            // Set to true together with a real google-services.json.
            buildConfigField("Boolean", "FIREBASE_ENABLED", "false")

            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                // R8 is on below, so Crashlytics needs the mapping file to
                // produce readable stack traces - but uploading it talks to
                // Firebase, which the placeholder credentials cannot do.
                // Turn this on in the same commit that adds the real
                // google-services.json.
                mappingFileUploadEnabled = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Firebase - BOM pins every Firebase artifact to one compatible set
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    // Google Mobile Ads (AdMob)
    implementation("com.google.android.gms:play-services-ads:22.6.0")

    // Google Play Games Services (cloud save / sign-in)
    implementation("com.google.android.gms:play-services-games-v2:22.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// An unsigned release APK installs nowhere and uploads nowhere, so say so
// loudly at the moment it is produced rather than letting it look like success.
tasks.matching { it.name == "packageRelease" }.configureEach {
    doFirst {
        if (releaseKeystore == null) {
            logger.warn(
                "\n*** Release build is UNSIGNED - no valid keystore.properties found. ***\n" +
                "    Copy keystore.properties.template to keystore.properties and fill it in.\n"
            )
        }
    }
}

// Dev-only: render generated levels to PNG for visual review.
tasks.register<JavaExec>("renderShapes") {
    group = "verification"
    description = "Renders generated shapes to build/shape-preview for eyeballing."
    dependsOn("compileDebugKotlin")
    mainClass.set("com.arrowescape.game.curve.ShapePreview")
    classpath = files(
        layout.buildDirectory.dir("tmp/kotlin-classes/debug"),
        configurations.getByName("debugRuntimeClasspath")
    )
}
