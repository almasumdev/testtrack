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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eazyverse.testtrack.data.AppUpdateService
import com.eazyverse.testtrack.data.AuthRepo
import com.eazyverse.testtrack.data.FeedbackWatch
import com.eazyverse.testtrack.data.PushRepo
import com.eazyverse.testtrack.data.Session
import com.eazyverse.testtrack.data.UpdateTier
import com.eazyverse.testtrack.domain.LaunchDestination
import com.eazyverse.testtrack.domain.launchDestination
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.ui.*
import com.eazyverse.testtrack.ui.JoinGroupDialog
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import com.eazyverse.testtrack.ui.theme.exchangeBackground
import com.eazyverse.testtrack.viewmodel.AppsViewModel
import com.eazyverse.testtrack.viewmodel.MembershipViewModel
import com.eazyverse.testtrack.viewmodel.ThemeChoice
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

object Routes {
    const val ONBOARDING = "onboarding"

    // There is no sign-in route. It is a sheet over whatever asked for it; see ui/SignInSheet.

    /** Not one screen: the five tabbed ones, held by [com.eazyverse.testtrack.ui.MainShell]. */
    const val HOME = "home"

    /** Asking the circle to look at your app. A page, because finding the app is its own step. */
    const val ABOUT = "about"
    const val REVIEW = "review?post={post}"

    fun review(post: String) = "review?post=${Uri.encode(post)}"

    /** A review of your app, or a case to decide, on its own page. */
    const val CHECK = "check/{id}"

    fun check(id: String) = "check/${Uri.encode(id)}"

    /** One crew on its own page. */
    const val CREW = "crew/{id}"
    const val CREW_MEMBERS = "crew/{id}/members"

    fun crew(id: String) = "crew/${Uri.encode(id)}"

    fun crewMembers(id: String) = "crew/${Uri.encode(id)}/members"

    /** Putting an app from My apps into a crew: its name for the crew, its sign-in, the seat. */
    const val CREW_ADD = "crew-add/{app}"

    fun crewAdd(appId: String) = "crew-add/${Uri.encode(appId)}"

    /** Asking for feedback on an app from My apps. */
    const val ASK_APP = "ask/{app}"

    fun askFor(appId: String) = "ask/${Uri.encode(appId)}"

    /** An app on My apps, on its own page. */
    const val APP = "app/{id}"

    fun app(id: String) = "app/${Uri.encode(id)}"

    /** Adding an app to My apps. */
    const val APP_ADD = "app-add"
}

class MainActivity : ComponentActivity() {
    /** Set when a crew notification was tapped, until the shell has switched to the Crew tab. */
    private val openCrew = mutableStateOf(false)

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
        openCrew.value = intent?.getBooleanExtra(EXTRA_OPEN_CREW, false) == true
        setContent {
            // The theme chosen on the You page, or the phone's own. See [ThemeChoice].
            TestTrackTheme(darkTheme = ThemeChoice.isDark(isSystemInDarkTheme())) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Edge-to-edge is mandatory from Android 15. Every pushed screen takes the safe
                    // drawing inset through [Inset] (status bar, cutout, nav bar and keyboard); the
                    // tab shell takes only the top one, because its ground and its floating dock run
                    // to the bottom edge under the system bar.
                    Box(Modifier.fillMaxSize().exchangeBackground()) {
                        if (Config.isConfigured) {
                            AppNav(
                                openCrew,
                                updateOffer(updateLauncher, ::openPlayStoreListing),
                            )
                        } else {
                            Inset { NotConfigured() }
                        }

                        // The testers group, checked in front of the person as soon as they sign
                        // in, and quietly on every launch after. See [MembershipViewModel].
                        val membership: MembershipViewModel = viewModel()
                        var wasSignedIn by remember { mutableStateOf(Session.signedIn) }
                        LaunchedEffect(Session.signedIn) {
                            // Feedback news while the app is closed. See [FeedbackWatch].
                            if (Session.signedIn) FeedbackWatch.schedule(this@MainActivity)
                            // Where our server can reach this phone. See [PushRepo.toServer].
                            if (Session.signedIn) PushRepo.toServer()
                            if (Session.signedIn && !wasSignedIn) membership.afterSignIn() else membership.quietly()
                            wasSignedIn = Session.signedIn
                        }
                        JoinGroupDialog(membership)

                        // Once per install: stop hearing from the old version's push. On its own, so
                        // a slow first Firestore connection never holds up anything above.
                        LaunchedEffect(Session.signedIn) { PushRepo.leaveOldSystem(this@MainActivity, AuthRepo.uid) }

                        /*
                         * Over everything, including the nav host.
                         *
                         * A build the server has moved past is not one screen out of date, it is
                         * wrong everywhere, so this cannot be something
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

    /** Where the update gate and button send somebody Play will not download for in the background. */
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
     * on: ignoring the thing the tester actually tapped.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_CREW, false)) openCrew.value = true
    }

    companion object {
        /** A crew notification was tapped: open the Crew tab. */
        const val EXTRA_OPEN_CREW = "openCrew"
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
    openCrew: MutableState<Boolean> = remember { mutableStateOf(false) },
    /** An optional update for the header's button; null when Play has none. */
    update: UpdateOffer? = null,
) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()

    /**
     * Where the app opens, decided once.
     *
     * `remember` is load-bearing, not an optimisation. Session flags are Compose state, so reading
     * them here unwrapped made this recompute on sign-out: the NavHost's `startDestination`
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
                update = update,
                onReview = { nav.push(Routes.review(it)) },
                onCheck = { nav.push(Routes.check(it)) },
                onOpenAbout = { nav.push(Routes.ABOUT) },
                onOpenCrew = { nav.push(Routes.crew(it)) },
                onCrewForApp = { nav.push(Routes.crewAdd(it)) },
                onAskForApp = { nav.push(Routes.askFor(it)) },
                onOpenApp = { nav.push(Routes.app(it)) },
                onAddApp = { nav.push(Routes.APP_ADD) },
                openCrew = openCrew,
                // The same teardown the other two do, and for the same reason: `replace` pops
                // everything above the graph root, so the shell does not survive underneath a
                // signed-out profile for Back to land on.
                onSignOut = {
                    scope.launch {
                        // Before the session goes: the request needs it.
                        PushRepo.forgetOnServer()
                        AuthRepo.signOut()
                        Session.signOut()
                        // Back to the app rather than out of it. There is something worth looking at
                        // signed out now, so sending somebody to a sign-in screen for choosing to
                        // sign out would be taking the app away as a punishment.
                        nav.replace(Routes.HOME)
                    }
                },
                // Pushed, not replaced: whoever asked was standing on a tab and wants to end up
                // back on it with the thing they wanted finally on it.
            )
        }

        composable(Routes.REVIEW, arguments = listOf(navArgument("post") { type = NavType.StringType })) { entry ->
            // The small post snapshot is saved in the back stack, including across process recreation.
            val post = remember(entry) { Json.decodeFromString<ApiPost>(requireNotNull(entry.arguments?.getString("post"))) }
            Inset {
                ReviewScreen(post = post, onClose = { nav.popBackStack() })
            }
        }

        composable(Routes.CHECK, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            // The Circle tab's own view model, borrowed from the home entry underneath: the review
            // is already loaded there, and deciding it there reloads the list this goes back to.
            val home = remember(entry) { nav.getBackStackEntry(Routes.HOME) }
            val feed: FeedViewModel = viewModel(home)
            Inset {
                CheckScreen(
                    feed,
                    requireNotNull(entry.arguments?.getString("id")),
                    onClose = { nav.popBackStack() },
                    onOpen = { nav.push(Routes.check(it)) },
                )
            }
        }

        composable(Routes.CREW, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            // The Crew tab's own view model, borrowed from the home entry underneath, as a case
            // borrows Circle's: the crew is already loaded there and opens on the first frame.
            val home = remember(entry) { nav.getBackStackEntry(Routes.HOME) }
            val crews: CrewViewModel = viewModel(home)
            val apps: AppsViewModel = viewModel(home)
            val id = requireNotNull(entry.arguments?.getString("id"))
            Inset {
                CrewPage(
                    crews,
                    id,
                    onBack = { nav.popBackStack() },
                    onSignIn = { nav.popBackStack() },
                    // Put it in again: the same app, from My apps, or added first if it is not there.
                    onAddToCrew = {
                        val pkg =
                            crews.crews
                                .firstOrNull { it.id == id }
                                ?.mine
                                ?.packageName
                        val app = apps.apps.firstOrNull { it.pkg == pkg }
                        nav.push(if (app != null) Routes.crewAdd(app.id) else Routes.APP_ADD)
                    },
                    onMembers = { nav.push(Routes.crewMembers(id)) },
                )
            }
        }

        composable(Routes.CREW_MEMBERS, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val home = remember(entry) { nav.getBackStackEntry(Routes.HOME) }
            val crews: CrewViewModel = viewModel(home)
            Inset { CrewMembersPage(crews, requireNotNull(entry.arguments?.getString("id")), onBack = { nav.popBackStack() }) }
        }

        composable(Routes.CREW_ADD, arguments = listOf(navArgument("app") { type = NavType.StringType })) { entry ->
            // Taken on the Crew tab's model, so the seat is already there on the way back; the
            // app is My apps' own, so nothing about it is asked again.
            val home = remember(entry) { nav.getBackStackEntry(Routes.HOME) }
            val crews: CrewViewModel = viewModel(home)
            val apps: AppsViewModel = viewModel(home)
            val context = LocalContext.current
            val appId = requireNotNull(entry.arguments?.getString("app"))
            LaunchedEffect(appId) { if (apps.byId(appId) == null) apps.load(context) }
            Inset {
                CrewAddScreen(crews, apps.byId(appId), onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
            }
        }

        composable(Routes.ASK_APP, arguments = listOf(navArgument("app") { type = NavType.StringType })) { entry ->
            val home = remember(entry) { nav.getBackStackEntry(Routes.HOME) }
            val apps: AppsViewModel = viewModel(home)
            val context = LocalContext.current
            val appId = requireNotNull(entry.arguments?.getString("app"))
            LaunchedEffect(appId) { if (apps.byId(appId) == null) apps.load(context) }
            Inset {
                AskScreen(
                    onDone = { nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                    app = apps.byId(appId),
                    loading = apps.loading,
                )
            }
        }

        composable(Routes.APP, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val home = remember(entry) { nav.getBackStackEntry(Routes.HOME) }
            val apps: AppsViewModel = viewModel(home)
            val membership = rememberMembership()
            Inset {
                AppPage(
                    apps,
                    requireNotNull(entry.arguments?.getString("id")),
                    onBack = { nav.popBackStack() },
                    onOpenCrew = { nav.push(Routes.crew(it)) },
                    onOpenRequest = { nav.push(Routes.check(it)) },
                    onCrew = { appId -> membership.require { nav.push(Routes.crewAdd(appId)) } },
                    onFeedback = { nav.push(Routes.askFor(it)) },
                )
            }
        }

        composable(Routes.APP_ADD) {
            Inset {
                AppAddScreen(
                    // Straight to the new app's page, with the add page gone from behind it.
                    onAdded = { id ->
                        nav.popBackStack()
                        nav.push(Routes.app(id))
                    },
                    onBack = { nav.popBackStack() },
                )
            }
        }

        composable(Routes.ABOUT) {
            Inset { AboutScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

/**
 * Navigate forward and drop everything behind, so Back never re-enters a finished step.
 *
 * Pops up to the graph, **exclusive**: every destination above the root goes, the root graph
 * entry stays. Both halves matter, and both were wrong before:
 *
 *  - Popping up to the *start destination* only works once. The first replace removes it, and
 *    every later `popUpTo` then names a destination no longer on the stack, matches nothing, and
 *    pops nothing, so finishing setup left `[SETUP, HOME]` and Back went back to setup.
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
