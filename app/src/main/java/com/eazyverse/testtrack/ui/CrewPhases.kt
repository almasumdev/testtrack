package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleX
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Undo2
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.ui.theme.Tt

/** The space under the bar, the same 8dp as every other page with a back bar. */
private val ListTop = 8.dp

/** Where a row sits in its run of rows, which rounds its outer corners. */
internal fun placeOf(
    index: Int,
    count: Int,
): TilePlace =
    when {
        count == 1 -> TilePlace.ONLY
        index == 0 -> TilePlace.FIRST
        index == count - 1 -> TilePlace.LAST
        else -> TilePlace.MIDDLE
    }

private fun LazyListScope.block(content: @Composable () -> Unit) = item { Box(Modifier.padding(horizontal = Gutter)) { content() } }

private fun <T> LazyListScope.rows(
    key: String,
    list: List<T>,
    id: (T) -> String,
    row: @Composable (T, TilePlace) -> Unit,
) = itemsIndexed(list, key = { _, it -> "$key-${id(it)}" }) { index, it ->
    Box(Modifier.animateItem().padding(horizontal = Gutter).padding(bottom = if (index < list.size - 1) 3.dp else 0.dp)) {
        row(it, placeOf(index, list.size))
    }
}

/** A crew's page, laid out the same way in every phase: hero, your app, then its lists. */
@Composable
private fun CrewList(
    crew: Crew,
    actions: CrewActions,
    content: LazyListScope.(onSheet: (RowSheet) -> Unit) -> Unit,
) {
    var sheet by remember { mutableStateOf<RowSheet?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = ListTop, bottom = Gutter + dockRoom())) {
        content { sheet = it }
    }
    sheet?.let { CrewRowSheet(it, crew.name, actions) { sheet = null } }
}

/**
 * Your app is in and the crew has not started: still filling, or full and waiting for midnight.
 * Or it was sent back, which is the whole page until it is put in again.
 */
@Composable
fun CrewLobbyPhase(
    crew: Crew,
    actions: CrewActions,
) {
    val mine = crew.mine
    val others = crew.seats.filter { !it.mine && it.state != SeatState.REJECTED }
    val asked = if (crew.mayVote) others.filter { it.state == SeatState.CHECKING } else emptyList()
    val install = others - asked.toSet()

    CrewList(crew, actions) { onSheet ->
        // Sent back by members, or taken out by the team before the run: either way it is not
        // in this crew any more, and its owner is told why rather than shown a lobby.
        if (mine != null && (mine.state == SeatState.REJECTED || mine.state == SeatState.REMOVED)) {
            block { SentBack(crew, mine, actions) }
            item { CrewHeading("Your app") }
            block { YourAppCard(mine, "Sent back", Tone.BAD, "Not in ${crew.name} any more.") }
            return@CrewList
        }
        block { LobbyHero(crew) }
        if (mine != null) {
            item { CrewHeading("Your app") }
            block { LobbyYourApp(crew, mine, actions) }
        }
        if (asked.isNotEmpty()) {
            item { CrewHeading("Does it install for you?", "${asked.size}") }
            item { AskedSteps() }
            rows("ask", asked, { it.id }) { seat, place -> AskedRow(seat, place, actions, onSheet) }
        }
        if (install.isNotEmpty()) {
            item { CrewHeading("Install these", "${install.size}") }
            rows("in", install, { it.id }) { seat, place -> InstallRow(seat, place, actions, onSheet) }
        }
    }
}

/** How many answers settle an app Play could not check, said the same way wherever it is said. */
private val DECIDES =
    "${CrewRules.VOTES_TO_DECIDE} Yes answers or ${CrewRules.VOTES_TO_DECIDE} No answers decide it."

/** What to do with an app Play could not check, as steps, over the rows that ask. */
@Composable
private fun AskedSteps() {
    Column(Modifier.padding(start = Gutter, end = Gutter, bottom = 10.dp)) {
        listOf(
            "Play couldn't check these, so members decide.",
            "1. Install the app from its row.",
            "2. Once it's on your phone, the row asks if it works. Answer Yes or No.",
            "3. Won't install? Tap the row and say so.",
            DECIDES,
        ).forEach { line ->
            Text(line, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = Tt.colors.text4)
        }
    }
}

@Composable
private fun LobbyHero(crew: Crew) {
    val checking = (crew.taken - crew.filled).coerceAtLeast(0)
    if (crew.filling) {
        CrewHero(
            chip = "Filling, ${crew.filled} of ${CrewRules.SEATS}",
            tone = Tone.ACCENT,
            icon = Lucide.Users,
            headline = "${CrewRules.SEATS - crew.filled} more and it starts",
            line = "It starts at midnight, your time, the day it's full. Install the others now, so day 1 is only opening them.",
            meter = {
                SeatMeter(crew.filled, checking)
                MeterLabels("${crew.filled} in" + if (checking > 0) ", $checking being checked" else "", "${CrewRules.SEATS} seats")
            },
        )
    } else if (crew.firstNight > 1) {
        // Put in by the team part-way through: this account's own run starts on a later day.
        CrewHero(
            chip = "Joining on day ${crew.firstNight}",
            tone = Tone.OK,
            icon = Lucide.Clock,
            headline = "Starts in ${untilStart(crew.startsIn)}",
            line =
                "This crew is already running. Your first day is day ${crew.firstNight}, from midnight, your time. " +
                    "Install the others before then and no day counts against you.",
            meter = {
                DayMeter(crew.firstNight - 1, done = true, from = crew.firstNight)
                MeterLabels("You join on day ${crew.firstNight}", "${crew.ownNights} ${if (crew.ownNights == 1) "day" else "days"} for you")
            },
        )
    } else {
        CrewHero(
            chip = "Full, ${CrewRules.SEATS} of ${CrewRules.SEATS}",
            tone = Tone.OK,
            icon = Lucide.Clock,
            headline = "Starts in ${untilStart(crew.startsIn)}",
            line = "Day 1 begins at midnight, your time. Install the other ${CrewRules.NIGHTLY} before then and no day counts against you.",
            meter = {
                SeatMeter(crew.filled, checking)
                MeterLabels("Everyone's in", "Day 1 at midnight, your time")
            },
        )
    }
}

@Composable
private fun LobbyYourApp(
    crew: Crew,
    mine: CrewSeat,
    actions: CrewActions,
) {
    val (chip, tone) =
        when {
            mine.state == SeatState.CHECKING -> "Checking" to Tone.WARN
            mine.reports > 0 -> "${mine.reports} of ${CrewRules.REPORTS_TO_RETURN} reports" to Tone.WARN
            else -> "In" to Tone.OK
        }
    val status =
        when {
            mine.state == SeatState.CHECKING -> "Play couldn't check it, so members are trying it."
            mine.reports > 0 -> "Play shows it to the testers group, but a member had trouble."
            !crew.filling -> "Play shows it to the testers group. The crew is full, so it stays in now."
            else -> "Play shows it to the testers group."
        }
    YourAppCard(
        mine,
        chip,
        tone,
        status,
        extra = {
            when {
                mine.state == SeatState.CHECKING ->
                    Quotes(emptyList(), "${mine.yes} Yes and ${mine.no} No so far. $DECIDES Make sure the sign-in works.")
                mine.reasons.isNotEmpty() ->
                    Quotes(mine.reasons, "One more report sends it back. Check the sign-in still works.")
            }
        },
        action =
            if (crew.filling) {
                {
                    Spacer(Modifier.height(12.dp))
                    val leaving = actions.busySeat == mine.id
                    Secondary(
                        if (leaving) "Taking it out" else "Take it back out",
                        Modifier.fillMaxWidth(),
                        destructive = true,
                        busy = leaving,
                    ) { actions.onLeave(mine) }
                    CardNote("Your ${CrewRules.SEAT_COST} tix come back in full.")
                }
            } else {
                null
            },
    )
}

/** Your app came back before the run: why, in the members' own words, and the way back in. */
@Composable
private fun SentBack(
    crew: Crew,
    mine: CrewSeat,
    actions: CrewActions,
) {
    val byTeam = mine.state == SeatState.REMOVED
    androidx.compose.foundation.layout.Column {
        CrewHero(
            chip = if (byTeam) "Taken out" else "Sent back",
            tone = Tone.BAD,
            icon = Lucide.Undo2,
            headline = if (byTeam) "The TestTrack team took your app out" else "Your app came back",
            line = mine.outReason.ifBlank { if (byTeam) "It is not in ${crew.name} any more." else "It came back to you before the run." },
            extra = {
                if (mine.reasons.isNotEmpty()) Quotes(mine.reasons, "What members wrote. Who wrote it isn't shown.", Tone.BAD)
            },
            action = if (byTeam) "Put it in a crew again" else "Fix it, then put it in again",
            onAction = actions.onTakeSeat,
        )
        CardNote(seatRefundLine(mine, if (byTeam) "" else " Fix what's wrong, then put it in a crew again."))
    }
}

/**
 * One crew's night. A row is a tap and the app opens; an app not on this phone offers Install
 * first. Above it, the night in one press, for the apps that are here.
 */
@Composable
fun CrewRunPhase(
    crew: Crew,
    actions: CrewActions,
) {
    val seatOf = { app: CrewApp -> crew.seats.first { it.id == app.id } }
    val owed = crew.apps.filter { !it.done && !it.reported }
    val settled = crew.apps.filter { it.done || it.reported }
    val here = owed.filter { actions.isInstalled(it.packageName) }
    val missing = owed - here.toSet()

    CrewList(crew, actions) { onSheet ->
        if (crew.out) {
            block {
                androidx.compose.foundation.layout.Column {
                    CrewHero(
                        chip = "Your app is out",
                        tone = Tone.BAD,
                        icon = Lucide.CircleX,
                        headline = "Your app left this run",
                        line = "${crew.mine?.outReason.orEmpty().ifBlank {
                            "It missed two days in a row."
                        }} Nobody opens it now, and there's nothing more for you to open here.",
                        action = "Put it in a new crew",
                        onAction = actions.onTakeSeat,
                    )
                    CardNote(
                        crew.mine?.let { seatOutLine(it) } ?: "Seats are kept once a run starts, so this one's tix aren't refunded.",
                    )
                }
            }
            return@CrewList
        }
        block { RunHero(crew, owed, here, missing, actions) }
        crew.mine?.let { mine ->
            item { CrewHeading("Your app") }
            block { TestersCard(crew, mine, actions) }
        }
        if (owed.isNotEmpty()) {
            item { CrewHeading("Tonight", "${owed.size}") }
            rows("t", owed, { it.id }) { app, place -> TonightRow(app, seatOf(app), place, actions, onSheet) }
        }
        if (settled.isNotEmpty()) {
            item { CrewHeading("Done", "${settled.size}") }
            rows("d", settled, { it.id }) { app, place -> DoneRow(app, seatOf(app), place, onSheet) }
        }
    }
}

@Composable
private fun RunHero(
    crew: Crew,
    owed: List<CrewApp>,
    here: List<CrewApp>,
    missing: List<CrewApp>,
    actions: CrewActions,
) {
    val done = owed.isEmpty()
    val last = crew.day >= CrewRules.RUN_DAYS
    val next = if (last) "That was the last night." else "Day ${crew.ownDay + 1} begins at midnight, your time."
    val line =
        when {
            done -> "All ${crew.apps.size} opened. $next"
            crew.atRisk -> "Miss tonight too and your app leaves this crew."
            missing.size == 1 -> "${missing.first().label} isn't on your phone yet, so install it first."
            missing.isNotEmpty() -> "${missing.size} apps aren't on your phone yet, so install them first."
            last -> "No missed days. Tonight is the last night."
            else -> "No missed days. Day ${crew.ownDay + 1} begins at midnight, your time."
        }
    CrewHero(
        chip = if (crew.atRisk) "Day ${crew.ownDay}, ${crew.myMisses} missed" else "Day ${crew.ownDay} of ${crew.ownNights}",
        tone =
            if (crew.atRisk) {
                Tone.WARN
            } else if (done) {
                Tone.OK
            } else {
                Tone.ACCENT
            },
        icon =
            if (crew.atRisk) {
                Lucide.TriangleAlert
            } else if (done) {
                Lucide.CircleCheck
            } else {
                Lucide.Clock
            },
        headline = if (done) "Tonight is done" else "${owed.size} left tonight",
        line = line,
        lineTone = if (crew.atRisk && !done) Tone.WARN else null,
        meter = {
            DayMeter(crew.day, done, from = crew.firstNight)
            // Tonight counts as done once it is, so the two sides always add up to this account's run.
            val daysDone = (crew.ownDay - if (done) 0 else 1).coerceIn(0, crew.ownNights)
            MeterLabels("$daysDone ${if (daysDone == 1) "day" else "days"} done", "${crew.ownNights - daysDone} to go")
        },
        action =
            when {
                here.isEmpty() -> null
                here.size == 1 -> "Open ${here.first().label}"
                missing.isEmpty() -> "Open all ${here.size}"
                else -> "Open the ${here.size} on your phone"
            },
        onAction = { actions.onOpenAll(crew.id) },
    )
}

/** Your app during a run, or after one: how many of the others opened it, and who. */
@Composable
private fun TestersCard(
    crew: Crew,
    mine: CrewSeat,
    actions: CrewActions,
) {
    val testers = crew.testers.size
    val finished = crew.phase == CrewPhase.RECORD
    val opened = if (finished) crew.testedEveryDay else crew.openedMineTonight
    val enough = opened >= CrewRules.FLOOR
    val status =
        if (finished) {
            "Tested by $opened people on all ${crew.finishedDays} days. " +
                if (enough) "Enough for Google." else "Google asks for ${CrewRules.FLOOR}."
        } else {
            "Opened by $opened of $testers testers so far tonight."
        }
    YourAppCard(
        mine.copy(notes = ""),
        "$opened of $testers",
        if (!finished) {
            Tone.ACCENT
        } else if (enough) {
            Tone.OK
        } else {
            Tone.WARN
        },
        status,
        extra = {
            Spacer(Modifier.height(14.dp))
            val fill =
                if (!finished) {
                    Tt.colors.accent
                } else if (enough) {
                    Status.posted
                } else {
                    Status.short
                }
            SegmentMeter(testers.coerceAtLeast(1)) { i -> if (i < opened) fill else Tt.colors.glass2 }
            MeterLabels(if (finished) "${crew.finishedDays} days done" else "Their day ${crew.day}", "${CrewRules.FLOOR} keep it counting")
        },
        action = {
            Spacer(Modifier.height(12.dp))
            Secondary("See who tested it", Modifier.fillMaxWidth(), onClick = actions.onMembers)
        },
    )
}

/** It is over, and this is what it was: the crew's figures, then what it did for your app. */
@Composable
fun CrewRecordPhase(
    crew: Crew,
    actions: CrewActions = CrewActions({}, {}, {}, {}, {}, {}),
) {
    val stayed = crew.stayed
    val enough = stayed >= CrewRules.FLOOR
    CrewList(crew, actions) { _ ->
        block {
            CrewHero(
                chip = "Finished after ${crew.finishedDays} days",
                tone = if (enough) Tone.OK else Tone.WARN,
                icon = if (enough) Lucide.CircleCheck else Lucide.TriangleAlert,
                headline = "${crew.name} finished",
                line =
                    if (enough) {
                        "$stayed of ${crew.members.size} went the distance, Google's ${CrewRules.FLOOR} or more. You can apply for production."
                    } else {
                        "$stayed of ${crew.members.size} went the distance. Google asks for ${CrewRules.FLOOR}, so this run may not count."
                    },
                lineTone = if (enough) null else Tone.WARN,
                extra = { StatTiles("${crew.finishedDays}" to "days tested", "$stayed" to "stayed", "${crew.myMisses}" to "you missed") },
                action = if (enough) null else "Put it in a new crew",
                onAction = actions.onTakeSeat,
            )
        }
        // Paid by the server as the crew closed; an older server, or a run with a miss, says nothing.
        crew.mine?.takeIf { it.refunded > 0 }?.let { mine ->
            block { CardNote("You never missed a day, so your ${mine.refunded} tix came back.") }
        }
        crew.mine?.let { mine ->
            item { CrewHeading("Your app") }
            block { TestersCard(crew, mine, actions) }
        }
    }
}

/** What came back for a seat that left before its run, as the server says it was paid. */
internal fun seatRefundLine(
    seat: CrewSeat,
    after: String = "",
): String =
    when {
        seat.refunded > 0 -> "Your ${seat.refunded} tix are back.$after"
        seat.charged == 0 -> "It was placed free by the TestTrack team, so there was nothing to refund.$after"
        seat.charged > 0 -> "Your ${seat.charged} tix are on their way back.$after"
        // An older server that does not say: the usual price, as before.
        else -> "Your ${CrewRules.SEAT_COST} tix are back.$after"
    }

/** What a seat out of a running crew came to: refunded only before its run began. */
internal fun seatOutLine(seat: CrewSeat): String =
    when {
        seat.refunded > 0 -> "Its ${seat.refunded} tix were refunded."
        seat.charged == 0 -> "It was placed free by the TestTrack team, so there was nothing to refund."
        else -> "Seats are kept once a run starts, so this one's tix aren't refunded."
    }
