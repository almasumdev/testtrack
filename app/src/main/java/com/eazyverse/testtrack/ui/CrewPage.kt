package com.eazyverse.testtrack.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.eazyverse.testtrack.viewmodel.leave
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
    /** The crew's members, and what each did with your app. */
    onMembers: () -> Unit = {},
) {
    val context = LocalContext.current
    val dialogs = rememberCrewDialogs()
    val crew = vm.crews.firstOrNull { it.id == id }
    LaunchedEffect(id) { if (crew == null) vm.load(context) }
    // Shown once and then gone from your crews: you took your app out, or it was sent back. The
    // page has nothing left to show, so it closes rather than waiting for a crew that is not coming.
    var seen by remember { mutableStateOf(false) }
    var closed by remember { mutableStateOf(false) }
    // Gone only once a load has finished without it, and closed once: a list caught mid-reload
    // must not close the page, and closing twice would take the page under it away too.
    LaunchedEffect(crew != null, vm.reloading) {
        if (crew != null) {
            seen = true
        } else if (seen && !vm.reloading && !closed) {
            closed = true
            onBack()
        }
    }
    CrewLifecycle(vm)
    val actions = crewActions(vm, dialogs, onSignIn, onAddToCrew, onMembers)

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                // Its name, and once it runs, the way to its members. Where it stands is the first card.
                PageBar(crew?.name ?: "Crew", onBack) {
                    if (crew != null && (crew.phase == CrewPhase.RUN || crew.phase == CrewPhase.RECORD)) {
                        MembersButton(crew.members.size, onMembers)
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
                else -> CrewRecordPhase(crew, actions)
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
    onMembers: () -> Unit = {},
): CrewActions {
    val context = LocalContext.current
    val membership = rememberMembership()
    // The app a picked picture is for, held while the picker is up.
    var picking by remember { mutableStateOf<CrewApp?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val app = picking
            picking = null
            if (app != null && uri != null) vm.pictures.pick(context.findActivity(), app.id, app.label, uri, app.day)
        }
    val waiting by vm.pictures.waiting.collectAsState()
    val stuck by vm.pictures.stuck.collectAsState()
    return withCrewExtras(
        vm,
        CrewActions(
            onOpen = { app -> membership.require { vm.openApp(context, app) } },
            onOpenAll = { id -> membership.require { vm.openAll(context, id) } },
            onInstall = { seat -> membership.require { openOnPlay(context, seat.packageName) } },
            onReport = { dialogs.reporting = it },
            onLeave = { dialogs.leaving = it },
            onVote = { seat, yes -> if (yes) vm.vote(context, seat, installed = true) else dialogs.refusing = seat },
            // Signed in and on the testers group first, then the page that puts an app in.
            onTakeSeat = { if (vm.signedOut) onSignIn() else membership.require { onAddToCrew() } },
            isInstalled = { pkg -> vm.isInstalled(context, pkg) },
            onMembers = onMembers,
            busySeat = vm.acting,
            onPickPicture = { app ->
                picking = app
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            // Read through [waiting], so a row redraws when its picture goes.
            sendingPicture = { app -> waiting.any { it.seatId == app.id && it.day == app.day } },
            waitingPicture = { id -> waiting.any { it.seatId == id } },
            latePicture = { id -> waiting.any { it.seatId == id && it.late() } },
            picturesStuck = stuck,
            onRetryPictures = { vm.pictures.send(context.findActivity()) },
        ),
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
                        // Back from Play, an app may be on the phone now: its row moves to Open.
                        vm.refreshInstalled(context)
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
                "It leaves ${seat.crew.ifBlank { "this crew" }} and your ${CrewRules.SEAT_COST} tix come back. You " +
                    "can put it in a crew again any time.",
            confirm = "Take it out",
            dismiss = "Keep it in",
            destructive = true,
            onConfirm = {
                dialogs.leaving = null
                vm.leave(context, seat)
            },
            onDismiss = { dialogs.leaving = null },
        )
    }

    // Asked once, at the first open: the control that floats over each app needs Android's
    // "display over other apps". Not now still runs the round, the old way.
    vm.needsOverlayFor?.let {
        Ask(
            title = "Keep the timer on screen?",
            body =
                "TestTrack can float a small timer over each app, with its sign-in to copy, so you " +
                    "know when 10 seconds are in and can go straight to the next. Android calls this " +
                    "“Display over other apps”.",
            confirm = "Allow",
            dismiss = "Not now",
            onConfirm = { vm.allowOverlay(context) },
            onDismiss = { vm.skipOverlay(context) },
        )
    }

    /*
     * The one permission the crew flow cannot do without, asked at the first app somebody opens,
     * with the app they were opening named in the question. Dismissing is allowed: refusing means
     * tonight does not count, which is theirs to choose, and the ask returns the next time.
     */
    CrewPictureAsks(vm)
    CrewExtrasAsks(vm)

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
