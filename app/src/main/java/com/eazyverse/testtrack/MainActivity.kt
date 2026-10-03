package com.eazyverse.testtrack

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eazyverse.testtrack.data.AppUpdateService
import com.eazyverse.testtrack.data.AuthRepo
import com.eazyverse.testtrack.data.CaptureService
import com.eazyverse.testtrack.data.Session
import com.eazyverse.testtrack.data.UpdateTier
import com.eazyverse.testtrack.data.leftRound
import com.eazyverse.testtrack.domain.LaunchDestination
import com.eazyverse.testtrack.domain.launchDestination
import com.eazyverse.testtrack.ui.*
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import com.eazyverse.testtrack.ui.theme.exchangeBackground
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING = "onboarding"

    // There is no sign-in route. It is a sheet over whatever asked for it; see ui/SignInSheet.

    /** Not one screen: the five tabbed ones, held by [com.eazyverse.testtrack.ui.MainShell]. */
    const val HOME = "home"
    const val SUBMIT = "submit"

    /** Asking the circle to look at your app. A page, because finding the app is its own step. */
    const val ASK = "ask"

    // The name is carried along, not looked up: whoever is navigating here almost always already
    // has it on screen (a row in Home, a row in Group), and showing that immediately in the app
    // bar beats leaving it blank for the gap before this screen's own load answers the same
    // question. Optional — the notification-tap path has no row to read a name from, and falls
    // back to the ordinary blank-then-populate the screen already does without one.
    // feedbackFor is the same idea again: a notification asking for feedback about one specific
    // app carries that app's id along, so the group screen underneath can open straight to its
    // feedback prompt instead of leaving the tester to find the right row themselves.
    const val GROUP = "group/{groupId}?name={name}&feedbackFor={feedbackFor}"

    fun group(
        groupId: String,
        name: String = "",
        feedbackFor: String = "",
    ) = "group/$groupId?name=${Uri.encode(name)}&feedbackFor=${Uri.encode(feedbackFor)}"

    /** Your own app's 14-day picture. */
    const val DASHBOARD = "dashboard/{appId}?name={name}"

    fun dashboard(
        appId: String,
        name: String = "",
    ) = "dashboard/$appId?name=${Uri.encode(name)}"
}

class MainActivity : ComponentActivity() {
    /** Set on the launch intent when a reminder is tapped. */
    private val openGroup = mutableStateOf<String?>(null)

    /** Set alongside [openGroup] when the tap was specifically an ask for feedback about one app. */
    private val openFeedbackFor = mutableStateOf<String?>(null)

    /**
     * Play hands back an IntentSender rather than an Intent, so the update flow needs its own
     * contract. Registered on the activity rather than in a composable because the blocked gate sits
     * over every screen, and the result is ignored: cancelling is an answer, and the gate stays up
     * with its button until the update is in.
     */
    private val updateLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.finishIconAnimation(this, this)
        // Both system bars fully clear, with no scrim Android would otherwise lay under the
        // navigation bar: the page and the floating dock run to the bottom edge behind it.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        Session.init(this)
        // The last update decision proven online, until Play answers in onStart. Here rather than in
        // App because it reads Session, which is only loaded now.
        AppUpdateService.applyCachedDecision()
        openGroup.value = intent?.getStringExtra(EXTRA_GROUP_ID)
        openFeedbackFor.value = intent?.getStringExtra(EXTRA_FEEDBACK_APP_ID)
        setContent {
            TestTrackTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Edge-to-edge is mandatory from Android 15. Every pushed screen takes the safe
                    // drawing inset through [Inset] (status bar, cutout, nav bar and keyboard); the
                    // tab shell takes only the top one, because its ground and its floating dock run
                    // to the bottom edge under the system bar.
                    Box(Modifier.fillMaxSize().exchangeBackground()) {
                        if (Config.isConfigured) AppNav(openGroup, openFeedbackFor) else Inset { NotConfigured() }

                        /*
                         * Over everything, including the nav host.
                         *
                         * A build that no longer counts a day the way its cohort does is not one
                         * screen out of date, it is wrong everywhere, so this cannot be something
                         * a screen opts into. See [UpdateGate] for why the tier is resolved
                         * against Play first: this only ever appears when there is genuinely an
                         * update to be had.
                         */
                        val tier by AppUpdateService.tier.collectAsState()
                        val downloaded by AppUpdateService.isDownloaded.collectAsState()
                        if (tier == UpdateTier.BLOCKED) {
                            Inset {
                                ForceUpdateScreen(
                                    downloaded = downloaded,
                                    canDownloadInApp = AppUpdateService.canDownloadInApp,
                                    onUpdate = {
                                        if (!AppUpdateService.startFlexibleUpdate(updateLauncher)) openPlayStoreListing()
                                    },
                                    onRestart = { AppUpdateService.completeUpdate() },
                                    onOpenStore = { openPlayStoreListing() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Coming back here in the middle of a round ends the round.
     *
     * The service opens the next app on a timer and does not care where the tester is, so without
     * this a round carried on after somebody had walked away from it: they would arrive back at
     * TestTrack, get a few seconds, and be taken into the next app again.
     *
     * Here rather than on a screen, so it holds however they got back — recents, the launcher, the
     * notification, the back gesture out of the app under test. A round that ended on its own has
     * already cleared itself before it brings the app forward, so this cannot fire on the normal
     * path.
     */
    override fun onResume() {
        super.onResume()
        CaptureService.leftRound(this)
    }

    override fun onStart() {
        super.onStart()
        AppUpdateService.registerInstallListener()
        // Every time the app comes forward, because the answer changes without us: a rollout
        // reaches this device, an admin raises the floor, a download finishes while we were away.
        lifecycleScope.launch { AppUpdateService.refresh() }
    }

    override fun onStop() {
        super.onStop()
        AppUpdateService.unregisterInstallListener()
    }

    /** Where the blocked gate sends somebody Play will not download for in the background. */
    private fun openPlayStoreListing() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * A reminder tapped while TestTrack is already running.
     *
     * The launch intent is `singleTop` + `clearTop`, so this arrives here rather than through
     * another `onCreate`, and without it the app would come forward on whatever screen it was left
     * on — ignoring the thing the tester actually tapped.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_GROUP_ID)?.let { openGroup.value = it }
        intent.getStringExtra(EXTRA_FEEDBACK_APP_ID)?.let { openFeedbackFor.value = it }
    }

    companion object {
        const val EXTRA_GROUP_ID = "groupId"
        const val EXTRA_FEEDBACK_APP_ID = "feedbackAppId"
    }
}

/** Compose gives us a Context; the Google authorization APIs need the Activity behind it. */
fun Context.findActivity(): Activity {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    error("no Activity in context chain")
}

@Composable
private fun NotConfigured() {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Text(
            "Not configured. Copy local.properties.example to local.properties, fill in the " +
                "keys and rebuild.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
fun AppNav(
    openGroup: MutableState<String?> = remember { mutableStateOf(null) },
    openFeedbackFor: MutableState<String?> = remember { mutableStateOf(null) },
) {
    val nav = rememberNavController()

    /**
     * Where the app opens, decided once.
     *
     * `remember` is load-bearing, not an optimisation. Session flags are Compose state, so reading
     * them here unwrapped made this recompute on sign-out — the NavHost's `startDestination`
     * changed underneath a navigation that was popping up to it, the back stack came out empty and
     * the app closed instead of landing on sign-in.
     *
     * Session was loaded synchronously in onCreate, so the right screen shows immediately rather
     * than flashing onboarding and then jumping.
     */
    val start =
        remember {
            when (launchDestination(Session.onboardingDone, Session.signedIn, AuthRepo.uid != null)) {
                LaunchDestination.INTRO -> Routes.ONBOARDING
                LaunchDestination.HOME -> Routes.HOME
            }
        }

    // Keep the tapped reminder until onboarding reaches home. A page change during
    // onboarding must not consume it or navigate past the introduction.
    val currentEntry by nav.currentBackStackEntryAsState()
    val route = currentEntry?.destination?.route
    val pending = openGroup.value
    LaunchedEffect(pending, route) {
        if (pending != null && route != null && route != Routes.ONBOARDING) {
            if (route == Routes.HOME) {
                nav.push(Routes.group(pending, feedbackFor = openFeedbackFor.value.orEmpty()))
            }
            openGroup.value = null
            openFeedbackFor.value = null
        }
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.ONBOARDING) {
            Inset {
                OnboardingScreen(onDone = {
                    Session.updateOnboardingDone()
                    nav.replace(Routes.HOME)
                })
            }
        }

        composable(Routes.HOME) {
            MainShell(
                onOpenGroup = { groupId, name -> nav.push(Routes.group(groupId, name)) },
                onSubmitApp = { nav.push(Routes.SUBMIT) },
                onAskForFeedback = { nav.push(Routes.ASK) },
                // The same teardown the other two do, and for the same reason: `replace` pops
                // everything above the graph root, so the shell does not survive underneath a
                // signed-out profile for Back to land on.
                onSignOut = {
                    AuthRepo.signOut()
                    Session.signOut()
                    // Back to the app rather than out of it. There is something worth looking at
                    // signed out now, so sending somebody to a sign-in screen for choosing to
                    // sign out would be taking the app away as a punishment.
                    nav.replace(Routes.HOME)
                },
                // Pushed, not replaced: whoever asked was standing on a tab and wants to end up
                // back on it with the thing they wanted finally on it.
            )
        }

        composable(Routes.ASK) {
            Inset {
                AskScreen(
                    onDone = { nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
        }

        composable(Routes.SUBMIT) {
            Inset {
                SubmitScreen(
                    onDone = { nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
        }

        composable(
            Routes.GROUP,
            arguments =
                listOf(
                    navArgument("groupId") { type = NavType.StringType },
                    navArgument("name") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("feedbackFor") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
        ) { entry ->
            Inset {
                GroupScreen(
                    groupId = entry.arguments?.getString("groupId").orEmpty(),
                    groupName = entry.arguments?.getString("name").orEmpty(),
                    feedbackForAppId = entry.arguments?.getString("feedbackFor").orEmpty(),
                    onOpenDashboard = { appId, name -> nav.push(Routes.dashboard(appId, name)) },
                    onBack = { nav.popBackStack() },
                )
            }
        }

        composable(
            Routes.DASHBOARD,
            arguments =
                listOf(
                    navArgument("appId") { type = NavType.StringType },
                    navArgument("name") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
        ) { entry ->
            Inset {
                DashboardScreen(
                    appId = entry.arguments?.getString("appId").orEmpty(),
                    appName = entry.arguments?.getString("name").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

/**
 * Navigate forward and drop everything behind, so Back never re-enters a finished step.
 *
 * Pops up to the graph, **exclusive** — every destination above the root goes, the root graph
 * entry stays. Both halves matter, and both were wrong before:
 *
 *  - Popping up to the *start destination* only works once. The first replace removes it, and
 *    every later `popUpTo` then names a destination no longer on the stack, matches nothing, and
 *    pops nothing — so finishing setup left `[SETUP, HOME]` and Back went back to setup.
 *  - Popping the graph *inclusive* takes the controller's own entry with it, leaving nothing to
 *    return to, and the activity finishes.
 */
private fun NavHostController.replace(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = false }
        launchSingleTop = true
    }
}

/** Forward, keeping what is behind. Single-top so a double tap cannot stack the same screen. */
private fun NavHostController.push(route: String) {
    navigate(route) { launchSingleTop = true }
}

/** The safe drawing inset, for every screen that is not the tab shell. */
@Composable
private fun Inset(content: @Composable () -> Unit) {
    Box(Modifier.safeDrawingPadding()) { content() }
}
