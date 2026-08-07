package com.eazyverse.testtrack

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eazyverse.testtrack.data.AuthRepo
import com.eazyverse.testtrack.data.Session
import com.eazyverse.testtrack.ui.*
import com.eazyverse.testtrack.ui.theme.TestTrackTheme

object Routes {
    const val ONBOARDING = "onboarding"
    const val AUTH = "auth"
    const val SETUP = "setup"
    const val HOME = "home"
    const val SUBMIT = "submit"

    const val GROUP = "group/{groupId}"
    fun group(groupId: String) = "group/$groupId"

    /** Your own app's 14-day picture. */
    const val DASHBOARD = "dashboard/{appId}"
    fun dashboard(appId: String) = "dashboard/$appId"
}

class MainActivity : ComponentActivity() {

    /** Set on the launch intent when a reminder is tapped. */
    private val openGroup = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        openGroup.value = intent?.getStringExtra(EXTRA_GROUP_ID)
        setContent {
            TestTrackTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Edge-to-edge is mandatory from Android 15, so every screen would otherwise
                    // start underneath the status bar. One inset here rather than in each screen;
                    // safeDrawing also covers the cutout, the nav bar and the keyboard.
                    Box(Modifier.safeDrawingPadding()) {
                        if (Config.isConfigured) AppNav(openGroup) else NotConfigured()
                    }
                }
            }
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
    }

    companion object {
        const val EXTRA_GROUP_ID = "groupId"
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
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun AppNav(openGroup: MutableState<String?> = remember { mutableStateOf(null) }) {
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
    val start = remember {
        when {
            !Session.onboardingDone -> Routes.ONBOARDING
            // Both halves must agree. The flags live in SharedPreferences, which Android's
            // auto-backup restores onto a fresh install; the Firebase session does not come back
            // with them. Trust the flags alone and the app sails past sign-in into a string of
            // PERMISSION_DENIEDs.
            !Session.signedIn || AuthRepo.uid == null -> Routes.AUTH
            !Session.setupComplete -> Routes.SETUP
            else -> Routes.HOME
        }
    }

    /**
     * A tapped reminder, honoured once.
     *
     * Only from [Routes.HOME]: a tester part-way through setup has no business being dropped into
     * a group screen, and a round in progress should not be yanked out from under them. Cleared
     * either way, so returning here later does not re-navigate.
     */
    val pending = openGroup.value
    LaunchedEffect(pending, start) {
        if (pending != null) {
            if (start == Routes.HOME) nav.push(Routes.group(pending))
            openGroup.value = null
        }
    }

    NavHost(navController = nav, startDestination = start) {

        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = {
                Session.updateOnboardingDone()
                nav.replace(Routes.AUTH)
            })
        }

        composable(Routes.AUTH) {
            AuthScreen(
                onSignedIn = {
                    // Signing back in on a phone that is already set up should not walk the
                    // checklist again. Every step but the first is derived from the account or
                    // from Settings, so by the time we are here the answer is already known.
                    nav.replace(if (Session.setupComplete) Routes.HOME else Routes.SETUP)
                }
            )
        }

        composable(Routes.SETUP) {
            SetupScreen(
                onFinished = { nav.replace(Routes.HOME) },
                // Same teardown as home. `replace` pops everything above the graph root, so
                // signing out of setup takes the home screen underneath it too — this route is
                // pushed from there, and leaving it on the stack would let Back return to a
                // signed-out home.
                onSignOut = {
                    AuthRepo.signOut()
                    Session.signOut()
                    nav.replace(Routes.AUTH)
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenGroup = { groupId -> nav.push(Routes.group(groupId)) },
                onSubmitApp = { nav.push(Routes.SUBMIT) },
                // Grants outlive the checklist: Drive access can be revoked from the account and
                // usage access from Settings, and both leave a working install that quietly cannot
                // report. Setup is where those are repaired, so it has to stay reachable.
                onOpenSetup = { nav.push(Routes.SETUP) },
                onSignOut = {
                    AuthRepo.signOut()
                    Session.signOut()
                    nav.replace(Routes.AUTH)
                }
            )
        }

        composable(Routes.SUBMIT) {
            SubmitScreen(
                onDone = { nav.popBackStack() },
                onBack = { nav.popBackStack() }
            )
        }

        composable(
            Routes.GROUP,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { entry ->
            GroupScreen(
                groupId = entry.arguments?.getString("groupId").orEmpty(),
                onOpenDashboard = { appId -> nav.push(Routes.dashboard(appId)) },
                onBack = { nav.popBackStack() }
            )
        }

        composable(
            Routes.DASHBOARD,
            arguments = listOf(navArgument("appId") { type = NavType.StringType })
        ) { entry ->
            DashboardScreen(
                appId = entry.arguments?.getString("appId").orEmpty(),
                onBack = { nav.popBackStack() }
            )
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
