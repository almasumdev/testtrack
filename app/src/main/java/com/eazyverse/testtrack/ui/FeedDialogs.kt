package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Live on Play, or still in closed testing.
 *
 * Written as the owner would say it rather than as two track names, because the person answering
 * knows what they did in Play Console and does not necessarily know what we call it.
 */
@Composable
fun LiveChoice(
    isLive: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Where is it?",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill("Live on Play", isLive) { onChange(true) }
            ChoicePill("Closed testing", !isLive) { onChange(false) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (isLive) {
                "Reviewers will be sent to leave a public review."
            } else {
                "Reviewers will write feedback here instead, since there is no listing yet."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChoicePill(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .clip(shape)
            .background(
                if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            ).border(
                1.dp,
                if (on) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                shape,
            ).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
            color =
                if (on) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

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
) {
    var reason by remember { mutableStateOf("") }
    val enough = reason.trim().length >= 15

    Ask(
        title = "Refuse this review?",
        body =
            "Three members will decide it, and whoever it goes against pays them. Say what is " +
                "wrong with it so they have something to go on.",
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
                "Accepting $appName's review instead costs nothing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
