package com.eazyverse.testtrack

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.eazyverse.testtrack.ui.CrewSeatScreen
import com.eazyverse.testtrack.ui.CrewViewModel
import com.eazyverse.testtrack.ui.FeedViewModel
import com.eazyverse.testtrack.viewmodel.AppsViewModel

/** What the Ask page leaves on the app page's entry when a request has gone up, for its message line. */
private const val POSTED_NOTE = "posted_note"

/**
 * The pages about one app on My apps: its own page, adding one, and feedback. Putting it in a crew is
 * the seat screen, opened from its page. Each borrows My apps' model from the home entry underneath, so an
 * app added or changed on one is the same app on all of them.
 */
internal fun NavGraphBuilder.appRoutes(
    nav: NavHostController,
    openCrew: MutableState<Boolean>,
    openCircle: MutableState<Boolean>,
) {
    composable(Routes.ASK_APP, arguments = listOf(navArgument("app") { type = NavType.StringType })) { entry ->
        val home = remember(entry) { nav.getBackStackEntry(Routes.HOME) }
        val apps: AppsViewModel = viewModel(home)
        val feed: FeedViewModel = viewModel(home)
        val context = LocalContext.current
        val appId = requireNotNull(entry.arguments?.getString("app"))
        LaunchedEffect(appId) { if (apps.byId(appId) == null) apps.load(context) }
        Inset {
            AskScreen(
                // Back to the app page, which says the request is up and lists it under Feedback.
                onDone = {
                    feed.load(context)
                    nav.previousBackStackEntry
                        ?.takeIf { it.destination.route == Routes.APP }
                        ?.savedStateHandle
                        ?.set(POSTED_NOTE, "Your request is on Circle.")
                    nav.popBackStack()
                },
                onBack = { nav.popBackStack() },
                app = apps.byId(appId),
                loading = apps.loading || apps.reading,
                offline = apps.offline,
                onRetry = { apps.load(context) },
                // Back to the shell, on the Circle tab, where reviews earn tix.
                onOpenCircle = {
                    openCircle.value = true
                    nav.popBackStack(Routes.HOME, false)
                },
            )
        }
    }

    composable(Routes.APP, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
        // My apps' model for the app, the Crew tab's for its crew tile and Circle's for its
        // feedback requests, all borrowed from the home entry underneath.
        val home = remember(entry) { nav.getBackStackEntry(Routes.HOME) }
        val apps: AppsViewModel = viewModel(home)
        val crews: CrewViewModel = viewModel(home)
        val feed: FeedViewModel = viewModel(home)
        val context = LocalContext.current
        val id = requireNotNull(entry.arguments?.getString("id"))
        // Circle may not have been opened yet: read it once, unless a read is already on its way.
        LaunchedEffect(Unit) { if (!feed.answered && feed.loadsStarted == feed.loadsFinished) feed.load(context) }
        val pkg = apps.byId(id)?.pkg
        val posted by entry.savedStateHandle.getStateFlow<String?>(POSTED_NOTE, null).collectAsState()
        Inset {
            AppPage(
                apps,
                id,
                onBack = { nav.popBackStack() },
                onOpenCrew = { nav.push(Routes.crew(it)) },
                onOpenRequest = { nav.push(Routes.check(it)) },
                onFeedback = { nav.push(Routes.askFor(it)) },
                // Check opens the first review waiting on the owner, in To check's order; the card opens the request.
                onCheck = { request ->
                    val waiting = request.reviews.filter { it.state == "submitted" }.map { it.id }
                    val first = feed.waiting.firstOrNull { it.id in waiting }?.id ?: waiting.firstOrNull()
                    nav.push(Routes.check(first ?: request.id))
                },
                crews = crews,
                requests = feed.requests.filter { it.packageName == pkg },
                requestsLoading = !feed.answered && feed.requests.isEmpty(),
                onSeat = { nav.push(Routes.crewSeat(it)) },
                onOpenChat = { nav.push(Routes.crewChat(it)) },
                message = posted,
                onMessageTaken = { entry.savedStateHandle[POSTED_NOTE] = null },
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

    composable(Routes.CREW_SEAT, arguments = listOf(navArgument("app") { type = NavType.StringType })) { entry ->
        // The Crew tab's own model takes the seat, so the tab shows it on the way back, and My apps'
        // model keeps the boxes, both borrowed from the home entry underneath.
        val home = remember(entry) { nav.getBackStackEntry(Routes.HOME) }
        val crews: CrewViewModel = viewModel(home)
        val apps: AppsViewModel = viewModel(home)
        Inset {
            CrewSeatScreen(
                crews,
                apps,
                requireNotNull(entry.arguments?.getString("app")),
                onBack = { nav.popBackStack() },
                // Back to the Crew tab, where the new seat is.
                onDone = {
                    openCrew.value = true
                    nav.popBackStack(Routes.HOME, false)
                },
                // Back to the shell, on the Circle tab, where reviews earn tix.
                onOpenCircle = {
                    openCircle.value = true
                    nav.popBackStack(Routes.HOME, false)
                },
                onOpenCrew = { nav.push(Routes.crew(it)) },
            )
        }
    }
}
