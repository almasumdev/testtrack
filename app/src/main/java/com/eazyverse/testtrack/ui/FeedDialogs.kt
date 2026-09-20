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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Putting one of your own apps in front of the community.
 *
 * Four things, and each is asked because something downstream cannot work without it.
 *
 * The package name is the one people forget is necessary. Every check a reviewer's phone makes is
 * against it: is this installed, did Play put it there, how long has it been open. Without it a
 * review is somebody's word.
 *
 * Whether the app is live is asked rather than detected, because nothing on a stranger's phone can
 * see which track an app is on and the owner is the only person who knows. It decides what a
 * reviewer is actually sent to do: leave a public review, or write notes here.
 *
 * The offer is last, and it is where the price appears, worked out as it is typed. A post buys
 * three reviews, so it costs three times what it pays for one. That is the last moment before
 * anything is spent and so the only moment the number has to be right by.
 */
@Composable
fun FeedPostDialog(
    tix: Int,
    onPost: (name: String, pkg: String, ask: String, reward: Int, isLive: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }
    var ask by remember { mutableStateOf("") }
    var offer by remember { mutableStateOf(FeedRules.MIN_REWARD.toString()) }
    var isLive by remember { mutableStateOf(false) }

    val reward = offer.toIntOrNull() ?: 0
    val cost = FeedRules.postCost(reward)
    val inRange = reward in FeedRules.MIN_REWARD..FeedRules.MAX_REWARD
    val affordable = inRange && cost <= tix
    val named = pkg.trim().contains('.') && pkg.trim().length > 3

    Ask(
        title = "Ask for a review",
        body = "Anyone can take it on. What you offer decides how fast they do.",
        confirm = "Post",
        confirmEnabled = name.isNotBlank() && ask.isNotBlank() && named && affordable,
        onConfirm = { onPost(name.trim(), pkg.trim(), ask.trim(), reward, isLive) },
        onDismiss = onDismiss,
        content = {
            DialogField(
                value = name,
                onValueChange = { name = it.take(FeedRules.APP_NAME_MAX) },
                label = "App",
                placeholder = "Nikosh Budget",
            )
            Spacer(Modifier.height(14.dp))
            DialogField(
                value = pkg,
                onValueChange = { pkg = it.take(200) },
                label = "Package name",
                placeholder = "com.eazyverse.nikosh",
                error =
                    if (pkg.isNotBlank() && !named) {
                        "That does not look like a package name."
                    } else {
                        null
                    },
                supporting =
                    "Every check runs against this: whether a reviewer really installed it, and " +
                        "whether Play put it there.",
            )
            Spacer(Modifier.height(14.dp))
            DialogField(
                value = ask,
                onValueChange = { ask = it.take(FeedRules.ASK_MAX) },
                label = "What should they look at",
                placeholder = "Is the monthly total readable at a glance?",
                supporting = "One thing, asked plainly. Broad asks come back broad.",
                singleLine = false,
                minHeight = 84.dp,
                maxLength = FeedRules.ASK_MAX,
            )
            Spacer(Modifier.height(14.dp))
            LiveChoice(isLive) { isLive = it }
            Spacer(Modifier.height(14.dp))
            DialogField(
                value = offer,
                onValueChange = { offer = it.filter(Char::isDigit).take(3) },
                label = "What a review earns",
                keyboardType = KeyboardType.Number,
                error =
                    when {
                        !inRange ->
                            "Between ${FeedRules.MIN_REWARD} and ${FeedRules.MAX_REWARD} tix."
                        !affordable -> "That would cost $cost tix and you have $tix."
                        else -> null
                    },
                supporting =
                    "Costs $cost tix, which is ${FeedRules.REVIEWS_BOUGHT} reviews' worth. " +
                        "You have $tix.",
            )
        },
    )
}

/**
 * Live on Play, or still in closed testing.
 *
 * Written as the owner would say it rather than as two track names, because the person answering
 * knows what they did in Play Console and does not necessarily know what we call it.
 */
@Composable
private fun LiveChoice(
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
