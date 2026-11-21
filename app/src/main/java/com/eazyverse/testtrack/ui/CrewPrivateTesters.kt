package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserPlus
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AppNights
import com.eazyverse.testtrack.viewmodel.PrivateResults
import com.eazyverse.testtrack.viewmodel.PrivateState
import com.eazyverse.testtrack.viewmodel.PrivateTester
import com.eazyverse.testtrack.viewmodel.PrivateTestsViewModel
import com.eazyverse.testtrack.viewmodel.ResultsFault

/*
 * The owner's private testers for one app in a running crew: people asked by email, who open the
 * app when they like. Only the owner sees them. Their nights in the crew's grid, without Google's
 * count, then a tile each, with the way to take somebody off.
 */

/** The way in, on the crew page under the owner's testers card. */
@Composable
internal fun PrivateTestersRow(
    line: String,
    onClick: () -> Unit,
) {
    Box(Modifier.padding(top = 8.dp)) { AskRow("Private testers", line, onClick, icon = Lucide.Lock) }
}

/** The page's wiring: the crew from the Crew tab's model, the list from the private testers' one. */
@Composable
fun PrivateTestersScreen(
    crews: CrewViewModel,
    privates: PrivateTestsViewModel,
    seatId: String,
    onBack: () -> Unit,
) {
    val crew = crews.crews.firstOrNull { c -> c.seats.any { it.id == seatId && it.mine } }
    val seat = crew?.seats?.firstOrNull { it.id == seatId }
    // Read on arrival, and again whenever the crews reload, which a private.changed event does.
    LaunchedEffect(seatId, crews.reloading) { if (!crews.reloading) privates.loadResults(seatId) }
    CrewLifecycle(crews)
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<PrivateTester?>(null) }
    val appName = seat?.appName ?: privates.results[seatId]?.appName.orEmpty()
    PrivateTestersBody(
        appName = appName,
        crewName = crew?.name.orEmpty(),
        today = crew?.day ?: 0,
        daysLeft = crew?.let { it.runDays - it.day } ?: 0,
        results = privates.results[seatId],
        fault = privates.faults[seatId],
        canAdd = crew?.phase == CrewPhase.RUN && seat?.state == SeatState.CONFIRMED,
        onBack = onBack,
        onRetry = { privates.loadResults(seatId) },
        onAdd = { adding = true },
        onRemove = { removing = it },
    )
    if (adding && crew != null && seat != null) {
        PrivateAddSheet(
            appName = seat.appName,
            packageName = seat.packageName,
            icon = seat.icon,
            crewName = crew.name,
            busy = privates.inviting,
            refusal = privates.refusal,
            onSend = { email, close -> privates.invite(seatId, email, close) },
            onDismiss = {
                adding = false
                privates.clearRefusal()
            },
        )
    }
    removing?.let { tester ->
        val asked = tester.state == PrivateState.INVITED
        val first = tester.name.substringBefore(' ')
        Ask(
            title = if (asked) "Cancel the invite to ${tester.name}?" else "Remove ${tester.name}?",
            body =
                if (asked) {
                    "$first can't accept it after this. You can ask again later."
                } else {
                    "$first stops testing $appName, and their nights leave this list."
                },
            confirm = if (asked) "Cancel invite" else "Remove",
            dismiss = "Keep",
            destructive = true,
            onConfirm = {
                removing = null
                privates.remove(tester)
            },
            onDismiss = { removing = null },
        )
    }
}

/** The page drawn from plain values, in every state it has. */
@Composable
fun PrivateTestersBody(
    appName: String,
    crewName: String,
    today: Int,
    daysLeft: Int,
    results: PrivateResults?,
    fault: ResultsFault?,
    canAdd: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAdd: () -> Unit,
    onRemove: (PrivateTester) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PageBar(
            "Private testers",
            onBack,
            subtitle = listOf(appName, crewName).filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                results == null && fault != null ->
                    if (fault.offline) {
                        RobotOffline("Private testers didn't load", onRetry = onRetry)
                    } else {
                        RobotState(
                            Prop.Notes,
                            "Private testers didn't load",
                            fault.message,
                            action = "Back",
                            onAction = onBack,
                            failed = true,
                        )
                    }
                results == null -> TestersLoading(gridRows = 2, tiles = 2)
                results.shown.isEmpty() ->
                    RobotState(
                        Prop.Letter,
                        "No private testers yet",
                        "Add people by the email of their TestTrack account. They open $appName when they like, and only you see it.",
                    )
                else -> PrivateTestersList(results, crewName, today, daysLeft, onRemove)
            }
            FootFade(Modifier.align(Alignment.BottomCenter))
        }
        Column(Modifier.fillMaxWidth().snackFloor().padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 6.dp + dockRoom())) {
            Primary(
                "Add a tester",
                Modifier.fillMaxWidth(),
                enabled = canAdd && results != null,
                mark = rememberVectorPainter(Lucide.UserPlus),
                onClick = onAdd,
            )
        }
    }
}

@Composable
private fun PrivateTestersList(
    results: PrivateResults,
    crewName: String,
    today: Int,
    daysLeft: Int,
    onRemove: (PrivateTester) -> Unit,
) {
    val t = Tt.colors
    val tiles = remember(results) { privateOrder(results.shown) }
    val testers = remember(tiles, results.runDays) { tiles.associate { it.id to privateTesterOf(it, results.runDays) } }
    // Only those who said yes have nights to draw.
    val rows = tiles.mapNotNull { testers[it.id]?.takeIf { row -> row.squares.isNotEmpty() } }
    var profile by remember { mutableStateOf<CrewTester?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp)) {
        item {
            IconLine(
                Lucide.Lock,
                t.text4,
                AnnotatedString("Only you see this. ${privateEndsLine(crewName, daysLeft)}"),
                tileStyle(12.5f, 17f, FontWeight.Normal),
                t.text4,
                Modifier.padding(horizontal = Gutter).padding(bottom = 12.dp),
            )
        }
        if (rows.isNotEmpty()) {
            item {
                Box(Modifier.padding(horizontal = Gutter)) {
                    val (chip, tone) = privateChipOf(results.shown)
                    TrackingGrid(
                        AppNights(results.appName, results.runDays, emptyList()),
                        rows,
                        today = if (today in 1..results.runDays) today else 0,
                        onFace = { profile = it },
                        chip = { StatusChip(chip, tone) },
                        showCounts = false,
                        note = "Opened or not, each night they were in",
                    )
                }
            }
        }
        item { CrewHeading("Testers", "${tiles.size}") }
        itemsIndexed(tiles, key = { _, it -> it.id }) { index, tester ->
            Box(Modifier.padding(horizontal = Gutter).padding(bottom = if (index < tiles.size - 1) 8.dp else 0.dp)) {
                TesterTile(
                    testers.getValue(tester.id),
                    onFace = { profile = testers.getValue(tester.id) },
                    dayLabel = privateNightsLabel(tester),
                    onRemove = if (tester.state.open) ({ onRemove(tester) }) else null,
                )
            }
        }
    }
    profile?.let { CrewMemberProfile(it) { profile = null } }
}
