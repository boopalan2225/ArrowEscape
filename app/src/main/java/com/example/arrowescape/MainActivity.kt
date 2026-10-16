package com.example.arrowescape

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.example.arrowescape.ads.AdManager
import com.example.arrowescape.ads.AdManagerImpl
import com.example.arrowescape.analytics.AnalyticsTracker
import com.example.arrowescape.analytics.DebugAnalyticsTracker
import com.example.arrowescape.cloud.CloudSave
import com.example.arrowescape.cloud.CloudSyncCoordinator
import com.example.arrowescape.cloud.PlayGamesCloudSave
import com.example.arrowescape.data.GameMode
import com.example.arrowescape.data.GamePreferences
import com.example.arrowescape.ui.dev.DevLevelScreen
import com.example.arrowescape.ui.game.CurvedGameScreen
import com.example.arrowescape.ui.game.CurvedGameViewModel
import com.example.arrowescape.ui.game.CurvedGameViewModelFactory
import com.example.arrowescape.ui.game.GameScreen
import com.example.arrowescape.ui.game.GameViewModel
import com.example.arrowescape.ui.game.GameViewModelFactory
import com.example.arrowescape.ui.home.HomeScreen
import com.example.arrowescape.ui.milestone.MilestoneData
import com.example.arrowescape.ui.milestone.MilestoneScreen
import com.example.arrowescape.ui.settings.SettingsScreen
import com.example.arrowescape.ui.theme.AppTheme
import com.example.arrowescape.ui.theme.ArrowEscapeTheme

class MainActivity : ComponentActivity() {

    private lateinit var preferences: GamePreferences
    private lateinit var adManager: AdManager
    private lateinit var analytics: AnalyticsTracker
    private lateinit var cloudSave: CloudSave

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize dependencies
        preferences = GamePreferences(applicationContext)
        adManager = AdManagerImpl()
        analytics = DebugAnalyticsTracker() // Use NoOpAnalyticsTracker for production
        cloudSave = PlayGamesCloudSave()

        // Initialize ads
        adManager.initialize(applicationContext)

        // Play Games sign-in is best-effort and silent on launch - a player who
        // has never connected this app to Play Games just plays locally, same
        // as before this existed. See Settings for the explicit sign-in button.
        cloudSave.initialize(this)
        cloudSave.signInSilently()
        CloudSyncCoordinator(cloudSave, preferences).start(lifecycleScope)

        analytics.trackAppOpen()

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
                    if (loaded != null) {
                        ArrowEscapeApp(
                            preferences = preferences,
                            adManager = adManager,
                            analytics = analytics,
                            cloudSave = cloudSave
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
    cloudSave: CloudSave
) {
    val navController = rememberNavController()
    val prefs by preferences.prefsFlow.collectAsState(initial = GamePreferences.Prefs())

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
            HomeScreen(
                prefs = prefs,
                onPlayClick = {
                    analytics.trackGameStarted(prefs.currentLevel)
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
        }

        composable("dev") {
            DevLevelScreen(
                onBackClick = { navController.popBackStack() },
                onPlayLevel = { level -> navController.navigate("game/$level") }
            )
        }

        composable("milestones") {
            MilestoneScreen(
                prefs = prefs,
                onBackClick = { navController.popBackStack() },
                onPlayLevel = { level ->
                    analytics.trackGameStarted(level)
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
            GameRoute(
                mode = prefs.mode,
                level = backStackEntry.arguments?.getInt("level") ?: prefs.currentLevel,
                navController = navController,
                gameViewModel = gameViewModel,
                curvedGameViewModel = curvedGameViewModel
            )
        }

        composable("settings") {
            val scope = rememberCoroutineScope()
            val account by cloudSave.account.collectAsState()
            val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
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
                    scope.launch { preferences.setTheme(themeId) }
                },
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
