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
import com.eazyverse.testtrack.ui.theme.Tt

/** The gutter every phase list opens on, matched to Circle so the two tabs sit level. */
private val ListTop = 8.dp

/**
 * Your seat is taken and the crew is filling. The one screen in the product with shared work in it.
 *
 * Two things are happening at once and the screen is in that order: what is owed of you, then what
 * is owed to you. Judging other people's apps comes first because it is the thing that moves, and
 * because a member who does it is a member whose own setup is finished.
 */
@Composable
fun CrewLobbyPhase(
    crew: Crew,
    onJudge: (CrewSeat) -> Unit,
    onLeave: () -> Unit = {},
) {
    val confirmed = CrewRules.filled(crew.seats)
    val checking = crew.seats.count { it.state == SeatState.CHECKING }
    val mine = crew.seats.firstOrNull { it.mine }
    val toJudge =
        crew.seats.filter { !it.judged && !it.mine && it.state == SeatState.CHECKING }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = ListTop, bottom = Gutter),
    ) {
        item {
            Box(Modifier.padding(horizontal = Gutter)) {
                CrewCard(padding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Lucide.Clock,
                            null,
                            Modifier.size(16.dp),
                            tint = Tt.colors.accentInk,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (confirmed >= CrewRules.SEATS) {
                                "Starts in ${untilStart(crew.startsIn)}"
                            } else {
                                "${CrewRules.SEATS - confirmed} more and it starts"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (confirmed >= CrewRules.SEATS) {
                            "Day 1 begins at 12:01 tonight, your time. Anything left to set " +
                                "up, do it before then and no day counts against you."
                        } else {
                            "${crew.name} runs the moment it is full. Nothing counts until it is."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SeatMeter(confirmed, checking)
                }
            }
        }

        if (mine != null) {
            item { CrewHeading("Your app") }
            item {
                Box(Modifier.padding(horizontal = Gutter)) {
                    CrewCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    mine.appName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    when (mine.state) {
                                        SeatState.CONFIRMED ->
                                            "Both members got it installed. You are in."
                                        SeatState.REJECTED ->
                                            "Two members could not install it. Fix it and " +
                                                "take a seat in the next crew."
                                        SeatState.CHECKING ->
                                            "${mine.greens} of ${CrewRules.VERDICTS} " +
                                                "installed it so far."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            SeatPill(mine.state)
                        }

                        // Leaving is only offered before the crew starts. Fourteen people are
                        // relying on it after that, and the server refuses it anyway; a button
                        // that is going to be refused is worse than no button.
                        if (crew.phase != CrewPhase.RUN && crew.phase != CrewPhase.RECORD) {
                            Spacer(Modifier.height(12.dp))
                            CardRule()
                            Spacer(Modifier.height(12.dp))
                            Secondary(
                                "Take it back out",
                                Modifier.fillMaxWidth(),
                                destructive = true,
                                onClick = onLeave,
                            )
                        }
                    }
                }
            }
        }

        item {
            CrewHeading(
                if (toJudge.isEmpty()) "Nothing waiting on you" else "Waiting on you",
                if (toJudge.isEmpty()) null else "${toJudge.size}",
            )
        }
        if (toJudge.isEmpty()) {
            item {
                Text(
                    "You have tried everything that needed a second pair of eyes. New arrivals " +
                        "will show up here.",
                    Modifier.padding(horizontal = Gutter, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            itemsIndexed(toJudge, key = { _, seat -> seat.id }) { index, seat ->
                ListTile(index, toJudge.size) {
                    CrewSeatRow(seat) {
                        ActionChip(
                            "Try it",
                            MaterialTheme.colorScheme.onPrimaryContainer,
                            MaterialTheme.colorScheme.primaryContainer,
                        ) { onJudge(seat) }
                    }
                }
            }
        }

        item { CrewHeading("The crew", "$confirmed of ${CrewRules.SEATS}") }
        itemsIndexed(
            crew.seats.filter { !it.mine },
            key = { _, seat -> "all-${seat.id}" },
        ) { index, seat ->
            ListTile(index, crew.seats.count { !it.mine }) {
                CrewSeatRow(seat)
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
    onOpen: (CrewApp) -> Unit,
    onReport: (CrewApp) -> Unit,
    onOpenAll: () -> Unit,
) {
    val owed = crew.apps.filter { !it.done && !it.reported }
    val settled = crew.apps.filter { it.done || it.reported }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = ListTop, bottom = Gutter),
    ) {
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
                        Primary("Open all ${owed.size}", onClick = onOpenAll)
                    }
                }
            }
        }

        if (owed.isNotEmpty()) {
            item { CrewHeading("Tonight", "${owed.size}") }
            itemsIndexed(owed, key = { _, app -> app.id }) { index, app ->
                ListTile(index, owed.size) {
                    CrewAppRow(app, onOpen = { onOpen(app) }, onReport = { onReport(app) })
                }
            }
        }

        if (settled.isNotEmpty()) {
            item { CrewHeading("Done", "${settled.size}") }
            itemsIndexed(settled, key = { _, app -> "s-${app.id}" }) { index, app ->
                ListTile(index, settled.size) {
                    CrewAppRow(app, onOpen = { onOpen(app) }, onReport = { onReport(app) })
                }
            }
        }

        item { CrewHeading("How everyone is doing", "${crew.members.size}") }
        itemsIndexed(crew.members, key = { _, m -> m.name }) { index, member ->
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
    val stayed = crew.members.count { !it.out }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = ListTop, bottom = Gutter),
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
                        "$stayed of ${crew.members.size} went the distance, which is over " +
                            "Google's ${CrewRules.FLOOR}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Tally(Modifier.weight(1f), "${crew.finishedDays}", "days tested")
                        Tally(Modifier.weight(1f), "$stayed", "still in")
                        Tally(Modifier.weight(1f), "${crew.tixEarned}", "tix earned")
                    }
                }
            }
        }

        item { CrewHeading("Everyone", "${crew.members.size}") }
        itemsIndexed(crew.members, key = { _, m -> "rec-${m.name}" }) { index, member ->
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
