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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import com.eazyverse.testtrack.ui.theme.Tt

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
) {
    val left = kind == CrewRunAppKind.LEFT
    val largeText = LocalDensity.current.fontScale > 1.3f
    val status = crewRunAppState(app, seat, kind, actions)
    var screenshotChoice by remember(app.id) { mutableStateOf(false) }
    CrewCard(modifier = Modifier.testTag("crew-run-app-${app.id}"), padding = PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppHeading(
                app,
                seat,
                Modifier.weight(1f).then(
                    if (left) {
                        Modifier
                    } else {
                        Modifier.clickable {
                            onSheet(RowSheet(seat, if (app.done || app.reported) SheetKind.DONE else SheetKind.TONIGHT, app))
                        }
                    },
                ),
            )
            if (!left && !app.reported && !largeText) {
                Spacer(Modifier.width(4.dp))
                InlineAction(
                    "Report",
                    leading = Lucide.Flag,
                    enabled = !app.reported,
                ) { actions.onReport(seat) }
            }
        }
        if (!left && !app.reported && largeText) {
            InlineAction(
                "Report",
                Modifier.align(Alignment.End),
                leading = Lucide.Flag,
                enabled = !app.reported,
            ) { actions.onReport(seat) }
        }
        Spacer(Modifier.height(10.dp))
        val band = if (status.action == "Add screenshot") status.copy(run = { screenshotChoice = true }) else status
        val busy = actions.busySeat == app.id
        StatusLine(
            band.icon,
            band.text,
            tone = band.tone,
            info = band.explanation,
            band = true,
            actions =
                if (band.action != null || band.alternate != null) {
                    {
                        band.action?.let { InlineAction(it, enabled = !busy, onClick = band.run) }
                        band.alternate?.let { InlineAction(it, enabled = !busy, onClick = band.runAlternate) }
                    }
                } else {
                    null
                },
        )
        if (!app.feedbackSent && !app.reported && kind == CrewRunAppKind.REQUIRED && !status.feedbackAction) {
            FeedbackAction(app, actions)
        }
    }
    if (screenshotChoice) {
        CrewScreenshotChoice(app, actions) { screenshotChoice = false }
    }
}

@Composable
private fun AppHeading(
    app: CrewApp,
    seat: CrewSeat,
    modifier: Modifier,
) {
    val side = 44.dp
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(side).testTag("crew-app-icon-${app.id}")) {
            AppMark(app.packageName, app.label, seat.icon, size = side)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).testTag("crew-app-text-${app.id}")) {
            Text(
                app.label,
                style = AppTileName,
                color = Tt.colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.packageName,
                style = tileStyle(12f, 14f, FontWeight.Normal),
                color = Tt.colors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FeedbackAction(
    app: CrewApp,
    actions: CrewActions,
) {
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(Modifier.fillMaxWidth()) {
            FeedbackLabel(app, Modifier.fillMaxWidth())
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InlineAction("Write", enabled = actions.busySeat != app.id) { actions.onFeedback(app) }
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FeedbackLabel(app, Modifier.weight(1f))
        InlineAction("Write", enabled = actions.busySeat != app.id) { actions.onFeedback(app) }
    }
}

@Composable
private fun FeedbackLabel(
    app: CrewApp,
    modifier: Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val ink = if (app.feedbackLate) Tt.colors.warn else Tt.colors.text2
        Icon(Lucide.MessageCircle, null, Modifier.size(14.dp), tint = ink)
        Spacer(Modifier.width(7.dp))
        Text(
            buildAnnotatedString {
                append("Private feedback")
                withStyle(SpanStyle(color = if (app.feedbackLate) ink else Tt.colors.text3)) {
                    append(if (app.feedbackLate) " · Overdue" else " · By night ${app.feedbackDueDay}")
                }
            },
            style = tileStyle(12f, 17f, FontWeight.Normal),
            color = ink,
        )
    }
}
