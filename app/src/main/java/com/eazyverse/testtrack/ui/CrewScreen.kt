package com.eazyverse.testtrack.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide

/**
 * Crew: one cohort, at whatever point it has reached.
 *
 * Four screens rather than one screen with four sets of branches, because they are four different
 * jobs. Choosing a crew to join, getting a crew ready, testing every night, and looking back at
 * what happened have almost nothing in common except the fifteen people, and a single screen that
 * tried to be all of them is what the old one was.
 *
 * Everything runs against [CrewViewModel]'s in-memory state. See its note, and docs/crew.md for
 * the rules underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewScreen(vm: CrewViewModel = viewModel()) {
    val snackbars = remember { SnackbarHostState() }
    var judging by remember { mutableStateOf<Pair<String, CrewSeat>?>(null) }
    var taking by remember { mutableStateOf<String?>(null) }
    val crew = vm.current

    LaunchedEffect(vm.message) {
        val message = vm.message ?: return@LaunchedEffect
        snackbars.showSnackbar(message)
        vm.clearMessage()
    }

    // Opening a crew is a push, so back should pop it rather than leaving the tab. Registered only
    // while one is open, so the bar's own handler still gets it on the tabs themselves.
    BackHandler(enabled = crew != null) { vm.show(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            if (crew == null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(start = Gutter, end = Gutter, top = 16.dp, bottom = 8.dp),
                ) {
                    PhaseTabs(vm.tab, { vm.badge(it) }) { vm.showTab(it) }
                }
            } else {
                // A crew that has been opened gets its name and where it has got to, because that
                // is the only thing on the screen saying which of four you are looking at.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(start = 6.dp, end = Gutter, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .clickable { vm.show(null) }
                            .padding(10.dp),
                    ) {
                        Icon(
                            Lucide.ArrowLeft,
                            "Back",
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        crew.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        crew.standing,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                crew != null && crew.phase == CrewPhase.LOBBY ->
                    CrewLobbyPhase(crew) { judging = crew.id to it }
                crew != null && crew.phase == CrewPhase.RUN ->
                    CrewRunPhase(
                        crew = crew,
                        onOpen = { vm.openApp(it) },
                        onReport = { vm.report(it) },
                        onOpenAll = { vm.openAll(crew.id) },
                    )
                crew != null -> CrewRecordPhase(crew)

                vm.tab == CrewPhase.RUN -> CrewRunTab(vm) { vm.show(it) }
                vm.tab == CrewPhase.LOBBY ->
                    CrewLobbyTab(vm) { id, seat -> judging = id to seat }
                vm.tab == CrewPhase.RECORD -> CrewRecordTab(vm) { vm.show(it) }
                else -> CrewOpenTab(vm) { taking = it }
            }
        }
    }

    judging?.let { (id, seat) ->
        CrewVerdictDialog(
            seat = seat,
            mayReject = vm.mayJudge(id),
            onVerdict = { installed ->
                judging = null
                vm.judge(id, seat, installed)
            },
            onDismiss = { judging = null },
        )
    }

    taking?.let { crewId ->
        CrewSeatDialog(
            crew = vm.opens.first { it.id == crewId }.name,
            nightly = CrewRules.nightly(vm.running + 1),
            onTake = {
                taking = null
                vm.takeSeat(crewId, it)
            },
            onDismiss = { taking = null },
        )
    }
}

/**
 * The four stages, as one control.
 *
 * Equal shares rather than each pill hugging its own word, so a three letter label is as easy to
 * hit as a six letter one. The count beside a label is what is outstanding there, and it is absent
 * rather than zero: a tab that says nothing is a tab with nothing owed.
 */
@Composable
private fun PhaseTabs(
    selected: CrewPhase,
    badge: (CrewPhase) -> Int,
    onSelect: (CrewPhase) -> Unit,
) {
    val pill = RoundedCornerShape(percent = 50)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(pill)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, pill)
            .padding(4.dp),
    ) {
        CrewPhase.entries.forEach { phase ->
            val current = phase == selected
            val count = badge(phase)
            Box(
                Modifier
                    .weight(1f)
                    .clip(pill)
                    .background(
                        if (current) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                    ).clickable { onSelect(phase) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        phase.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                        color =
                            if (current) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                    if (count > 0) {
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "$count",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color =
                                if (current) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Did it install?
 *
 * Two answers and no free text, because the question has two answers. What the dialog spends its
 * words on instead is the thing that makes the answer trustworthy: a "not found" on Play usually
 * means the reader is not in the testers group yet, not that the app is broken, so somebody who
 * has never got an app working is told to fix their own side first rather than having their report
 * quietly thrown away.
 */
@Composable
fun CrewVerdictDialog(
    seat: CrewSeat,
    mayReject: Boolean,
    onVerdict: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    Ask(
        title = "Did ${seat.appName} install?",
        body =
            "Open ${seat.owner}'s Play listing and try it. Two members have to get it working " +
                "before the seat counts.",
        confirm = "It installed",
        onConfirm = { onVerdict(true) },
        onDismiss = onDismiss,
        content = {
            Text(
                if (mayReject) {
                    "If Play says it cannot find it, report that instead. Nothing happens on " +
                        "one report: somebody else tries it before the seat is refused."
                } else {
                    "Reports only count once you have one app of your own working. Play hides " +
                        "closed tests from anybody outside the testers group, so a \"not found\" " +
                        "before then is usually about your account rather than their app."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Secondary("Play could not find it", enabled = mayReject) { onVerdict(false) }
        },
    )
}

/** Taking the seat: which app is going in. */
@Composable
fun CrewSeatDialog(
    crew: String,
    nightly: Int,
    onTake: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    Ask(
        title = "Take a seat in $crew",
        body =
            "Your app goes in with ${CrewRules.SEATS - 1} others. You open theirs every night " +
                "for ${CrewRules.RUN_DAYS} days, and they open yours. That puts you on " +
                "$nightly apps a night.",
        confirm = "Take it",
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onTake(name.trim()) },
        onDismiss = onDismiss,
        content = {
            DialogField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = "App",
                placeholder = "Nikosh Budget",
                supporting =
                    "Two members will install it before the seat counts toward the " +
                        "${CrewRules.SEATS}.",
            )
        },
    )
}
