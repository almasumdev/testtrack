package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.GroupWork
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
    var loading by mutableStateOf(true)
        private set
    var message by mutableStateOf<String?>(null)

    /** Across every cohort — the number the tester actually cares about first thing. */
    val doneToday: Int get() = groups.sumOf { it.done }
    val dueToday: Int get() = groups.sumOf { it.toTest }

    fun load() {
        val uid = AuthRepo.uid ?: run { loading = false; return }

        Cache.get<List<GroupProgress>>(Cache.groups(uid))?.let { groups = it; loading = false }
        Cache.get<List<TestApp>>(Cache.pending(uid))?.let { pending = it }

        viewModelScope.launch {
            runCatching {
                pending = Repo.pendingApps(uid)
                Cache.put(Cache.pending(uid), pending)

                groups = Repo.myGroups(uid).map { group ->
                    val others = Repo.appsInGroup(group.id).filter { it.ownerUid != uid }
                    val day = group.dayIndex()
                    val done = if (day == null) 0 else Repo.myProofsForDay(uid, group.id, day).size
                    GroupProgress(group, others.size, done)
                }
                Cache.put(Cache.groups(uid), groups)
                message = null
            }.onFailure { message = it.message ?: "Could not load your groups" }
            loading = false
        }
    }

    fun withdraw(app: TestApp) {
        message = null
        viewModelScope.launch {
            runCatching { Repo.deleteApp(app.packageName) }
                .onSuccess { load() }
                .onFailure { message = it.message ?: "Could not withdraw ${app.label}" }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenGroup: (String) -> Unit,
    onSubmitApp: () -> Unit,
    onSignOut: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val context = LocalContext.current

    // A round finishes with the tester back here, days marked off that this screen has not heard
    // about yet.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.load()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var confirmSignOut by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("TestTrack", fontWeight = FontWeight.Bold) },
                actions = {
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

            when {
                vm.groups.isEmpty() && vm.loading -> {
                    SectionLabel("Your groups")
                    Panel { SkeletonRows(2, showTrailing = false) }
                }

                vm.groups.isEmpty() && vm.pending.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.GroupWork,
                    title = "No groups yet",
                    subtitle = "Submit an app you've put on a closed track. An admin places it " +
                        "into a testing group, and your 14 days start once that group is full."
                )

                vm.groups.isEmpty() -> Unit

                else -> {
                    SectionLabel("Your groups")
                    Panel {
                        vm.groups.forEach { progress ->
                            GroupRow(progress) { onOpenGroup(progress.group.id) }
                        }
                    }
                }
            }

            if (vm.pending.isNotEmpty()) {
                SectionLabel("Waiting for a group")
                Panel {
                    vm.pending.forEach { app ->
                        PendingRow(app) { vm.withdraw(app) }
                    }
                }
            }

            vm.message?.let { Failure(it) }

            Spacer(Modifier.height(24.dp))
            Primary("Submit an app", Modifier.padding(horizontal = Gutter), onClick = onSubmitApp)
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
                    Cache.clear()
                    onSignOut()
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
    var menuOpen by remember { mutableStateOf(false) }

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
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Default.MoreVert, "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Withdraw", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        onWithdraw()
                    }
                )
            }
        }
    }
}
