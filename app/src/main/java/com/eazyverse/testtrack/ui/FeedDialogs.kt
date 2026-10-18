package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    stake: Int = REFUSE_STAKE,
) {
    var reason by remember { mutableStateOf("") }
    val enough = reason.trim().length >= 15

    Ask(
        title = "Refuse this review?",
        body =
            "Three members check it. Refusing holds $stake tix from you until they decide: if " +
                "they agree, you get them back and the slot reopens; if not, the reviewer is paid " +
                "and your $stake tix pay the members.",
        confirm = "Refuse it",
        destructive = true,
        confirmEnabled = enough,
        onConfirm = { onRefuse(reason.trim()) },
        onDismiss = onDismiss,
        content = {
            DialogField(
                value = reason,
                onValueChange = { reason = it.take(280) },
                label = "What is wrong with it",
                placeholder = "The screenshot is of a different app's listing.",
                supporting =
                    if (enough) {
                        "The reviewer sees this too."
                    } else {
                        "A few more words. This is all the three of them will have."
                    },
                singleLine = false,
                minHeight = 96.dp,
                maxLength = 280,
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

/** Three members at five tix each: what a refusal holds, as the server has it. */
const val REFUSE_STAKE = 15
