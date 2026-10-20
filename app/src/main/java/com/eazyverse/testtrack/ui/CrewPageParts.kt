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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/** How loudly a chip or a line speaks: going, done, needs a look, out, or just a fact. */
enum class Tone { ACCENT, OK, WARN, BAD, QUIET }

@Composable
internal fun Tone.ink(): Color =
    when (this) {
        Tone.ACCENT -> Tt.colors.accentInk
        Tone.OK -> Status.posted
        Tone.WARN -> Status.short
        Tone.BAD -> Status.missed
        Tone.QUIET -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
internal fun Tone.soft(): Color =
    when (this) {
        Tone.ACCENT -> Tt.colors.tile
        Tone.OK -> Status.postedSoft
        Tone.WARN -> Status.shortSoft
        Tone.BAD -> Status.missedSoft
        Tone.QUIET -> Status.neutralSoft
    }

/** One status, said in a word or two, in the one chip style every state uses. */
@Composable
internal fun StatusChip(
    text: String,
    tone: Tone,
    icon: ImageVector? = null,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tone.soft())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(13.dp), tint = tone.ink())
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
            fontWeight = FontWeight.Bold,
            color = tone.ink(),
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

/** The fourteen days, with those behind filled, tonight lit, and the rest empty. */
@Composable
internal fun DayMeter(
    day: Int,
    done: Boolean = false,
) {
    val past = if (done) Status.posted else Tt.colors.accent
    val now = Tt.colors.accent.copy(alpha = 0.45f)
    SegmentMeter(CrewRules.RUN_DAYS) { i ->
        when {
            i < day - 1 -> past
            i == day - 1 -> if (done) past else now
            else -> Tt.colors.glass2
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
        Text(start, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = Tt.colors.text4)
        Text(end, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = Tt.colors.text4)
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
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 23.sp, lineHeight = 27.sp),
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

/** Words members wrote, quoted, in a tinted block; who wrote them is never shown. */
@Composable
internal fun Quotes(
    lines: List<String>,
    footer: String,
    tone: Tone = Tone.WARN,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tone.soft())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        lines.forEach { Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = Tt.colors.text2) }
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
            .clip(RoundedCornerShape(14.dp))
            .background(Tt.colors.glass2)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { content() }
}

/** A small centred line under a card: what happens to your tix, and the like. */
@Composable
internal fun CardNote(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(top = 10.dp),
        style = MaterialTheme.typography.bodySmall,
        color = Tt.colors.text4,
        textAlign = TextAlign.Center,
    )
}

/** Three figures side by side, each in its own tile: how a finished run went. */
@Composable
internal fun StatTiles(vararg pairs: Pair<String, String>) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        pairs.forEach { (value, label) ->
            Column(
                Modifier
                    .weight(
                        1f,
                    ).clip(RoundedCornerShape(14.dp))
                    .background(Tt.colors.glass2)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = Tt.colors.text4)
            }
        }
    }
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
            AppMark(seat.packageName, seat.appName, seat.icon, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(seat.appName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(status, style = MaterialTheme.typography.bodySmall, color = Tt.colors.text4)
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
            .clip(shape)
            .background(Tt.colors.glass)
            .border(1.dp, Tt.colors.glassBorder, shape)
            .clickableRole("Members, $count", onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Users, null, Modifier.size(16.dp), tint = Tt.colors.accentInk)
        Spacer(Modifier.width(6.dp))
        Text("$count", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Tt.colors.accentInk)
    }
}
