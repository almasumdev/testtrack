package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The parts a crew's own page is made of, the same in every state it can be in.
 *
 * One hero card first, saying where the crew stands: a chip, a headline, one line, a meter, and at
 * most one main button. Then the reader's own app, then the lists. No rules between anything: a
 * card is set apart by its own edge and a block inside one by its tint.
 */

/**
 * How loudly a chip or a line speaks: going, done, needs a look, out, or just a fact. [TILE] is the
 * accent's quieter tile ink, such as a reward or a lit profile fact; [PLAIN] is ordinary words on
 * the glass, such as "Offline".
 */
enum class Tone { ACCENT, OK, WARN, BAD, QUIET, TILE, PLAIN }

@Composable
internal fun Tone.ink(): Color =
    when (this) {
        Tone.ACCENT -> Tt.colors.accentInk
        Tone.OK -> Status.posted
        Tone.WARN -> Status.short
        Tone.BAD -> Status.missed
        Tone.QUIET -> Tt.colors.text2
        Tone.TILE -> Tt.colors.tileInk
        Tone.PLAIN -> Tt.colors.text2
    }

@Composable
internal fun Tone.soft(): Color =
    when (this) {
        Tone.ACCENT -> Tt.colors.tile
        Tone.OK -> Status.postedSoft
        Tone.WARN -> Status.shortSoft
        Tone.BAD -> Status.missedSoft
        Tone.QUIET -> Status.neutralSoft
        Tone.TILE -> Tt.colors.tile
        Tone.PLAIN -> Tt.colors.glass2
    }

/**
 * How big a read-only chip is drawn. [SMALL] is a fact under a profile's name, [MEDIUM] a status
 * on a row, [LARGE] a fact sized with [ActionChip] so a row does not change size between the two.
 * [FACT] is a fact under an app's name on its page: 13sp words, a 14dp icon, 11 by 5 inside.
 */
enum class ChipSize { SMALL, MEDIUM, LARGE, FACT }

/** One status, said in a word or two, in the one chip style every state uses. Nothing to tap. */
@Composable
internal fun StatusChip(
    text: String,
    tone: Tone,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    size: ChipSize = ChipSize.MEDIUM,
) {
    val ink = tone.ink()
    val pad =
        when (size) {
            ChipSize.SMALL -> PaddingValues(start = if (icon != null) 8.dp else 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
            ChipSize.MEDIUM -> PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ChipSize.LARGE -> PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ChipSize.FACT -> PaddingValues(horizontal = 11.dp, vertical = 5.dp)
        }
    val style =
        when (size) {
            ChipSize.SMALL -> MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp)
            ChipSize.MEDIUM -> MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp)
            ChipSize.LARGE -> MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp)
            ChipSize.FACT -> MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)
        }
    Row(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tone.soft())
            .padding(pad),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            val glyph =
                when (size) {
                    ChipSize.SMALL -> 12.dp
                    ChipSize.MEDIUM -> 13.dp
                    ChipSize.LARGE, ChipSize.FACT -> 14.dp
                }
            Icon(icon, null, Modifier.size(glyph), tint = ink)
            Spacer(Modifier.width(if (size == ChipSize.LARGE || size == ChipSize.FACT) 6.dp else 5.dp))
        }
        Text(
            text,
            style = style,
            fontWeight = if (size == ChipSize.MEDIUM) FontWeight.Bold else FontWeight.SemiBold,
            color = ink,
            maxLines = 1,
        )
    }
}

/** A row of equal segments, each its own colour: seats taken, or days of the run. */
@Composable
internal fun SegmentMeter(
    count: Int,
    fill: @Composable (Int) -> Color,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(fill(i)),
            )
        }
    }
}

/** The run's days, with those behind filled, tonight lit, and the rest empty. */
@Composable
internal fun DayMeter(
    day: Int,
    done: Boolean = false,
    /** The first day that is this account's; days before it were the crew's before it joined. */
    from: Int = 1,
    /** How many nights the crew runs, which the team can extend. */
    total: Int = CrewRules.RUN_DAYS,
) {
    val past = if (done) Status.posted else Tt.colors.accent
    val now = Tt.colors.accent.copy(alpha = 0.45f)
    SegmentMeter(total) { i ->
        when {
            i < from - 1 -> Tt.colors.glassBorderStrong
            i < day - 1 -> past
            i == day - 1 -> if (done) past else now
            else -> Tt.colors.track
        }
    }
}

/** What the two ends of a meter say, small and quiet. */
@Composable
internal fun MeterLabels(
    start: String,
    end: String,
) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(start, Modifier.weight(1f), style = TextStyles.SmallLine, color = Tt.colors.text4)
        Text(end, style = TextStyles.SmallLine, color = Tt.colors.text4)
    }
}

/** Where the crew stands, at the top of its page, in the same shape whatever that is. */
@Composable
internal fun CrewHero(
    chip: String,
    tone: Tone,
    icon: ImageVector?,
    headline: String,
    line: String,
    lineTone: Tone? = null,
    meter: (@Composable () -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    CrewCard(
        padding =
            androidx.compose.foundation.layout
                .PaddingValues(16.dp),
    ) {
        StatusChip(chip, tone, icon)
        Spacer(Modifier.height(10.dp))
        Text(
            headline,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            line,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (lineTone != null) FontWeight.SemiBold else FontWeight.Normal,
            color = lineTone?.ink() ?: Tt.colors.text2,
        )
        if (meter != null) {
            Spacer(Modifier.height(14.dp))
            meter()
        }
        extra?.invoke()
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            Primary(action, Modifier.fillMaxWidth(), onClick = onAction)
        }
    }
}

/**
 * Words members wrote, quoted, in an edged block; who wrote them is never shown. A [title] leads
 * in the tone's colour, [labels] say when above each line (labels[i] for lines[i], blank draws
 * nothing), and [more] sits between the lines and the footer.
 */
@Composable
internal fun Quotes(
    lines: List<String>,
    footer: String,
    tone: Tone = Tone.WARN,
    labels: List<String> = emptyList(),
    title: String = "",
    more: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, tone.ink().copy(alpha = 0.42f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (title.isNotBlank()) {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = tone.ink())
        }
        lines.forEachIndexed { i, line ->
            Column {
                val label = labels.getOrNull(i).orEmpty()
                if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.labelSmall, color = Tt.colors.text4)
                Text("“$line”", style = MaterialTheme.typography.bodyMedium, color = Tt.colors.text2)
            }
        }
        more?.invoke()
        if (footer.isNotBlank()) Text(footer, style = MaterialTheme.typography.bodySmall, color = Tt.colors.text4)
    }
}

/** A tinted block inside a card: a sign-in, or a line that stands apart from the card's text. */
@Composable
internal fun TintBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .tileFrame(RoundedCornerShape(10.dp), fill = Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { content() }
}

/**
 * The reader's own app in this crew: its icon and name, where it stands, its sign-in as the crew
 * sees it, and whatever goes with that state below.
 */
@Composable
internal fun YourAppCard(
    seat: CrewSeat,
    chip: String,
    tone: Tone,
    status: String,
    extra: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    CrewCard(
        padding =
            androidx.compose.foundation.layout
                .PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppMark(seat.packageName, seat.appName, seat.icon, size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    seat.appName,
                    style = TextStyles.SeatName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(status, style = MaterialTheme.typography.bodySmall, color = Tt.colors.text3)
            }
            Spacer(Modifier.width(10.dp))
            StatusChip(chip, tone)
        }
        if (seat.notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            TintBox { SignInDetails(seat.notes) }
        }
        extra?.invoke()
        action?.invoke()
    }
}

/** A thin-edged outline around the members button in the bar: people, and how many. */
@Composable
internal fun MembersButton(
    count: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(Tt.colors.bg)
            .border(1.dp, Tt.colors.cardLine, shape)
            .clickableRole("$count members", onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Users, null, Modifier.size(16.dp), tint = Tt.colors.accentInk)
        Spacer(Modifier.width(6.dp))
        Text("$count", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Tt.colors.accentInk)
    }
}
