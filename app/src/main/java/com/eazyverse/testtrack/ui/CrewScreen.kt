package com.eazyverse.testtrack.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.ui.theme.Tt

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
    /** A crew's own page, from a tap on it in any of the four tabs. */
    onOpenCrew: (String) -> Unit = {},
    /** The page that puts an app into a crew. */
    onAddToCrew: () -> Unit = {},
    /** My apps as it stands, for the Open tab's Get ready; null until it has answered. */
    myApps: List<AppEntry>? = null,
    onAddApp: () -> Unit = {},
    onOpenApp: (String) -> Unit = {},
    vm: CrewViewModel = viewModel(),
) {
    val context = LocalContext.current
    val dialogs = rememberCrewDialogs()

    // Reloaded on every arrival: a crew fills, an app is sent back and a fortnight starts while
    // somebody is on another screen.
    LaunchedEffect(Unit) { vm.load(context) }
    CrewLifecycle(vm)
    val actions = crewActions(vm, dialogs, onSignIn, onAddToCrew)

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        // Nothing of its own, the same as Circle's: the header above brings its
                        // space below itself and the tabs bring their own touch padding.
                        .padding(start = Gutter, end = Gutter),
                ) {
                    PhaseTabs(vm.tab, { vm.badge(it) }) { vm.showTab(it) }
                }
                PageMessage(vm.message, vm::clearMessage)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.running > 0) TestingNotifications()
            when {
                vm.offline && vm.crews.isEmpty() -> RobotOffline("Crews didn't load", onRetry = { vm.load(context) })
                vm.tab == CrewPhase.RUN ->
                    LoadSwap(vm.firstLoad) { if (it) CrewRunLoading() else CrewRunTab(vm, onSignIn, actions, onOpenCrew) }
                vm.tab == CrewPhase.LOBBY ->
                    LoadSwap(vm.firstLoad) { if (it) CrewLobbyLoading() else CrewLobbyTab(vm, onSignIn, actions, onOpenCrew) }
                vm.tab == CrewPhase.RECORD ->
                    LoadSwap(vm.firstLoad) { if (it) CrewRecordLoading() else CrewRecordTab(vm, onSignIn, onOpenCrew) }
                else -> CrewOpenTab(vm, actions, rememberCrewGuide(vm, myApps, onAddApp, onOpenApp))
            }
        }
    }
    CrewDialogsHost(vm, dialogs)
}

/**
 * The Open tab's Get ready, read again on every return to TestTrack: the switches are turned on
 * in Android's settings and the group joined in a browser, and coming back should show the tick.
 */
@Composable
private fun rememberCrewGuide(
    vm: CrewViewModel,
    myApps: List<AppEntry>?,
    onAddApp: () -> Unit,
    onOpenApp: (String) -> Unit,
): CrewGuide {
    val context = LocalContext.current
    val membership = rememberMembership()
    var resumes by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watcher = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumes++ }
        owner.lifecycle.addObserver(watcher)
        onDispose { owner.lifecycle.removeObserver(watcher) }
    }
    val permissions = remember(resumes) { vm.permissionsReady(context) }
    // Ready for a crew: checked on Play and still in closed testing. A live app goes to Feedback.
    val waiting = myApps?.filter { !it.live }
    val app = waiting?.let { list -> list.any { it.verified } }
    return CrewGuide(
        ready = CrewReady(app = if (vm.signedOut) false else app, member = membership.member, permissions = permissions),
        hasApps = !waiting.isNullOrEmpty(),
        onApp = { waiting?.firstOrNull { !it.verified }?.let { onOpenApp(it.id) } ?: onAddApp() },
        onJoin = { membership.show() },
        onAllow = { vm.allowPermissions(context.findActivity()) },
    )
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
    /** Whether an app is on this phone, which decides whether its row offers Install or Open. */
    val isInstalled: (String) -> Boolean = { true },
    /** A crew's members page, from the button in its bar or its Your app card. */
    val onMembers: () -> Unit = {},
    /** The seat an answer, report or withdrawal is being sent for. */
    val busySeat: String? = null,
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
 * Reporting an app: what went wrong, in words its owner can act on.
 *
 * A reason is required, because a report is a message to the owner about what to fix and the
 * owner is told straight away. Who sent it is not.
 */
@OptIn(ExperimentalLayoutApi::class)
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
            // The usual reasons, one tap each; the field below takes anything else.
            val quick =
                if (answering) {
                    listOf("Play says it isn't available", "The testers link shows nothing", "It needs a newer Android")
                } else {
                    listOf("It won't install from Play", "It crashes when it opens", "The sign-in doesn't work")
                }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                quick.forEach { text ->
                    val picked = reason.trim() == text
                    ActionChip(
                        text,
                        if (picked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        if (picked) MaterialTheme.colorScheme.primaryContainer else Status.neutralSoft,
                    ) { reason = text }
                }
            }
            Spacer(Modifier.height(12.dp))
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
internal fun openOnPlay(
    context: Context,
    packageName: String,
) {
    val page = Uri.parse("https://play.google.com/apps/testing/$packageName")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
