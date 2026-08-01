# Arrow Escape

A minimalist, offline-first 2D puzzle game for Android.

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
- Clean, minimal UI

## Tech Stack

- **Kotlin** - Primary language
- **Jetpack Compose** - UI framework
- **Material 3** - Design system
- **ViewModel + Flow** - State management
- **DataStore** - Persistence
- **Google Mobile Ads SDK** - Monetization
- **JUnit** - Unit testing

## Project Structure

```
com.example.arrowescape/
  - game/
    - model/       # ArrowTile, Direction, GameState
    - engine/      # GameEngine, CollisionChecker, GameSolver
    - level/       # LevelGenerator, DifficultyConfig
  - ui/
    - home/        # Home screen
    - game/        # Game screen + ViewModel
    - settings/    # Settings screen
    - components/  # Reusable UI components
    - theme/       # Colors, typography
  - ads/           # AdManager abstraction
  - analytics/     # Analytics abstraction
  - data/          # GamePreferences (DataStore)
  - MainActivity.kt
```

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

Before publishing, create a privacy policy and update the Settings screen URL.

## Play Store Checklist

- [ ] Replace AdMob test IDs with production IDs
- [ ] Create privacy policy
- [ ] Create store listing assets (screenshots, icon, feature graphic)
- [ ] Create signed release keystore
- [ ] Build release AAB: `./gradlew bundleRelease`
- [ ] Test on multiple devices/screen sizes
- [ ] Complete Play Console app setup

## License

Proprietary - All rights reserved.

## Credits

Original implementation based on the Arrow Escape game specification.
