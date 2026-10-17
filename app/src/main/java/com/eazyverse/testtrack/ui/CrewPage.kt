package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.viewmodel.report
import com.eazyverse.testtrack.viewmodel.vote

/**
 * One crew on a page of its own, opened from any of Crew's tabs or from its app on My apps.
 *
 * It was drawn inside the Crew tab, over the tab row, with the dock still under it, so it read as
 * the tab changing rather than somewhere new, and the system's back went to the tab's own
 * handler rather than the way every other page here goes back. Now it is a page like a review or
 * a case, on the Crew tab's own model, so it opens with the crew already there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewPage(
    vm: CrewViewModel,
    id: String,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onAddToCrew: () -> Unit,
) {
    val context = LocalContext.current
    val dialogs = rememberCrewDialogs()
    val crew = vm.crews.firstOrNull { it.id == id }
    LaunchedEffect(id) { if (crew == null) vm.load(context) }
    // Shown once and then gone from your crews: you took your app out, or it was sent back. The
    // page has nothing left to show, so it closes rather than waiting for a crew that is not coming.
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(crew != null) {
        if (crew != null) {
            seen = true
        } else if (seen) {
            onBack()
        }
    }
    CrewLifecycle(vm)
    val actions = crewActions(vm, dialogs, onSignIn, onAddToCrew)

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                // Its name and where it has got to: the only thing saying which crew this is.
                PageBar(crew?.name ?: "Crew", onBack) {
                    crew?.let {
                        Text(
                            it.standing,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                PageMessage(vm.message, vm::clearMessage)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                crew == null && vm.offline -> RobotOffline("This crew didn't load", onRetry = { vm.load(context) })
                crew == null && !vm.loading ->
                    RobotState(
                        Prop.Notes,
                        "You're not in this crew",
                        "Your app was taken out or sent back, so this crew is no longer yours to see.",
                        action = "Back",
                        onAction = onBack,
                    )
                crew == null -> CrewLobbyLoading()
                crew.phase == CrewPhase.LOBBY -> CrewLobbyPhase(crew, actions)
                crew.phase == CrewPhase.RUN -> CrewRunPhase(crew, actions)
                else -> CrewRecordPhase(crew)
            }
        }
    }
    CrewDialogsHost(vm, dialogs)
}

/** The seat a dialog is about, for each dialog a crew's rows can open. */
internal class CrewDialogs {
    /** Being given up, confirmed first: it is a place fourteen people share. */
    var leaving by mutableStateOf<CrewSeat?>(null)

    /** Being reported: its seat id, name and owner. */
    var reporting by mutableStateOf<CrewSeat?>(null)

    /** Answered "it didn't install", which asks why first. */
    var refusing by mutableStateOf<CrewSeat?>(null)
}

@Composable
internal fun rememberCrewDialogs() = remember { CrewDialogs() }

/** What a crew's rows can do, wired the same on the tab and on a crew's page. */
@Composable
internal fun crewActions(
    vm: CrewViewModel,
    dialogs: CrewDialogs,
    onSignIn: () -> Unit,
    onAddToCrew: () -> Unit,
): CrewActions {
    val context = LocalContext.current
    val membership = rememberMembership()
    return CrewActions(
        onOpen = { app -> membership.require { vm.openApp(context, app) } },
        onOpenAll = { id -> membership.require { vm.openAll(context, id) } },
        onInstall = { seat -> membership.require { openOnPlay(context, seat.packageName) } },
        onReport = { dialogs.reporting = it },
        onLeave = { dialogs.leaving = it },
        onVote = { seat, yes -> if (yes) vm.vote(context, seat, installed = true) else dialogs.refusing = seat },
        // Signed in and on the testers group first, then the page that puts an app in.
        onTakeSeat = { if (vm.signedOut) onSignIn() else membership.require { onAddToCrew() } },
    )
}

/**
 * Coming back from somebody's app is the whole interaction, so resume is where a visit is
 * measured and recorded, and where the crew's own news starts being listened to again.
 */
@Composable
internal fun CrewLifecycle(vm: CrewViewModel) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watcher =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        vm.settle(context)
                        vm.startLive(context)
                    }
                    Lifecycle.Event.ON_PAUSE -> vm.stopLive()
                    else -> Unit
                }
            }
        owner.lifecycle.addObserver(watcher)
        onDispose {
            owner.lifecycle.removeObserver(watcher)
            vm.stopLive()
        }
    }
}

/** The dialogs a crew's rows open, and the usage-access ask a first visit can raise. */
@Composable
internal fun CrewDialogsHost(
    vm: CrewViewModel,
    dialogs: CrewDialogs,
) {
    val context = LocalContext.current
    dialogs.reporting?.let { seat ->
        CrewReportDialog(
            seat = seat,
            onReport = { reason ->
                dialogs.reporting = null
                vm.report(context, seat.id, seat.appName, reason)
            },
            onDismiss = { dialogs.reporting = null },
        )
    }

    dialogs.refusing?.let { seat ->
        CrewReportDialog(
            seat = seat,
            answering = true,
            onReport = { reason ->
                dialogs.refusing = null
                vm.vote(context, seat, installed = false, reason = reason)
            },
            onDismiss = { dialogs.refusing = null },
        )
    }

    dialogs.leaving?.let { seat ->
        Ask(
            title = "Take ${seat.appName} back out?",
            body =
                "The seat is freed for somebody else and this crew goes back to waiting. You " +
                    "can put an app in again straight away.",
            confirm = "Take it out",
            destructive = true,
            onConfirm = {
                dialogs.leaving = null
                vm.leave(context, seat)
            },
            onDismiss = { dialogs.leaving = null },
        )
    }

    /*
     * The one permission the crew flow cannot do without, asked at the first app somebody opens,
     * with the app they were opening named in the question. Dismissing is allowed: refusing means
     * tonight does not count, which is theirs to choose, and the ask returns the next time.
     */
    vm.needsUsageFor?.let { label ->
        Ask(
            title = "Let TestTrack see the time",
            body =
                "Android only tells us how long $label was open if usage access is on. Without " +
                    "it a visit cannot be counted, however long you stay.",
            confirm = "Open settings",
            dismiss = "Not now",
            onConfirm = { vm.openUsageSettings(context.findActivity()) },
            onDismiss = { vm.clearUsageAsk() },
        )
    }
}
