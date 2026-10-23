# Arrow Escape: Tap & Solve

A minimalist, offline-first 2D puzzle game for Android.

## Store Listing

| Field | Value |
| --- | --- |
| App name | Arrow Escape: Tap & Solve |
| Package ID | `com.clevercorner.arrowescape` |
| Short description | Solve arrow puzzles, clear the board, and escape every level! |

## Game Concept

Tap arrows to make them escape the board! Each arrow can only escape if its path is clear in the direction it's pointing.

- **UP arrows** escape through the top
- **DOWN arrows** escape through the bottom  
- **LEFT arrows** escape through the left edge
- **RIGHT arrows** escape through the right edge

Clear all arrows to complete the level. But be careful - tap a blocked arrow and you lose a life!

## Features

- 100+ progressively challenging levels
- Deterministic level generation (same level = same puzzle)
- All levels guaranteed solvable
- 3 lives per level
- Watch rewarded ad to continue
- Offline gameplay
- No login required
- Sound and haptic feedback
- Confetti celebration on every level cleared
- In-app review asked for only after a run of wins (see Store review below)
- Terms of Service, Privacy Policy and open-source Licenses in About
- Clean, minimal UI
- Five appearances: Classic, Midnight, Forest, Sunset, plus **System** (follows the phone)
- Each theme is a whole mood: its own animated home scenery, motto, corner style **and sound pack**
- A "coming soon" banner closes the Journey map so finishing feels like being up to date

## Themes

A theme is two pieces of data and no screen-specific code:

| Piece | File | Holds |
| --- | --- | --- |
| Palette | `ui/theme/ThemePalettes.kt` | Material scheme + game colours |
| Persona | `ui/theme/ThemePersona.kt` | Tagline, crest, drifting scenery, corner radius, sound pack |

The non-Classic sound packs are synthesised - re-tune them by editing the
tables in `tools/generate_sfx.py` and running:

```bash
python3 tools/generate_sfx.py
```

## Tech Stack

- **Kotlin** - Primary language
- **Jetpack Compose** - UI framework
- **Material 3** - Design system
- **ViewModel + Flow** - State management
- **DataStore** - Persistence
- **Google Mobile Ads SDK** - Monetization
- **Firebase Analytics + Crashlytics** - Product telemetry and crash reporting
- **JUnit** - Unit testing

## Project Structure

```
com.clevercorner.arrowescape/   # namespace (code root)
  - game/
    - model/       # ArrowTile, Direction, GameState
    - engine/      # GameEngine, CollisionChecker, GameSolver
    - level/       # LevelGenerator, DifficultyConfig
    - curve/       # Curved-arrow engine
  - ui/
    - home/        # Home screen
    - game/        # Game screen + ViewModel
    - settings/    # Settings screen
    - components/  # Reusable UI components
    - theme/       # Colors, typography
  - ads/           # AdManager abstraction
  - analytics/     # Event catalog + tracker sinks (Logcat/Firebase/Crashlytics)
  - cloud/         # Play Games cloud save
  - crash/         # CrashReporter abstraction
  - data/          # GamePreferences (DataStore)
  - review/        # When and how to ask for a store review
  - MainActivity.kt
```

### Package naming

`namespace` and `applicationId` are the same value, `com.clevercorner.arrowescape`
- Kotlin package root (where `BuildConfig` is generated) and Play Store
identity are one and the same, **permanent** once published.

Anything that matches on applicationId must use this exact value - notably
`package_name` in `google-services.json`.

## Building

1. Open in Android Studio (Hedgehog or newer)
2. Sync Gradle
3. Run on emulator or device (API 24+)

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

### Run Tests
```bash
./gradlew test
```

## Configuration

### AdMob Setup (Before Release)

1. Create AdMob account at https://admob.google.com
2. Create app and ad units (rewarded + interstitial)
3. Update `app/build.gradle.kts`:
   - Replace test App ID in `manifestPlaceholders`
   - Replace ad unit IDs in `release` buildType

**Current Test IDs (debug only):**
- App ID: `ca-app-pub-3940256099942544~3347511713`
- Rewarded: `ca-app-pub-3940256099942544/5224354917`  
- Interstitial: `ca-app-pub-3940256099942544/1033173712`

### Privacy Policy

The in-app Privacy Policy, Terms of Service and Licenses screens live under
`ui/settings` and are reachable from About. Play also wants the privacy
policy at a public URL - publish the same text and paste the link into the
Play Console listing.

## Release Signing

Release builds are signed from credentials in `keystore.properties` at the
repo root. That file and every `*.jks` are gitignored; the committed
`keystore.properties.template` documents the expected keys.

Without a valid file the release build still succeeds but produces
`app-release-unsigned.apk` and prints a warning - so CI never silently ships
something unsigned, and a fresh clone still builds.

### One-time setup

```bash
keytool -genkeypair -v \
  -keystore arrowescape-release.jks \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias arrowescape

cp keystore.properties.template keystore.properties
# then fill in storePassword / keyPassword
```

Signing schemes: v1 is off (minSdk is 26, so nothing needs it), v2 and v3 are
what Android verifies, and v4 is on for incremental install on API 30+.

> **Back up the `.jks` and its passwords offline.** The upload key cannot be
> rotated after publishing without Google's key-reset process, and losing it
> means losing the ability to update the app.

## Store review

The in-app review sheet is offered on timing alone - never on sentiment.
Play's In-App Review guidelines forbid screening players ("do you like the
game?", then sending only the happy ones to the store) and forbid custom star
UI that triggers the flow, so there is no pre-prompt dialog here on purpose.

`review/ReviewPolicy` is pure Kotlin and holds every rule:

| Rule | Default |
| --- | --- |
| Levels cleared back to back | 3 |
| Minimum levels ever cleared | 6 |
| Levels of quiet between asks | 30 |
| Maximum asks, ever | 3 |

`ReviewPrompter` keeps the streak in DataStore and raises a flag;
`ReviewPromptEffect` - one line in each game screen - waits out the
celebration, then launches Play's flow. Play reports success even when it
shows nothing (quota spent, already reviewed), so only a genuine error falls
back to opening the store listing.

Nothing appears on a sideloaded build: the flow needs an app installed by
Play. Test it with an internal-testing track or Play's FakeReviewManager.

## Telemetry

Firebase Analytics and Crashlytics are wired up but **switched off**. The
checkedin `app/google-services.json` is a placeholder, so nothing is sent
anywhere until a real project replaces it.

### How it fits together

```
call site -> AnalyticsTracker.log(Events.foo(...))
                 |
      RateLimitedAnalyticsTracker     <- drops chatty events
                 |
      CompositeAnalyticsTracker       <- fans out, one bad sink cannot break the rest
         /          |         \
    Logcat    Firebase    Crashlytics breadcrumbs
```

- Event names and parameter keys live in **one** place: `analytics/Events`.
  Adding an event is a single factory function; no sink changes.
- `AnalyticsTracker` has exactly one method, so a new backend is one class.
- `Telemetry.create()` is the only place that decides which sinks exist.

### Rate limiting

`RateLimitedAnalyticsTracker` caps events that would otherwise burn the
Firebase quota (see `AnalyticsLimits`):

| Event | Cap | Why |
| --- | --- | --- |
| `valid_move`, `invalid_move` | 200/session, 1/sec | Fires on every tap |
| `ad_load_failed`, `ad_show_failed`, `ad_not_ready` | 20/session, 1/5sec | Arrives in retry bursts |
| `ad_load_requested` | 100/session, 1/sec | Funnel denominator only |

Impressions, rewards and revenue are **never** limited - that is the money.

### Ad callbacks

Every AdMob callback is logged, so the funnel is visible end to end:
`ad_load_requested` -> `ad_loaded` / `ad_load_failed` -> `ad_show_requested` ->
`ad_impression` -> `ad_clicked` -> `ad_reward_earned` -> `ad_dismissed`, plus
`ad_revenue` from the paid-event listener and `ad_not_ready` / `ad_show_failed`
on the sad paths. All carry an `ad_format` of `rewarded` or `interstitial`.

### Going live

1. Create a Firebase project and register `com.clevercorner.arrowescape`.
2. Replace `app/google-services.json` with the downloaded file.
3. Set `FIREBASE_ENABLED` to `true` in both buildTypes in `app/build.gradle.kts`.
4. Set `mappingFileUploadEnabled = true` in the `release` buildType so
   Crashlytics can deobfuscate R8 stack traces.
5. Disclose analytics collection in the privacy policy and the Play Console
   Data Safety form.

Until step 3, `Telemetry` falls back to Logcat-only and the manifest keeps
Firebase collection disabled.

## Play Store Checklist

- [ ] Replace AdMob test IDs with production IDs
- [ ] Replace placeholder `google-services.json` and enable `FIREBASE_ENABLED`
- [ ] Host the Privacy Policy at a public URL and link it in the Play Console
- [ ] Review the Terms, Privacy Policy and Licenses copy with a human lawyer
- [ ] Create store listing assets (screenshots, icon, feature graphic)
- [ ] Create signed release keystore (see Release Signing) and back it up
- [ ] Confirm `applicationId` is `com.clevercorner.arrowescape` - it cannot change later
- [ ] Build release AAB: `./gradlew bundleRelease`
- [ ] Test on multiple devices/screen sizes
- [ ] Complete Play Console app setup

## License

Proprietary - All rights reserved.

## Credits

Original implementation based on the Arrow Escape game specification.
