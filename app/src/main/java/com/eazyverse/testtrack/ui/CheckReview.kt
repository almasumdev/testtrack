package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleHelp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Text
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.domain.Waiting
import com.eazyverse.testtrack.domain.closeTo
import com.eazyverse.testtrack.domain.pays
import com.eazyverse.testtrack.domain.timeLeft
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * A review of your app, on its own page: who sent it and how long is left, what they sent as one
 * message in their words with their screenshot, and one filled Accept with Refuse in red under it.
 * Refusing is not free, so the line about it says what it starts.
 */

/**
 * The page for one review of your app. [pager] is "1 of 2" while more of this app's reviews wait
 * behind it; [proof] draws a screenshot by its ref, given the width it has.
 */
@Composable
fun ReviewCheck(
    review: Waiting,
    busy: Boolean = false,
    proof: @Composable (String) -> Unit = {},
    onBack: () -> Unit = {},
    onApprove: () -> Unit = {},
    onReject: () -> Unit = {},
    /** Can't find it on Play: ask the reviewer for a new screenshot. */
    onAskAgain: () -> Unit = {},
    pager: String? = null,
) {
    val now = System.currentTimeMillis()
    val until = review.decideBy
    var viewing by remember { mutableStateOf<String?>(null) }
    CheckFrame(
        title = "Check review",
        onBack = onBack,
        barEnd = pager,
        foot = {
            Primary(
                if (review.pays > 0) "Accept and pay ${review.pays} tix" else "Accept",
                busy = busy,
                onClick = onApprove,
            )
            Box(Modifier.fillMaxWidth().heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                InlineAction("Refuse", enabled = !busy, color = Tt.colors.bad, onClick = onReject)
            }
        },
    ) {
        CheckAppHead(review.appName, review.packageName, review.icon, review.isLive)
        Spacer(Modifier.height(18.dp))
        ReviewerRow(review.reviewerName.ifBlank { "A reviewer" }) {
            if (until != null) {
                val left = timeLeft(until, now)
                StatusChip(
                    if (until > now) "$left left" else left,
                    if (closeTo(until, now)) Tone.WARN else Tone.QUIET,
                    Lucide.Clock,
                )
            }
            if (review.pays > 0) StatusChip("Pays ${review.pays} tix" + if (review.noBox) ", half" else "", Tone.QUIET)
        }
        if (review.isLive && review.kept == false) {
            Spacer(Modifier.height(14.dp))
            InfoLine(
                Lucide.TriangleAlert,
                AnnotatedString("They uninstalled it within a day of reviewing. Google may doubt the review."),
                warn = true,
                title = "Why it matters",
            ) { CalloutLine(AnnotatedString("Check it before it accepts itself.")) }
        }
        askedLine(review.feedback, review.needsChars, owner = true)?.let { (line, figure) ->
            Spacer(Modifier.height(14.dp))
            InfoLine(Lucide.Text, strong(line, figure))
        }
        if (review.noBox) {
            Spacer(Modifier.height(14.dp))
            val back = review.reward - review.pays
            InfoLine(
                Lucide.Info,
                AnnotatedString("Play showed them no review box, so they sent it here. The other $back tix come back to you."),
            )
        }
        Spacer(Modifier.height(14.dp))
        Thread {
            val again = review.recheckedRef.isNotBlank()
            val first = textMeta(review.written, review.needsChars, review.textUse)
            ThreadBubble(
                ThreadSide.LEFT,
                text = review.written,
                pictureRef = review.proofRef,
                meta = if (again) listOf(first, "first screenshot").filter { it.isNotBlank() }.joinToString(", ").cap() else first,
                picture = proof,
                onPicture = { viewing = it },
            )
            if (again) {
                ThreadNote("You asked for a new one")
                ThreadBubble(
                    ThreadSide.LEFT,
                    pictureRef = review.recheckedRef,
                    meta = "New, taken on Play when asked",
                    picture = proof,
                    onPicture = { viewing = it },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (!review.noBox && !review.checked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoLine(Lucide.CircleHelp, AnnotatedString("Can't find it on Play?"))
                Box(Modifier.offset(x = 4.dp)) { InlineAction("Ask for a new one", enabled = !busy, onClick = onAskAgain) }
            }
            Spacer(Modifier.height(4.dp))
        }
        val accepts =
            if (until != null && until > now) {
                val left = timeLeft(until, now)
                strong("Accepts itself in $left if you do nothing.", left)
            } else {
                AnnotatedString("It accepts itself after a day if you do nothing.")
            }
        InfoLine(Lucide.Clock, accepts, title = "If you refuse it") {
            CalloutLine(
                strong(
                    "Refuse it and ${FeedRules.JURORS.words()} members decide. Whoever they decide against pays them ${FeedRules.STAKE} tix.",
                    "${FeedRules.STAKE} tix",
                ),
            )
        }
    }
    viewing?.let { ref -> ProofViewer(ref, onDismiss = { viewing = null }, picture = proof) }
}

/** A count said as a word, as copy says small numbers. */
internal fun Int.words(): String =
    when (this) {
        1 -> "one"
        2 -> "two"
        3 -> "three"
        4 -> "four"
        5 -> "five"
        else -> "$this"
    }

private fun String.cap(): String = replaceFirstChar { it.uppercase() }
