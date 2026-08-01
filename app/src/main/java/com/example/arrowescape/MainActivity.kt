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
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.arrowescape.ads.AdManager
import com.example.arrowescape.ads.AdManagerImpl
import com.example.arrowescape.analytics.AnalyticsTracker
import com.example.arrowescape.analytics.DebugAnalyticsTracker
import com.example.arrowescape.data.GamePreferences
import com.example.arrowescape.ui.game.GameScreen
import com.example.arrowescape.ui.game.GameViewModel
import com.example.arrowescape.ui.game.GameViewModelFactory
import com.example.arrowescape.ui.home.HomeScreen
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

    // Shared ViewModel for game state
    val context = androidx.compose.ui.platform.LocalContext.current
    val gameViewModel: GameViewModel = viewModel(
        factory = GameViewModelFactory(preferences, adManager, analytics, context)
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
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }

        composable("game") {
            GameScreen(
                viewModel = gameViewModel,
                onBackClick = {
                    gameViewModel.saveGameState()
                    navController.popBackStack()
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                prefs = prefs,
                onSoundToggle = { enabled ->
                    gameViewModel.setSoundEnabled(enabled)
                },
                onHapticsToggle = { enabled ->
                    gameViewModel.setHapticsEnabled(enabled)
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
