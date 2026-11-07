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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Play step, to copy before going, and on the proof step, to put in the box. On a live app's Play
 * step, the way out when Play shows no review box: sending it here instead, for half.
 */

@Composable
internal fun WrittenAsk(
    job: ReviewJob,
    gap: Dp,
    /** The proof step's box, which the ready review can be put in from that step. */
    onNote: (String) -> Unit,
    /** Play shows no review box: on to send it here instead. */
    onNoBox: () -> Unit = {},
) {
    if (job.settled) return
    if (job.step == ReviewStep.LEAVE && job.live && !job.noBox) NoBoxLink(gap, onNoBox)
    if (job.needsChars <= 0 || (job.step != ReviewStep.LEAVE && job.step != ReviewStep.PROVE)) return
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
        Text(
            if (job.noBox) "Your feedback, sent here" else kind.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = t.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (job.noBox) {
                "Write at least ${job.needsChars} characters for the developer in the box below. It is all they get, " +
                    "so say what you liked, what went wrong and what you would change."
            } else {
                "Give it stars and write at least ${job.needsChars} characters on Google Play. " +
                    "Then paste the same text here with your screenshot."
            },
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

/**
 * Play showed no review box. Most of the time something on the reviewer's side hides it, so the
 * fixes come first; only then the way to send it here, said with what it pays.
 */
@Composable
private fun NoBoxLink(
    gap: Dp,
    onNoBox: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Spacer(Modifier.height(gap))
    QuietAction("No review option on Play?", "Help", onClick = { open = true })
    if (!open) return
    Ask(
        title = "No review option on Google Play",
        confirm = "Send it here instead",
        dismiss = "I found it",
        onConfirm = {
            open = false
            onNoBox()
        },
        onDismiss = { open = false },
    ) {
        DialogSteps(
            listOf(
                "In Play, tap your picture. Make sure it is the account you installed the app with.",
                "Scroll down the app's Play page. If it says you are a tester, tap Leave and wait a few minutes.",
                "If it says Edit your review, you reviewed this app before. That review counts for nothing new.",
                "Update the Play Store app, then open the app's page again.",
            ).map { AnnotatedString(it) },
        )
        Spacer(Modifier.height(8.dp))
        DialogNote(
            "Still no review box? Send your feedback here with a screenshot of the Play page. " +
                "It pays half the reward, because the developer gets no review on Play.",
        )
    }
}

/** Sent in TestTrack because Play showed no review box: said before anything else about it. */
@Composable
internal fun NoBoxNote(
    noBox: Boolean,
    reward: Int,
) {
    if (!noBox) return
    Spacer(Modifier.height(12.dp))
    DialogNote(
        "Play showed them no review box, so they sent their feedback here with a screenshot of the Play page. " +
            "Accepting pays them ${reward / 2} tix, half the reward, and the rest comes back to you.",
    )
}

/** The screenshot sent with the review, and the new one if the owner asked to see it again. */
@Composable
internal fun Screenshots(
    first: String,
    again: String,
    proof: @Composable (String) -> Unit,
) {
    if (first.isNotBlank()) {
        Spacer(Modifier.height(14.dp))
        if (again.isNotBlank()) Text("First screenshot", style = MaterialTheme.typography.labelMedium, color = Tt.colors.text3)
        proof(first)
    }
    if (again.isNotBlank()) {
        Spacer(Modifier.height(14.dp))
        Text("New screenshot, taken when asked", style = MaterialTheme.typography.labelMedium, color = Tt.colors.text3)
        proof(again)
    }
}

/** The owner cannot find the review on Play: one ask for a new screenshot, before deciding. */
@Composable
internal fun CantFind(onAskAgain: () -> Unit) {
    Spacer(Modifier.height(14.dp))
    QuietAction("Can't find it on Google Play?", "Ask for a new screenshot", onAskAgain)
}
