package com.eazyverse.testtrack.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.data.*
import kotlinx.coroutines.delay
import java.util.Date

@Composable
fun Header(
    group: TestGroup,
    done: Int,
    total: Int,
) {
    val day = group.dayIndex()

    Column(Modifier.padding(start = Gutter, end = Gutter, top = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (day != null) {
                    "Day ${day + 1}"
                } else if (group.running) {
                    "Finished"
                } else {
                    "Forming"
                },
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            if (day != null) {
                Spacer(Modifier.width(7.dp))
                Text(
                    "of ${group.runDays}",
                    Modifier.padding(bottom = 5.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            when {
                day != null ->
                    "$done of $total done · ${group.size} of ${TestGroup.CAPACITY} members"
                // A run that has ended is not one that has not started. `startDate > 0` is what
                // tells them apart, and the copy has to as well.
                group.running ->
                    "All ${group.runDays} days are behind you. Testing on is welcome but no longer " +
                        "asked for. Your dashboard has the full record."
                else ->
                    "${group.size} of ${TestGroup.THRESHOLD} members so far. The run starts once " +
                        "the ${TestGroup.THRESHOLD}th joins."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (day != null) {
            Spacer(Modifier.height(14.dp))
            // The same segmented bar the home screen uses. One bar per app, so the day reads as
            // twelve things to do rather than a percentage, and the two screens agree about what
            // the same day looks like.
            Meter(done = done, total = total)

            /*
             * When the day turns over, spelled out.
             *
             * The run's clock starts at the hour the group did, so this is almost never midnight
             * and there is no way to work it out from anything else on screen. Somebody testing
             * after dinner can put half a round on one day and half on the next and see only that
             * their count went back to zero.
             *
             * The date is in it as well as the time. "Ends at 8:33 pm" on an evening after 8:33 is
             * a sentence about tomorrow that reads as one about tonight.
             */
            group.dayEndsAt()?.let { ends ->
                Spacer(Modifier.height(12.dp))
                DayEndBlock(day = day, endsAt = ends)
            }
        }
        // A short-handed group used to be called out here in red. It is not shown any more, and
        // the reason is that being short is now something an admin can choose: a run can be
        // started deliberately with eight people, and telling those eight every day that they are
        // five short reads as a fault in their group rather than a decision about it.
        //
        // Nothing is hidden that a tester can act on. Filling an empty slot was never theirs to
        // do, the day count in front of them is the same either way, and the admin console still
        // shows the warning in full, which is where the person who can fix it is looking.
    }
}

/**
 * When the day turns over, spelled out and counted down.
 *
 * The run's clock starts at the hour the group did, so this is almost never midnight and there is
 * no way to work it out from anything else on screen. Somebody testing after dinner can put half
 * a round on one day and half on the next and see only that their count went back to zero.
 *
 * The clock time is in it as well as the countdown. "3h left" on its own forces the reader to do
 * the arithmetic themselves if they want to know whether that is worth waiting up for; the two
 * together answer both "how soon" and "how late" in one glance.
 */
@Composable
private fun DayEndBlock(
    day: Int,
    endsAt: Long,
) {
    val context = LocalContext.current
    val clockTime =
        remember(endsAt) {
            val time = DateFormat.getTimeFormat(context).format(Date(endsAt))
            val today = DateFormat.getDateFormat(context).format(Date())
            val then = DateFormat.getDateFormat(context).format(Date(endsAt))
            if (today == then) "$time today" else "$time tomorrow"
        }

    // Recomputed on a timer rather than once: a countdown that never counts down is just the
    // clock time again, in more words.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val remaining = (endsAt - now).coerceAtLeast(0L)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Status.neutralSoft)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.HourglassEmpty,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                formatCountdown(remaining),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Day ${day + 1} ends $clockTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "3h 42m left", or just the minutes once it's under an hour — nobody needs the seconds. */
private fun formatCountdown(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h left"
        hours > 0 -> "${hours}h ${minutes}m left"
        else -> "${minutes}m left"
    }
}

/**
 * One control, whichever one applies.
 *
 * Obstacles are offered in the order they have to clear: a "Start testing" button next to an
 * ungranted permission is a button that fails.
 */
@Composable
fun Action(
    group: TestGroup,
    outstanding: Int,
    remaining: Int,
    uploading: Int,
    usageGranted: Boolean,
    canReturn: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onEndSession: () -> Unit,
    onGrantUsage: () -> Unit,
    onGrantOverlay: () -> Unit,
) {
    Column(Modifier.padding(start = Gutter, end = Gutter, top = 18.dp)) {
        when {
            group.dayIndex() == null -> Unit

            CaptureService.roundActive -> {
                Text(
                    CaptureService.status
                        ?: "Testing ${CaptureService.roundIndex} of ${CaptureService.roundTotal}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onStop,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) { Text("Stop") }
            }

            uploading > 0 ->
                Row(
                    Modifier.fillMaxWidth().height(46.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Uploading $uploading proof${if (uploading == 1) "" else "s"}…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

            !usageGranted -> {
                Text(
                    "We record how long you spend in each app, and Android only shares that with " +
                        "usage access switched on. Turn it on for TestTrack and come back here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Primary("Open usage access", onClick = onGrantUsage)
            }

            !canReturn -> {
                Text(
                    "A round opens each app and brings you back on its own. Android only lets us " +
                        "do that with permission to display over other apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Primary("Allow TestTrack to switch apps", onClick = onGrantOverlay)
            }

            // Nothing to run, but for two very different reasons. A round only walks apps that are
            // actually on the phone, so a cohort none of which are installed also has nothing
            // outstanding — and calling that "done", in green, over a header reading "0 of 11
            // done" is the screen contradicting itself.
            outstanding == 0 && remaining > 0 ->
                Text(
                    "$remaining app${if (remaining == 1) "" else "s"} still to test, and " +
                        "${if (remaining == 1) "it isn't" else "none of them are"} installed on this " +
                        "phone yet. Tap Install on ${if (remaining == 1) "it" else "each one"} to " +
                        "join the test and get it from Play. A round can only open what's already here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Status.missed,
                )

            outstanding == 0 ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Everything's done for today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Status.posted,
                    )
                    if (CaptureService.sessionActive) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onEndSession) { Text("Stop sharing") }
                    }
                }

            else -> {
                // The explanation used to sit under the button on every visit to this screen,
                // which is fine reading it once and furniture every time after. Behind the icon
                // it is there exactly when it's wanted, and takes nothing from anyone who
                // already knows how a round works.
                var showHowItWorks by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Primary(
                        "Start testing · $outstanding left",
                        tall = true,
                        modifier = Modifier.weight(1f),
                        onClick = onStart,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { showHowItWorks = true },
                        modifier = Modifier.size(46.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "How a round works",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (showHowItWorks) {
                    Ask(
                        title = "How a round works",
                        /*
                         * This used to end "so leave the phone alone until you're back here",
                         * which read as an instruction to sit still through the round. It was
                         * aimed at the wrong thing: what breaks a visit is switching away from
                         * the app, not touching it. Told to do nothing, a tester does nothing,
                         * and twelve people opening apps and staring at them is not testing and
                         * would not survive anybody looking at it.
                         */
                        body =
                            "Each app opens for about twenty seconds, then moves to the " +
                                "next one on its own. Have a proper look while you're in there. " +
                                "The screenshot is taken at a moment you won't know in advance, " +
                                "so stay in the app until it hands over.",
                        confirm = "Got it",
                        onConfirm = { showHowItWorks = false },
                        onDismiss = { showHowItWorks = false },
                        dismiss = null,
                    )
                }
                if (CaptureService.sessionActive) {
                    TextButton(
                        onClick = onEndSession,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text("Stop screen sharing") }
                } else {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
