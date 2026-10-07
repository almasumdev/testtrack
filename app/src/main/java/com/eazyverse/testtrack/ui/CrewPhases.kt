package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Tt

/** The gutter every phase list opens on, matched to Circle so the two tabs sit level. */
private val ListTop = 16.dp

/**
 * Your app is in and the crew has not started: still filling, or full and waiting for midnight.
 *
 * What is owed of you comes first: your own app's state, and if it was sent back, why. Then the
 * crew, every app with a way to install it before day 1 and a way to say it will not.
 */
@Composable
fun CrewLobbyPhase(
    crew: Crew,
    actions: CrewActions,
) {
    val mine = crew.mine
    val others = crew.seats.filter { !it.mine && it.state != SeatState.REJECTED }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = ListTop, bottom = Gutter + dockRoom()),
    ) {
        if (!crew.out) {
            item {
                Box(Modifier.padding(horizontal = Gutter)) {
                    CrewCard(padding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Lucide.Clock, null, Modifier.size(16.dp), tint = Tt.colors.accentInk)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (crew.filling) {
                                    "${CrewRules.SEATS - crew.filled} more and it starts"
                                } else {
                                    "Starts in ${untilStart(crew.startsIn)}"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (crew.filling) {
                                "${crew.name} starts at midnight the day it is full. Install the " +
                                    "others meanwhile, so day 1 is only opening them."
                            } else {
                                "Day 1 begins at midnight, your time. Install the other " +
                                    "${others.size} before then and no day counts against you."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        SeatMeter(crew.filled, crew.taken - crew.filled)
                    }
                }
            }
        }

        if (mine != null) {
            item { CrewHeading("Your app") }
            item { Box(Modifier.padding(horizontal = Gutter)) { YourSeat(crew, mine, actions) } }
        }

        if (!crew.out && others.isNotEmpty()) {
            item { CrewHeading("Install these", "${others.size}") }
            itemsIndexed(others, key = { _, seat -> seat.id }) { index, seat ->
                ListTile(index, others.size) {
                    CrewSeatRow(seat, onInstall = { actions.onInstall(seat) }, onReport = { actions.onReport(seat) })
                }
            }
        }
    }
}

/** Your own app's place, and the one thing you can do about it at this point. */
@Composable
private fun YourSeat(
    crew: Crew,
    mine: CrewSeat,
    actions: CrewActions,
) {
    CrewCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(mine.appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    when (mine.state) {
                        SeatState.CONFIRMED ->
                            if (mine.reports > 0) {
                                "Play shows it to the testers group. ${mine.reports} of ${CrewRules.REPORTS_TO_RETURN} reports so far."
                            } else {
                                "Play shows it to the testers group."
                            }
                        SeatState.CHECKING -> "Waiting for Play. We check it again every few minutes."
                        SeatState.REJECTED, SeatState.REMOVED -> mine.outReason.ifBlank { "It came back to you." }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            SeatPill(mine.state)
        }

        // What reports said, for the owner only, without who said it.
        if (mine.reasons.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Notice(Status.short, Lucide.TriangleAlert) {
                Text(
                    mine.reasons.joinToString("\n") { "\u201C$it\u201D" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        when {
            mine.state == SeatState.REJECTED -> {
                Spacer(Modifier.height(12.dp))
                Primary("Put it in again", Modifier.fillMaxWidth(), onClick = actions.onTakeSeat)
            }
            // Leaving is only offered while it is filling. Once it is full fourteen people are
            // counting on it, and the server refuses it anyway.
            crew.filling -> {
                Spacer(Modifier.height(12.dp))
                CardRule()
                Spacer(Modifier.height(12.dp))
                Secondary(
                    "Take it back out",
                    Modifier.fillMaxWidth(),
                    destructive = true,
                    onClick = { actions.onLeave(mine) },
                )
            }
        }
    }
}

/**
 * One crew's night, as a list you can walk down.
 *
 * A row is a tap and the app opens. There is no sheet in between and no second confirmation,
 * because this list is used fourteen times a night for fourteen nights and anything charged per
 * app is charged nearly two hundred times.
 *
 * Above it, the same night in one press. Everything below the button exists for the evening
 * somebody wants to pick their way through; the button is for the other four nights in five.
 */
@Composable
fun CrewRunPhase(
    crew: Crew,
    actions: CrewActions,
) {
    val seatOf = { app: CrewApp -> crew.seats.first { it.id == app.id } }
    val owed = crew.apps.filter { !it.done && !it.reported }
    val settled = crew.apps.filter { it.done || it.reported }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = ListTop, bottom = Gutter + dockRoom()),
    ) {
        if (crew.out) {
            item {
                Box(Modifier.padding(horizontal = Gutter)) {
                    Notice(Status.missed, Lucide.TriangleAlert) {
                        Text(
                            "Your app left this run. ${crew.mine?.outReason.orEmpty()} Nobody opens it now, and " +
                                "you have nothing more to open here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        if (!crew.out) {
            item {
                Box(Modifier.padding(horizontal = Gutter)) {
                    CrewCard(padding = PaddingValues(16.dp)) {
                        Text(
                            if (owed.isEmpty()) "Tonight is done" else "${owed.size} left tonight",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (owed.isEmpty()) {
                                "All ${crew.apps.size} opened. Day ${crew.day + 1} begins at " +
                                    "midnight, your time."
                            } else {
                                "Day ${crew.day} of ${CrewRules.RUN_DAYS}" +
                                    if (crew.atRisk) {
                                        " · ${crew.myMisses} " +
                                            if (crew.myMisses == 1) "day missed" else "days missed"
                                    } else {
                                        " · no gaps"
                                    }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                when {
                                    owed.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                                    crew.atRisk -> Status.short
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                        Spacer(Modifier.height(16.dp))
                        DayTrack(crew.day)
                        if (crew.atRisk && owed.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            RiskNote(listOf(crew.name))
                        }
                        if (owed.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Primary("Open all ${owed.size}") { actions.onOpenAll(crew.id) }
                        }
                    }
                }
            }
        }

        if (owed.isNotEmpty()) {
            item { CrewHeading("Tonight", "${owed.size}") }
            itemsIndexed(owed, key = { _, app -> app.id }) { index, app ->
                ListTile(index, owed.size) {
                    CrewAppRow(app, onOpen = { actions.onOpen(app) }, onReport = { actions.onReport(seatOf(app)) })
                }
            }
        }

        if (settled.isNotEmpty()) {
            item { CrewHeading("Done", "${settled.size}") }
            itemsIndexed(settled, key = { _, app -> "s-${app.id}" }) { index, app ->
                ListTile(index, settled.size) {
                    CrewAppRow(app, onOpen = { actions.onOpen(app) }, onReport = { actions.onReport(seatOf(app)) })
                }
            }
        }

        item { CrewHeading("How everyone is doing", "${crew.members.size}") }
        itemsIndexed(crew.members, key = { _, m -> m.id }) { index, member ->
            ListTile(index, crew.members.size) {
                CrewMemberRow(member)
            }
        }
    }
}

/**
 * It is over, and this is what it was.
 *
 * Kept rather than cleared, because the fortnight is the thing somebody did and the record of it
 * is the only evidence they have that they did it. The crew's own figures come first, then
 * everybody in it.
 */
@Composable
fun CrewRecordPhase(crew: Crew) {
    val stayed = crew.stayed

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = ListTop, bottom = Gutter + dockRoom()),
    ) {
        item {
            Box(Modifier.padding(horizontal = Gutter)) {
                CrewCard(padding = PaddingValues(16.dp)) {
                    Text(
                        "${crew.name} finished",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$stayed of ${crew.members.size} went the distance" +
                            if (stayed >= CrewRules.FLOOR) {
                                ", which is Google's ${CrewRules.FLOOR} or more."
                            } else {
                                ". Google asks for ${CrewRules.FLOOR}."
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Tally(Modifier.weight(1f), "${crew.finishedDays}", "days tested")
                        Tally(Modifier.weight(1f), "$stayed", "still in")
                        Tally(Modifier.weight(1f), "${crew.myMisses}", "you missed")
                    }
                }
            }
        }

        item { CrewHeading("Everyone", "${crew.members.size}") }
        itemsIndexed(crew.members, key = { _, m -> "rec-${m.id}" }) { index, member ->
            ListTile(index, crew.members.size) {
                CrewMemberRow(member)
            }
        }
    }
}

/**
 * Fourteen days as fourteen marks, with today lit.
 *
 * The same idea as [SeatMeter] and deliberately the same shape, because both answer the same
 * question: how much of this is behind me. One control learned twice is one control.
 */
@Composable
private fun DayTrack(day: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(CrewRules.RUN_DAYS) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            index < day - 1 -> MaterialTheme.colorScheme.primary
                            index == day - 1 -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            )
        }
    }
}
