package com.eazyverse.testtrack.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.report
import com.eazyverse.testtrack.viewmodel.vote

/**
 * Crew: one cohort, at whatever point it has reached.
 *
 * Four screens rather than one screen with four sets of branches, because they are four different
 * jobs. Choosing a crew to join, getting a crew ready, testing every night, and looking back at
 * what happened have almost nothing in common except the fifteen people, and a single screen that
 * tried to be all of them is what the old one was.
 *
 * Everything runs against [CrewViewModel], which asks the API. See docs/crew.md for the rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewScreen(
    onSignIn: () -> Unit = {},
    /** A crew to open as soon as the list has it, from a tap on its app in My apps. */
    openCrew: String? = null,
    onOpened: () -> Unit = {},
    vm: CrewViewModel = viewModel(),
) {
    val context = LocalContext.current
    val membership = rememberMembership()

    /** The seat being given up, which is confirmed first: it is a place fourteen people share. */
    var leaving by remember { mutableStateOf<CrewSeat?>(null) }

    /** The app being reported: its seat id, name and owner. */
    var reporting by remember { mutableStateOf<CrewSeat?>(null) }

    /** The app being answered "it didn't install" for, which asks why first. */
    var refusing by remember { mutableStateOf<CrewSeat?>(null) }
    var taking by remember { mutableStateOf(false) }

    // Reloaded on every arrival: a crew fills, an app is sent back and a fortnight starts while
    // somebody is on another screen.
    LaunchedEffect(Unit) { vm.load(context) }

    // Coming back from somebody's app is the whole interaction, so resume is where a visit is
    // measured and recorded. Nothing happens here unless this screen sent them somewhere.
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
    val crew = vm.current
    LaunchedEffect(openCrew, vm.crews) {
        if (openCrew != null && vm.crews.any { it.id == openCrew }) {
            vm.show(openCrew)
            onOpened()
        }
    }

    // Opening a crew is a push, so back should pop it rather than leaving the tab. Registered only
    // while one is open, so the bar's own handler still gets it on the tabs themselves.
    BackHandler(enabled = crew != null) { vm.show(null) }

    val actions =
        CrewActions(
            onOpen = { app -> membership.require { vm.openApp(context, app) } },
            onOpenAll = { id -> membership.require { vm.openAll(context, id) } },
            onInstall = { seat -> membership.require { openOnPlay(context, seat.packageName) } },
            onReport = { reporting = it },
            onLeave = { leaving = it },
            onVote = { seat, yes -> if (yes) vm.vote(context, seat, installed = true) else refusing = seat },
            onTakeSeat = { if (vm.signedOut) onSignIn() else membership.require { taking = true } },
        )

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                if (crew == null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            // Nothing of its own, the same as Circle's: the header above brings its
                            // space below itself and the tabs bring their own touch padding.
                            .padding(start = Gutter, end = Gutter),
                    ) {
                        PhaseTabs(vm.tab, { vm.badge(it) }) { vm.showTab(it) }
                    }
                } else {
                    // A crew that has been opened gets its name and where it has got to, because that
                    // is the only thing on the screen saying which of four you are looking at.
                    PageBar(crew.name, onBack = { vm.show(null) }) {
                        Text(
                            crew.standing,
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
            if (vm.running > 0) TestingNotifications()
            when {
                crew != null && crew.phase == CrewPhase.LOBBY -> CrewLobbyPhase(crew, actions)
                crew != null && crew.phase == CrewPhase.RUN -> CrewRunPhase(crew, actions)
                crew != null -> CrewRecordPhase(crew)
                vm.offline && vm.crews.isEmpty() -> RobotOffline("Crews didn't load", onRetry = { vm.load(context) })
                vm.tab == CrewPhase.RUN ->
                    LoadSwap(vm.firstLoad) { if (it) CrewRunLoading() else CrewRunTab(vm, onSignIn, actions) { id -> vm.show(id) } }
                vm.tab == CrewPhase.LOBBY ->
                    LoadSwap(vm.firstLoad) { if (it) CrewLobbyLoading() else CrewLobbyTab(vm, onSignIn, actions) { id -> vm.show(id) } }
                vm.tab == CrewPhase.RECORD ->
                    LoadSwap(vm.firstLoad) { if (it) CrewRecordLoading() else CrewRecordTab(vm, onSignIn) { id -> vm.show(id) } }
                else -> CrewOpenTab(vm, actions)
            }
        }
    }

    reporting?.let { seat ->
        CrewReportDialog(
            seat = seat,
            onReport = { reason ->
                reporting = null
                vm.report(context, seat.id, seat.appName, reason)
            },
            onDismiss = { reporting = null },
        )
    }

    refusing?.let { seat ->
        CrewReportDialog(
            seat = seat,
            answering = true,
            onReport = { reason ->
                refusing = null
                vm.vote(context, seat, installed = false, reason = reason)
            },
            onDismiss = { refusing = null },
        )
    }

    leaving?.let { seat ->
        Ask(
            title = "Take ${seat.appName} back out?",
            body =
                "The seat is freed for somebody else and this crew goes back to waiting. You " +
                    "can put an app in again straight away.",
            confirm = "Take it out",
            destructive = true,
            onConfirm = {
                leaving = null
                vm.leave(context, seat)
            },
            onDismiss = { leaving = null },
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

    if (taking) {
        CrewSeatDialog(
            crew = vm.open?.name,
            nightly = CrewRules.nightly(vm.running + 1),
            busy = vm.seating,
            error = vm.seatError,
            onEdit = vm::clearSeatError,
            onTake = { pkg, name, notes -> vm.takeSeat(context, pkg, name, notes) { taking = false } },
            onDismiss = {
                if (!vm.seating) {
                    taking = false
                    vm.clearSeatError()
                }
            },
        )
    }
}

/** What a crew's rows can ask for, passed down as one value rather than six lambdas each. */
class CrewActions(
    val onOpen: (CrewApp) -> Unit,
    val onOpenAll: (String?) -> Unit,
    val onInstall: (CrewSeat) -> Unit,
    val onReport: (CrewSeat) -> Unit,
    val onLeave: (CrewSeat) -> Unit,
    val onTakeSeat: () -> Unit,
    /** An answer for an app waiting on members. */
    val onVote: (CrewSeat, Boolean) -> Unit = { _, _ -> },
)

/**
 * Crew's four, drawn by the same row Circle's three use. See [WordTabs].
 */
@Composable
private fun PhaseTabs(
    selected: CrewPhase,
    badge: (CrewPhase) -> Int,
    onSelect: (CrewPhase) -> Unit,
) {
    WordTabs(
        labels = CrewPhase.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
        selected = CrewPhase.entries.indexOf(selected),
        // Four words and their counts have less room than Circle's three, so they sit closer.
        counts = CrewPhase.entries.map(badge),
        gap = 14,
    ) { onSelect(CrewPhase.entries[it]) }
}

/**
 * Taking the seat: which app is going in, and how the others get into it.
 *
 * The Play link or package is the whole of it: the server looks it up on Play as a member of the
 * testers group sees it, takes the name from there, and only lets it in if it is visible. That
 * look-up is a real page load, so the dialog stays up and the robot looks while it runs, and a
 * refusal is said here, next to the field it is about.
 */
@Composable
fun CrewSeatDialog(
    crew: String?,
    nightly: Int,
    busy: Boolean,
    error: String?,
    onEdit: () -> Unit,
    onTake: (pkg: String, name: String, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pkg by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val ready = pkg.trim().contains('.') && pkg.trim().length > 3
    Ask(
        title = if (crew != null) "Take a seat in $crew" else "Start a new crew",
        body =
            "Your app goes in with ${CrewRules.SEATS - 1} others. You open theirs every day for " +
                "${CrewRules.RUN_DAYS} days, and they open yours. That puts you on $nightly apps a day.",
        confirm = if (busy) "Checking…" else "Take it",
        confirmEnabled = ready && !busy,
        dismiss = if (busy) null else "Cancel",
        looking = busy,
        onConfirm = { onTake(pkg.trim(), name.trim(), notes.trim()) },
        onDismiss = onDismiss,
        content = {
            DialogField(
                value = pkg,
                onValueChange = {
                    pkg = it.take(2048)
                    onEdit()
                },
                label = "Play link or package name",
                placeholder = "com.eazyverse.nikosh",
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
                error = error,
                supporting =
                    "We check it on Play the way the crew will see it. Your closed test needs " +
                        "the testers group added.",
            )
            Spacer(Modifier.height(14.dp))
            DialogField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = "Name (optional)",
                placeholder = "Taken from Play",
                imeAction = ImeAction.Next,
            )
            Spacer(Modifier.height(14.dp))
            DialogField(
                value = notes,
                onValueChange = { notes = it.take(280) },
                label = "Getting in (optional)",
                placeholder = "Test account: demo@example.com / demo",
                supporting =
                    "A login or code if yours needs one. Fourteen strangers read this, so make " +
                        "it a throwaway.",
                singleLine = false,
                minHeight = 72.dp,
                maxLength = 280,
            )
            if (busy) {
                Spacer(Modifier.height(10.dp))
                DialogNote("Looking it up on Play. This can take half a minute.")
            }
        },
    )
}

/**
 * Reporting an app: what went wrong, in words its owner can act on.
 *
 * A reason is required, because a report is a message to the owner about what to fix and the
 * owner is told straight away. Who sent it is not.
 */
@Composable
fun CrewReportDialog(
    seat: CrewSeat,
    onReport: (String) -> Unit,
    onDismiss: () -> Unit,
    /** An answer for an app waiting on members, rather than a report on one already in. */
    answering: Boolean = false,
) {
    var reason by remember { mutableStateOf("") }
    val enough = reason.trim().length >= CrewRules.REPORT_MIN_CHARS
    Ask(
        title = if (answering) "${seat.appName} didn't install?" else "Report ${seat.appName}?",
        body =
            if (answering) {
                "${seat.owner} is told what you write, without your name. Two members saying it " +
                    "didn't install send it back to be fixed."
            } else {
                "${seat.owner} is told what you write, without your name. Before the run starts, " +
                    "${CrewRules.REPORTS_TO_RETURN} reports send an app back to be fixed."
            },
        confirm = if (answering) "Send" else "Report",
        confirmEnabled = enough,
        onConfirm = { onReport(reason.trim()) },
        onDismiss = onDismiss,
        content = {
            DialogField(
                value = reason,
                onValueChange = { reason = it.take(280) },
                label = "What went wrong",
                placeholder = "Play says the item was not found",
                singleLine = false,
                minHeight = 72.dp,
                maxLength = 280,
                error =
                    if (reason.isNotBlank() && !enough) {
                        "A little more, so they know what to fix."
                    } else {
                        null
                    },
            )
            if (!answering) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "It also stops counting against you if you cannot open it.",
                    style = DialogBody,
                    color = Tt.colors.text4,
                )
            }
        },
    )
}

/**
 * The app's closed test on Play: the page where a tester joins it and then installs.
 *
 * The opt-in page rather than the listing, because a closed test has to be joined before Play
 * will offer the install, and the listing on its own says "not found" until then.
 */
private fun openOnPlay(
    context: Context,
    packageName: String,
) {
    val page = Uri.parse("https://play.google.com/apps/testing/$packageName")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
