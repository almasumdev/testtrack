package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BellRing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.data.*

/**
 * A day already missed, and what it costs.
 *
 * Two tones on purpose. The first miss is recoverable and says so, because a warning that reads
 * like a final notice teaches people to ignore the final notice. The second is the last one they
 * get, and names the apps rather than counting them, so nobody has to work out which.
 */
@Composable
fun MissedDayNotice(
    standing: Enforcement.Standing,
    onDismiss: () -> Unit,
) {
    val tone = if (standing.finalWarning) Status.missed else Status.short

    Notice(
        tone = tone,
        icon = Lucide.TriangleAlert,
        modifier = Modifier.padding(horizontal = Gutter).padding(top = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (standing.finalWarning) {
                    "Last chance in ${standing.groupName}"
                } else {
                    "You missed yesterday in ${standing.groupName}"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tone,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Lucide.X, "Close", tint = tone, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (standing.finalWarning) {
                "That's two days running without opening ${standing.names}. Open ${standing.it} " +
                    "today and you keep your place. Leave it another day and your app goes back " +
                    "to the queue."
            } else {
                "You didn't open ${standing.names}. Open ${standing.it} today and nothing comes " +
                    "of it. Miss a second day in a row and your app leaves the crew."
            },
            style = MaterialTheme.typography.bodySmall,
            color = tone,
        )
    }
}

/**
 * Something that has already happened to this tester, with a way to say they have read it.
 *
 * Dismissal is explicit rather than timed. "Your app was removed from its crew" is the kind of
 * thing someone should have to acknowledge, not something that quietly disappears while the phone
 * is in a pocket.
 */
@Composable
fun EventNotice(
    notice: Enforcement.Notice,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .padding(top = 18.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Text(
            notice.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            notice.body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text("Got it")
        }
    }
}

/**
 * Asks for notifications, at the point where they start to matter.
 *
 * Not at sign-in. Before a run is under way there is nothing to be warned about, and a permission
 * asked for ahead of its reason is the one people decline. Shown only while a cohort is running,
 * which is exactly when a missed day costs something.
 */
@Composable
fun NotificationPrompt(onGrant: () -> Unit) {
    Notice(
        tone = Status.short,
        icon = Lucide.BellRing,
        modifier = Modifier.padding(horizontal = Gutter).padding(top = 18.dp),
    ) {
        Text(
            "Turn on notifications",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Status.short,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Your run has started, and we warn you the day before a missed day costs you your " +
                "place in the crew. Without notifications that warning has nowhere to go.",
            // Ordinary text now that the edge carries the tone. A whole paragraph in amber was
            // the loudest thing on Home and the least worth reading twice.
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onGrant, modifier = Modifier.align(Alignment.End)) {
            Text("Turn them on")
        }
    }
}
