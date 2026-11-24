package com.eazyverse.testtrack.ui

import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.ImageUp
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Text
import com.eazyverse.testtrack.domain.ChatThread
import com.eazyverse.testtrack.domain.clockTime
import com.eazyverse.testtrack.domain.instantOf
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.ProofEditing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The capture half of the review: the one-time guide, the ask for "Display over other apps", and
 * step four, the Send page: the picture box, the text with its count, and the developer's messages.
 */

/**
 * How the Google Play step works, shown once: the first time somebody taps "Open Google Play".
 * Saying it before Play opens is the difference between a button they expect and one that
 * appears from nowhere over somebody else's app.
 */
@Composable
fun ReviewGuideDialog(
    live: Boolean,
    onOk: () -> Unit,
    onDismiss: () -> Unit,
) {
    Ask(
        title = if (live) "Review it on Google Play" else "Send feedback on Google Play",
        note = "Android asks once to capture the screen. Drag the button aside if it's in the way.",
        confirm = "Got it",
        dismiss = null,
        onConfirm = onOk,
        onDismiss = onDismiss,
    ) {
        DialogSteps(
            (
                if (live) {
                    listOf(
                        "The app's Play page opens.",
                        "Rate it and write your review.",
                        "Once posted, tap the blue camera button.",
                    )
                } else {
                    listOf(
                        "The app's Play page opens.",
                        "Under testing, send the developer private feedback.",
                        "Once sent, tap the blue camera button.",
                    )
                }
            ).map { AnnotatedString(it) },
        )
    }
}

/** The camera button floats over Play, which needs the one grant Android only gives in Settings. */
@Composable
fun OverlayPermissionDialog(
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Ask(
        title = "Allow the camera button",
        icon = Lucide.Layers,
        body = "It floats over Google Play. Turn on Display over other apps, then come back.",
        confirm = "Open settings",
        onConfirm = onOpen,
        onDismiss = onDismiss,
    )
}

/** The review screenshot's tools: crop and turn, and boxes over anything personal. */
private val ProofTools = setOf(PictureTool.CROP, PictureTool.HIDE)

/**
 * The screenshot in the picture editor before it is used: crop it and hide anything personal. The
 * edited picture is written by [ProofEditing.save] and its file goes to [onKeep], which returns once
 * it is kept (the editor waits for that) and throws when it could not be. Closing without using it
 * keeps the picture as it was. The button and failure words default to a review screenshot's; another use passes its own.
 */
@Composable
internal fun ProofPictureEditor(
    uri: Uri,
    onKeep: suspend (Uri) -> Unit,
    onClose: () -> Unit,
    primaryLabel: String = "Use screenshot",
    failText: String = "The edited screenshot could not be saved. Try again.",
) {
    val context = LocalContext.current
    PictureEditor(
        source = uri,
        tools = ProofTools,
        primaryLabel = primaryLabel,
        failText = failText,
        unreadable = "That image could not be opened. Choose another screenshot.",
        write = { ProofEditing.save(context, it) },
        onDone = onKeep,
        onClose = onClose,
    )
}

/**
 * Step four, the Send page under the route. A new screenshot asked for starts with what was sent
 * and the developer's ask; no review box starts with its half-pay note. Then the picture box, the
 * text with its count (not when only a new screenshot is asked for), who sees the picture, and
 * what the developer asked, as their own messages.
 */
@Composable
internal fun SendProof(
    job: ReviewJob,
    shown: Uri?,
    note: String,
    onNote: (String) -> Unit,
    onCapture: () -> Unit,
    /** Null where the picture must be taken now, on Play, rather than picked. */
    onGallery: (() -> Unit)?,
    onRemove: () -> Unit,
    /** Opens the editor that covers anything personal. */
    onEdit: (Uri) -> Unit,
    /** What is happening to the picture right now, if anything. */
    working: String? = null,
    /** The picture is going to TestTrack: [working] only dims it, the Send button shows the progress. */
    sending: Boolean = false,
) {
    val rechecking = job.rechecking
    val updating = job.updating
    val half = job.noBox && !rechecking && !updating
    val developer = ThreadApp(job.post.packageName, job.post.appName, job.post.icon)
    Column(Modifier.fillMaxWidth()) {
        if (updating) {
            Thread {
                ThreadGroup(
                    listOf(ThreadPart.Picture(job.review.proofRef, width = 110.dp, height = 150.dp)),
                    side = ThreadSide.RIGHT,
                    time = "What you sent",
                )
            }
        } else if (rechecking || half) {
            Thread {
                if (rechecking) RecheckAsked(job, developer)
                if (half) {
                    val pays = "${job.reward} tix"
                    ThreadNote(
                        noteText("No review box, half pay"),
                        NoteTone.INFO,
                        Lucide.Coins,
                        more =
                            strong(
                                "Play shows you no review box, so you send it here. It pays $pays and needs ${job.needsChars}+ characters.",
                                pays,
                            ),
                        moreTitle = "Half pay",
                    )
                }
            }
        }
        SmallHeading(if (rechecking || updating) "Your new screenshot" else "Your review", info = proofInfo(job))
        ProofBox(
            picture = shown,
            onCapture = onCapture,
            onGallery = onGallery,
            onRemove = onRemove,
            onHideDetails = { shown?.let(onEdit) },
            onRetake = onCapture,
            onOpen = shown?.let { picture -> { onEdit(picture) } },
            working = working,
            dimOnly = sending,
        )
        if (!rechecking) {
            Spacer(Modifier.height(20.dp))
            SendNote(job, note, onNote)
        }
        Spacer(Modifier.height(10.dp))
        if (updating) {
            InfoLine(
                "You can send one update.",
                icon = Lucide.ImageUp,
                info = "The developer sees both screenshots. If they refuse it, you can still answer once.",
                infoTitle = "One update",
                infoLook = InfoDotLook.ACCENT,
            )
        } else {
            InfoLine(
                "The developer sees this picture. Hide anything personal.",
                icon = Lucide.EyeOff,
                info = "If they dispute it, ${FeedRules.JURORS.words()} members and the TestTrack team see it too.",
                infoTitle = "Who sees it",
                infoLook = InfoDotLook.ACCENT,
            )
        }
        if (!rechecking && !updating && !job.noBox) ProveAsks(job, developer)
    }
}

/** What the picture has to show, behind the heading's (i). */
private fun proofInfo(job: ReviewJob): String =
    when {
        job.updating && job.live && !job.noBox -> "Take it once your review shows on Google Play."
        job.rechecking -> "Take it now, on Google Play. A picture from the gallery won't do."
        job.noBox -> "A screenshot of the app's page on Google Play, with no review box on it."
        job.live -> "A screenshot of your rating and review on Google Play."
        else -> "A screenshot of the private feedback you sent on Google Play."
    }

/** The text that goes with the picture: its count under it, and the ready review one tap away. */
@Composable
private fun SendNote(
    job: ReviewJob,
    note: String,
    onNote: (String) -> Unit,
) {
    val t = Tt.colors
    val needs = job.needsChars
    val max = if (needs > 0) NOTE_MAX else OPTIONAL_NOTE_MAX
    val have = plainLength(note)
    OutlinedField(
        value = note,
        onValueChange = { onNote(it.take(max)) },
        label = proofLabel(job),
        placeholder = proofHint(job),
        singleLine = false,
        minLines = 3,
        maxLines = 6,
        imeAction = ImeAction.Default,
        maxLength = max,
        hint =
            when {
                needs <= 0 -> null
                have >= needs -> "$have characters, enough"
                else -> "$have of $needs characters"
            },
        hintTone =
            when {
                needs <= 0 -> null
                have >= needs -> t.ok
                have > 0 -> t.warn
                else -> t.text3
            },
    )
    val ready = if (job.updating) null else readyReview(job)
    if (ready != null && note.trim() != ready.trim()) {
        Spacer(Modifier.height(2.dp))
        Box(Modifier.offset(x = (-8).dp)) {
            InlineAction("Put the ready review in", leading = Lucide.Text) { onNote(ready.take(max)) }
        }
    }
}

/** The box's label: the review's own words on a live app, feedback otherwise. */
private fun proofLabel(job: ReviewJob): String =
    when {
        job.needsChars <= 0 -> "A note for the developer (optional)"
        job.live && !job.noBox -> "Your review text"
        else -> "Your feedback for the developer"
    }

/** What goes in the box, said inside it once it has focus. */
private fun proofHint(job: ReviewJob): String? =
    when {
        job.updating -> if (job.live && !job.noBox) "My review shows on Play now." else "This one shows it."
        job.needsChars <= 0 -> null
        job.noBox -> "What worked and what to improve"
        job.live -> "The same words you posted on Play"
        else -> "The same feedback you sent on Play"
    }

/**
 * The developer could not find it on Play: the first screenshot on the right, their ask on the
 * left under the app's icon, and by when the new one has to come.
 */
@Composable
private fun RecheckAsked(
    job: ReviewJob,
    developer: ThreadApp,
) {
    val review = job.review
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val hour24 = DateFormat.is24HourFormat(LocalContext.current)
    val sent = instantOf(review.submittedAt)
    val asked = instantOf(review.checkedAt)
    sent?.let { ThreadDay(ChatThread.dayLabel(it.atZone(zone).toLocalDate(), today)) }
    if (review.proofRef.isNotBlank()) {
        ThreadGroup(
            listOf(ThreadPart.Picture(review.proofRef, width = 110.dp, height = 150.dp)),
            side = ThreadSide.RIGHT,
            time = "Your first screenshot",
        )
    }
    if (asked != null && (sent == null || !sameDay(sent, asked, zone))) {
        ThreadDay(ChatThread.dayLabel(asked.atZone(zone).toLocalDate(), today))
    }
    ThreadGroup(
        listOf(ThreadPart.Words(ASKED_FOR_NEW)),
        face = ThreadFace.APP,
        app = developer,
        time = ChatThread.clock(asked, hour24 = hour24),
    )
    job.checkBy?.let { due ->
        val by = sendBy(due, zone, hour24)
        ThreadNote(noteText("Send it by $by", by), NoteTone.WARN, Lucide.Clock)
    }
}

/** "Sat, 2:10 PM", or "Sat, 14:10" on a 24 hour phone: the day and the time a new screenshot is due. */
private fun sendBy(
    due: Instant,
    zone: ZoneId,
    hour24: Boolean,
): String = due.atZone(zone).format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())) + ", " + clockTime(due, hour24, zone)

/** What the developer said, as the reviewer reads it, when asking for a new screenshot. */
internal const val ASKED_FOR_NEW = "I can't find your review on Play. Please send a new screenshot."

private const val NOTE_MAX = 600

/** An optional note is a line or two for the developer, not a review. */
private const val OPTIONAL_NOTE_MAX = 280
