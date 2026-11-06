package com.clevercorner.arrowescape

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clevercorner.arrowescape.ads.AdManager
import com.clevercorner.arrowescape.analytics.AnalyticsTracker
import com.clevercorner.arrowescape.analytics.Events
import com.clevercorner.arrowescape.crash.CrashKeys
import com.clevercorner.arrowescape.crash.CrashReporter
import com.clevercorner.arrowescape.cloud.CloudSave
import com.clevercorner.arrowescape.cloud.CloudSyncCoordinator
import com.clevercorner.arrowescape.cloud.PlayGamesCloudSave
import com.clevercorner.arrowescape.data.GameMode
import com.clevercorner.arrowescape.data.GamePreferences
import com.clevercorner.arrowescape.economy.CoinBank
import com.clevercorner.arrowescape.economy.CoinRules
import com.clevercorner.arrowescape.game.daily.DailyCalendar
import com.clevercorner.arrowescape.notifications.DailyReminderWorker
import com.clevercorner.arrowescape.notifications.rememberNotificationPermissionLauncher
import com.clevercorner.arrowescape.review.ReviewPolicy
import com.clevercorner.arrowescape.review.ReviewState
import com.clevercorner.arrowescape.ui.coins.CoinRewardHost
import com.clevercorner.arrowescape.ui.coins.CoinScreen
import com.clevercorner.arrowescape.ui.coins.rememberCoinAdLauncher
import com.clevercorner.arrowescape.ui.review.RatingScreen
import com.clevercorner.arrowescape.ui.daily.DailyScreen
import com.clevercorner.arrowescape.ui.dev.DevLevelScreen
import com.clevercorner.arrowescape.ui.game.CurvedGameScreen
import com.clevercorner.arrowescape.ui.game.CurvedGameViewModel
import com.clevercorner.arrowescape.ui.game.CurvedGameViewModelFactory
import com.clevercorner.arrowescape.ui.game.GameScreen
import com.clevercorner.arrowescape.ui.game.GameFeedback
import com.clevercorner.arrowescape.ui.game.GameViewModel
import com.clevercorner.arrowescape.ui.game.GameViewModelFactory
import com.clevercorner.arrowescape.ui.home.HomeScreen
import com.clevercorner.arrowescape.ui.milestone.MilestoneData
import com.clevercorner.arrowescape.ui.milestone.MilestoneScreen
import com.clevercorner.arrowescape.ui.settings.AboutScreen
import com.clevercorner.arrowescape.ui.settings.LicensesScreen
import com.clevercorner.arrowescape.ui.settings.PrivacyPolicyScreen
import com.clevercorner.arrowescape.ui.settings.TermsOfServiceScreen
import com.clevercorner.arrowescape.ui.settings.SettingsScreen
import com.clevercorner.arrowescape.ui.theme.AppTheme
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme
import com.clevercorner.arrowescape.ui.tutorial.GameTour
import com.clevercorner.arrowescape.ui.tutorial.HomeTour
import com.clevercorner.arrowescape.ui.tutorial.LocalSpotlight
import com.clevercorner.arrowescape.ui.tutorial.SpotlightOverlay
import com.clevercorner.arrowescape.ui.tutorial.SpotlightState
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    // Process-scoped collaborators, borrowed rather than built: see
    // ArrowEscapeApplication for why an Activity must not own them.
    private val app: ArrowEscapeApplication get() = application as ArrowEscapeApplication
    private val preferences: GamePreferences get() = app.preferences
    private val adManager: AdManager get() = app.adManager
    private val analytics: AnalyticsTracker get() = app.analytics
    private val crashReporter: CrashReporter get() = app.crashReporter
    private val feedback: GameFeedback get() = app.feedback
    private val coinBank: CoinBank get() = app.coinBank

    /** Activity-scoped: signing in needs an Activity to show a dialog over. */
    private lateinit var cloudSave: CloudSave

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        cloudSave = PlayGamesCloudSave()

        // Nothing below this line is needed to draw the first frame, and all
        // of it is slow: MobileAds.initialize does disk and IPC work that
        // Google explicitly recommends keeping off the main thread, and Play
        // Games sign-in talks to another process. Run on the main thread
        // during onCreate, they simply postponed the splash.
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { adManager.initialize(applicationContext) }

            // Warming the ad cache is the app's call, not a ViewModel's. It
            // used to happen in both game ViewModels' constructors - during
            // composition, twice, for something no player sees for minutes.
            adManager.loadRewardedAd(applicationContext)
            adManager.loadInterstitialAd(applicationContext)

            // Play Games sign-in is best-effort and silent on launch - a player
            // who has never connected this app to Play Games just plays
            // locally. See Settings for the explicit sign-in button.
            cloudSave.initialize(this@MainActivity)
            cloudSave.signInSilently()
            CloudSyncCoordinator(cloudSave, preferences).start(lifecycleScope)

            analytics.log(Events.appOpen())
        }

        // Every theme has its own voice. One listener for the whole app -
        // the board sounds the same in both modes, so this was never a
        // per-ViewModel concern.
        feedback.followTheme(preferences.prefsOrDefault, lifecycleScope)

        // Coins settle themselves: the bank watches saved progress and pays
        // whatever the rules say is owed. No screen has to remember to.
        coinBank.start(lifecycleScope)

        // Crash reports are far easier to triage with the player's mode and
        // level attached, so keep those keys current for the whole session.
        lifecycleScope.launch {
            preferences.prefs.filterNotNull().collect { prefs ->
                crashReporter.setKey(CrashKeys.GAME_MODE, prefs.mode.name)
                crashReporter.setKey(CrashKeys.CURRENT_LEVEL, prefs.currentLevel)
            }
        }

        // The app cannot honestly draw anything until it knows the player's
        // theme and whether they have seen the tutorial. Holding the splash
        // for that first disk read is the honest way to wait - the window
        // used to sit blank and white instead, which reads as a slow app.
        splash.setKeepOnScreenCondition { preferences.prefs.value == null }

        setContent {
            val loaded by preferences.prefs.collectAsState()
            // `owned`, not `resolve`: a paid theme the wallet cannot account
            // for falls back to the phone's own light or dark rather than
            // being worn for free. Dev mode is the one exception, and it is a
            // view over the wallet rather than a change to it.
            val theme = AppTheme.owned(
                savedId = loaded?.themeId,
                systemDark = isSystemInDarkTheme(),
                unlocked = AppTheme.unlockedIds(
                    owned = loaded?.wallet?.unlockedThemes.orEmpty(),
                    devMode = loaded?.devMode == true
                )
            )

            // One-time move of a pre-per-mode save into its owning mode.
            LaunchedEffect(Unit) { preferences.migrateLegacyProgress() }

            ArrowEscapeTheme(theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Gated on the real value: a default Prefs would claim
                    // the tutorial was never seen, and returning players
                    // would get a flash of coach marks.
                    if (loaded != null) {
                        Box(Modifier.fillMaxSize()) {
                            ArrowEscapeApp(
                                preferences = preferences,
                                adManager = adManager,
                                analytics = analytics,
                                cloudSave = cloudSave,
                                feedback = feedback,
                                coinBank = coinBank
                            )

                            // Above every screen, because a grant can settle
                            // while the player is on any of them - mid-daily,
                            // mid-journey, or five seconds after launch.
                            CoinRewardHost(coinBank)
                        }
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
    feedback: GameFeedback,
    coinBank: CoinBank
) {
    val navController = rememberNavController()
    // Not seeded with a caller-supplied value: this composable is only
    // reached once the real preferences have loaded, so the shared flow is
    // already holding them.
    val prefs by preferences.prefsOrDefault.collectAsState()

    // One launcher for every "watch an ad for coins" button in the app; the
    // grant is claimed in the reward callback, never on dismissal.
    val watchAdForCoins = rememberCoinAdLauncher(adManager, coinBank)
    val scopeForReview = rememberCoroutineScope()

    /** Remembers a rating decision, then leaves the screen. */
    val finishRating: (ReviewState) -> Unit = { state ->
        scopeForReview.launch { preferences.saveReviewState(state) }
        navController.popBackStack()
    }

    // Shared ViewModels for game state
    val context = androidx.compose.ui.platform.LocalContext.current

    // Grid-based (legacy) game ViewModel
    val gameViewModel: GameViewModel = viewModel(
        factory = GameViewModelFactory(preferences, adManager, analytics, feedback, context)
    )

    // Curved arrow game ViewModel (Option 2 revamp)
    val curvedGameViewModel: CurvedGameViewModel = viewModel(
        factory = CurvedGameViewModelFactory(preferences, adManager, analytics, feedback, context)
    )

    /**
     * Opens [level] in the current mode's game screen.
     *
     * The board starts building here, before the screen exists, so the level
     * the player tapped is the one named in the header from the very first
     * frame - and the generator has a head start on the navigation animation.
     * `startLevel` is idempotent, so the game screen asking for the same
     * level a moment later costs nothing.
     */
    val playLevel: (Int) -> Unit = { level ->
        when (prefs.mode) {
            GameMode.CURVED -> curvedGameViewModel.startLevel(level)
            GameMode.CLASSIC -> gameViewModel.startLevel(level)
        }
        navController.navigate("game/$level")
    }

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
                    playLevel(prefs.currentLevel)
                },
                onModeSelect = { mode ->
                    scope.launch { preferences.setGameMode(mode) }
                },
                onJourneyClick = {
                    navController.navigate("milestones")
                },
                onDailyClick = {
                    navController.navigate("daily")
                },
                onCoinsClick = {
                    navController.navigate("coins")
                },
                onRateClick = {
                    navController.navigate("rate")
                },
                onRateDismiss = {
                    // "Not now" spends one of the quota and resets the run,
                    // so the invitation needs a fresh streak to come back.
                    scope.launch {
                        preferences.saveReviewState(
                            ReviewPolicy.afterAsk(prefs.review, prefs.totalLevelsCompleted)
                        )
                    }
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
                            },
                            // Skip ends the tutorial, not the page. Marking
                            // only the Home half done used to drop the
                            // player into the in-game tour moments later,
                            // which reads as the app ignoring them.
                            onSkip = {
                                scope.launch { preferences.setWalkthroughsDone(true) }
                            }
                        )
                    }
                }
            }
        }

        composable("dev") {
            DevLevelScreen(
                onBackClick = { navController.popBackStack() },
                onPlayLevel = playLevel
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

        // Today's puzzle. No level argument on purpose: the date is the
        // argument, and it comes from the clock, not from the back stack.
        composable("daily") {
            DailyScreen(
                puzzle = DailyCalendar.puzzleFor(LocalDate.now()),
                prefs = prefs,
                viewModel = curvedGameViewModel,
                doubler = CoinRules.dailyDoublerOffer(prefs.wallet, prefs.daily, LocalDate.now()),
                adReady = adManager.isRewardedAdReady(),
                onWatchAd = watchAdForCoins,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("terms") {
            TermsOfServiceScreen(onBackClick = { navController.popBackStack() })
        }

        // The purse: what it holds, how it fills, and who to invite.
        composable("coins") {
            CoinScreen(
                wallet = prefs.wallet,
                daily = prefs.daily,
                // Both journeys count: a chapter is a chapter, whichever
                // game you beat it in. Each mode's own chapter length, via
                // chaptersClearedFor - see ArrowEscapeApplication.coinBank
                // for why a single shared constant here was wrong.
                chaptersCleared = GameMode.entries.sumOf { mode ->
                    val journey = MilestoneData.forMode(mode)
                    val highest = prefs.progressOf(mode).highestLevel
                    journey.chaptersClearedFor(highest)
                        .coerceIn(0, journey.chapterCountFor(highest))
                },
                adOffer = CoinRules.adOffer(prefs.wallet, LocalDate.now()),
                adReady = adManager.isRewardedAdReady(),
                onWatchAd = watchAdForCoins,
                ensureReferralCode = coinBank::myReferralCode,
                onRedeem = { code -> coinBank.redeem(code, prefs.wallet) },
                onBackClick = { navController.popBackStack() }
            )
        }

        // Reached by choice only - from Settings, or the card on Home. The
        // automatic path is still Play's own sheet; see RatingScreen.
        composable("rate") {
            RatingScreen(
                onRateClick = {
                    // Navigation to the Play listing lands later; the
                    // decision is already worth remembering, because a
                    // player who said yes must never be asked again.
                    finishRating(ReviewPolicy.afterRated(prefs.review))
                },
                onLaterClick = {
                    finishRating(ReviewPolicy.afterAsk(prefs.review, prefs.totalLevelsCompleted))
                }
            )
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
                    playLevel(level)
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
                            // The tour is cut to the mode being played:
                            // curved boards have no turn pads or locked
                            // pairs to teach.
                            steps = GameTour.steps(prefs.mode),
                            progress = arrowsLeft,
                            onFinish = {
                                scope.launch { preferences.setGameTourDone(true) }
                            },
                            onSkip = {
                                scope.launch { preferences.setWalkthroughsDone(true) }
                            }
                        )
                    }
                }
            }
        }

        composable("settings") {
            val scope = rememberCoroutineScope()
            val account by cloudSave.account.collectAsState()
            val activity = context as? android.app.Activity
            val auditionTheme = rememberThemeAudition(feedback)
            val requestNotificationPermission = rememberNotificationPermissionLauncher { granted ->
                if (granted) {
                    scope.launch { preferences.setNotificationsEnabled(true) }
                    DailyReminderWorker.schedule(context.applicationContext)
                }
            }
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
                onNotificationsToggle = { enabled ->
                    if (enabled) {
                        requestNotificationPermission()
                        scope.launch { preferences.setNotificationsEnabled(true) }
                        DailyReminderWorker.schedule(context.applicationContext)
                    } else {
                        scope.launch { preferences.setNotificationsEnabled(false) }
                        DailyReminderWorker.cancel(context.applicationContext)
                    }
                },
                onDevModeToggle = { enabled ->
                    scope.launch { preferences.setDevMode(enabled) }
                },
                onThemeSelect = { themeId ->
                    auditionTheme(themeId, prefs.soundEnabled)
                    scope.launch { preferences.setTheme(themeId) }
                },
                onBuyTheme = { theme -> coinBank.buyTheme(theme.id) },
                onCoinsClick = { navController.navigate("coins") },
                onRateClick = { navController.navigate("rate") },
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
                        // A dev convenience - but never a downgrade. See
                        // Journey.devUnlockLevelCount for why this needs the
                        // player's own current progress as well as the
                        // catalogue: a fixed target (even a dynamic one
                        // scaled off the catalogue alone) still goes stale
                        // the moment unbounded dev-stepping walks past it.
                        val highest = prefs.progressOf(mode).highestLevel
                        val maxLevel = MilestoneData.forMode(mode).devUnlockLevelCount(highest)
                        preferences.unlockAllLevels(mode, maxLevel)
                    }
                },
                onResetProgress = { mode ->
                    scope.launch { preferences.resetProgress(mode) }
                },
                onResetCoins = {
                    scope.launch { preferences.resetWallet() }
                }
            )
        }
    }
}

/**
 * Plays whichever look you just tapped in Settings, so a theme is heard as
 * well as seen. Uses the app's own voice rather than a private one: a second
 * SoundPool to preview a sound the app can already make is pure duplication,
 * and the pack it leaves behind is the one the player just chose anyway.
 */
@Composable
private fun rememberThemeAudition(feedback: GameFeedback): (String, Boolean) -> Unit {
    val systemDark = isSystemInDarkTheme()

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
    val leaveToPreviousScreen = {
        if (!navController.popBackStack()) {
            navController.popBackStack("home", inclusive = false)
        }
        Unit
    }
    val leaveToHome = { navController.popBackStack("home", inclusive = false); Unit }

    when (mode) {
        GameMode.CURVED -> {
            // Normally a no-op: whoever navigated here already dealt this
            // level, which is why the header names it from the first frame.
            // It matters after process death, when the back stack is
            // restored onto this screen with nobody to deal the board.
            LaunchedEffect(level) { curvedGameViewModel.resumeLevel(level) }
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
            // See the curved branch: a restoration net, not a second request.
            LaunchedEffect(level) { gameViewModel.resumeLevel(level) }
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
