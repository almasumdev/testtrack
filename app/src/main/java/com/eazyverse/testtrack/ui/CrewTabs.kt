package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Barlow
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
 * The top card keeps tonight's total, counted opens and one way to open the ready apps.
 * Updates hold warnings, installs and pictures; the guide explains the round.
 */
@Composable
fun CrewRunTab(
    vm: CrewViewModel,
    onSignIn: () -> Unit,
    actions: CrewActions,
    onOpenCrew: (String) -> Unit,
) {
    val runs = vm.crews.filter { it.phase == CrewPhase.RUN }

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

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = Gutter + dockRoom()),
    ) {
        item("hero") {
            Box(Modifier.padding(horizontal = Gutter)) {
                CrewRunSummary(runs, vm.tonight, vm.doneTonight, actions)
            }
        }
        item("updates") {
            CrewRunUpdates(runs, vm.tonight, actions, onOpenCrew)
        }

        item { CrewHeading("Your runs") }
        itemsIndexed(runs, key = { _, crew -> crew.id }) { index, crew ->
            // Fades in, fades out and glides to its new place when the list changes.
            Box(Modifier.animateItem().padding(start = Gutter, end = Gutter, top = if (index > 0) 8.dp else 0.dp)) {
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
        contentPadding = PaddingValues(top = 12.dp, bottom = Gutter + dockRoom()),
    ) {
        itemsIndexed(lobbies, key = { _, crew -> crew.id }) { index, crew ->
            // Fades in, fades out and glides to its new place when the list changes.
            Box(Modifier.animateItem().padding(start = Gutter, end = Gutter, top = if (index > 0) 10.dp else 0.dp)) {
                CrewLobbyRow(crew) { onOpenCrew(crew.id) }
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
            "A crew lands here the day its last night is over, and stays for good.",
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
            "A crew lands here the day its last night is over, and stays.",
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = Gutter + dockRoom()),
    ) {
        itemsIndexed(finished, key = { _, crew -> crew.id }) { index, crew ->
            // Fades in, fades out and glides to its new place when the list changes.
            Box(Modifier.animateItem().padding(start = Gutter, end = Gutter, top = if (index > 0) 8.dp else 0.dp)) {
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
    HairlineRow(
        leading = { CrewMark(mark, markTone) },
        title = title,
        note = AnnotatedString(line),
        noteTone = lineTone?.ink() ?: Tt.colors.text3,
        onClick = onOpen,
        trailing = {
            StatusChip(chip, chipTone)
            Icon(Lucide.ChevronRight, null, Modifier.padding(start = 2.dp).size(18.dp), tint = Tt.colors.text4)
        },
    )
}

/** The crew's number on a neutral tile; the tone lives in the number, not the tile. */
@Composable
private fun CrewMark(
    mark: String,
    markTone: Tone,
) {
    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Tt.colors.tile), contentAlignment = Alignment.Center) {
        Text(
            mark,
            style = TextStyle(fontFamily = Barlow, fontSize = 20.sp, lineHeight = 22.sp, fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = markTone.ink(),
        )
    }
}
