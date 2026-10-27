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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
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
        RobotState(
            Prop.Lock,
            "Sign in to see tonight's apps",
            "Hold a seat and fourteen nights of testing start here, one night at a time.",
            action = "Sign in",
            onAction = onSignIn,
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
    // Tonight is one of them until it is done, as the crew's own page counts it.
    val nightsLeft = (going.ifEmpty { runs }).maxOf { (CrewRules.RUN_DAYS - it.day + if (left > 0) 1 else 0).coerceAtLeast(0) }
    val crews = "${going.size} ${if (going.size == 1) "crew" else "crews"}"

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = Gutter + dockRoom()),
    ) {
        item {
            Column(Modifier.padding(horizontal = Gutter)) {
                CrewHero(
                    chip = "Tonight, $crews",
                    tone =
                        if (left == 0) {
                            Tone.OK
                        } else if (risky.isNotEmpty()) {
                            Tone.WARN
                        } else {
                            Tone.ACCENT
                        },
                    icon =
                        if (left == 0) {
                            Lucide.CircleCheck
                        } else if (risky.isNotEmpty()) {
                            Lucide.TriangleAlert
                        } else {
                            Lucide.Clock
                        },
                    headline = if (left == 0) "Tonight is done" else "$left left tonight",
                    line =
                        when {
                            // The payoff. Nothing else in a fortnight of opening apps says a night
                            // went right, and a zero does not feel like one.
                            left == 0 -> "All $total opened, across $crews. Nothing counts against you."
                            risky.isNotEmpty() -> {
                                val verb = if (risky.size == 1) "is" else "are"
                                "${risky.joinToString()} $verb one missed day from dropping your app."
                            }
                            else -> "$done of $total done, across $crews."
                        },
                    lineTone = if (left > 0 && risky.isNotEmpty()) Tone.WARN else null,
                    meter = {
                        val lit = if (left == 0) Status.posted else Tt.colors.accent
                        SegmentMeter(total.coerceIn(1, 42)) { i -> if (i < done) lit else Tt.colors.glass2 }
                        MeterLabels("$done of $total opened", "$nightsLeft ${if (nightsLeft == 1) "night" else "nights"} to go")
                    },
                    extra = {
                        StatTiles(
                            "${CrewRules.nightly(going.size)}" to "apps a night",
                            "$nightsLeft" to "nights to go",
                            "${going.size}" to if (going.size == 1) "crew" else "crews",
                        )
                    },
                    // The whole night, across every crew, in one press: the apps on this phone.
                    action = if (left > 0) "Open all $left" else null,
                    onAction = { actions.onOpenAll(null) },
                )
                PicturesDueCard(going.flatMap { it.apps }, actions)
            }
        }

        item { CrewHeading("Your runs", "${runs.size}") }
        itemsIndexed(runs, key = { _, crew -> crew.id }) { index, crew ->
            // Fades in, fades out and glides to its new place when the list changes.
            Box(Modifier.animateItem()) {
                ListTile(index, runs.size) {
                    CrewListRow(crew) { onOpenCrew(crew.id) }
                }
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
        RobotState(
            Prop.Lock,
            "Sign in to see your crew",
            "A crew you have joined waits here until all fifteen apps are in.",
            action = "Sign in",
            onAction = onSignIn,
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
        item { CrewHeading("Your crews", "${lobbies.size}", top = 8.dp) }
        itemsIndexed(lobbies, key = { _, crew -> crew.id }) { index, crew ->
            // Fades in, fades out and glides to its new place when the list changes.
            Box(Modifier.animateItem()) {
                ListTile(index, lobbies.size) {
                    val mine = crew.mine
                    val (chip, tone) =
                        when {
                            crew.out -> "Sent back" to Tone.BAD
                            mine?.state == SeatState.CHECKING -> "Checking" to Tone.WARN
                            mine != null && mine.reports > 0 -> "${mine.reports} of ${CrewRules.REPORTS_TO_RETURN} reports" to Tone.WARN
                            else -> "In" to Tone.OK
                        }
                    CrewSummaryRow(
                        mark = if (crew.filling) "${crew.filled}" else "${CrewRules.SEATS}",
                        markTone =
                            if (crew.out) {
                                Tone.BAD
                            } else if (crew.filling) {
                                Tone.ACCENT
                            } else {
                                Tone.OK
                            },
                        title = crew.name,
                        line =
                            when {
                                crew.out -> "Sent your app back"
                                crew.filling -> {
                                    val more = CrewRules.SEATS - crew.filled
                                    "${crew.filled} of ${CrewRules.SEATS} in, $more more to start"
                                }
                                else -> "Full, starts in ${untilStart(crew.startsIn)}"
                            },
                        chip = chip,
                        chipTone = tone,
                        onOpen = { onOpenCrew(crew.id) },
                    )
                }
            }
        }
        if (vm.sentBack.isNotEmpty() && vm.blocked == null) {
            item {
                Box(Modifier.padding(start = Gutter, end = Gutter, top = 16.dp)) {
                    Primary("Put an app in again", Modifier.fillMaxWidth(), onClick = actions.onTakeSeat)
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
        RobotState(
            Prop.Lock,
            "Sign in to see your record",
            "A crew lands here the day its fourteen nights are up, and stays for good.",
            action = "Sign in",
            onAction = onSignIn,
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
            // Fades in, fades out and glides to its new place when the list changes.
            Box(Modifier.animateItem()) {
                ListTile(index, finished.size) {
                    CrewRecordRow(crew) { onOpenCrew(crew.id) }
                }
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
    val enough = stayed >= CrewRules.FLOOR
    CrewSummaryRow(
        mark = "${crew.finishedDays}",
        markTone = if (enough) Tone.OK else Tone.WARN,
        title = crew.name,
        line = "$stayed of ${crew.members.size} stayed the whole run",
        chip = if (enough) "Counts" else "Under ${CrewRules.FLOOR}",
        chipTone = if (enough) Tone.OK else Tone.WARN,
        onOpen = onOpen,
    )
}

/**
 * One of your crews, as a line. The mark is the day it is on, amber where a day was missed, the
 * same colour the warning above uses, so one hue means one thing down the whole screen.
 */
@Composable
fun CrewListRow(
    crew: Crew,
    onOpen: () -> Unit,
) {
    val (chip, tone) =
        when {
            crew.out -> "Your app is out" to Tone.BAD
            crew.atRisk -> "${crew.myMisses} missed" to Tone.WARN
            crew.owed == 0 -> "Done" to Tone.OK
            else -> "${crew.owed} left" to Tone.ACCENT
        }
    CrewSummaryRow(
        mark = "${crew.day}",
        markTone = if (crew.atRisk || crew.out) Tone.WARN else Tone.OK,
        title = crew.name,
        line = "Day ${crew.day} of ${CrewRules.RUN_DAYS}" + if (crew.atRisk) ", missed yesterday" else "",
        lineTone = if (crew.atRisk) Tone.WARN else null,
        chip = chip,
        chipTone = tone,
        onOpen = onOpen,
    )
}

/** A crew as one row in a tab: its mark, its name, one line, where it stands, and a chevron. */
@Composable
private fun CrewSummaryRow(
    mark: String,
    markTone: Tone,
    title: String,
    line: String,
    chip: String,
    chipTone: Tone,
    onOpen: () -> Unit,
    lineTone: Tone? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(markTone.soft()), contentAlignment = Alignment.Center) {
            Text(mark, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = markTone.ink())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                line,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                color = lineTone?.ink() ?: Tt.colors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusChip(chip, chipTone)
        Icon(Lucide.ChevronRight, null, Modifier.padding(start = 2.dp).size(18.dp), tint = Tt.colors.text4)
    }
}
