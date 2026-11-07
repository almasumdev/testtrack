package com.eazyverse.testtrack.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.domain.Evidence
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * Pictures and an answer in a dispute. The owner may attach a screenshot to a refusal; the
 * reviewer, once refused, answers once with words, a screenshot, or both. The members deciding
 * see both sides.
 */

/** Add one screenshot from the gallery, optionally: a link to add it, and one to take it back out. */
@Composable
internal fun AddPicture(
    picture: Uri?,
    onPicture: (Uri?) -> Unit,
    question: String,
) {
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) onPicture(uri)
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        QuietAction(question, if (picture == null) "Add" else "Change") {
            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        if (picture != null) {
            Spacer(Modifier.width(8.dp))
            RowPill("Remove", PillKind.GHOST, onClick = { onPicture(null) })
        }
    }
    if (picture != null) {
        Spacer(Modifier.height(4.dp))
        Text("Screenshot added.", style = MaterialTheme.typography.bodySmall, color = Tt.colors.text3)
    }
}

/**
 * On the reviewer's page for a refused review: why it was refused, the owner's screenshot if they
 * added one, and the one answer the reviewer may give, or what they gave.
 */
@Composable
internal fun ReplyToRefusal(
    job: ReviewJob,
    gap: androidx.compose.ui.unit.Dp,
    onReply: (String, Uri?) -> Unit,
) {
    if (job.review.state != "disputed") return
    val review = job.review
    var answering by remember { mutableStateOf(false) }
    Spacer(Modifier.height(gap))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EvidenceLine("Why it was refused", review.reason)
        if (review.ownerProofRef.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("The developer's screenshot", style = MaterialTheme.typography.labelMedium, color = Tt.colors.text3)
            ProofImage(review.ownerProofRef, whole = true)
        }
        if (job.canReply) {
            Spacer(Modifier.height(6.dp))
            RowPill("Answer the developer", PillKind.SOFT, onClick = { answering = true })
            Text(
                "You can answer once. The three members and the developer see it.",
                style = MaterialTheme.typography.bodySmall,
                color = Tt.colors.text3,
            )
        } else {
            Spacer(Modifier.height(6.dp))
            EvidenceLine("Your answer", review.reply)
            if (review.replyProofRef.isNotBlank()) ProofImage(review.replyProofRef, whole = true)
        }
    }
    if (answering) {
        var text by remember { mutableStateOf("") }
        var picture by remember { mutableStateOf<Uri?>(null) }
        Ask(
            title = "Answer the developer",
            body = "Say what you did, or show the review on Google Play. You can answer once.",
            confirm = "Send",
            confirmEnabled = text.trim().isNotEmpty() || picture != null,
            onConfirm = {
                answering = false
                onReply(text.trim(), picture)
            },
            onDismiss = { answering = false },
        ) {
            DialogField(
                value = text,
                onValueChange = { text = it.take(FeedRules.REFUSE_MAX) },
                label = "Your answer",
                placeholder = "It is on Google Play under my name.",
                singleLine = false,
                minHeight = 96.dp,
                maxLength = FeedRules.REFUSE_MAX,
            )
            Spacer(Modifier.height(8.dp))
            AddPicture(picture, { picture = it }, "Show your review on Play?")
        }
    }
}

@Composable
private fun EvidenceLine(
    title: String,
    text: String,
) {
    if (text.isBlank()) return
    Text(title, style = MaterialTheme.typography.labelMedium, color = Tt.colors.text3)
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Tt.colors.text)
}

/** For the members deciding, and the owner: what each side showed. */
@Composable
internal fun DisputeEvidence(
    evidence: Evidence,
    proof: @Composable (String) -> Unit,
) {
    if (evidence.isEmpty) return
    Spacer(Modifier.height(14.dp))
    if (evidence.ownerProofRef.isNotBlank()) {
        Text("The developer's screenshot", style = MaterialTheme.typography.labelMedium, color = Tt.colors.text3)
        proof(evidence.ownerProofRef)
        Spacer(Modifier.height(12.dp))
    }
    if (evidence.reply.isNotBlank() || evidence.replyProofRef.isNotBlank()) {
        EvidenceLine("The reviewer's answer", evidence.reply)
        if (evidence.replyProofRef.isNotBlank()) {
            Spacer(Modifier.padding(top = 6.dp))
            proof(evidence.replyProofRef)
        }
    }
}
