package com.eazyverse.testtrack.ui

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Timer
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.viewmodel.AppsViewModel
import com.eazyverse.testtrack.viewmodel.PrivateTestsViewModel
import com.eazyverse.testtrack.viewmodel.UninstallPhone
import com.eazyverse.testtrack.viewmodel.allowOverlay
import com.eazyverse.testtrack.viewmodel.leave
import com.eazyverse.testtrack.viewmodel.leaveVolunteer
import com.eazyverse.testtrack.viewmodel.privateCountsLine
import com.eazyverse.testtrack.viewmodel.report
import com.eazyverse.testtrack.viewmodel.vote
import kotlinx.coroutines.delay

/** The short pause on a ticked-off app before the next confirm opens. */
private const val UNINSTALL_TICK_MS = 400L

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
    apps: AppsViewModel = viewModel(),
    /** Adding an app to My apps, from the seat picker's list. */
    onAddApp: () -> Unit = {},
    /** The seat screen, for the app picked or put back in. */
    onSeat: (String) -> Unit = {},
    /** The crew's members, and what each did with your app. */
    onMembers: () -> Unit = {},
    /** Another crew's page, for an app moved there; null leaves the finished page without the way. */
    onOpenCrew: ((String) -> Unit)? = null,
    /** The crew's chat. */
    onChat: () -> Unit = {},
    /** The owner's private testers for their app in this crew, by its seat. */
    onPrivateTesters: (String) -> Unit = {},
    privates: PrivateTestsViewModel = viewModel(),
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
    CrewLifecycle(vm, id)
    // A finished crew's page draws its own record, read on its own for the testers it carries.
    val finished = crew?.phase == CrewPhase.RECORD
    LaunchedEffect(id, finished) { if (finished) vm.records.loadOne(id) }
    val record = if (finished) vm.records.record(id) else null
    // Uninstall all on the finished page. A tick after an app is gone, then the next confirm.
    val phone = remember(context) { UninstallPhone.of(context) }
    val uninstallActions =
        remember(vm, phone, id) {
            UninstallActions(
                onAll = { packages -> vm.uninstall.start(phone, id, packages) },
                onOne = { pkg -> phone.ask(pkg) },
                onStop = { vm.uninstall.stop() },
                onNext = { vm.uninstall.next(phone) },
            )
        }
    val uninstallRun = vm.uninstall.state
    val uninstallStep = uninstallRun?.step
    val uninstallDone = uninstallRun?.removed?.size
    LaunchedEffect(uninstallStep, uninstallDone) {
        if (uninstallStep == UninstallStep.TICK) {
            delay(UNINSTALL_TICK_MS)
            vm.uninstall.next(phone)
        }
    }
    // Nothing to see in the members once the details are cleared, or in a crew that never ran.
    val noMembers = crew?.pruned == true || record?.pruned == true || record?.outcome == RecordOutcome.CANCELLED
    // The owner's app in a running crew can have private testers: their counts for its row.
    val privateSeat = crew?.mine?.takeIf { crew.phase == CrewPhase.RUN && it.state == SeatState.CONFIRMED }
    LaunchedEffect(privateSeat?.id, vm.reloading) {
        if (privateSeat != null && !vm.reloading) privates.loadResults(privateSeat.id, quiet = true)
    }
    // Put it in again: the seat screen on the same app when it is on My apps, else the picker.
    var seating by remember { mutableStateOf(false) }
    val actions =
        crewActions(vm, dialogs, onSignIn, onAddToCrew = {
            apps.load(context)
            seating = true
        }, onMembers = onMembers, onFindCrew = {
            // Back to the Crew tab's Open list, where the running crews with spots are.
            vm.showTab(CrewPhase.OPEN)
            onBack()
        }, privateLine = { seat ->
            if (seat.id == privateSeat?.id) privateCountsLine(privates.results[seat.id]) else null
        }, onPrivateTesters = { onPrivateTesters(it.id) })

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // Its name, and once it runs, the way to its members. Where it stands is the first card.
            PageBar(crew?.name ?: "Crew", onBack) {
                // A volunteer has no app here, so nobody's testing of theirs to look at.
                val membersPage = crew?.phase == CrewPhase.RUN || (crew?.phase == CrewPhase.RECORD && !noMembers)
                if (crew != null && crew.volunteer == null && membersPage) {
                    MembersButton(crew.members.size, onMembers)
                }
            }
        },
    ) { padding ->
        // The chat's button floats over the page; gone once a finished crew's details are cleared.
        CrewChatEntry(crew, record?.pruned == true, onChat, Modifier.padding(padding)) {
            Column(Modifier.fillMaxSize()) {
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
                    crew == null -> CrewCardLoading()
                    crew.phase == CrewPhase.LOBBY -> CrewLobbyPhase(crew, actions)
                    crew.phase == CrewPhase.RUN -> CrewRunPhase(crew, actions)
                    else ->
                        CrewRecordPhase(
                            crew,
                            record,
                            failed = id in vm.records.openFailed,
                            busy = vm.records.busy[id],
                            onRetry = { vm.records.loadOne(id) },
                            onAnswer = { vm.records.answerProdAccess(id, it) },
                            onClaim = { vm.records.claimPerfect(id) },
                            onMembers = onMembers,
                            onOpenCrew = onOpenCrew,
                            installed = { pkg -> vm.copyOf(context, pkg).installed },
                            run = vm.uninstall.state,
                            uninstall = uninstallActions,
                        )
                }
            }
        }
    }
    CrewDialogsHost(vm, dialogs)
    val pkg = crew?.mine?.packageName
    val startId = apps.apps.firstOrNull { it.pkg == pkg }?.id
    LaunchedEffect(seating, startId) {
        if (seating && startId != null) {
            seating = false
            onSeat(startId)
        }
    }
    // While My apps is read for the crew's own app there is nothing to pick yet.
    if (seating && startId == null && !(pkg != null && (apps.loading || apps.reading))) {
        CrewSeatSheet(
            crews = vm,
            apps = apps,
            onPick = onSeat,
            onAddApp = onAddApp,
            onDismiss = { seating = false },
        )
    }
}

/** The seat a dialog is about, for each dialog a crew's rows can open. */
internal class CrewDialogs {
    /** Being given up, confirmed first: it is a place fourteen people share. */
    var leaving by mutableStateOf<CrewSeat?>(null)

    /** Being reported: its seat id, name and owner. */
    var reporting by mutableStateOf<CrewSeat?>(null)

    /** Answered "it didn't install", which asks why first. */
    var refusing by mutableStateOf<CrewSeat?>(null)

    /** A volunteer spot being left, confirmed first: that crew never takes them back. */
    var leavingVolunteer by mutableStateOf<Crew?>(null)
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
    onFindCrew: () -> Unit = {},
    privateLine: (CrewSeat) -> String? = { null },
    onPrivateTesters: (CrewSeat) -> Unit = {},
): CrewActions {
    val context = LocalContext.current
    val membership = rememberMembership()
    // The app a picked picture is for, held while the picker is up.
    var picking by remember { mutableStateOf<CrewApp?>(null) }
    val pickPicture =
        rememberImagePicker { uri ->
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
            // Signed in and on the testers group first, then the sheet that puts an app in.
            onTakeSeat = { if (vm.signedOut) onSignIn() else membership.require { onAddToCrew() } },
            isInstalled = { pkg -> vm.isInstalled(context, pkg) },
            onMembers = onMembers,
            busySeat = vm.acting,
            onPickPicture = { app ->
                picking = app
                pickPicture()
            },
            // Read through [waiting], so a row redraws when its picture goes.
            sendingPicture = { app -> waiting.any { it.seatId == app.id && it.day == app.day } },
            waitingPicture = { id -> waiting.any { it.seatId == id } },
            latePicture = { id -> waiting.any { it.seatId == id && it.late() } },
            picturesStuck = stuck,
            onRetryPictures = { vm.pictures.send(context.findActivity()) },
            onLeaveVolunteer = { dialogs.leavingVolunteer = it },
            onFindCrew = onFindCrew,
            privateLine = privateLine,
            onPrivateTesters = onPrivateTesters,
        ),
    )
}

/**
 * Coming back from somebody's app is the whole interaction, so resume is where a visit is
 * measured and recorded, and where the crew's own news starts being listened to again.
 */
@Composable
internal fun CrewLifecycle(
    vm: CrewViewModel,
    crewId: String? = null,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val phone = remember(context) { UninstallPhone.of(context) }
    DisposableEffect(owner) {
        val watcher =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        // Back from Play, an app may be on the phone now: its row moves to Open.
                        vm.refreshInstalled(context)
                        // Back from Android's uninstall confirm: read what it came to, fresh.
                        vm.uninstall.onResume(phone, crewId)
                        vm.settle(context)
                        vm.startLive(context)
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        vm.uninstall.onPause()
                        vm.stopLive()
                    }
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
            running = vm.crews.any { crew -> crew.phase == CrewPhase.RUN && crew.seats.any { it.id == seat.id } },
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
            title = "Take ${seat.appName} out?",
            body = "Your ${CrewRules.SEAT_COST} tix come back. You can put it in a crew again any time.",
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

    dialogs.leavingVolunteer?.let { crew ->
        val tix = crew.volunteer?.tixEarned ?: 0
        val kept = if (tix > 0) "You keep the $tix tix you earned. " else ""
        Ask(
            title = "Leave ${crew.name}?",
            body =
                if (crew.phase == CrewPhase.LOBBY) {
                    "You can't volunteer here again."
                } else {
                    "${kept}Tonight won't pay, and you can't volunteer here again."
                },
            confirm = "Leave",
            dismiss = "Stay",
            destructive = true,
            onConfirm = {
                dialogs.leavingVolunteer = null
                vm.leaveVolunteer(context, crew)
            },
            onDismiss = { dialogs.leavingVolunteer = null },
        )
    }

    // Asked once, at the first open: the control that floats over each app needs Android's
    // "display over other apps". Not now still runs the round, the old way.
    vm.needsOverlayFor?.let {
        Ask(
            title = "Keep the timer on screen?",
            icon = Lucide.Timer,
            body =
                "A small timer floats over each app with its sign-in, so you know when " +
                    "${CrewRules.OPEN_SECONDS} seconds are in.",
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
            icon = Lucide.Clock,
            body = "Without usage access, Android won't say how long $label was open, so the visit can't count.",
            confirm = "Open settings",
            dismiss = "Not now",
            onConfirm = { vm.openUsageSettings(context.findActivity()) },
            onDismiss = { vm.clearUsageAsk() },
        )
    }
}
