package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
    onOpenChat: (String) -> Unit,
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
                CrewListRow(crew, onChat = { onOpenChat(crew.id) }) { onOpenCrew(crew.id) }
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
    onOpenChat: (String) -> Unit,
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
                CrewLobbyRow(crew, onChat = { onOpenChat(crew.id) }) { onOpenCrew(crew.id) }
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
 * months. Read from the kept records rather than the crew list: a member whose app was moved or
 * taken out cannot open that crew any more and still has its record. Each tile says what came of
 * the run, and asks the one or two things it still can.
 */
@Composable
fun CrewRecordTab(
    vm: CrewViewModel,
    onSignIn: () -> Unit,
    onOpenCrew: (String) -> Unit,
) {
    val context = LocalContext.current
    val records = vm.records.items

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
    if (records == null && vm.records.failed) {
        RobotOffline("Your record didn't load", onRetry = { vm.load(context) })
        return
    }
    if (vm.firstLoad || records == null) {
        CrewRecordLoading()
        return
    }
    if (records.isEmpty()) {
        RobotState(
            Prop.Calendar,
            "Nothing finished yet",
            "A crew lands here the day its last night is over, and stays.",
        )
        return
    }

    // Only a crew still in the crew list can be opened; the others answer 403 now.
    val crews = vm.crews.filter { it.phase == CrewPhase.RECORD }.associateBy { it.id }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = Gutter + dockRoom()),
    ) {
        itemsIndexed(records, key = { _, record -> record.crewId }) { index, record ->
            val crew = crews[record.crewId]
            // Fades in, fades out and glides to its new place when the list changes.
            Box(Modifier.animateItem().padding(start = Gutter, end = Gutter, top = if (index > 0) 8.dp else 0.dp)) {
                RecordTile(
                    record = record,
                    crew = crew,
                    busy = vm.records.busy[record.crewId],
                    onOpen = crew?.let { { onOpenCrew(it.id) } },
                    onAnswer = { vm.records.answerProdAccess(record.crewId, it) },
                    onClaim = { vm.records.claimPerfect(record.crewId) },
                )
            }
        }
    }
}
