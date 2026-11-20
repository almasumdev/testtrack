package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.domain.Waiting
import com.eazyverse.testtrack.domain.closeTo
import com.eazyverse.testtrack.domain.pays
import com.eazyverse.testtrack.domain.timeLeft
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * A review of your app, on its own page, as a chat thread: the reviewer's screenshot and words on
 * the left under their name, what happened since as one-line notes, and the answers pinned under
 * it. The bar says whose it is and, under that, how long is left and what it pays.
 */

/**
 * The page for one review of your app. [pager] is "1 of 2" while more of this app's reviews wait
 * behind it; [proof] draws a screenshot by its ref, given the width it has. With [settled] it is
 * a review already decided, seen again from its request: refused with the members deciding (and
 * [onAcceptLater] while that is still open), or accepted.
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
    settled: SettledReview? = null,
    onAcceptLater: () -> Unit = {},
) {
    val now = System.currentTimeMillis()
    val name = review.reviewerName.trim()
    val (subtitle, tone) = checkSubtitle(review.pays, review.decideBy, settled, now)
    CheckFrame(
        title = if (name.isBlank()) "Check review" else "$name's review",
        onBack = onBack,
        barEnd = pager,
        subtitle = subtitle.ifBlank { null },
        subtitleTone = tone,
        foot = {
            when {
                settled?.accepted == true -> Primary("Done", onClick = onBack)
                settled != null -> {
                    if (settled.canAcceptLater) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Refused by mistake?", style = tileStyle(13.5f, 18f, FontWeight.Normal), color = Tt.colors.text3)
                            Spacer(Modifier.width(4.dp))
                            InlineAction("Accept after all", enabled = !busy, underline = true, onClick = onAcceptLater)
                        }
                    }
                    Primary("Done", onClick = onBack)
                }
                else -> {
                    AcceptHint(review.decideBy, now)
                    Spacer(Modifier.height(10.dp))
                    Primary(
                        if (review.pays > 0) "Accept and pay ${review.pays} tix" else "Accept",
                        busy = busy,
                        onClick = onApprove,
                    )
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!review.noBox && !review.checked) {
                            InlineAction("Ask for a new one", leading = Lucide.RotateCcw, enabled = !busy, onClick = onAskAgain)
                        }
                        InlineAction("Refuse", leading = Lucide.X, enabled = !busy, color = Tt.colors.bad, onClick = onReject)
                    }
                }
            }
        },
    ) {
        CheckAppHead(review.appName, review.packageName, review.icon, review.isLive)
        Spacer(Modifier.height(6.dp))
        ReviewThread(review, settled, proof)
    }
}

/**
 * The bar's subtitle and its tone: "1d 4h left, pays 20 tix", amber under twelve hours; "Accepted,
 * paid 20 tix" in green; "Refused, members deciding" in amber. Blank when there is nothing to say.
 */
internal fun checkSubtitle(
    pays: Int,
    decideBy: Long?,
    settled: SettledReview?,
    now: Long,
): Pair<String, BarTone> =
    when {
        settled?.accepted == true -> (if (pays > 0) "Accepted, paid $pays tix" else "Accepted") to BarTone.OK
        settled != null -> "Refused, members deciding" to BarTone.WARN
        else -> {
            val left = decideBy?.let { if (it > now) "${timeLeft(it, now)} left" else timeLeft(it, now) }
            val paid = if (pays > 0) "pays $pays tix" else null
            val line = listOfNotNull(left, paid).joinToString(", ").replaceFirstChar { it.uppercase() }
            line to if (decideBy != null && closeTo(decideBy, now)) BarTone.WARN else BarTone.NORMAL
        }
    }

/** Over the answers: it accepts itself if you do nothing, amber when that is close; the (i) says when and what refusing starts. */
@Composable
private fun AcceptHint(
    until: Long?,
    now: Long,
) {
    val t = Tt.colors
    val ink = if (until != null && closeTo(until, now)) t.warn else t.accentInk
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Lucide.Clock, null, Modifier.size(14.dp), tint = ink)
        Spacer(Modifier.width(6.dp))
        Text("Accepts itself if you do nothing.", style = tileStyle(12.5f, 16f, FontWeight.SemiBold), color = ink, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        InfoDot("If you do nothing", ink) {
            CalloutLine(
                if (until != null && until > now) {
                    val left = timeLeft(until, now)
                    strong("It accepts itself in $left and they are paid.", left)
                } else {
                    AnnotatedString("It accepts itself after a day and they are paid.")
                },
            )
            CalloutLine(
                strong(
                    "Refuse it and ${FeedRules.JURORS.words()} members decide. Whoever they decide against pays them ${FeedRules.STAKE} tix.",
                    "${FeedRules.STAKE} tix",
                ),
            )
        }
    }
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
