package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Rocket
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Ticket
import com.eazyverse.testtrack.ui.theme.Tt
import java.time.Instant

/**
 * One finished crew on the Record tab: its name and days, what came of it, the reader's app, and
 * then whatever it still asks of them (a perfect run's tix, the production question) or what they
 * answered. Opening the card opens the crew, where the reader can still open it.
 */
@Composable
internal fun RecordTile(
    record: CrewRecord,
    /** The crew from the crew list, for whether its detail is still kept; null when it cannot be opened. */
    crew: Crew?,
    busy: RecordBusy?,
    onOpen: (() -> Unit)?,
    onAnswer: (Boolean) -> Unit,
    onClaim: () -> Unit,
    now: Instant = Instant.now(),
) {
    CrewCard(padding = PaddingValues(16.dp), onClick = onOpen) {
        CrewTileHeading(record.crewName.ifBlank { crewNameOf(record.crewNumber) }, recordDates(record))
        Spacer(Modifier.height(10.dp))
        val (headline, tone) = recordHeadline(record)
        Text(headline, style = tileStyle(15f, 20f, FontWeight.Medium), color = tone.ink())
        CrewTileApp(record.packageName, record.appName, record.appIcon, maxLines = 1)

        detailsClear(crew?.detailsUntil, record.pruned || crew?.pruned == true, now)?.let { (text, last) ->
            Spacer(Modifier.height(10.dp))
            StatusChip(text, if (last) Tone.WARN else Tone.QUIET, Lucide.Clock)
        }

        if (canClaimPerfect(record)) {
            Spacer(Modifier.height(12.dp))
            RecordAsk(
                icon = Lucide.Star,
                title = "Perfect run",
                hint = "You kept every night.",
                reward = null,
            ) {
                AskPill("Claim ${record.perfectClaimAmount} tix", RecordBusy.CLAIM, busy, onClaim)
            }
        }
        if (asksProdAccess(record, now)) {
            Spacer(Modifier.height(12.dp))
            RecordAsk(
                icon = Lucide.Rocket,
                title = "Got production access?",
                hint = "Answer once Google replies to your apply.",
                reward = record.prodAccessReward.takeIf { it > 0 }?.let { "+$it tix" },
            ) {
                AskPill("Yes", RecordBusy.YES, busy) { onAnswer(true) }
                AskPill("No", RecordBusy.NO, busy) { onAnswer(false) }
            }
        }

        RecordResults(record, now)
    }
}

/**
 * Something a record asks once: a small icon and title with its reward at the end, a hint, and
 * its buttons at the end of the hint. With little room, or large text, the buttons drop to a row
 * of their own, still at the end.
 */
@Composable
internal fun RecordAsk(
    icon: ImageVector,
    title: String,
    hint: String,
    reward: String?,
    actions: @Composable () -> Unit,
) {
    val t = Tt.colors
    val fontScale = LocalDensity.current.fontScale
    val narrow = LocalConfiguration.current.screenWidthDp / fontScale <= 340f || fontScale > 1.3f
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, Modifier.padding(top = 2.dp).size(14.dp), tint = t.text3)
            Spacer(Modifier.width(6.dp))
            Text(title, Modifier.weight(1f), style = tileStyle(13f, 18f, FontWeight.SemiBold), color = t.text)
            if (reward != null) {
                Spacer(Modifier.width(8.dp))
                Text(reward, style = tileStyle(13f, 18f, FontWeight.Bold), color = t.accentInk)
            }
        }
        Spacer(Modifier.height(2.dp))
        val hintText = @Composable { modifier: Modifier ->
            Text(hint, modifier, style = tileStyle(12f, 16f, FontWeight.Normal), color = t.text3)
        }
        if (narrow) {
            hintText(Modifier)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) { actions() }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                hintText(Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            }
        }
    }
}

/**
 * One of a record's answers: a plain text button. While any answer of this crew is sending, the
 * tapped one shows a loader in its label's place and the others wait in text4.
 */
@Composable
internal fun AskPill(
    label: String,
    which: RecordBusy,
    busy: RecordBusy?,
    onClick: () -> Unit,
) = InlineAction(label, enabled = busy == null, busy = busy == which, onClick = onClick)

/** One answer or claim of a record: its chip, and the tix it paid where it paid some. */
internal data class RecordResultChip(
    val text: String,
    val tone: Tone,
    val icon: ImageVector,
    val paid: String? = null,
)

/** What the reader answered or claimed, in the order the tile and the crew page show them. */
internal fun recordResultChips(
    record: CrewRecord,
    now: Instant,
): List<RecordResultChip> =
    buildList {
        if (record.perfectClaimedAt != null && record.perfectClaimAmount > 0) {
            add(RecordResultChip("${record.perfectClaimAmount} tix claimed", Tone.QUIET, Lucide.Ticket))
        }
        val reward = record.prodAccessReward.takeIf { it > 0 }?.let { "+$it tix" }
        when {
            record.prodAccess == true -> add(RecordResultChip("Production access", Tone.OK, Lucide.CircleCheck, reward))
            record.prodAccess == false -> add(RecordResultChip("No production access yet", Tone.QUIET, Lucide.Hourglass, reward))
            prodAccessMissed(record, now) -> add(RecordResultChip("Not answered", Tone.QUIET, Lucide.Clock))
        }
    }

/** What the reader answered or claimed, each as a chip on its own line. */
@Composable
private fun RecordResults(
    record: CrewRecord,
    now: Instant,
) {
    recordResultChips(record, now).forEach { chip ->
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) { RecordResult(chip) }
    }
}

/** One result: the chip, then the tix it paid in quiet text. */
@Composable
internal fun RecordResult(chip: RecordResultChip) {
    StatusChip(chip.text, chip.tone, chip.icon)
    if (chip.paid != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Ticket, null, Modifier.size(13.dp), tint = Tt.colors.text3)
            Spacer(Modifier.width(5.dp))
            Text(chip.paid, style = tileStyle(12.5f, 16f, FontWeight.SemiBold), color = Tt.colors.text3)
        }
    }
}
