package com.eazyverse.testtrack

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        setContent {
            TestTrackTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Edge-to-edge is mandatory from Android 15, so every screen would otherwise
                    // start underneath the status bar. One inset here rather than in each screen;
                    // safeDrawing also covers the cutout, the nav bar and the keyboard.
                    Box(Modifier.safeDrawingPadding()) {
                        if (Config.isConfigured) AppNav() else NotConfigured()
                    }
                }
            }
        }
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
fun AppNav() {
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

    NavHost(navController = nav, startDestination = start) {

        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = {
                Session.updateOnboardingDone()
                nav.replace(Routes.AUTH)
            })
        }

        composable(Routes.AUTH) {
            // Setup decides for itself which steps are still outstanding.
            AuthScreen(onSignedIn = { nav.replace(Routes.SETUP) })
        }

        composable(Routes.SETUP) {
            SetupScreen(onFinished = { nav.replace(Routes.HOME) })
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenGroup = { groupId -> nav.navigate(Routes.group(groupId)) },
                onSubmitApp = { nav.navigate(Routes.SUBMIT) },
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
                onOpenDashboard = { appId -> nav.navigate(Routes.dashboard(appId)) },
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
 * Pops up to the start destination, not the graph. Popping the graph itself takes the
 * NavController's whole stack with it and the activity finishes — which is what "sign out closes
 * the app" turned out to be. This is only safe because [AppNav] remembers its start destination,
 * so the thing being popped to does not move when session state changes.
 */
private fun NavHostController.replace(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
    }
}
