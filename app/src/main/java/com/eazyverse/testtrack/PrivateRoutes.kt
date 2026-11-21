package com.eazyverse.testtrack

import android.net.Uri
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.eazyverse.testtrack.ui.CrewViewModel
import com.eazyverse.testtrack.ui.PrivateTestScreen
import com.eazyverse.testtrack.ui.PrivateTestersScreen
import com.eazyverse.testtrack.viewmodel.PrivateTestsViewModel

/** The two private testing pages: the owner's list for one app, and one test the reader runs. */
object PrivateRoutes {
    const val TESTERS = "private/testers/{seat}"
    const val TEST = "private/test/{id}"

    fun testers(seatId: String) = "private/testers/${Uri.encode(seatId)}"

    fun test(id: String) = "private/test/${Uri.encode(id)}"
}

/**
 * Both pages borrow the Crew tab's model and the private testers' one from the home entry
 * underneath, so an answer or an invite on a page is already on the tab behind it.
 */
internal fun NavGraphBuilder.privateRoutes(nav: NavHostController) {
    composable(PrivateRoutes.TESTERS, arguments = listOf(navArgument("seat") { type = NavType.StringType })) { entry ->
        val home = remember(entry) { runCatching { nav.getBackStackEntry(Routes.HOME) }.getOrNull() ?: entry }
        val crews: CrewViewModel = viewModel(home)
        val privates: PrivateTestsViewModel = viewModel(home)
        val seat = requireNotNull(entry.arguments?.getString("seat"))
        Inset { PrivateTestersScreen(crews, privates, seat, onBack = { nav.popBackStack() }) }
    }

    composable(PrivateRoutes.TEST, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
        val home = remember(entry) { runCatching { nav.getBackStackEntry(Routes.HOME) }.getOrNull() ?: entry }
        val crews: CrewViewModel = viewModel(home)
        val privates: PrivateTestsViewModel = viewModel(home)
        val id = requireNotNull(entry.arguments?.getString("id"))
        Inset { PrivateTestScreen(crews, privates, id, onBack = { nav.popBackStack() }) }
    }
}
