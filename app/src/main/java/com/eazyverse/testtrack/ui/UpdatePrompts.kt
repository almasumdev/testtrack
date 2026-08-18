package com.eazyverse.testtrack.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.data.UpdateGate

/**
 * The optional offer, in the same card as the other things home says to you.
 *
 * Dismissible, and dismissing it is remembered against the build it was offering rather than as a
 * flag, so waving one away does not silence the next.
 */
@Composable
fun UpdateNotice(onUpdate: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .padding(top = 18.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Status.neutralSoft)
            .padding(14.dp)
    ) {
        Text(
            "A newer TestTrack is out",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Your cohort and this copy of the app should agree about what a day looks like. " +
                "Updating takes a moment and happens in the background.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) {
                Text("Not now", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onUpdate) { Text("Update") }
        }
    }
}

/** The download has landed and is waiting on a restart nobody has asked for yet. */
@Composable
fun RestartNotice(onRestart: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .padding(top = 18.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Status.postedSoft)
            .padding(14.dp)
    ) {
        Text(
            "The update is ready",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Status.posted
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "It installs when TestTrack restarts. Nothing you have done today is lost.",
            style = MaterialTheme.typography.bodySmall,
            color = Status.posted
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onRestart) { Text("Restart now", color = Status.posted) }
        }
    }
}

/**
 * The one screen in this app that will not let somebody past.
 *
 * Only ever reached when [UpdateGate] has an answer from Play that this install can be updated
 * right now, so the button it offers always leads somewhere. That is the whole reason the tier is
 * resolved the way it is: a wall in front of a door that does not open is worse than no wall.
 *
 * Back is swallowed. Leaving by the back gesture would drop them onto a screen this is here to
 * keep them off, and the app would be in the state the block exists to prevent.
 */
@Composable
fun ForceUpdate(
    downloaded: Boolean,
    canDownloadInApp: Boolean,
    onUpdate: () -> Unit,
    onRestart: () -> Unit
) {
    val context = LocalContext.current
    BackHandler(enabled = true) {}

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Update to carry on",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "This version of TestTrack no longer counts a day the same way your cohort does. " +
                    "Carrying on with it would show you a record nobody else can see.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            when {
                downloaded -> Primary("Restart and finish", onClick = onRestart)
                canDownloadInApp -> Primary("Update now", onClick = onUpdate)
                // Play can serve it but not in the background, which is the flow it declines on
                // some installs. The listing still works, so send them there rather than leaving
                // a button that quietly does nothing.
                else -> Primary("Open Play Store") {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=${context.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }
    }
}
