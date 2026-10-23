package com.eazyverse.testtrack

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.eazyverse.testtrack.ui.AppAddScreen
import com.eazyverse.testtrack.ui.AppPage
import com.eazyverse.testtrack.ui.AskScreen
import com.eazyverse.testtrack.ui.CrewAddScreen
import com.eazyverse.testtrack.ui.CrewViewModel
import com.eazyverse.testtrack.ui.rememberMembership
import com.eazyverse.testtrack.viewmodel.AppsViewModel

/**
 * The pages about one app on My apps: its own page, adding one, and the two things it can be put
 * up for, a crew and feedback. Each borrows My apps' model from the home entry underneath, so an
 * app added or changed on one is the same app on all of them.
 */
internal fun NavGraphBuilder.appRoutes(nav: NavHostController) {
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
            CrewAddScreen(
                crews,
                apps.byId(appId),
                onDone = { nav.popBackStack() },
                onBack = { nav.popBackStack() },
                loading = apps.loading || apps.reading,
                offline = apps.offline,
                onRetry = { apps.load(context) },
            )
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
                loading = apps.loading || apps.reading,
                offline = apps.offline,
                onRetry = { apps.load(context) },
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

    composable(Routes.APP_ADD) { entry ->
        val home = remember(entry) { nav.getBackStackEntry(Routes.HOME) }
        val apps: AppsViewModel = viewModel(home)
        Inset {
            AppAddScreen(
                // Straight to the new app's page, with the add page gone from behind it. The
                // app goes on My apps' list first, so its page has it before the list is read.
                onAdded = { app ->
                    apps.put(app)
                    nav.popBackStack()
                    nav.push(Routes.app(app.id))
                },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
