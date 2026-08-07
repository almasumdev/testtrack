package com.eazyverse.testtrack.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
                    val others = Repo.appsInGroup(group.id).filter { it.ownerUid != uid }
                    val day = group.dayIndex()
                    val done = if (day == null) 0 else Repo.myProofsForDay(uid, group.id, day).size
                    GroupProgress(group, others.size, done)
                }

                // Published together, at the end. Assigning as each query lands would repaint a
                // screen that already has content with a half-updated one.
                pending = waiting
                groups = progress
                Cache.put(Cache.pending(uid), waiting)
                Cache.put(Cache.groups(uid), progress)
                message = null
            }.onFailure { message = it.message ?: "We couldn't load your groups. Check your connection and try again." }
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
     * The daily pass, and a catch-up while the app is open.
     *
     * Scheduling here rather than at sign-in because this is the screen a tester always reaches,
     * and re-scheduling is free — the work is unique and KEEP, so opening the app twenty times
     * does not push the evening reminder twenty times further away. Draining events here as well
     * means an admin decision lands within seconds for anyone with the app in front of them,
     * rather than waiting for the next background run.
     */
    fun catchUp(context: Context) {
        val uid = AuthRepo.uid ?: return
        ReminderWorker.schedule(context)
        viewModelScope.launch { runCatching { AdminEvents.drain(context, uid) } }
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
            runCatching { Repo.deleteApp(app.packageName) }
                .onSuccess { load() }
                .onFailure { message = it.message ?: "We couldn't withdraw ${app.label}. Try again in a moment." }
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

    LaunchedEffect(Unit) { vm.catchUp(context) }

    var confirmSignOut by remember { mutableStateOf(false) }

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
                    Panel {
                        if (vm.groups.isEmpty()) Blank("None yet.")
                        else vm.groups.forEach { progress ->
                            GroupRow(progress) { onOpenGroup(progress.group.id) }
                        }
                    }

                    if (vm.pending.isNotEmpty()) {
                        SectionLabel("Waiting for a group")
                        Panel {
                            vm.pending.forEach { app -> PendingRow(app) { vm.withdraw(app) } }
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
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    "You'll need to sign in with Google again. Nothing you've already reported " +
                        "is lost."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    CaptureService.endSession(context)
                    vm.signOut(context) {
                        Cache.clear()
                        onSignOut()
                    }
                }) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            }
        )
    }
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
                        "Day ${day + 1} of $RUN_DAYS · " +
                            if (left <= 0) "all done" else "$left left today"
                    }
                    group.running -> "Run finished"
                    else -> "Forming · ${group.size} of ${TestGroup.THRESHOLD} members"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (group.atRisk) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Short ${group.stillNeeded} member${if (group.stillNeeded == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Status.missed
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PendingRow(app: TestApp, onWithdraw: () -> Unit) {
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
        }
        // Named rather than hidden behind three dots. A menu holding one item is a tap spent
        // discovering that there was only ever one thing to do.
        TextButton(onClick = onWithdraw) {
            Text("Withdraw", color = MaterialTheme.colorScheme.error)
        }
    }
}
