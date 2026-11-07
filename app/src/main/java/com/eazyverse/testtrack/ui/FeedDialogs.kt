package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Refusing a review, which costs something.
 *
 * The reason is required because it is the only thing three strangers will have to judge by: a
 * refusal with no account of itself asks them to guess, and one of the two people involved pays
 * for that guess.
 */
@Composable
fun FeedRefuseDialog(
    appName: String,
    onRefuse: (String) -> Unit,
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
    val sent = (problems.filter { it in ticked } + listOfNotNull(reason.trim().ifBlank { null })).joinToString(". ")
    val enough = ticked.isNotEmpty() || reason.trim().length >= REFUSE_MIN_CHARS

    Ask(
        title = "Refuse this review?",
        body =
            "${FeedRules.JURORS} members check it. Refusing holds $stake tix from you until they decide. If " +
                "they agree, you get them back and the slot reopens. If not, the reviewer is paid " +
                "and your $stake tix pay the members.",
        confirm = "Refuse it",
        destructive = true,
        confirmEnabled = enough,
        onConfirm = { onRefuse(sent.take(FeedRules.REFUSE_MAX)) },
        onDismiss = onDismiss,
        content = {
            Text("What was missing", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            problems.forEach { problem ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            problem in ticked,
                            role = Role.Checkbox,
                        ) { on -> ticked = if (on) ticked + problem else ticked - problem },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = problem in ticked, onCheckedChange = null)
                    Spacer(Modifier.width(10.dp))
                    Text(problem, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            DialogField(
                value = reason,
                onValueChange = { reason = it.take(FeedRules.REFUSE_MAX) },
                label = if (ticked.isEmpty()) "What is wrong with it" else "Anything else (optional)",
                placeholder = "The screenshot is of a different app's listing.",
                supporting =
                    if (enough || ticked.isNotEmpty()) {
                        "The reviewer sees this too."
                    } else {
                        "A few more words. This is all the three of them will have."
                    },
                singleLine = false,
                minHeight = 96.dp,
                maxLength = FeedRules.REFUSE_MAX,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Accepting $appName's review instead costs nothing more.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
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
            listOf("The text is not what is on Google Play", "The text is too short or off topic")
        } else {
            emptyList()
        }
