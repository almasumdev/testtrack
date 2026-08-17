package com.eazyverse.testtrack.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.GroupWork
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.data.*
import kotlinx.coroutines.launch

/** One group as home needs it: the cohort, plus how much of today this tester still owes it. */
data class GroupProgress(val group: TestGroup, val toTest: Int, val done: Int)

class HomeViewModel : ViewModel() {

    var groups by mutableStateOf<List<GroupProgress>>(emptyList())
        private set
    var pending by mutableStateOf<List<TestApp>>(emptyList())
        private set
    /**
     * Everything this screen shows is in hand.
     *
     * One flag for the page, not one per list: whichever half arrives first would otherwise be
     * drawn over the half that hasn't, and a total struck across sections is wrong until both are
     * counted. Sticky once set — a refresh over content already on screen must not blank it.
     */
    var ready by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)

    /** Days already missed, and how close that is to costing them the group. */
    var standings by mutableStateOf<List<Enforcement.Standing>>(emptyList())
        private set

    /** Removals and departures, held until acknowledged rather than only sent as notifications. */
    var notices by mutableStateOf<List<Enforcement.Notice>>(emptyList())
        private set

    /**
     * Notifications are switched off, and this account is in a running cohort.
     *
     * Worth a prompt now that a missed day costs something: the warning that says "open them today
     * or your app leaves the group" arrives as a notification, and a tester who declined the
     * permission gets no warning at all before the removal lands.
     */
    var askForNotifications by mutableStateOf(false)
        private set

    /** Across every cohort — the number the tester actually cares about first thing. */
    val doneToday: Int get() = groups.sumOf { it.done }
    val dueToday: Int get() = groups.sumOf { it.toTest }

    fun load() {
        val uid = AuthRepo.uid ?: run { ready = true; return }

        showCached(uid)

        viewModelScope.launch {
            runCatching {
                val waiting = Repo.pendingApps(uid)
                val progress = Repo.myGroups(uid).map { group ->
                    // active, not placed: a removed app is still in the group and is owed
                    // nothing, so counting it here would ask for a day on an app this tester has
                    // been told to uninstall.
                    val others = Repo.appsInGroup(group.id)
                        .filter { it.ownerUid != uid && it.active }
                    val day = group.dayIndex()
                    val done = if (day == null) 0 else Repo.myProofsForDay(uid, group.id, day, group.startDate).size
                    GroupProgress(group, others.size, done)
                }

                // Published together, at the end. Assigning as each query lands would repaint a
                // screen that already has content with a half-updated one.
                pending = waiting
                groups = progress
                Cache.put(Cache.pending(uid), waiting)
                Cache.put(Cache.groups(uid), progress)
                message = null
            }.onFailure {
                message = it.friendly(
                    "We couldn't load your groups. Check your connection and try again."
                )
            }
            ready = true
        }
    }

    /**
     * Makes this device reachable, and puts it on the right lists.
     *
     * Here rather than at sign-in because the cohort is only known once the groups are loaded, and
     * because membership changes without the app being reinstalled — an admin places an app, and
     * the next time home refreshes, the topic follows.
     */
    fun registerForPush(context: Context) {
        val uid = AuthRepo.uid ?: return
        viewModelScope.launch {
            PushRepo.register(context, uid)
            if (ready) PushRepo.syncTopics(context, groups.map { it.group.id })
        }
    }

    /**
     * The daily pass, plus everything waiting, in the order the two have to happen.
     *
     * Scheduled here rather than at sign-in because this is the screen a tester always reaches,
     * and re-scheduling is free: the work is unique and KEEP, so opening the app twenty times does
     * not push the evening reminder twenty times further away.
     *
     * Drain first and sweep second, in one coroutine rather than two. They overlap on exactly one
     * thing: a removal is both an event addressed to this device and a cohort that has vanished
     * from under it. The event is the better of the two messages because it knows the cause, so it
     * claims the group and the sweep stays quiet — which only works if the event has already been
     * read by the time the sweep looks.
     */
    fun catchUp(context: Context) {
        val uid = AuthRepo.uid ?: return
        ReminderWorker.schedule(context)
        viewModelScope.launch {
            runCatching { AdminEvents.drain(context, uid) }
            standings = Enforcement.sweep(context, uid).standings
            notices = Enforcement.notices(context)
            refreshNotificationPrompt(context)
        }
    }

    /** Anything parked by a background sweep, picked up when the screen opens. */
    fun showNotices(context: Context) {
        notices = Enforcement.notices(context)
        refreshNotificationPrompt(context)
    }

    fun dismissNotice(context: Context, id: String) {
        Enforcement.dismiss(context, id)
        notices = Enforcement.notices(context)
    }

    private fun refreshNotificationPrompt(context: Context) {
        askForNotifications = !PushRepo.granted(context) && groups.any { it.group.running }
    }

    /** Both halves or neither — see [ready]. A partial hit is a head start, not a screen. */
    private fun showCached(uid: String) {
        val cachedGroups = Cache.get<List<GroupProgress>>(Cache.groups(uid)) ?: return
        val cachedPending = Cache.get<List<TestApp>>(Cache.pending(uid)) ?: return
        groups = cachedGroups
        pending = cachedPending
        ready = true
    }

    /**
     * Unhooks the account, then hands back to navigate.
     *
     * `viewModelScope` outlives the dialog and is only torn down by the navigation [then]
     * performs, so the await always completes. See [releaseSession] for why the order matters.
     */
    fun signOut(context: Context, then: () -> Unit) {
        viewModelScope.launch {
            releaseSession(context)
            then()
        }
    }

    fun withdraw(app: TestApp) {
        message = null
        viewModelScope.launch {
            runCatching { Repo.withdrawApp(app.packageName) }
                .onSuccess { load() }
                .onFailure { message = it.friendly("We couldn't withdraw ${app.label}. Try again in a moment.") }
        }
    }

    /**
     * Saves the way into an app that is still waiting for a group.
     *
     * Reloads rather than patching the row in place, for the same reason [withdraw] does: this
     * list is whatever Firestore last said, and a row edited locally would keep showing the new
     * login even on the attempt where the write never landed.
     */
    fun updateNotes(app: TestApp, notes: String) {
        message = null
        viewModelScope.launch {
            runCatching { Repo.updateNotes(app.packageName, notes) }
                .onSuccess { load() }
                .onFailure {
                    message = it.friendly(
                        "We couldn't save your notes for ${app.label}. Try again in a moment."
                    )
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenGroup: (String) -> Unit,
    onSubmitApp: () -> Unit,
    onOpenSetup: () -> Unit,
    onSignOut: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val context = LocalContext.current

    // A round finishes with the tester back here, days marked off that this screen has not heard
    // about yet.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.load()
                Session.refreshNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keyed on the group list, so placement into a new cohort subscribes to it without waiting for
    // the next cold start.
    LaunchedEffect(vm.ready, vm.groups) { if (vm.ready) vm.registerForPush(context) }

    LaunchedEffect(Unit) {
        vm.catchUp(context)
    }

    // Anything a background sweep parked while the app was shut.
    LaunchedEffect(vm.ready) { if (vm.ready) vm.showNotices(context) }

    /**
     * Below Android 13 there is no runtime permission to ask for, so the system dialog never
     * appears and the prompt would be a button that does nothing. Sending those to the app's
     * notification settings is the only way to turn them back on there.
     */
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.showNotices(context) }

    val askNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }

    var confirmSignOut by remember { mutableStateOf(false) }

    /**
     * The app whose notes are being written, or null.
     *
     * Editable before a group exists on purpose. A developer submits, then spends days waiting for
     * a cohort, and that gap is exactly when they realise the test account they gave has expired.
     */
    var editingNotes by remember { mutableStateOf<TestApp?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("TestTrack", fontWeight = FontWeight.Bold) },
                // Both actions shown rather than folded behind an overflow. Two is under the
                // threshold where a menu earns its extra tap, and neither is findable by guessing
                // that a three-dot icon contains it.
                actions = {
                    IconButton(onClick = onOpenSetup) {
                        Icon(Icons.Outlined.Tune, "Setup")
                    }
                    IconButton(onClick = { confirmSignOut = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Sign out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            if (!vm.ready) {
                SkeletonPage(rows = 5, showAction = false, showTrailing = false)
            } else {
                if (vm.groups.isNotEmpty()) {
                    Column(Modifier.padding(start = Gutter, end = Gutter, top = 4.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${vm.doneToday} of ${vm.dueToday}",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (vm.dueToday == 0) "nothing due today"
                            else "tested today across ${vm.groups.size} " +
                                if (vm.groups.size == 1) "group" else "groups",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(14.dp))
                        Meter(if (vm.dueToday == 0) 0f else vm.doneToday.toFloat() / vm.dueToday)
                    }
                }

                // Above everything, because a tester one day from losing their place should not
                // have to scroll to a group row to find that out.
                vm.standings.forEach { MissedDayNotice(it) }

                // Things that already happened, kept until acknowledged. A notification is not a
                // record: it can be swiped off a lock screen unread.
                vm.notices.forEach { notice ->
                    EventNotice(notice) { vm.dismissNotice(context, notice.id) }
                }

                if (vm.askForNotifications) NotificationPrompt(onGrant = askNotifications)

                // Nothing at all: one empty state rather than two headed sections standing over
                // nothing.
                if (vm.groups.isEmpty() && vm.pending.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.GroupWork,
                        title = "No groups yet",
                        subtitle = "Submit an app you've put on a closed track. An admin places " +
                            "it into a testing group, and your 14 days start once that group is " +
                            "full."
                    )
                } else {
                    SectionLabel("Your groups")
                    Rows {
                        if (vm.groups.isEmpty()) Blank("None yet.")
                        else vm.groups.forEachIndexed { index, progress ->
                            if (index > 0) RowDivider()
                            GroupRow(progress) { onOpenGroup(progress.group.id) }
                        }
                    }

                    if (vm.pending.isNotEmpty()) {
                        SectionLabel("Waiting for a group")
                        Rows {
                            vm.pending.forEachIndexed { index, app ->
                                if (index > 0) RowDivider()
                                PendingRow(
                                    app = app,
                                    onEditNotes = { editingNotes = app },
                                    onWithdraw = { vm.withdraw(app) }
                                )
                            }
                        }
                    }
                }

                vm.message?.let { Failure(it) }

                Spacer(Modifier.height(24.dp))
                Primary(
                    "Submit an app",
                    Modifier.padding(horizontal = Gutter),
                    onClick = onSubmitApp
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmSignOut) {
        Ask(
            title = "Sign out?",
            body = "You'll need to sign in with Google again. Nothing you've already reported " +
                "is lost.",
            confirm = "Sign out",
            onConfirm = {
                confirmSignOut = false
                CaptureService.endSession(context)
                vm.signOut(context) {
                    Cache.clear()
                    onSignOut()
                }
            },
            onDismiss = { confirmSignOut = false }
        )
    }

    editingNotes?.let { app ->
        NotesDialog(
            app = app,
            onSave = {
                editingNotes = null
                vm.updateNotes(app, it)
            },
            onDismiss = { editingNotes = null }
        )
    }
}

/**
 * The way into an app, written before anybody has been asked to open it.
 *
 * Not destructive, and an empty field saved is a real answer rather than a slip to guard against:
 * clearing the field is the only way to take down a login that has stopped working.
 */
@Composable
private fun NotesDialog(app: TestApp, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember(app.packageName) { mutableStateOf(app.notes) }

    Ask(
        title = "Notes for your testers",
        confirm = "Save",
        onConfirm = { onSave(text.trim()) },
        onDismiss = onDismiss,
        content = {
            DialogField(
                value = text,
                // Held to the cap as they type rather than checked on save, because the limit is
                // the document's. Refusing it afterwards would throw away whatever they had
                // already written past the end.
                onValueChange = { text = it.take(NOTES_MAX) },
                label = "Notes",
                placeholder = "Test account: demo@example.com / pass1234",
                supporting = "Anything they need to get past the front door. Everyone in your " +
                    "group and the admins can read this, so use a throwaway account, never your " +
                    "own password.",
                singleLine = false
            )
        }
    )
}

@Composable
private fun GroupRow(progress: GroupProgress, onClick: () -> Unit) {
    val group = progress.group
    val day = group.dayIndex()

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Gutter, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ring(progress.done, progress.toTest)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                group.name.ifBlank { "Group" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    day != null -> {
                        val left = progress.toTest - progress.done
                        "Day ${day + 1} of ${group.runDays} · " +
                            if (left <= 0) "all done" else "$left left today"
                    }
                    group.running -> "Run finished"
                    else -> "Forming · ${group.size} of ${TestGroup.THRESHOLD} members"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A day already missed, and what it costs.
 *
 * Two tones on purpose. The first miss is recoverable and says so, because a warning that reads
 * like a final notice teaches people to ignore the final notice. The second is the last one they
 * get, and names the apps rather than counting them, so nobody has to work out which.
 */
@Composable
private fun MissedDayNotice(standing: Enforcement.Standing) {
    val tone = if (standing.finalWarning) Status.missed else Status.short
    val ground = if (standing.finalWarning) Status.missedSoft else Status.shortSoft

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .padding(top = 18.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ground)
            .padding(14.dp)
    ) {
        Text(
            if (standing.finalWarning) "Last chance in ${standing.groupName}"
            else "You missed yesterday in ${standing.groupName}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = tone
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (standing.finalWarning)
                "That's two days running without opening ${standing.names}. Open ${standing.it} " +
                    "today and you keep your place. Leave it another day and your app goes back " +
                    "to the queue."
            else
                "You didn't open ${standing.names}. Open ${standing.it} today and nothing comes " +
                    "of it. Miss a second day in a row and your app leaves the group.",
            style = MaterialTheme.typography.bodySmall,
            color = tone
        )
    }
}

/**
 * Something that has already happened to this tester, with a way to say they have read it.
 *
 * Dismissal is explicit rather than timed. "Your app was removed from its group" is the kind of
 * thing someone should have to acknowledge, not something that quietly disappears while the phone
 * is in a pocket.
 */
@Composable
private fun EventNotice(notice: Enforcement.Notice, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .padding(top = 18.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Text(
            notice.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            notice.body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text("Got it")
        }
    }
}

/**
 * Asks for notifications, at the point where they start to matter.
 *
 * Not at sign-in. Before a run is under way there is nothing to be warned about, and a permission
 * asked for ahead of its reason is the one people decline. Shown only while a cohort is running,
 * which is exactly when a missed day costs something.
 */
@Composable
private fun NotificationPrompt(onGrant: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .padding(top = 18.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Status.shortSoft)
            .padding(14.dp)
    ) {
        Text(
            "Turn on notifications",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Status.short
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Your run has started, and we warn you the day before a missed day costs you your " +
                "place in the group. Without notifications that warning has nowhere to go.",
            style = MaterialTheme.typography.bodySmall,
            color = Status.short
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onGrant, modifier = Modifier.align(Alignment.End)) {
            Text("Turn them on")
        }
    }
}

@Composable
private fun PendingRow(app: TestApp, onEditNotes: () -> Unit, onWithdraw: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.packageName, app.label)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Shown back to the owner, clipped the same way the group's rows clip it, so what they
            // are reading here is what the twelve people will be reading there.
            if (app.notes.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    app.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // Two labels, because an owner who has never written notes has no reason to expect the row
        // hides a field, and "Notes" on its own reads as a heading rather than something to press.
        TextButton(onClick = onEditNotes) {
            Text(if (app.notes.isBlank()) "Add notes" else "Edit notes")
        }
        // Named rather than hidden behind three dots. A menu holding one item is a tap spent
        // discovering that there was only ever one thing to do.
        TextButton(onClick = onWithdraw) {
            Text("Withdraw", color = MaterialTheme.colorScheme.error)
        }
    }
}
