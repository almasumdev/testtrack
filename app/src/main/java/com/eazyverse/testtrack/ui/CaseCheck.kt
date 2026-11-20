package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.Text
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.domain.Case
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * A case between a developer and a reviewer, for a member to decide: both sides as one thread,
 * the reviewer on the left and the developer on the right, then two choices and one button to send
 * the vote. Once it is counted the page stays, with the vote on it, until the others are in.
 */

/**
 * The page for one case. [voted] is the side sent once the vote is counted (true for the review
 * standing): the choices then lock and the page says what it is waiting for. [case] then carries
 * the votes in, this one included.
 */
@Composable
fun CaseCheck(
    case: Case,
    busy: Boolean = false,
    proof: @Composable (String) -> Unit = {},
    onBack: () -> Unit = {},
    onVote: (forReviewer: Boolean) -> Unit = {},
    voted: Boolean? = null,
) {
    var picked by rememberSaveable(case.id) { mutableStateOf<Boolean?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    val choice = voted ?: picked
    val share = if (case.stake > 0) case.stake / FeedRules.JURORS else FeedRules.JUROR_FEE
    val stake = if (case.stake > 0) case.stake else FeedRules.STAKE
    CheckFrame(
        title = "Decide a case",
        onBack = onBack,
        foot =
            if (voted == null) {
                {
                    Primary("Send my vote", busy = busy, enabled = choice != null) { choice?.let(onVote) }
                }
            } else {
                null
            },
    ) {
        CheckAppHead(case.appName, case.packageName, case.icon, case.isLive)
        Spacer(Modifier.height(14.dp))
        ChipRow {
            if (case.votesNeeded > 0) StatusChip("${case.votesCast} of ${case.votesNeeded} votes in", Tone.QUIET, Lucide.Users)
            StatusChip("$share tix to you", Tone.QUIET)
        }
        askedLine(case.feedback, case.needsChars, owner = false)?.let { (line, figure) ->
            Spacer(Modifier.height(14.dp))
            InfoLine(Lucide.Text, strong(line, figure))
        }
        if (case.noBox) {
            Spacer(Modifier.height(14.dp))
            InfoLine(Lucide.Info, AnnotatedString("Play showed them no review box, so they sent it in TestTrack for half pay."))
        }
        val thread: @Composable () -> Unit = { CaseThread(case, proof) { viewing = it } }
        val vote: @Composable () -> Unit = {
            ThreadHeading("Your vote", Modifier.padding(top = 22.dp, bottom = 10.dp))
            VoteTiles(choice, enabled = voted == null && !busy, dimOthers = voted != null) { picked = it }
            Spacer(Modifier.height(12.dp))
            if (voted == null) {
                InfoLine(Lucide.Scale, strong("Whoever you decide against pays $stake tix, $share to each of you.", "$stake tix"))
            } else {
                val left = (case.votesNeeded - case.votesCast).coerceAtLeast(0)
                InfoLine(
                    Lucide.Hourglass,
                    AnnotatedString(
                        if (left > 0) {
                            "Waiting for ${left.words()} more ${if (left == 1) "vote" else "votes"}. " +
                                "Your $share tix come once it is decided."
                        } else {
                            "That was the last vote. Your $share tix come once it is decided."
                        },
                    ),
                )
            }
        }
        if (voted == null) {
            // The thread's first group brings its own 12dp.
            Spacer(Modifier.height(4.dp))
            thread()
            vote()
        } else {
            vote()
            ThreadHeading("What each side showed", Modifier.padding(top = 22.dp))
            thread()
        }
    }
    viewing?.let { ref -> ProofViewer(ref, onDismiss = { viewing = null }, picture = proof) }
}

/** Both sides, in the order it happened: the review, the refusal, and the reviewer's one answer. */
@Composable
private fun CaseThread(
    case: Case,
    proof: @Composable (String) -> Unit,
    onPicture: (String) -> Unit,
) {
    Thread {
        ThreadGroup(
            listOfNotNull(
                case.proofRef.takeIf { it.isNotBlank() }?.let { ThreadPart.Picture(it) },
                reviewWords(case.written, case.needsChars, case.textUse),
            ),
            who = "Reviewer",
            picture = proof,
            onPicture = onPicture,
        )
        if (case.recheckedRef.isNotBlank()) {
            ThreadNote("The developer asked for a new one")
            ThreadGroup(
                listOf(ThreadPart.Picture(case.recheckedRef)),
                time = "New, taken on Play when asked",
                picture = proof,
                onPicture = onPicture,
            )
        }
        ThreadNote("The developer refused it", NoteTone.WARN, Lucide.Scale)
        val evidence = case.evidence
        ThreadGroup(
            listOfNotNull(
                case.reason.takeIf { it.isNotBlank() }?.let { ThreadPart.Words(it) },
                evidence.ownerProofRef.takeIf { it.isNotBlank() }?.let { ThreadPart.Picture(it) },
            ),
            side = ThreadSide.RIGHT,
            who = "Developer",
            tone = ThreadTone.SIDE,
            picture = proof,
            onPicture = onPicture,
        )
        if (evidence.reply.isNotBlank() || evidence.replyProofRef.isNotBlank()) {
            ThreadNote("The reviewer answered")
            ThreadGroup(
                listOfNotNull(
                    evidence.reply.takeIf { it.isNotBlank() }?.let { ThreadPart.Words(it) },
                    evidence.replyProofRef.takeIf { it.isNotBlank() }?.let { ThreadPart.Picture(it) },
                ),
                who = "Reviewer",
                picture = proof,
                onPicture = onPicture,
            )
        }
    }
}

/**
 * The two sides as tiles; the one picked filled, with a check. Not [enabled] while the vote goes or
 * once it is in; [dimOthers] fades the side not picked once it is counted.
 */
@Composable
private fun VoteTiles(
    picked: Boolean?,
    enabled: Boolean,
    dimOthers: Boolean,
    onPick: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VoteTile(Lucide.Check, "The review stands", "The reviewer is paid", picked == true, enabled, dimOthers, Modifier.weight(1f)) {
            onPick(true)
        }
        VoteTile(Lucide.X, "The developer is right", "Nothing is paid", picked == false, enabled, dimOthers, Modifier.weight(1f)) {
            onPick(false)
        }
    }
}

@Composable
private fun VoteTile(
    icon: ImageVector,
    name: String,
    line: String,
    selected: Boolean,
    enabled: Boolean,
    dim: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(TileRadius)
    Box(
        modifier
            .alpha(if (dim && !selected) 0.45f else 1f)
            .clip(shape)
            .background(if (selected) t.leafAccentSoft else t.bg)
            .border(1.dp, if (selected) Color.Transparent else t.cardLine, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 10.dp),
    ) {
        Column {
            Icon(icon, null, Modifier.size(18.dp), tint = t.tileInk)
            Spacer(Modifier.height(6.dp))
            Text(
                name,
                style = tileStyle(15f, 18f, if (selected) FontWeight.Bold else FontWeight.SemiBold),
                color = if (selected) t.tabSelected else t.text,
            )
            Spacer(Modifier.height(2.dp))
            Text(line, style = tileStyle(12f, 15f, FontWeight.Normal), color = t.text3)
        }
        if (selected) Icon(Lucide.Check, null, Modifier.align(Alignment.TopEnd).size(15.dp), tint = t.tabSelected)
    }
}
