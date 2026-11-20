package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.eazyverse.testtrack.domain.textUseLabel
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * Optional suggested wording to copy on the Play step.
 * On a live app's Play step, also offers help when Play shows no review box.
 */

@Composable
internal fun WrittenAsk(
    job: ReviewJob,
    onNoBox: () -> Unit = {},
) {
    if (job.settled) return
    val offered = job.review.offered.trim()
    // On the proof step the ready review is the developer's bubble beside the box instead (ProveAsks).
    val showOffer = offered.isNotEmpty() && job.needsChars > 0 && job.step == ReviewStep.LEAVE
    val showNoBoxHelp = job.step == ReviewStep.LEAVE && job.live && !job.noBox
    if (!showOffer && !showNoBoxHelp) return
    val clipboard = LocalClipboardManager.current
    val t = Tt.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ReviewSectionGap)) {
        if (showOffer) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ReviewLineGap)) {
                ReviewSectionTitle(Lucide.MessageSquare, "Suggested wording")
                ReviewBullet(body = "Optional. Edit it to match your experience.", highlights = listOf("Optional", "your experience"))
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
                Row(horizontalArrangement = Arrangement.spacedBy(ReviewLineGap)) {
                    RowPill("Copy", PillKind.SOFT, onClick = { clipboard.setText(AnnotatedString(offered)) })
                }
            }
        }
        if (showNoBoxHelp) ReviewNoPlayOption(job, onNoBox)
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

/**
 * The screenshot sent with the review, and the new one if the owner asked to see it again. A tap
 * opens one whole, titled with [who] sent it.
 */
@Composable
internal fun Screenshots(
    first: String,
    again: String,
    proof: @Composable (String) -> Unit,
    who: String? = null,
) {
    if (first.isNotBlank()) {
        Spacer(Modifier.height(14.dp))
        if (again.isNotBlank()) Text("First screenshot", style = MaterialTheme.typography.labelMedium, color = Tt.colors.text3)
        OpensWhole(first, who, proof)
    }
    if (again.isNotBlank()) {
        Spacer(Modifier.height(14.dp))
        Text("New screenshot, taken when asked", style = MaterialTheme.typography.labelMedium, color = Tt.colors.text3)
        OpensWhole(again, who, proof)
    }
}
