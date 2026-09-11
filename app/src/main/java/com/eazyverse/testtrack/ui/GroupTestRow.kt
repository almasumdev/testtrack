package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.SquareCheck
import com.eazyverse.testtrack.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestRow(
    app: TestApp,
    done: Boolean,
    /** Today's [done] came from a screenshot picked by hand — marked yellow, not green. */
    manual: Boolean,
    /** The run is under way — the only state in which a visit can be banked against a day. */
    live: Boolean,
    /** Distinguishes a run that is over from one that has not begun. Only read when not [live]. */
    ended: Boolean,
    /** This app is on screen right now, being visited. */
    busy: Boolean,
    /** This app's proof is uploading. Not done yet, and not something to start again either. */
    sending: Boolean,
    /** Whose app this is, if the roster has arrived. The row works without it. */
    owner: Tester?,
    /** Whether the caller has personally opted out of this app. Only ever true if [TestApp.skippable]. */
    skipped: Boolean,
    /** A visit already served, waiting only on a picture — see CaptureService.missed. */
    canPickScreenshot: Boolean,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onOpenStore: () -> Unit,
    onReportProblem: () -> Unit,
    onClearReport: () -> Unit,
    /** Whether the caller has already left today's note on this app — see [Repo.submitFeedback]. */
    feedbackGiven: Boolean,
    onGiveFeedback: () -> Unit,
    /** Tapped the chip again after already sending today's note. */
    onFeedbackAlreadyGiven: () -> Unit,
    onToggleSkip: () -> Unit,
    onPickScreenshot: () -> Unit,
) {
    val context = LocalContext.current
    // Keyed on the revision as well as the package: without it this row holds the answer it was
    // first given, and an app installed since then keeps offering Install.
    val info =
        remember(app.packageName, InstalledApps.revision) {
            InstalledApps.cachedInfo(context, app.packageName)
        }

    var details by remember { mutableStateOf(false) }
    if (details) AppDetails(app) { details = false }

    var reportDetails by remember { mutableStateOf(false) }
    if (reportDetails) {
        // Only the person who filed it gets a way to take it back — everyone else reading this
        // dialog is just catching up on why the row says what it says.
        val mine = app.reportedBy.isNotBlank() && app.reportedBy == AuthRepo.uid
        Ask(
            title = "Reported",
            body = app.reportedReason.ifBlank { "No further detail was given." },
            confirm = if (mine) "Clear report" else "Close",
            onConfirm = {
                reportDetails = false
                if (mine) onClearReport()
            },
            onDismiss = { reportDetails = false },
            dismiss = if (mine) "Close" else null,
            destructive = mine,
        )
    }

    val iconGap = 12.dp

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Gutter, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(
            // Icon squared off against the three lines beside it rather than a fixed number, so
            // it grows or shrinks with whatever the tester's own text size setting makes those
            // three lines add up to.
            Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            /*
             * How long they have held on to it, on the icon it belongs to.
             *
             * This was a line of text under the package name, which is a whole row of type spent
             * on one small number, thirteen times down the screen. Play counts continuous days
             * installed and nothing else, so it is worth showing and not worth a line.
             *
             * Long press gives the sentence back. A corner badge reading "2d" is a reminder for
             * somebody who already knows what it counts, and a tooltip is where the answer goes
             * for somebody who does not.
             */
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(info.streakLabel) } },
                state = rememberTooltipState(),
                // Nothing to explain where there is no badge, and a tooltip that opens on an app
                // the tester has not installed yet would be answering a question nobody asked.
                enableUserInput = info.streakBadge != null,
            ) {
                Box(
                    Modifier
                        .heightIn(min = 44.dp, max = 58.dp)
                        .fillMaxHeight()
                        .aspectRatio(1f),
                ) {
                    AppIcon(app.packageName, app.label, modifier = Modifier.fillMaxSize())
                    info.streakBadge?.let { badge ->
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 5.dp, y = 5.dp)
                                // Painted in the page ground and padded, which draws a ring
                                // cutting the badge out of the icon behind it. Without it the
                                // pill sits on whatever colour that corner of the icon happens
                                // to be.
                                .clip(RoundedCornerShape(9.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .padding(2.dp),
                        ) {
                            Text(
                                badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(Status.neutralSoft)
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(iconGap))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        /*
                         * What the developer left for you, when there is any.
                         *
                         * The row can only ever show the first two lines of it and none of it can
                         * be selected, which is the one thing a test login exists to be: copied
                         * into somebody else's sign-in screen. Shown only where notes exist, so
                         * the button is a promise that there is something behind it rather than
                         * furniture on every row.
                         */
                        if (app.notes.isNotBlank()) {
                            Spacer(Modifier.width(2.dp))
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable { details = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Lucide.Info,
                                    contentDescription = "How to get into ${app.label}",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    /*
                     * Today's answer, on the name it is about — a glyph, not a word. The row
                     * used to say it three different ways further down (a pill, a colour, an
                     * icon-only button in disguise); one mark up here, checked or not, replaces
                     * all of them.
                     */
                    Icon(
                        if (done) Lucide.SquareCheck else Lucide.Square,
                        contentDescription = if (done) "Done today" else "Not done today",
                        modifier = Modifier.size(18.dp),
                        tint =
                            when {
                                !done -> MaterialTheme.colorScheme.outline
                                manual -> Status.short
                                else -> Status.posted
                            },
                    )
                }
                /*
                 * Whose app this is, on two lines of its own.
                 *
                 * A cohort is thirteen strangers holding each other to a fortnight, and the only
                 * other thing on the row is a package name. Somebody who cannot get past a login
                 * screen needs to know who to ask, and somebody deciding whether the notes are
                 * worth trusting needs to know who wrote them.
                 *
                 * Name above address, not joined by a separator: the two ran together as
                 * "abdudevs · abdudevs@gmail.com" often enough to look broken, because Google
                 * hands back a display name for every account and for most of these it is the
                 * address with the domain taken off. Stacked, the repetition reads as one person
                 * rather than a bug, and the address gets the whole width instead of being cut
                 * in half.
                 */
                val who =
                    remember(owner, app.ownerEmail) {
                        owner?.displayName?.takeIf { it.isNotBlank() }
                            ?: app.ownerEmail.substringBefore('@')
                    }
                // Both lines always reserved, blank or not — a row missing owner data used to be
                // two lines shorter than one that had it, which is the bulk of why rows in this
                // list did not line up. A blank line here reads as nothing to say, not as a
                // layout glitch.
                Spacer(Modifier.height(3.dp))
                Text(
                    who.ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    app.ownerEmail.ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        /*
         * Every action this row could take, flush with the row's own left edge rather than
         * indented under the icon, scrolling sideways rather than wrapping so the row's height
         * never depends on how many of them apply.
         */
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                busy -> Working("Opening")

                // Ahead of everything else, including Install/Open. An upload in flight is
                // about this exact row and outranks anything the row would otherwise be saying.
                sending -> Working("Uploading")

                // A visit already served, waiting only on a picture — see
                // CaptureService.missed. Ahead of Reported: reporting an app excuses it from
                // judgment, but it does not un-happen a visit that already earned its usage
                // figure.
                canPickScreenshot -> ActionChip("Pick a screenshot", Status.short, Status.shortSoft, onPickScreenshot)

                // Tappable, not a label — the whole point is to read back what was typed, see
                // [reportDetails]. Gone once [done]: this tester just proved the app works by
                // testing it, so there is nothing current left for the report to excuse.
                app.reportedAt > 0L && !done -> ActionChip("Reported", Status.short, Status.shortSoft) { reportDetails = true }

                !info.installed -> ActionChip("Install", Status.missed, Status.missedSoft, onInstall)

                // Today's visit is banked. Open used to stay regardless, on the idea that
                // finishing today is not a reason to take away the only way into the app — but
                // mid-round that read as the row asking to be opened again right after it just
                // finished, which is exactly backwards. Tomorrow this clears with [done] itself.
                //
                // Nothing shown here at all, Done pill included: the checkbox beside the app's
                // name already says done, top-right of the same row, and repeating it here said
                // the same word twice for every finished app in the list.
                live && done -> Unit

                // Off the clock. `live` alone was not enough for this once it stayed true after
                // a run ends — a day with no run left to file a visit under has nothing here to
                // offer either way.
                live ->
                    ActionChip(
                        "Open",
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.primaryContainer,
                        onOpen,
                    )

                else ->
                    Pill(
                        if (ended) "Ended" else "Waiting",
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        Status.neutralSoft,
                    )
            }

            // The only route to a newer build once one is out — kept separate from Open/Install
            // above rather than folded into either, since checking the Store is not the same
            // errand as opening the app or installing it the first time. Shown only once one is
            // actually out: latestVersionCode only ever moves forward from the owner's own
            // Play-installed copy (HomeViewModel.syncMyVersions), so an app that has never synced
            // one, or is already current, has nothing to route to yet.
            if (info.installed && !busy && !sending && app.latestVersionCode > info.versionCode) {
                ActionChip("Update", MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft, onOpenStore)
            }

            /*
             * Available whether or not it is installed — won't-install and crashes-on-open are
             * both things only the tester staring at the error can see. Gone once reported: a
             * second report would only be refused, see firestore.rules' set-once guard, and the
             * Reported chip above already reads the first one back.
             */
            if (app.reportedAt <= 0L) {
                ActionChip(
                    "Report",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    Status.neutralSoft,
                    onReportProblem,
                )
            }

            /*
             * A note about the app, not a complaint about it — nothing here excuses the day, so
             * it stays offered whether or not [done] yet. Installed only: there's nothing to have
             * found in an app the tester hasn't opened. Muted once sent, but still tappable, so a
             * second tap gets an answer instead of silently doing nothing.
             */
            if (info.installed && app.reportedAt <= 0L) {
                if (feedbackGiven) {
                    ActionChip("Feedback", MaterialTheme.colorScheme.outline, Status.neutralSoft, onFeedbackAlreadyGiven)
                } else {
                    ActionChip("Feedback", MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft, onGiveFeedback)
                }
            }

            /*
             * Only where an admin has opened this specific app up to it — most rows never show
             * this at all. Independent of Install/Open on purpose: skipping is about what counts
             * against the day, not about hiding the app, so a tester can still install and open
             * it later even after marking it skipped, and un-skipping is the same tap in reverse.
             */
            if (app.skippable) {
                if (skipped) {
                    ActionChip("Skipped", MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft, onToggleSkip)
                } else {
                    ActionChip("Skip", MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft, onToggleSkip)
                }
            }
        }
    }
}
