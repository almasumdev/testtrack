package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.SquareCheck
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * The crew tab's card, which is Circle's card.
 *
 * Same corner, same hairline, same surface. Two tabs of one app that draw their own boxes are two
 * apps, and the only thing that should tell a crew card from a Circle card is what is written in
 * it.
 */
@Composable
fun CrewCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(Tt.colors.glass)
            .border(1.dp, Tt.colors.glassBorder, RoundedCornerShape(CardRadius))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** The state of one seat, said in a word rather than a colour nobody can name. */
@Composable
fun SeatPill(state: SeatState) {
    when (state) {
        SeatState.CONFIRMED -> Pill("In", Status.posted, Status.postedSoft)
        SeatState.CHECKING -> Pill("Checking", MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft)
        SeatState.REJECTED -> Pill("Sent back", Status.short, Status.shortSoft)
        SeatState.REMOVED -> Pill("Out", Status.missed, Status.missedSoft)
    }
}

/**
 * One app that has taken a seat, and what you can do about it before the run.
 *
 * The owner's initial leads, not the app's icon: in a crew the person is the thing you are about
 * to spend a fortnight with, and the app is what they brought. Their notes (a test login, a code)
 * are under the name, because they are what gets you past the front door once it is installed.
 */
@Composable
fun CrewSeatRow(
    seat: CrewSeat,
    /** Only where the list mixes crews. Inside one crew's page it would be on every row. */
    showCrew: Boolean = false,
    onInstall: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    /** For an app waiting on members, where this account may answer: Yes or No. */
    onVote: ((Boolean) -> Unit)? = null,
) {
    val waiting = seat.state == SeatState.CHECKING
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Initial(seat.owner.take(1).uppercase(), size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    seat.appName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(
                        if (seat.mine) "Yours" else seat.owner,
                        seat.crew.takeIf { showCrew && it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            // Only where it says something: every app on this list is in, so "In" on each is noise.
            when {
                waiting && seat.myVote != null ->
                    Pill(if (seat.myVote) "You: Yes" else "You: No", Status.short, Status.shortSoft)
                seat.reported -> Pill("Reported", Status.short, Status.shortSoft)
                seat.state != SeatState.CONFIRMED -> SeatPill(seat.state)
            }
        }
        if (seat.notes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                seat.notes,
                Modifier.padding(start = 46.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (waiting) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Play could not check it. Members decide: ${seat.yes} Yes, ${seat.no} No so far.",
                Modifier.padding(start = 46.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Status.short,
            )
        }
        if (waiting && onVote != null && seat.myVote == null) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(start = 46.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onInstall?.let {
                    ActionChip("Get it on Play", MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft, it)
                }
                ActionChip(
                    "It installed",
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    MaterialTheme.colorScheme.primaryContainer,
                ) { onVote(true) }
                ActionChip("It didn't", MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft) { onVote(false) }
            }
        } else if (onInstall != null || (onReport != null && !seat.reported && !waiting)) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(start = 46.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onInstall?.let {
                    ActionChip(
                        "Get it on Play",
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.primaryContainer,
                        it,
                    )
                }
                if (onReport != null && !seat.reported && !waiting) {
                    ActionChip("Report", MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft, onReport)
                }
            }
        }
    }
}

/**
 * One member of a running crew, and how their fortnight is going.
 *
 * The day count is theirs rather than the crew's. Members keep their own calendars, so two people
 * in one crew can be a day apart and both be on time; a single crew-wide number would have to be
 * wrong for somebody. See docs/crew.md.
 */
@Composable
fun CrewMemberRow(member: CrewMember) {
    val faded = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Initial(member.name.take(1).uppercase(), size = 30.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (member.mine) "You" else member.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (member.mine) FontWeight.Bold else FontWeight.Medium,
                color = if (member.out) faded else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                // Their day, in their calendar: somebody three hours behind is "3 of 14 so far",
                // not missing.
                when {
                    member.out -> "Out of the run"
                    member.day < 1 -> "Starts at their midnight"
                    member.day > CrewRules.RUN_DAYS -> "Finished all ${CrewRules.RUN_DAYS} days"
                    member.nightDone -> "Day ${member.day} · done today"
                    else -> "Day ${member.day} · ${member.doneToday} of ${member.owedToday} so far"
                } + if (member.missed > 0 && !member.out) " · ${member.missed} missed" else "",
                style = MaterialTheme.typography.bodySmall,
                color = faded,
            )
        }
        Spacer(Modifier.width(10.dp))
        when {
            member.out -> Unit
            member.nightDone -> Icon(Lucide.Check, null, Modifier.size(18.dp), tint = Status.posted)
            member.missedYesterday -> Icon(Lucide.TriangleAlert, null, Modifier.size(17.dp), tint = Status.short)
            else -> Unit
        }
    }
}

/**
 * One app owed a visit tonight, in the shape the group screen already uses.
 *
 * Two tiers rather than one line. The app, its owner and today's answer sit on top; the actions
 * get a line of their own underneath, flush with the row's left edge. That is what buys them their
 * visibility: squeezed onto the right of a single line they had a third of the width between them
 * and read as decoration, and the one drawn as a label stopped reading as a control at all.
 *
 * Today's answer is a checkbox on the name rather than a word further down. The row said it three
 * ways once, as a pill, a colour and a button in disguise; one mark, ticked or not, replaces all
 * of them, which is also why a finished row shows no buttons.
 */
@Composable
fun CrewAppRow(
    app: CrewApp,
    onOpen: () -> Unit,
    onReport: () -> Unit,
) {
    val settled = app.done || app.reported
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppMark(app.label, done = settled)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.label,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (settled) Lucide.SquareCheck else Lucide.Square,
                        contentDescription = if (settled) "Done today" else "Not done today",
                        modifier = Modifier.size(18.dp),
                        tint =
                            when {
                                app.reported -> Status.short
                                app.done -> Status.posted
                                else -> MaterialTheme.colorScheme.outline
                            },
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    app.owner,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Nothing at all once the day is banked. The checkbox above has already said it, and a
        // row that offers Open right after finishing reads as asking to be done twice.
        if (!settled) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip(
                    "Open",
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    MaterialTheme.colorScheme.primaryContainer,
                    onOpen,
                )
                ActionChip(
                    "Report",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    Status.neutralSoft,
                    onReport,
                )
            }
        } else if (app.reported) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Reported. It no longer counts against you.",
                style = MaterialTheme.typography.bodySmall,
                color = Status.short,
            )
        }
    }
}

/**
 * An app's face, before there is an icon to draw.
 *
 * Hue off the name, so the same app is the same colour on every phone with nothing stored, and a
 * list of fourteen reads as fourteen different things rather than fourteen failed loads. A visited
 * one is drained rather than removed: the row still has to be findable when somebody scrolls back
 * to check they did it.
 */
@Composable
private fun AppMark(
    name: String,
    done: Boolean,
) {
    val (fill, ink) = Tt.colors.nameTile((name.hashCode().rem(360) + 360).rem(360).toFloat())
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (done) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    fill
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color =
                if (done) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    ink
                },
        )
    }
}

/**
 * How much of the night is behind you, as one bar.
 *
 * A continuous fill rather than the counted ticks [SeatMeter] uses, and the difference is the
 * point: fifteen seats are fifteen people you could name, while twenty eight apps is a quantity.
 * Ticks would be unreadable at that width and would invite counting something nobody counts.
 *
 * Animated because the only time it moves is when somebody has just finished an app, and a bar
 * that jumps has not noticed. This is the one moment in the evening worth acknowledging.
 */
@Composable
fun NightProgress(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val target = if (total == 0) 0f else done.toFloat() / total
    val fill by animateFloatAsState(target, tween(600), label = "night")
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fill.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * A crew that is one missed day from dropping your app, said once.
 *
 * Inside the night's card rather than on every row it applies to. A warning repeated eight times
 * is a warning nobody finishes reading.
 */
@Composable
fun RiskNote(crews: List<String>) {
    Notice(Status.short, Lucide.TriangleAlert) {
        Text(
            if (crews.size == 1) {
                "${crews.first()} is one missed day from dropping your app."
            } else {
                "${crews.joinToString(" and ")} are each one missed day from dropping your app."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Three figures under the night's count.
 *
 * What the count cannot say on its own: what the commitment is every evening, and how many
 * evenings of it are left. Somebody deciding whether to join a third crew is asking both.
 */
@Composable
fun CrewTallies(
    nightly: Int,
    nightsLeft: Int,
    crews: Int,
) {
    Row(Modifier.fillMaxWidth()) {
        Tally(Modifier.weight(1f), "$nightly", "apps a night")
        Tally(
            Modifier.weight(1f),
            "$nightsLeft",
            if (nightsLeft == 1) "night to go" else "nights to go",
        )
        Tally(Modifier.weight(1f), "$crews", if (crews == 1) "crew" else "crews")
    }
}
