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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.eazyverse.testtrack.data.AuthRepo
import com.eazyverse.testtrack.data.Session
import com.eazyverse.testtrack.ui.*
import com.eazyverse.testtrack.ui.theme.TestTrackTheme

object Routes {
    const val ONBOARDING = "onboarding"
    const val AUTH = "auth"
    const val SETUP = "setup"
    const val TODAY = "today"
    const val GRID = "grid"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Session.init(this)
        setContent {
            TestTrackTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (Config.isConfigured) AppNav() else NotConfigured()
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

    // Session was loaded synchronously in onCreate, so the right screen shows immediately rather
    // than flashing onboarding and then jumping.
    val start = when {
        !Session.onboardingDone -> Routes.ONBOARDING
        !Session.signedIn -> Routes.AUTH
        !Session.setupComplete -> Routes.SETUP
        else -> Routes.TODAY
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
            SetupScreen(onFinished = { nav.replace(Routes.TODAY) })
        }

        composable(Routes.TODAY) {
            TodayScreen(
                onGrid = { nav.navigate(Routes.GRID) },
                onSignOut = {
                    AuthRepo.signOut()
                    Session.signOut()
                    nav.replace(Routes.AUTH)
                }
            )
        }

        composable(Routes.GRID) {
            GridScreen(onBack = { nav.popBackStack() })
        }
    }
}

/** Navigate forward and drop everything behind, so Back never re-enters a finished step. */
private fun NavHostController.replace(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
    }
}
