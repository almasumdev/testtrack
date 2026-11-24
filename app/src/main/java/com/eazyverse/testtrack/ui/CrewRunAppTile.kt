package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.FeedbackUpload

internal enum class CrewRunAppKind { REQUIRED, OPTIONAL, LEFT }

internal data class CrewRunAppEntry(
    val app: CrewApp,
    val kind: CrewRunAppKind,
)

/** One stable list, including optional apps and installed apps that left, with no repeated seats. */
internal fun crewRunAppEntries(
    crew: Crew,
    installed: (String) -> Boolean,
): List<CrewRunAppEntry> =
    (
        crew.apps.map { CrewRunAppEntry(it, CrewRunAppKind.REQUIRED) } +
            crew.optional.map { CrewRunAppEntry(it, CrewRunAppKind.OPTIONAL) } +
            crew.left.filter { installed(it.packageName) }.map { CrewRunAppEntry(it, CrewRunAppKind.LEFT) }
    ).distinctBy { it.app.id }

/** Testing and feedback share one app tile; its header still opens the sign-in sheet. */
@Composable
internal fun CrewRunAppTile(
    app: CrewApp,
    seat: CrewSeat,
    actions: CrewActions,
    onSheet: (RowSheet) -> Unit,
    kind: CrewRunAppKind = CrewRunAppKind.REQUIRED,
    /** The reader volunteers here: reports and Play feedback stay with members. */
    volunteer: Boolean = false,
) {
    val left = kind == CrewRunAppKind.LEFT
    val reports = !left && !app.reported && !volunteer
    val largeText = LocalDensity.current.fontScale > 1.3f
    val status = crewRunAppState(app, seat, kind, actions)
    var screenshotChoice by remember(app.id) { mutableStateOf(false) }
    val feedbackLine = !volunteer && !app.feedbackSent && !app.reported && kind == CrewRunAppKind.REQUIRED && !status.feedbackAction
    // The feedback line's button keeps its 48dp touch target, which already leaves about 15dp under the
    // words, so the card adds no bottom padding of its own there: the gap stays the tile's usual 14dp.
    val padding = PaddingValues(start = 14.dp, top = 14.dp, end = 14.dp, bottom = if (feedbackLine) 0.dp else 14.dp)
    CrewCard(modifier = Modifier.testTag("crew-run-app-${app.id}"), padding = padding) {
        AppHead(
            app.packageName,
            app.label,
            seat.icon,
            live = null,
            sub = app.packageName,
            modifier =
                if (left) {
                    Modifier
                } else {
                    Modifier.clickable {
                        onSheet(RowSheet(seat, if (app.done || app.reported) SheetKind.DONE else SheetKind.TONIGHT, app))
                    }
                },
            trailing =
                if (reports && !largeText) {
                    { InlineAction("Report", leading = Lucide.Flag, enabled = !app.reported) { actions.onReport(seat) } }
                } else {
                    null
                },
            mark = 44.dp,
            markTag = "crew-app-icon-${app.id}",
            textTag = "crew-app-text-${app.id}",
        )
        if (reports && largeText) {
            InlineAction(
                "Report",
                Modifier.align(Alignment.End),
                leading = Lucide.Flag,
                enabled = !app.reported,
            ) { actions.onReport(seat) }
        }
        Spacer(Modifier.height(10.dp))
        val band = if (status.action == "Add screenshot") status.copy(run = { screenshotChoice = true }) else status
        val busy = actions.busySeat == app.id || status.busy
        StatusLine(
            band.icon,
            band.text,
            tone = band.tone,
            info = band.explanation,
            band = true,
            actions =
                if (band.action != null || band.alternate != null) {
                    {
                        band.action?.let { InlineAction(it, busy = busy, onClick = band.run) }
                        band.alternate?.let { InlineAction(it, enabled = !busy, onClick = band.runAlternate) }
                    }
                } else {
                    null
                },
        )
        if (feedbackLine) FeedbackAction(app, actions)
    }
    if (screenshotChoice) {
        CrewScreenshotChoice(app, actions) { screenshotChoice = false }
    }
}

@Composable
private fun FeedbackAction(
    app: CrewApp,
    actions: CrewActions,
) {
    val upload = actions.feedbackUpload(app.id)
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(Modifier.fillMaxWidth()) {
            FeedbackLabel(app, upload, Modifier.fillMaxWidth())
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedbackButton(app, upload, actions)
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FeedbackLabel(app, upload, Modifier.weight(1f))
        FeedbackButton(app, upload, actions)
    }
}

/** Write, busy while the picture goes up (nothing to tap), Retry when it did not go, or nothing once sent. */
@Composable
private fun FeedbackButton(
    app: CrewApp,
    upload: FeedbackUpload?,
    actions: CrewActions,
) {
    when {
        upload == null -> InlineAction("Write", enabled = actions.busySeat != app.id) { actions.onFeedback(app) }
        upload.failed -> InlineAction("Retry") { actions.onRetryFeedback(app) }
        // Nothing to tap, but the line keeps the height the button gave it, so the tile never jumps.
        upload.sent -> Spacer(Modifier.height(48.dp))
        else -> InlineAction("Write", busy = true) {}
    }
}

@Composable
private fun FeedbackLabel(
    app: CrewApp,
    upload: FeedbackUpload?,
    modifier: Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val failed = upload?.failed == true
        val sent = upload?.sent == true
        val ink =
            when {
                sent -> Tt.colors.ok
                app.feedbackLate || failed -> Tt.colors.warn
                else -> Tt.colors.text2
            }
        val icon =
            when {
                failed -> Lucide.TriangleAlert
                sent -> Lucide.CircleCheck
                upload != null -> Lucide.CloudUpload
                else -> Lucide.MessageCircle
            }
        Icon(icon, null, Modifier.size(14.dp), tint = ink)
        Spacer(Modifier.width(7.dp))
        Text(
            buildAnnotatedString {
                when {
                    failed -> append("Picture not sent")
                    sent -> append("Feedback sent")
                    upload != null -> append("Uploading picture")
                    app.feedbackLate -> append("Private feedback overdue")
                    else -> {
                        append("Private feedback ")
                        withStyle(SpanStyle(color = Tt.colors.text3)) { append("by night ${app.feedbackDueDay}") }
                    }
                }
            },
            style = TextStyles.SmallNote,
            color = ink,
        )
    }
}
