package com.eazyverse.testtrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Wallet
import com.eazyverse.testtrack.domain.ChatThread
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * A sent review as the reviewer sees it, sent, refused, approved or not paid: one chat thread.
 * What I sent is on the right with its "Sent" time; the developer is on the left as "Developer"
 * under the app's icon, never their name or photo; what happened is said in one-line notes.
 */

/** The thread for a review in submitted, disputed, approved or rejected. */
@Composable
internal fun ReviewWaitThread(job: ReviewJob) {
    val review = job.review
    val developer = ThreadApp(job.post.packageName, job.post.appName, job.post.icon)
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val sent = reviewInstant(review.submittedAt)
    val asked = reviewInstant(review.checkedAt)
    val again = review.recheckedRef.isNotBlank()
    // Not found on Play, and no new screenshot came in time: refused without a dispute.
    val unproven = review.state == "rejected" && asked != null && !again
    val refused = !unproven && (review.reason.isNotBlank() || review.ownerProofRef.isNotBlank())
    // Sending a new screenshot moves the sent time, so a thread with one starts on the ask's day.
    val first = if (again || unproven) asked ?: sent else sent
    val day: (Instant) -> String = { ChatThread.dayLabel(it.atZone(zone).toLocalDate(), today) }
    Thread {
        first?.let { ThreadDay(day(it)) }
        val mine =
            listOfNotNull(
                review.proofRef.takeIf { it.isNotBlank() }?.let {
                    if (again || unproven) ThreadPart.Picture(it, 110.dp, 150.dp) else ThreadPart.Picture(it, 120.dp, 165.dp)
                },
                review.written.takeIf { it.isNotBlank() }?.let { ThreadPart.Words(it) },
            )
        if (again || unproven) {
            ThreadGroup(mine, side = ThreadSide.RIGHT, time = "First screenshot")
            ThreadGroup(listOf(ThreadPart.Words(ASKED_FOR_NEW)), face = ThreadFace.APP, app = developer, time = ChatThread.clock(asked))
        } else {
            ThreadGroup(mine, side = ThreadSide.RIGHT, sent = true, time = sentAt(sent))
        }
        if (again) {
            if (sent != null && first != null && !sameDay(first, sent, zone)) ThreadDay(day(sent))
            ThreadGroup(
                listOf(ThreadPart.Picture(review.recheckedRef, 110.dp, 150.dp)),
                side = ThreadSide.RIGHT,
                sent = true,
                time = "New, " + sentAt(sent).replaceFirstChar { it.lowercase() },
            )
        }
        // The refusal is only timed while the members decide: deciding moves the time it carries.
        val refusedAt = if (review.state == "disputed") reviewInstant(review.decidedAt) else null
        if (refused) {
            val before = if (again) sent else first
            if (refusedAt != null && before != null && !sameDay(before, refusedAt, zone)) ThreadDay(day(refusedAt))
            ThreadGroup(
                listOfNotNull(
                    review.reason.takeIf { it.isNotBlank() }?.let { ThreadPart.Words(it) },
                    review.ownerProofRef.takeIf { it.isNotBlank() }?.let { ThreadPart.Picture(it, 120.dp, 160.dp) },
                ),
                face = ThreadFace.APP,
                app = developer,
                time = ChatThread.clock(refusedAt),
            )
        }
        val answer = answerParts(review.reply, review.replyProofRef)
        val answeredAt = sentAt(reviewInstant(review.repliedAt))
        when (review.state) {
            "submitted" -> SubmittedNote(job)
            "disputed" -> {
                val stake = "${FeedRules.STAKE} tix"
                ThreadNote(
                    noteText("Refused. ${FeedRules.JURORS} members decide"),
                    NoteTone.WARN,
                    Lucide.Scale,
                    more = strong("If they side with the developer, you pay $stake.", stake),
                    moreTitle = "What happens now",
                )
                if (answer.isNotEmpty()) {
                    ThreadGroup(answer, side = ThreadSide.RIGHT, sent = true, time = answeredAt)
                    ThreadNote(noteText("Answer sent"), NoteTone.QUIET, Lucide.CheckCheck)
                }
            }
            "approved" -> {
                if (refused) ThreadGroup(answer, side = ThreadSide.RIGHT, sent = true, time = answeredAt)
                val paid = "+${job.reward} tix"
                ThreadNote(noteText("Approved, $paid", paid), NoteTone.OK, Lucide.CircleCheck)
                if (job.live) {
                    ThreadNote(
                        noteText("Keep the app a few more days"),
                        NoteTone.QUIET,
                        Lucide.Smartphone,
                        more = AnnotatedString("So your review counts on Google Play."),
                        moreTitle = "Why keep it",
                    )
                }
            }
            "rejected" -> {
                if (refused) ThreadGroup(answer, side = ThreadSide.RIGHT, sent = true, time = answeredAt)
                val fee = "${FeedRules.STAKE} tix"
                ThreadNote(
                    noteText("Not paid"),
                    NoteTone.BAD,
                    Lucide.Wallet,
                    more =
                        if (unproven) {
                            AnnotatedString("No new screenshot came in time, so it was not paid.")
                        } else {
                            strong("The members sided with the developer. Their $fee fee came from your tix.", fee)
                        },
                    moreTitle = "Why",
                )
            }
        }
    }
}

/** Sent and with the developer: how long they have, or that it pays half. */
@Composable
private fun SubmittedNote(job: ReviewJob) {
    if (job.noBox) {
        val pays = "${job.reward} tix"
        ThreadNote(
            noteText("Sent here, half pay"),
            NoteTone.INFO,
            Lucide.Coins,
            more = strong("Play showed you no review box, so it pays $pays.", pays),
            moreTitle = "Half pay",
        )
    } else if (job.live) {
        ThreadNote(
            "With the developer",
            NoteTone.QUIET,
            Lucide.Hourglass,
            more = "They can accept it any time.",
            moreTitle = "What happens now",
        )
    } else {
        ThreadNote(
            "With the developer, up to a day",
            NoteTone.QUIET,
            Lucide.Hourglass,
            more = "If they don't answer, it accepts itself.",
            moreTitle = "What happens now",
        )
    }
}

/** My one answer to a refusal, words and picture, as bubbles; none when there was no answer. */
private fun answerParts(
    reply: String,
    pictureRef: String,
): List<ThreadPart> =
    listOfNotNull(
        reply.takeIf { it.isNotBlank() }?.let { ThreadPart.Words(it) },
        pictureRef.takeIf { it.isNotBlank() }?.let { ThreadPart.Picture(it, 110.dp, 150.dp) },
    )

/** "Sent 9:41 AM", or just "Sent" without a time. */
private fun sentAt(at: Instant?): String = listOf("Sent", ChatThread.clock(at)).filter { it.isNotBlank() }.joinToString(" ")
