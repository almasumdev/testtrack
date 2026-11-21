package com.eazyverse.testtrack.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.domain.ChatSignals
import com.eazyverse.testtrack.viewmodel.PrivateState
import com.eazyverse.testtrack.viewmodel.PrivateTestsViewModel
import com.eazyverse.testtrack.viewmodel.VolunteerViewModel

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
    /** A crew's chat, from the chat mark on its Run or Lobby tile. */
    onOpenChat: (String) -> Unit = {},
    /** The page that puts an app into a crew. */
    onAddToCrew: () -> Unit = {},
    /** My apps as it stands, for the Open tab's join steps; null until it has answered. */
    myApps: List<AppEntry>? = null,
    onAddApp: () -> Unit = {},
    /** Circle, where reviews earn the tix a seat costs. */
    onOpenCircle: () -> Unit = {},
    /** A private test's own page, from its tile on the Run tab. */
    onOpenPrivate: (String) -> Unit = {},
    vm: CrewViewModel = viewModel(),
    privates: PrivateTestsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val dialogs = rememberCrewDialogs()

    // Reloaded on every arrival: a crew fills, an app is sent back and a fortnight starts while
    // somebody is on another screen.
    LaunchedEffect(Unit) { vm.load(context) }
    // A chat push: the tiles and the tabs' chat dots read their counts again.
    LaunchedEffect(Unit) { ChatSignals.news.collect { vm.load(context) } }
    CrewLifecycle(vm)
    val actions = crewActions(vm, dialogs, onSignIn, onAddToCrew)
    val volunteers = rememberVolunteerSection(vm, onSignIn, onOpenCrew)
    // The asks made of the reader, read again after every crew load: a private.changed event is one.
    LaunchedEffect(vm.reloading, vm.signedOut) { if (!vm.reloading && !vm.signedOut) privates.loadMine() }
    val privateRun = rememberPrivateRun(vm, privates, onOpenPrivate)
    val invites = if (vm.signedOut) emptyList() else privates.mine.orEmpty().filter { it.state == PrivateState.INVITED }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // The tabs bring their own gutter and room above, the same as Circle's.
            PhaseTabs(vm.tab, { vm.badge(it) }, { chatUnreadOf(vm.crews, it) > 0 }) { vm.showTab(it) }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.running > 0 && vm.tab != CrewPhase.OPEN) {
                TestingNotifications(Modifier.padding(start = Gutter, end = Gutter, top = 12.dp))
            }
            // An invite waits over every tab until it is answered.
            PrivateInviteRows(invites, { privates.pick(it) }, Modifier.padding(start = Gutter, end = Gutter, top = 12.dp))
            // The first answer moves the tab itself (Open to Run when a crew is running), so the page
            // changes under the reader. Fade through, as the main tabs do, rather than cut. Null is
            // the offline page. Nothing animates on a return: the tab is already settled then.
            val page = if (vm.offline && vm.crews.isEmpty()) null else vm.tab
            AnimatedContent(page, Modifier.fillMaxSize(), transitionSpec = fadeThrough(), label = "crew page") { shown ->
                when (shown) {
                    null -> RobotOffline("Crews didn't load", onRetry = { vm.load(context) })
                    CrewPhase.RUN ->
                        LoadSwap(vm.firstLoad) {
                            if (it) CrewRunLoading() else CrewRunTab(vm, onSignIn, actions, onOpenCrew, onOpenChat, privateRun)
                        }
                    CrewPhase.LOBBY ->
                        LoadSwap(vm.firstLoad) { if (it) CrewLobbyLoading() else CrewLobbyTab(vm, onSignIn, actions, onOpenCrew, onOpenChat) }
                    CrewPhase.RECORD ->
                        LoadSwap(vm.firstLoad) { if (it) CrewRecordLoading() else CrewRecordTab(vm, onSignIn, onOpenCrew) }
                    CrewPhase.OPEN -> CrewOpenTab(vm, actions, rememberCrewGuide(vm, myApps, onSignIn, onAddApp, onOpenCircle), volunteers)
                }
            }
        }
    }
    CrewDialogsHost(vm, dialogs)
    VolunteerJoinHost(vm, viewModel(), onOpenCrew)
    PrivateInviteHost(privates)
}

/** The sheet for the invite picked over the tabs. Yes asks for the testers group first. */
@Composable
private fun PrivateInviteHost(privates: PrivateTestsViewModel) {
    val membership = rememberMembership()
    val invite = privates.picked ?: return
    PrivateInviteSheet(
        invite,
        busy = privates.busy == invite.id,
        onAccept = { close -> membership.require { privates.accept(invite, close) } },
        onDecline = { close -> privates.decline(invite, close) },
        onDismiss = { privates.pick(null) },
    )
}

/**
 * The Open tab's volunteer list, read whenever the tab shows and after each crew load. Volunteer
 * asks a stranger to sign in, and a member to be on the testers group, before the sheet opens.
 */
@Composable
private fun rememberVolunteerSection(
    vm: CrewViewModel,
    onSignIn: () -> Unit,
    onOpenCrew: (String) -> Unit,
    volunteers: VolunteerViewModel = viewModel(),
): VolunteerSection {
    val membership = rememberMembership()
    LaunchedEffect(vm.tab, vm.reloading) { if (vm.tab == CrewPhase.OPEN && !vm.reloading) volunteers.load() }
    return VolunteerSection(
        spots = volunteers.spots,
        failed = volunteers.failed,
        onVolunteer = { spot ->
            val blocked = volunteers.blocked
            when {
                vm.signedOut -> onSignIn()
                blocked != null -> Snacks.error(blocked)
                else -> membership.require { volunteers.pick(spot) }
            }
        },
        onOpen = { onOpenCrew(it.crewId) },
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
    /** A picture from the phone for an app whose own picture failed tonight. */
    val onPickPicture: (CrewApp) -> Unit = {},
    /** Tonight's picture of this app is on the phone, waiting to send. */
    val sendingPicture: (CrewApp) -> Boolean = { false },
    /** A picture of this app from any night is waiting to send, and whether one has waited over 3 hours. */
    val waitingPicture: (String) -> Boolean = { false },
    val latePicture: (String) -> Boolean = { false },
    /** Why waiting pictures could not go yet, and another try at sending them. */
    val picturesStuck: String? = null,
    val onRetryPictures: () -> Unit = {},
    /** The version of an app on this phone, or null when it is not here. */
    val localVersion: (String) -> Long? = { null },
    /** Whether Google Play installed the copy here; true when it is not here at all. */
    val fromPlay: (String) -> Boolean = { true },
    /** To an app's Play page, for an update or for its feedback. */
    val onUpdateApp: (CrewApp) -> Unit = {},
    /** Private feedback on Play for an app, with its picture: by camera, or picked from the phone. */
    val onFeedback: (CrewApp) -> Unit = {},
    val onPickFeedback: (CrewApp) -> Unit = {},
    /** The owner's own seat: tell the crew about an update, or mark it down tonight. */
    val onTellUpdate: (CrewSeat) -> Unit = {},
    val onMarkDown: (CrewSeat) -> Unit = {},
    /** Why Update cannot be tapped yet for this seat, or null when it can. */
    val updateBlocked: (CrewSeat) -> String? = { null },
    /** The owner of an app taken out asks to get it back. */
    val onAppeal: (CrewSeat) -> Unit = {},
    /** Android's own uninstall prompt for an app that left the run. */
    val onUninstall: (String) -> Unit = {},
    /** A volunteer leaves their spot, confirmed first; and the way to the volunteer list once it is over. */
    val onLeaveVolunteer: (Crew) -> Unit = {},
    val onFindCrew: () -> Unit = {},
    /** The owner's private testers: the row's counts line, null where the row stays away, and its page. */
    val privateLine: (CrewSeat) -> String? = { null },
    val onPrivateTesters: (CrewSeat) -> Unit = {},
)

/**
 * Crew's four, drawn by the same track Circle's three use. See [SegmentedTabs].
 */
@Composable
private fun PhaseTabs(
    selected: CrewPhase,
    badge: (CrewPhase) -> Int,
    chatDot: (CrewPhase) -> Boolean,
    onSelect: (CrewPhase) -> Unit,
) {
    SegmentedTabs(
        labels = CrewPhase.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
        selected = CrewPhase.entries.indexOf(selected),
        counts = CrewPhase.entries.map(badge),
        chatDots = CrewPhase.entries.map(chatDot),
    ) { onSelect(CrewPhase.entries[it]) }
}

/** What can be wrong with an app in a crew, said the way its owner can act on. */
internal val crewReportReasons =
    listOf(
        "It won't install from Play",
        "Play says it isn't available",
        "The testers link shows nothing",
        "It needs a newer Android",
        "It crashes when it opens",
        "The sign-in details don't work",
        "It freezes or never loads",
        "It's not the app that was listed",
    )

/** Answering that an app did not install: only the install reasons. */
private val crewInstallReasons = crewReportReasons.take(4)

private const val SOMETHING_ELSE = "Something else"
private const val DETAILS_MAX = 280

/**
 * Reporting an app: what went wrong, in words its owner can act on.
 *
 * A reason is required, because a report is a message to the owner about what to fix and the
 * owner is told straight away. Who sent it is not. A listed reason may carry details; "Something
 * else" is only its details, so those need a sentence.
 */
@Composable
fun CrewReportDialog(
    seat: CrewSeat,
    onReport: (String) -> Unit,
    onDismiss: () -> Unit,
    /** An answer for an app waiting on members, rather than a report on one already in. */
    answering: Boolean = false,
    /** Sent during the run, when a report lets off only tonight; before it, the whole run. */
    running: Boolean = false,
) {
    var reason by rememberSaveable { mutableStateOf<String?>(null) }
    var details by rememberSaveable { mutableStateOf("") }
    val other = reason == SOMETHING_ELSE
    val said = details.trim()
    val ready = reason != null && (!other || said.length >= CrewRules.REPORT_MIN_CHARS)
    val owner = seat.owner.substringBefore(' ')
    val options = (if (answering) crewInstallReasons else crewReportReasons) + SOMETHING_ELSE
    Ask(
        title = if (answering) "${seat.appName} didn't install?" else "Report ${seat.appName}?",
        body =
            "$owner sees what you write, not your name. " +
                when {
                    answering -> "Two members saying it send it back to be fixed."
                    running -> "If you can't open it, tonight won't count against you."
                    else -> "${CrewRules.REPORTS_TO_RETURN} reports send it back to be fixed."
                },
        confirm = if (answering) "Send" else "Report",
        confirmEnabled = ready,
        onConfirm = {
            val picked = reason ?: return@Ask
            onReport(
                when {
                    other -> said
                    said.isEmpty() -> picked
                    else -> "$picked: $said"
                },
            )
        },
        onDismiss = onDismiss,
    ) {
        DropdownField("What's wrong", options.map { it to it }, reason, onPick = { reason = it })
        Spacer(Modifier.height(14.dp))
        OutlinedField(
            value = details,
            onValueChange = { details = it.take(DETAILS_MAX) },
            label = "Details",
            placeholder = "Anything that helps them fix it",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            maxLength = DETAILS_MAX,
            hint = if (other) null else "Optional",
            imeAction = ImeAction.Default,
        )
    }
}

/**
 * The app's page in the Play app, by name. Not the web join page: a browser uses whichever Google
 * account it is signed in to, which may not be the one on the testers group, and then says
 * "App not available" for an app this member can really get.
 */
internal fun openOnPlay(
    context: Context,
    packageName: String,
) {
    openPlayListingOf(context, packageName)
}
