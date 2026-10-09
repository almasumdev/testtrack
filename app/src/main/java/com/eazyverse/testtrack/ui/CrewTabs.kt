package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The four tabs, each holding every crew that has reached that point.
 *
 * A tab is a stage rather than a crew, which is what makes it survive somebody being in four at
 * once: Run is your evening across every running crew, Lobby is every crew of yours still filling,
 * Record is everything finished, and Open is the one crew in the product taking seats. Nothing
 * asks the reader to pick a crew before it will show them anything.
 *
 * The alternative, one merged screen with no tabs, put tonight's work first but buried the other
 * three states under it. The alternative before that, tabs that showed one crew, could not say
 * what a second crew was doing at all.
 */

/**
 * The night in one card, then the crews it is spread across.
 *
 * Not the apps. Twenty eight rows in one column answered "what do I owe" and nothing else, and it
 * asked somebody to work through two crews' evenings as one undifferentiated wall. A crew is the
 * unit a run actually has: it has its own day, its own fortnight and its own way of going wrong,
 * and the grid of apps belongs inside it rather than poured out here.
 *
 * What stays at the top is the only thing that spans crews, which is the size of the evening. The
 * three figures under it are what the count cannot say on its own: what tonight will cost every
 * night, and how many nights of it are left.
 */
@Composable
fun CrewRunTab(
    vm: CrewViewModel,
    onSignIn: () -> Unit,
    actions: CrewActions,
    onOpenCrew: (String) -> Unit,
) {
    val runs = vm.crews.filter { it.phase == CrewPhase.RUN }
    val going = runs.filter { !it.out }

    // Before the empty case, not after it. Whether anything is running is a question about an
    // account, and answering it with "Nothing running" to somebody the app cannot identify is a
    // result it never got back.
    if (vm.signedOut) {
        Gated(
            heading = "Sign in to see tonight’s apps",
            body = "Hold a seat and fourteen nights of testing start here, one night at a time.",
            onSignIn = onSignIn,
            mark = { NightsMark() },
        )
        return
    }
    if (vm.firstLoad) {
        CrewRunLoading()
        return
    }
    if (runs.isEmpty()) {
        RobotState(
            Prop.Kite,
            "Nothing running",
            "When a crew of yours fills up it starts that night, and what you owe shows up here.",
        )
        return
    }

    val left = vm.tonight.size
    val done = vm.doneTonight
    val total = left + done
    val risky = runs.filter { it.atRisk }.map { it.name }
    val nightsLeft = (going.ifEmpty { runs }).maxOf { (CrewRules.RUN_DAYS - it.day + 1).coerceAtLeast(0) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = Gutter + dockRoom()),
    ) {
        item {
            Box(Modifier.padding(horizontal = Gutter)) {
                CrewCard(padding = PaddingValues(16.dp)) {
                    Text(
                        if (left == 0) "Tonight is done" else "$left left tonight",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (left == 0) {
                            // The payoff. Nothing else in a fortnight of opening apps says a
                            // night went right, and a zero does not feel like one.
                            "All $total opened, across " +
                                "${going.size} ${if (going.size == 1) "crew" else "crews"}. " +
                                "Nothing counts against you."
                        } else {
                            "$done of $total done, across " +
                                "${going.size} ${if (going.size == 1) "crew" else "crews"}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    NightProgress(done, total)
                    Spacer(Modifier.height(18.dp))
                    CrewTallies(
                        nightly = CrewRules.nightly(going.size),
                        nightsLeft = nightsLeft,
                        crews = going.size,
                    )
                    if (risky.isNotEmpty() && left > 0) {
                        Spacer(Modifier.height(14.dp))
                        RiskNote(risky)
                    }
                    if (left > 0) {
                        // The whole night, across every crew, in one press. A member with two runs
                        // going came here to finish an evening, not to visit two screens first.
                        Spacer(Modifier.height(16.dp))
                        Primary("Open all $left") { actions.onOpenAll(null) }
                    }
                }
            }
        }

        item { CrewHeading("Your runs", "${runs.size}") }
        itemsIndexed(runs, key = { _, crew -> crew.id }) { index, crew ->
            ListTile(index, runs.size) {
                CrewListRow(crew) { onOpenCrew(crew.id) }
            }
        }
    }
}

/**
 * Every crew of yours that has not started, and any app of yours sent back to fix.
 *
 * Each crew is a card you open. What is on the card is the two numbers somebody came here for:
 * how full it is, or how long until their midnight, and where their own app stands.
 */
@Composable
fun CrewLobbyTab(
    vm: CrewViewModel,
    onSignIn: () -> Unit,
    actions: CrewActions,
    onOpenCrew: (String) -> Unit,
) {
    val lobbies = vm.crews.filter { it.phase == CrewPhase.LOBBY }

    if (vm.signedOut) {
        Gated(
            heading = "Sign in to see your crew",
            body = "A crew you have joined waits here until all fifteen apps are in.",
            onSignIn = onSignIn,
            mark = { SeatsMark() },
        )
        return
    }
    if (vm.firstLoad) {
        CrewLobbyLoading()
        return
    }
    if (lobbies.isEmpty()) {
        RobotState(
            Prop.Blocks,
            "No crew waiting to start",
            "Put an app in the open crew and it waits here until the crew is full.",
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = Gutter + dockRoom()),
    ) {
        itemsIndexed(lobbies, key = { _, crew -> crew.id }) { _, crew ->
            val mine = crew.mine
            Box(Modifier.padding(start = Gutter, end = Gutter, bottom = 12.dp)) {
                CrewCard(padding = PaddingValues(16.dp), onClick = { onOpenCrew(crew.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Lucide.Clock,
                            null,
                            Modifier.size(16.dp),
                            tint = Tt.colors.accentInk,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when {
                                crew.out -> "${crew.name} sent your app back"
                                crew.filling -> "${crew.name} needs ${CrewRules.SEATS - crew.filled} more"
                                else -> "${crew.name} starts in ${untilStart(crew.startsIn)}"
                            },
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Icon(
                            Lucide.ChevronRight,
                            null,
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            crew.out -> "Open it to see why, then fix it and put it in again."
                            crew.filling -> "It starts at midnight the day it is full. Install the others meanwhile."
                            else -> "Day 1 begins at midnight, your time. Install the others before then."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!crew.out) {
                        Spacer(Modifier.height(16.dp))
                        SeatTiles(crew.seats)
                    }
                    if (mine != null) {
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                mine.appName,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(10.dp))
                            SeatPill(mine.state)
                        }
                    }
                }
            }
        }
        if (vm.sentBack.isNotEmpty() && vm.blocked == null) {
            item {
                Box(Modifier.padding(horizontal = Gutter)) {
                    Primary("Put an app in again", onClick = actions.onTakeSeat)
                }
            }
        }
    }
}

/**
 * Everything of yours that is over.
 *
 * A list rather than the newest one, because a record's whole job is to still be there in six
 * months. What each row carries is the only two figures anybody comes back for: how long it ran
 * and how many stayed.
 */
@Composable
fun CrewRecordTab(
    vm: CrewViewModel,
    onSignIn: () -> Unit,
    onOpenCrew: (String) -> Unit,
) {
    val finished = vm.crews.filter { it.phase == CrewPhase.RECORD }

    if (vm.signedOut) {
        Gated(
            heading = "Sign in to see your record",
            body = "A crew lands here the day its fourteen nights are up, and stays for good.",
            onSignIn = onSignIn,
            mark = { RecordMark() },
        )
        return
    }
    if (vm.firstLoad) {
        CrewRecordLoading()
        return
    }
    if (finished.isEmpty()) {
        RobotState(
            Prop.Calendar,
            "Nothing finished yet",
            "A crew lands here the day its fourteen are up, and stays.",
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = Gutter + dockRoom()),
    ) {
        item { CrewHeading("Finished", "${finished.size}") }
        itemsIndexed(finished, key = { _, crew -> crew.id }) { index, crew ->
            ListTile(index, finished.size) {
                CrewRecordRow(crew) { onOpenCrew(crew.id) }
            }
        }
    }
}

/** One finished crew, with what it came to. */
@Composable
private fun CrewRecordRow(
    crew: Crew,
    onOpen: () -> Unit,
) {
    val stayed = crew.stayed
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = Gutter, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Status.shortSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${crew.finishedDays}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Status.short,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                crew.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "$stayed of ${crew.members.size} stayed the whole run",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Lucide.ChevronRight,
            null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One of your crews, as a line.
 *
 * The mark is the day it is on, which is the only number that separates two runs at a glance, and
 * it goes amber where a day has been missed. That is the same colour the warning above uses and
 * the same colour the heading above those rows implies, so one hue means one thing down the whole
 * screen.
 */
@Composable
fun CrewListRow(
    crew: Crew,
    onOpen: () -> Unit,
) {
    val tone = if (crew.atRisk) Status.short else Status.posted
    val soft = if (crew.atRisk) Status.shortSoft else Status.postedSoft
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = Gutter, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(soft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${crew.day}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = tone,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                crew.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(
                    crew.standing,
                    "${crew.owed} left".takeIf { crew.owed > 0 },
                    "missed yesterday".takeIf { crew.atRisk },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (crew.atRisk) Status.short else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Lucide.ChevronRight,
            null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
