package com.eazyverse.testtrack

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.eazyverse.testtrack.data.Session
import com.eazyverse.testtrack.ui.BlockedMembersScreen
import com.eazyverse.testtrack.ui.CrewChatScreen
import com.eazyverse.testtrack.ui.CrewViewModel
import com.eazyverse.testtrack.viewmodel.CrewChatViewModel

/**
 * A crew's chat, on its own page over the crew's. It borrows the Crew tab's model from the home
 * entry underneath, for the crew's name and its members' profiles; the chat itself has a model
 * of its own, made and dropped with the page.
 */
internal fun NavGraphBuilder.chatRoutes(nav: NavHostController) {
    composable(Routes.CREW_CHAT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
        val home = remember(entry) { runCatching { nav.getBackStackEntry(Routes.HOME) }.getOrNull() ?: entry }
        val crews: CrewViewModel = viewModel(home)
        val chat: CrewChatViewModel = viewModel()
        val id = requireNotNull(entry.arguments?.getString("id"))
        Inset {
            CrewChatScreen(
                chat,
                crews,
                id,
                onBack = { nav.popBackStack() },
                onMembers = { nav.push(Routes.crewMembers(id)) },
            )
        }
    }

    composable(Routes.BLOCKED) {
        Inset { BlockedMembersScreen(onBack = { nav.popBackStack() }) }
    }
}

/** A tapped chat notification: that crew's chat, once someone is signed in to read it. */
@Composable
internal fun OpenChatOnTap(
    nav: NavHostController,
    openChat: MutableState<String?>,
) {
    val id = openChat.value
    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        openChat.value = null
        if (Session.signedIn) nav.push(Routes.crewChat(id))
    }
}
