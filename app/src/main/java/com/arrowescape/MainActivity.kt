package com.arrowescape

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arrowescape.ads.AdManager
import com.arrowescape.ads.AdManagerImpl
import com.arrowescape.analytics.AnalyticsTracker
import com.arrowescape.analytics.Events
import com.arrowescape.analytics.Telemetry
import com.arrowescape.crash.CrashKeys
import com.arrowescape.crash.CrashReporter
import com.arrowescape.cloud.CloudSave
import com.arrowescape.cloud.CloudSyncCoordinator
import com.arrowescape.cloud.PlayGamesCloudSave
import com.arrowescape.data.GameMode
import com.arrowescape.data.GamePreferences
import com.arrowescape.ui.dev.DevLevelScreen
import com.arrowescape.ui.game.CurvedGameScreen
import com.arrowescape.ui.game.CurvedGameViewModel
import com.arrowescape.ui.game.CurvedGameViewModelFactory
import com.arrowescape.ui.game.GameScreen
import com.arrowescape.ui.game.GameFeedback
import com.arrowescape.ui.game.GameViewModel
import com.arrowescape.ui.game.GameViewModelFactory
import com.arrowescape.ui.home.HomeScreen
import com.arrowescape.ui.milestone.MilestoneData
import com.arrowescape.ui.milestone.MilestoneScreen
import com.arrowescape.ui.settings.AboutScreen
import com.arrowescape.ui.settings.LicensesScreen
import com.arrowescape.ui.settings.PrivacyPolicyScreen
import com.arrowescape.ui.settings.TermsOfServiceScreen
import com.arrowescape.ui.settings.SettingsScreen
import com.arrowescape.ui.theme.AppTheme
import com.arrowescape.ui.theme.ArrowEscapeTheme
import com.arrowescape.ui.tutorial.GameTour
import com.arrowescape.ui.tutorial.HomeTour
import com.arrowescape.ui.tutorial.LocalSpotlight
import com.arrowescape.ui.tutorial.SpotlightOverlay
import com.arrowescape.ui.tutorial.SpotlightState

class MainActivity : ComponentActivity() {

    private lateinit var preferences: GamePreferences
    private lateinit var adManager: AdManager
    private lateinit var analytics: AnalyticsTracker
    private lateinit var crashReporter: CrashReporter
    private lateinit var cloudSave: CloudSave

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize dependencies
        // Telemetry comes first: everything built after it can be reported on.
        val telemetry = Telemetry.create(applicationContext)
        analytics = telemetry.analytics
        crashReporter = telemetry.crashReporter

        preferences = GamePreferences(applicationContext)
        adManager = AdManagerImpl(analytics)
        cloudSave = PlayGamesCloudSave()

        // Initialize ads
        adManager.initialize(applicationContext)

        // Play Games sign-in is best-effort and silent on launch - a player who
        // has never connected this app to Play Games just plays locally, same
        // as before this existed. See Settings for the explicit sign-in button.
        cloudSave.initialize(this)
        cloudSave.signInSilently()
        CloudSyncCoordinator(cloudSave, preferences).start(lifecycleScope)

        analytics.log(Events.appOpen())

        // Crash reports are far easier to triage with the player's mode and
        // level attached, so keep those keys current for the whole session.
        lifecycleScope.launch {
            preferences.prefsFlow.collect { prefs ->
                crashReporter.setKey(CrashKeys.GAME_MODE, prefs.mode.name)
                crashReporter.setKey(CrashKeys.CURRENT_LEVEL, prefs.currentLevel)
            }
        }

        setContent {
            // null until preferences load, so a saved theme never flashes the default first.
            val loaded by preferences.prefsFlow.collectAsState(initial = null)
            val theme = AppTheme.resolve(loaded?.themeId, isSystemInDarkTheme())

            // One-time move of a pre-per-mode save into its owning mode.
            LaunchedEffect(Unit) { preferences.migrateLegacyProgress() }

            ArrowEscapeTheme(theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Seeding with the loaded value matters: a default Prefs
                    // would claim the tutorial was never seen, and returning
                    // players would get a flash of coach marks.
                    loaded?.let { initial ->
                        ArrowEscapeApp(
                            preferences = preferences,
                            adManager = adManager,
                            analytics = analytics,
                            cloudSave = cloudSave,
                            initialPrefs = initial
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArrowEscapeApp(
    preferences: GamePreferences,
    adManager: AdManager,
    analytics: AnalyticsTracker,
    cloudSave: CloudSave,
    initialPrefs: GamePreferences.Prefs
) {
    val navController = rememberNavController()
    val prefs by preferences.prefsFlow.collectAsState(initial = initialPrefs)

    // Shared ViewModels for game state
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Grid-based (legacy) game ViewModel
    val gameViewModel: GameViewModel = viewModel(
        factory = GameViewModelFactory(preferences, adManager, analytics, context)
    )
    
    // Curved arrow game ViewModel (Option 2 revamp)
    val curvedGameViewModel: CurvedGameViewModel = viewModel(
        factory = CurvedGameViewModelFactory(preferences, adManager, analytics, context)
    )

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            val scope = rememberCoroutineScope()
            // The controls report where they are, the tour reads it; Home
            // itself knows nothing about being toured.
            val spotlight = remember { SpotlightState() }

            CompositionLocalProvider(LocalSpotlight provides spotlight) {
                Box(Modifier.fillMaxSize()) {
                    HomeScreen(
                prefs = prefs,
                onPlayClick = {
                    analytics.log(Events.gameStarted(prefs.currentLevel))
                    navController.navigate("game/${prefs.currentLevel}")
                },
                onModeSelect = { mode ->
                    scope.launch { preferences.setGameMode(mode) }
                },
                onJourneyClick = {
                    navController.navigate("milestones")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                        onDevClick = {
                            navController.navigate("dev")
                        }
                    )

                    if (!prefs.tutorialDone) {
                        SpotlightOverlay(
                            state = spotlight,
                            steps = HomeTour.steps,
                            onFinish = {
                                scope.launch { preferences.setTutorialDone(true) }
                            }
                        )
                    }
                }
            }
        }

        composable("dev") {
            DevLevelScreen(
                onBackClick = { navController.popBackStack() },
                onPlayLevel = { level -> navController.navigate("game/$level") }
            )
        }

        composable("about") {
            AboutScreen(
                onBackClick = { navController.popBackStack() },
                onTermsClick = { navController.navigate("terms") },
                onPrivacyClick = { navController.navigate("privacy") },
                onLicensesClick = { navController.navigate("licenses") }
            )
        }

        composable("terms") {
            TermsOfServiceScreen(onBackClick = { navController.popBackStack() })
        }

        composable("licenses") {
            LicensesScreen(onBackClick = { navController.popBackStack() })
        }

        composable("privacy") {
            PrivacyPolicyScreen(onBackClick = { navController.popBackStack() })
        }

        composable("milestones") {
            MilestoneScreen(
                prefs = prefs,
                onBackClick = { navController.popBackStack() },
                onPlayLevel = { level ->
                    analytics.log(Events.gameStarted(level))
                    navController.navigate("game/$level")
                }
            )
        }

        // The only game route. Level always comes from a fixed nav
        // argument, never from a live-recomposing prefs value. There used
        // to be a second route with no argument for the Home Play button,
        // reading prefs.currentLevel directly - the moment a level
        // completed and bumped that value, this screen own LaunchedEffect
        // on level saw a new key and silently restarted into the next
        // level, skipping the completion dialog entirely. One route,
        // always a captured argument, removes the trap.
        composable(
            route = "game/{level}",
            arguments = listOf(navArgument("level") { type = NavType.IntType })
        ) { backStackEntry ->
            val scope = rememberCoroutineScope()
            val spotlight = remember { SpotlightState() }

            CompositionLocalProvider(LocalSpotlight provides spotlight) {
                Box(Modifier.fillMaxSize()) {
                    GameRoute(
                        mode = prefs.mode,
                        level = backStackEntry.arguments?.getInt("level") ?: prefs.currentLevel,
                        navController = navController,
                        gameViewModel = gameViewModel,
                        curvedGameViewModel = curvedGameViewModel
                    )

                    if (!prefs.gameTourDone) {
                        // Arrows left is the honest signal that a guided move
                        // landed - no timers, no guessing.
                        val classicState by gameViewModel.gameState.collectAsState()
                        val curvedState by curvedGameViewModel.gameState.collectAsState()
                        val arrowsLeft = when (prefs.mode) {
                            GameMode.CLASSIC -> classicState.remainingCount
                            GameMode.CURVED -> curvedState.remainingCount
                        }

                        SpotlightOverlay(
                            state = spotlight,
                            steps = GameTour.steps,
                            progress = arrowsLeft,
                            onFinish = {
                                scope.launch { preferences.setGameTourDone(true) }
                            }
                        )
                    }
                }
            }
        }

        composable("settings") {
            val scope = rememberCoroutineScope()
            val account by cloudSave.account.collectAsState()
            val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
            val auditionTheme = rememberThemeAudition(context)
            SettingsScreen(
                prefs = prefs,
                account = account,
                onSignInClick = { activity?.let(cloudSave::signIn) },
                onSignOutClick = { cloudSave.signOut() },
                onSoundToggle = { enabled ->
                    gameViewModel.setSoundEnabled(enabled)
                },
                onHapticsToggle = { enabled ->
                    gameViewModel.setHapticsEnabled(enabled)
                },
                onDevModeToggle = { enabled ->
                    scope.launch { preferences.setDevMode(enabled) }
                },
                onThemeSelect = { themeId ->
                    auditionTheme(themeId, prefs.soundEnabled)
                    scope.launch { preferences.setTheme(themeId) }
                },
                onAboutClick = { navController.navigate("about") },
                onHowToPlayClick = {
                    // Replaying means arming both tours and going back to Home;
                    // the in-game half lights up again on the next level opened.
                    scope.launch { preferences.setWalkthroughsDone(false) }
                    navController.popBackStack("home", inclusive = false)
                },
                onPrivacyClick = { navController.navigate("privacy") },
                onBackClick = {
                    navController.popBackStack()
                },
                onUnlockAllLevels = { mode ->
                    scope.launch {
                        preferences.unlockAllLevels(mode, MilestoneData.forMode(mode).totalLevels)
                    }
                },
                onResetProgress = { mode ->
                    scope.launch { preferences.resetProgress(mode) }
                }
            )
        }
    }
}

/**
 * Plays whichever look you just tapped in Settings, so a theme is heard as
 * well as seen. Released with the screen that asked for it.
 */
@Composable
private fun rememberThemeAudition(context: android.content.Context): (String, Boolean) -> Unit {
    val systemDark = isSystemInDarkTheme()
    val feedback = remember { GameFeedback(context.applicationContext) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }

    return { themeId, soundEnabled ->
        feedback.useTheme(AppTheme.resolve(themeId, systemDark))
        if (soundEnabled) feedback.playSuccessSound()
    }
}

/**
 * Plays [level] in [mode]. The two engines have identical navigation needs, so
 * the only thing that differs is which screen and ViewModel get used.
 */
@Composable
private fun GameRoute(
    mode: GameMode,
    level: Int,
    navController: NavHostController,
    gameViewModel: GameViewModel,
    curvedGameViewModel: CurvedGameViewModel
) {
    val leaveToPreviousScreen = { navController.popBackStack(); Unit }
    val leaveToHome = { navController.popBackStack("home", inclusive = false); Unit }

    when (mode) {
        GameMode.CURVED -> {
            LaunchedEffect(level) { curvedGameViewModel.startLevel(level) }
            CurvedGameScreen(
                viewModel = curvedGameViewModel,
                onBackClick = {
                    curvedGameViewModel.saveGameState()
                    leaveToPreviousScreen()
                },
                onHomeClick = {
                    curvedGameViewModel.saveGameState()
                    leaveToHome()
                }
            )
        }

        GameMode.CLASSIC -> {
            LaunchedEffect(level) { gameViewModel.startLevel(level) }
            GameScreen(
                viewModel = gameViewModel,
                onBackClick = {
                    gameViewModel.saveGameState()
                    leaveToPreviousScreen()
                },
                onHomeClick = {
                    gameViewModel.saveGameState()
                    leaveToHome()
                }
            )
        }
    }
}
