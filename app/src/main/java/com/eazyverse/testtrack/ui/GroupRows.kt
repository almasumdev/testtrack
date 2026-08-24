package com.eazyverse.testtrack.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.data.*
import java.util.Date

/**
 * Apps nobody is being asked for, and what to do about them.
 *
 * Deliberately not called "uninstall these", which is what it said first and which assumes the
 * tester ever got it installed. The commonest reason an app ends up here is that it was never
 * installable: a closed testing track set up wrong, a listing that answers with an error, and
 * thirteen people who could not have opened it however willing they were. Telling those thirteen
 * to uninstall something they never had reads as an app that has lost track of itself.
 *
 * A removal is not a punishment being announced to the group either, so this says as little about
 * the developer as it can. The reason line is shown when an admin typed one, because "not on
 * closed testing yet" is worth knowing and stops people trying again.
 *
 * "Until it comes back" rather than a date, because there is no date. An admin decides, and
 * anything more definite would be invented.
 */
@Composable
fun Skipped(
    apps: List<TestApp>,
    onUninstall: (TestApp) -> Unit,
) {
    // Read once, out here. Inside the remember lambda it is a composable call in a place that is
    // not composable, which the compiler catches and is right to.
    val context = LocalContext.current

    SectionLabel("Not being tested")
    Rows {
        apps.forEachIndexed { index, app ->
            if (index > 0) RowDivider()
            val info =
                remember(app.packageName, InstalledApps.revision) {
                    InstalledApps.cachedInfo(context, app.packageName)
                }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        app.removedReason.ifBlank {
                            "Skipped for now. It does not count towards your day."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Offered only to somebody who actually has it. The rest have nothing to do here.
                if (info.installed) {
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = { onUninstall(app) }) {
                        Text("Uninstall", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun MineRow(
    app: TestApp,
    reporters: Int,
    group: TestGroup,
    broadcastCooldown: Long,
    broadcasting: Boolean,
    feedbackAskCooldown: Long,
    askingForFeedback: Boolean,
    onEditNotes: () -> Unit,
    onBroadcast: () -> Unit,
    onAskForFeedback: () -> Unit,
    onClick: () -> Unit,
) {
    val expected = (group.size - 1).coerceAtLeast(0)

    // Until now this row had no way to say it at all — an owner found out their own app had
    // been reported broken only by opening the Dashboard, if they thought to. "Reported" would
    // collide with the stat line right below it, which already uses that word for "posted a
    // proof" — see DayState.POSTED's legend — so the count is worded as "tested" here instead,
    // just on this row, rather than renaming that vocabulary everywhere it's established.
    val problem = app.reportedAt > 0L

    // Brought in line with TestRow: same icon-matches-the-text-block sizing, actions on their
    // own line as chips rather than a text button, and the row itself — not a trailing arrow —
    // is what says "there's more here, tap it."
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Gutter, vertical = 10.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .heightIn(min = 44.dp, max = 58.dp)
                    .fillMaxHeight()
                    .aspectRatio(1f),
            ) {
                AppIcon(app.packageName, app.label, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (problem) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Status.shortSoft)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "Reported",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Status.short,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        problem ->
                            app.reportedReason.ifBlank {
                                "Marked as broken. Excused until you sort it out."
                            }
                        group.dayIndex() != null -> "$reporters of $expected tested today"
                        else -> "Waiting for the run to start"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem) Status.short else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Two labels, because the two states are not the same job. An owner who has never
            // written notes has no reason to expect the row hides a field, and "Notes" on its
            // own reads as a heading rather than something to press.
            ActionChip(
                if (app.notes.isBlank()) "Add notes" else "Edit notes",
                MaterialTheme.colorScheme.onSurfaceVariant,
                Status.neutralSoft,
                onEditNotes,
            )

            // Only once there's a cohort to tell — nothing to broadcast to while the group is
            // still forming. Cooldown shown rather than merely enforced, the same reasoning as
            // Dashboard's own copy of this button: a chip that just refuses to do anything reads
            // as broken, not as "wait a while."
            if (group.dayIndex() != null && expected > 0) {
                ActionChip(
                    when {
                        broadcasting -> "Sending…"
                        broadcastCooldown > 0L -> "Notify again in ${formatWait(broadcastCooldown)}"
                        else -> "Notify testers"
                    },
                    if (broadcastCooldown > 0L || broadcasting) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    Status.neutralSoft,
                ) {
                    if (broadcastCooldown <= 0L && !broadcasting) onBroadcast()
                }

                // [broadcast]'s reverse and its own cooldown — an owner curious how it's going
                // for their cohort has had no way to ask, short of writing to each of them one at
                // a time outside the app entirely.
                ActionChip(
                    when {
                        askingForFeedback -> "Sending…"
                        feedbackAskCooldown > 0L -> "Ask again in ${formatWait(feedbackAskCooldown)}"
                        else -> "Ask for feedback"
                    },
                    if (feedbackAskCooldown > 0L || askingForFeedback) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    Status.neutralSoft,
                ) {
                    if (feedbackAskCooldown <= 0L && !askingForFeedback) onAskForFeedback()
                }
            }
        }
    }
}

/**
 * Sends a tester to this app's listing, to install it.
 *
 * The Play app is asked directly first, by `market://`, and only then the https address. Both
 * reach the same listing, but a closed-testing listing is visible only to an account on the tester
 * list: opened in a browser signed in as somebody else, it reports the app as missing. Going to
 * the Play app first keeps that from happening on a phone where it would have worked.
 *
 * A device with neither is possible, a stripped ROM or Play disabled, and that is the one case
 * worth a message rather than a shrug, because the tester is stuck on a step they cannot complete
 * from here.
 */
fun openInPlay(
    context: Context,
    app: TestApp,
    onFailure: (String) -> Unit,
) {
    fun open(uri: String) =
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

    open(app.storeAppUri)
        .recoverCatching { open(app.storeUrl).getOrThrow() }
        .onFailure {
            onFailure(
                "We couldn't open Play on this phone. Install ${app.label} from " +
                    "${app.storeUrl}, using the account you joined the testers group with.",
            )
        }
}

/** What [entry] means, in my own words — see [History.type]. */
private fun History.verb() =
    when (type) {
        HISTORY_JOINED -> "You joined"
        HISTORY_REMOVED -> "You were taken out of testing"
        HISTORY_RESTORED -> "You were put back in testing"
        HISTORY_KICKED -> "You were kicked from the group"
        else -> type
    }

/**
 * One line of a member's own record — the same shape as the admin console's history row, but
 * without a name, since every row here is about the one person reading it.
 */
@Composable
fun MyHistoryRow(entry: History) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            entry.verb() + (if (entry.appLabel.isNotBlank()) " — ${entry.appLabel}" else ""),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            DateFormat.getDateFormat(context).format(Date(entry.createdAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A [TextButton] enforces Material's ~40dp minimum touch height no matter what contentPadding
 * says, which is too tall for a row meant to hold five or six of these side by side. Built on a
 * plain clickable [Box] instead — the same shape the [reportDetails]-reading "Reported" chip
 * already used — so the padding below is the actual, final size.
 */
@Composable
fun ActionChip(
    text: String,
    tone: Color,
    background: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = tone,
            maxLines = 1,
        )
    }
}
