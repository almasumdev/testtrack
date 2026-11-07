package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.domain.textUseLabel
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * A request that asks for written or detailed feedback, on the review page: how much text it
 * takes, and the owner's ready-written review if one was handed to this reviewer. Shown on the
 * Play step, to copy before going, and on the proof step, to put in the box.
 */

@Composable
internal fun WrittenAsk(
    job: ReviewJob,
    gap: Dp,
    /** The proof step's box, which the ready review can be put in from that step. */
    onNote: (String) -> Unit,
) {
    if (job.settled || job.needsChars <= 0 || (job.step != ReviewStep.LEAVE && job.step != ReviewStep.PROVE)) return
    val onUse = onNote.takeIf { job.step == ReviewStep.PROVE }
    val t = Tt.colors
    val kind = FeedRules.Feedback.of(job.post.feedback)
    val offered = job.review.offered.trim()
    val clipboard = LocalClipboardManager.current
    Spacer(Modifier.height(gap))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(t.glass)
            .border(1.dp, t.glassBorder, RoundedCornerShape(CardRadius))
            .padding(14.dp),
    ) {
        Text(kind.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = t.text)
        Spacer(Modifier.height(4.dp))
        Text(
            "Give it stars and write at least ${job.needsChars} characters on Google Play. " +
                "Then paste the same text here with your screenshot.",
            style = MaterialTheme.typography.bodySmall,
            color = t.text2,
        )
        if (offered.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "A ready review from the developer",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = t.text3,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Optional. Use it, change it, or write your own. It was given to you only.",
                style = MaterialTheme.typography.bodySmall,
                color = t.text3,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                offered,
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(t.glass2.copy(alpha = 0.55f))
                    .padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = t.text,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RowPill("Copy", PillKind.SOFT, onClick = { clipboard.setText(AnnotatedString(offered)) })
                if (onUse != null) RowPill("Put it in the box", PillKind.GHOST, onClick = { onUse(offered) })
            }
        }
    }
}

/** What a request asked each reviewer to write, said above what they sent. Nothing for stars alone. */
@Composable
internal fun AskedFor(
    feedback: String,
    needs: Int,
    /** Said to the owner, who asked, rather than to a member deciding a case. */
    owner: Boolean,
) {
    if (needs <= 0) return
    val kind =
        FeedRules.Feedback
            .of(feedback)
            .label
            .lowercase()
    Spacer(Modifier.height(12.dp))
    DialogNote((if (owner) "You asked for $kind" else "The request asked for $kind") + ": at least $needs characters.")
}

/** The text a reviewer sent, how long it is, and whether it was the ready review they were given. */
@Composable
internal fun TextGiven(
    written: String,
    needs: Int,
    textUse: String?,
    note: String = "Their note",
) {
    if (written.isBlank()) return
    Said(if (needs > 0) "Their text from Google Play, ${plainLength(written)} characters" else note, written)
    textUseLabel(textUse)?.let {
        Spacer(Modifier.height(6.dp))
        DialogNote(it)
    }
}

/** On a request's card: the written feedback it asks for, beside its kind. Nothing for stars alone. */
@Composable
internal fun FeedbackPill(feedback: String) {
    when (FeedRules.Feedback.of(feedback)) {
        FeedRules.Feedback.STARS -> Unit
        FeedRules.Feedback.WRITTEN -> Pill("Written", Tt.colors.accentInk, Tt.colors.glass2)
        FeedRules.Feedback.DETAILED -> Pill("Detailed", Tt.colors.accentInk, Tt.colors.glass2)
    }
}
