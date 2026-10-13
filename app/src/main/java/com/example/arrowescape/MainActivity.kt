package com.example.arrowescape

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import kotlinx.coroutines.launch
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.arrowescape.ads.AdManager
import com.example.arrowescape.ads.AdManagerImpl
import com.example.arrowescape.analytics.AnalyticsTracker
import com.example.arrowescape.analytics.DebugAnalyticsTracker
import com.example.arrowescape.data.GamePreferences
import com.example.arrowescape.ui.dev.DevLevelScreen
import com.example.arrowescape.ui.game.CurvedGameScreen
import com.example.arrowescape.ui.game.CurvedGameViewModel
import com.example.arrowescape.ui.game.CurvedGameViewModelFactory
import com.example.arrowescape.ui.game.GameScreen
import com.example.arrowescape.ui.game.GameViewModel
import com.example.arrowescape.ui.game.GameViewModelFactory
import com.example.arrowescape.ui.home.HomeScreen
import com.example.arrowescape.ui.milestone.MilestoneScreen
import com.example.arrowescape.ui.settings.SettingsScreen
import com.example.arrowescape.ui.theme.ArrowEscapeTheme

class MainActivity : ComponentActivity() {

    private lateinit var preferences: GamePreferences
    private lateinit var adManager: AdManager
    private lateinit var analytics: AnalyticsTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize dependencies
        preferences = GamePreferences(applicationContext)
        adManager = AdManagerImpl()
        analytics = DebugAnalyticsTracker() // Use NoOpAnalyticsTracker for production

        // Initialize ads
        adManager.initialize(applicationContext)

        analytics.trackAppOpen()

        setContent {
            ArrowEscapeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ArrowEscapeApp(
                        preferences = preferences,
                        adManager = adManager,
                        analytics = analytics
                    )
                }
            }
        }
    }
}

@Composable
fun ArrowEscapeApp(
    preferences: GamePreferences,
    adManager: AdManager,
    analytics: AnalyticsTracker
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
            HomeScreen(
                prefs = prefs,
                onPlayClick = {
                    analytics.trackGameStarted(prefs.currentLevel)
                    navController.navigate("game")
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

        // Game route WITH level argument (from Journey screen)
        composable(
            route = "game/{level}",
            arguments = listOf(navArgument("level") { type = NavType.IntType })
        ) { backStackEntry ->
            val level = backStackEntry.arguments?.getInt("level") ?: prefs.currentLevel
            
            if (prefs.curvedMode) {
                // Use curved arrow game (Option 2)
                LaunchedEffect(level) {
                    curvedGameViewModel.startLevel(level)
                }
                
                CurvedGameScreen(
                    viewModel = curvedGameViewModel,
                    onBackClick = {
                        curvedGameViewModel.saveGameState()
                        navController.popBackStack()
                    },
                    onHomeClick = {
                        curvedGameViewModel.saveGameState()
                        navController.popBackStack("home", inclusive = false)
                    }
                )
            } else {
                // Use legacy grid-based game
                LaunchedEffect(level) {
                    gameViewModel.startLevel(level)
                }
                
                GameScreen(
                    viewModel = gameViewModel,
                    onBackClick = {
                        gameViewModel.saveGameState()
                        navController.popBackStack()
                    },
                    onHomeClick = {
                        gameViewModel.saveGameState()
                        navController.popBackStack("home", inclusive = false)
                    }
                )
            }
        }

        // Game route WITHOUT level argument (from Home Play button)
        composable("game") {
            if (prefs.curvedMode) {
                // Use curved arrow game (Option 2)
                LaunchedEffect(prefs.currentLevel) {
                    curvedGameViewModel.startLevel(prefs.currentLevel)
                }
                
                CurvedGameScreen(
                    viewModel = curvedGameViewModel,
                    onBackClick = {
                        curvedGameViewModel.saveGameState()
                        navController.popBackStack()
                    },
                    onHomeClick = {
                        curvedGameViewModel.saveGameState()
                        navController.popBackStack("home", inclusive = false)
                    }
                )
            } else {
                // Use legacy grid-based game
                LaunchedEffect(prefs.currentLevel) {
                    gameViewModel.startLevel(prefs.currentLevel)
                }
                
                GameScreen(
                    viewModel = gameViewModel,
                    onBackClick = {
                        gameViewModel.saveGameState()
                        navController.popBackStack()
                    },
                    onHomeClick = {
                        gameViewModel.saveGameState()
                        navController.popBackStack("home", inclusive = false)
                    }
                )
            }
        }

        composable("settings") {
            val scope = rememberCoroutineScope()
            SettingsScreen(
                prefs = prefs,
                onSoundToggle = { enabled ->
                    gameViewModel.setSoundEnabled(enabled)
                },
                onHapticsToggle = { enabled ->
                    gameViewModel.setHapticsEnabled(enabled)
                },
                onCurvedModeToggle = { enabled ->
                    scope.launch { preferences.setCurvedMode(enabled) }
                },
                onDevModeToggle = { enabled ->
                    scope.launch { preferences.setDevMode(enabled) }
                },
                onBackClick = {
                    navController.popBackStack()
                },
                onUnlockAllLevels = {
                    scope.launch { preferences.unlockAllLevels() }
                },
                onResetProgress = {
                    scope.launch { preferences.resetProgress() }
                }
            )
        }
    }
}
