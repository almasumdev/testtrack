package com.eazyverse.testtrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.Text
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.domain.ChatThread
import com.eazyverse.testtrack.domain.Evidence
import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.domain.RequestReview
import com.eazyverse.testtrack.domain.Waiting
import com.eazyverse.testtrack.domain.pays
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * The body of the Check review page: what the reviewer sent and everything since, in order, as
 * one thread. The reviewer is on the left under their name; the owner, reading, on the right.
 */

/** A review already decided, seen again from its request. */
class SettledReview(
    /** Accepted (and paid), rather than refused with the members deciding. */
    val accepted: Boolean,
    /** What the owner said when refusing it, and what each side showed. */
    val reason: String = "",
    val evidence: Evidence = Evidence(),
    /** Refused and still open: the owner can accept it after all. */
    val canAcceptLater: Boolean = false,
)

/** The pictures the owner and the reviewer add to a refusal: a little smaller than the review's own. */
private fun disputePicture(ref: String) = ThreadPart.Picture(ref, width = 130.dp, height = 170.dp)

/** What the owner said, for want of their own words, when asking for a new screenshot. */
private const val ASKED_AGAIN = "Can't find it on Play. Please send a new screenshot."

@Composable
internal fun ReviewThread(
    review: Waiting,
    settled: SettledReview?,
    proof: @Composable (String) -> Unit,
) {
    val name = review.reviewerName.trim().ifBlank { "Reviewer" }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val sent = review.sentAt?.let(Instant::ofEpochMilli)
    val asked = review.askedAt?.let(Instant::ofEpochMilli)
    val again = review.recheckedRef.isNotBlank()
    Thread {
        sent?.let { ThreadDay(ChatThread.dayLabel(it.atZone(zone).toLocalDate(), today)) }
        if (review.needsChars > 0) {
            val figure = "${review.needsChars}+"
            ThreadNote(noteText("You asked for $figure characters", figure), icon = Lucide.Text)
        }
        val sentTime = ChatThread.clock(sent)
        ThreadGroup(
            listOfNotNull(
                review.proofRef.takeIf { it.isNotBlank() }?.let { ThreadPart.Picture(it) },
                reviewWords(review.written, review.needsChars, review.textUse),
            ),
            who = name,
            time = if (again) listOf("First screenshot", sentTime).filter { it.isNotBlank() }.joinToString(", ") else sentTime,
            picture = proof,
        )
        if (review.noBox) {
            val back = review.reward - review.pays
            ThreadNote(
                noteText("No review box, half pay"),
                NoteTone.INFO,
                Lucide.Coins,
                more =
                    strong(
                        "Play showed them no review box, so they sent it here. It pays ${review.pays} tix and the other $back tix come back to you.",
                        "${review.pays} tix",
                    ),
                moreTitle = "Half pay",
            )
        }
        if (review.isLive && review.kept == false) {
            ThreadNote(
                "Uninstalled within a day",
                NoteTone.WARN,
                Lucide.TriangleAlert,
                more = "Google may doubt the review. Check it before it accepts itself.",
                moreTitle = "Why it matters",
            )
        }
        if (again) {
            if (asked != null && sent != null && !sameDay(asked, sent, zone)) {
                ThreadDay(ChatThread.dayLabel(asked.atZone(zone).toLocalDate(), today))
            }
            ThreadGroup(
                listOf(ThreadPart.Words(ASKED_AGAIN)),
                side = ThreadSide.RIGHT,
                time = ChatThread.clock(asked),
                picture = proof,
            )
            ThreadGroup(
                listOf(ThreadPart.Picture(review.recheckedRef)),
                who = name,
                time = "New, taken on Play when asked",
                picture = proof,
            )
        }
        if (settled != null && !settled.accepted) {
            val evidence = settled.evidence
            ThreadGroup(
                listOfNotNull(
                    settled.reason.takeIf { it.isNotBlank() }?.let { ThreadPart.Words(it) },
                    evidence.ownerProofRef.takeIf { it.isNotBlank() }?.let(::disputePicture),
                ),
                side = ThreadSide.RIGHT,
                picture = proof,
            )
            ThreadNote(
                noteText("Refused. ${FeedRules.JURORS} members decide"),
                NoteTone.WARN,
                Lucide.Scale,
                more = strong("${FeedRules.STAKE} tix are held from you until they decide.", "${FeedRules.STAKE} tix"),
                moreTitle = "What refusing started",
            )
            ThreadGroup(
                listOfNotNull(
                    evidence.reply.takeIf { it.isNotBlank() }?.let { ThreadPart.Words(it) },
                    evidence.replyProofRef.takeIf { it.isNotBlank() }?.let(::disputePicture),
                ),
                who = name,
                picture = proof,
            )
        }
        if (settled?.accepted == true) {
            val paid = "${review.pays} tix"
            ThreadNote(
                if (review.pays > 0) noteText("Accepted, $paid paid", paid) else noteText("Accepted"),
                NoteTone.OK,
                Lucide.CircleCheck,
            )
        }
    }
}

internal fun sameDay(
    a: Instant,
    b: Instant,
    zone: ZoneId,
): Boolean = a.atZone(zone).toLocalDate() == b.atZone(zone).toLocalDate()

/** A request's reviews that open the Check review page: waiting on the owner, refused and deciding, or accepted. */
internal val RequestReview.opensCheck: Boolean get() = state == "submitted" || state == "disputed" || state == "approved"

/**
 * A request's review that was refused (the members still deciding) or accepted, as the Check
 * review page draws it: the review, and how it was decided. Null for any other state.
 */
internal fun settledCheck(
    request: Request,
    review: RequestReview,
): Pair<Waiting, SettledReview>? {
    val accepted = review.state == "approved"
    if (!accepted && review.state != "disputed") return null
    val shown =
        Waiting(
            id = review.id,
            appName = request.appName,
            written = review.written,
            proofRef = review.proofRef,
            reviewerName = review.reviewerName,
            reward = request.reward,
            isLive = request.isLive,
            decideBy = review.decideBy,
            packageName = request.packageName,
            icon = request.icon,
            feedback = request.feedback,
            needsChars = request.needsChars,
            textUse = review.textUse,
            noBox = review.noBox,
            recheckedRef = review.recheckedRef,
            checked = review.checked,
        )
    return shown to SettledReview(accepted, review.reason, review.evidence, review.canAcceptLater)
}
