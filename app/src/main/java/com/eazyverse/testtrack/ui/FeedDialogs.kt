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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.ImagePlus
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Scale
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * Refusing a review, which costs something, in a sheet over the review.
 *
 * The reason is required because it is the only thing three strangers will have to judge by: a
 * refusal with no account of itself asks them to guess, and one of the two people involved pays
 * for that guess. Ticking what was missing is enough; with nothing ticked, a few words are.
 */
@Composable
fun FeedRefuseSheet(
    onRefuse: (String, Uri?) -> Unit,
    onDismiss: () -> Unit,
    /** What refusing holds from the owner until the members decide. */
    stake: Int = FeedRules.STAKE,
    /** Characters of text the request asked for, which adds the text's own problems to tick. */
    needsChars: Int = 0,
) {
    var reason by remember { mutableStateOf("") }
    // What was missing, ticked: the members see these first, then any words of the owner's own.
    val problems = remember(needsChars) { refuseProblems(needsChars) }
    var ticked by remember { mutableStateOf(emptySet<String>()) }
    var picture by remember { mutableStateOf<Uri?>(null) }
    val sent = (problems.filter { it in ticked } + listOfNotNull(reason.trim().ifBlank { null })).joinToString(". ")
    val enough = ticked.isNotEmpty() || reason.trim().length >= REFUSE_MIN_CHARS
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) picture = uri
        }
    val t = Tt.colors
    PickerSheet(
        title = "Refuse this review?",
        onDismiss = onDismiss,
        skipHalf = true,
        fitMax = 0.9f,
        leading = { IconSquare(Lucide.Scale, Tone.ACCENT) },
    ) { close ->
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 18.dp)) {
            InfoLine(
                Lucide.Lock,
                strong("$stake tix are held from you until ${FeedRules.JURORS.words()} members decide.", "$stake tix"),
                title = "What refusing starts",
            ) {
                CalloutLine(
                    strong(
                        "If they agree with you, the tix come back and the slot opens again. If not, the reviewer is paid " +
                            "and your $stake tix pay the members.",
                    ),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("What was missing", style = tileStyle(14f, 18f, FontWeight.Bold), color = t.text)
            Spacer(Modifier.height(2.dp))
            problems.forEach { problem ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .toggleable(problem in ticked, role = Role.Checkbox) { on ->
                            ticked = if (on) ticked + problem else ticked - problem
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = problem in ticked, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Text(problem, style = tileStyle(14.5f, 19f, FontWeight.Normal), color = t.text)
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedField(
                value = reason,
                onValueChange = { reason = it.take(FeedRules.REFUSE_MAX) },
                label = if (ticked.isEmpty()) "What is wrong with it" else "Anything else",
                placeholder =
                    if (ticked.isEmpty()) {
                        "A few words. The reviewer sees this too."
                    } else {
                        "Optional. The reviewer sees this too."
                    },
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                maxLength = FeedRules.REFUSE_MAX,
            )
            Spacer(Modifier.height(10.dp))
            // Shown to the three members beside the reviewer's own: the Play page with no review on it.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RowPill(
                    if (picture == null) "Add your screenshot" else "Change your screenshot",
                    PillKind.SOFT,
                    leading = Lucide.ImagePlus,
                    leadingSize = 15.dp,
                ) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                if (picture != null) RowPill("Remove", PillKind.GHOST) { picture = null }
            }
            Spacer(Modifier.height(8.dp))
            InfoLine(Lucide.EyeOff, AnnotatedString("The reviewer sees it. Hide your name and email."))
            Spacer(Modifier.height(14.dp))
            Primary("Refuse and hold $stake tix", enabled = enough, destructive = true) {
                val reasonSent = sent.take(FeedRules.REFUSE_MAX)
                val pictureSent = picture
                close { onRefuse(reasonSent, pictureSent) }
            }
        }
    }
}

/**
 * The app's own floor for a refusal's reason, which the server does not set: enough words for
 * three strangers to judge by.
 */
private const val REFUSE_MIN_CHARS = 15

/** The usual problems with a review, to tick; a text's own only when the request asked for one. */
private fun refuseProblems(needsChars: Int): List<String> =
    listOf("No review on Google Play", "The screenshot is of something else") +
        if (needsChars > 0) {
            listOf("The text isn't what's on Play", "The text is too short or off topic")
        } else {
            emptyList()
        }
