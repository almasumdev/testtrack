package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.Config
import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * An app a tester cannot install, said once and in a form the developer can act on.
 *
 * The reasons are the server's own codes, so they are the same list in the app, the admin panel
 * and the developer's message. "Something else" asks for a sentence instead. Two reports from
 * different testers take the request off the feed until its owner fixes it and relists it.
 */

/** The reasons, as the server takes them, in the words the tester picks from. */
val installReasons: List<Pair<String, String>> =
    listOf(
        "not_in_group" to "The testers link doesn't let me in",
        "not_available" to "Play says it isn't available for my phone or country",
        "not_found" to "Play says the app can't be found",
        "device" to "It needs a newer Android or another kind of phone",
        "crashes" to "It installs but crashes when it opens",
        "other" to "Something else",
    )

private const val NOTE_MIN = 10
private const val NOTE_MAX = 280

/** The quiet way into a report, beside giving the review up. */
@Composable
internal fun ReportLink(onReport: () -> Unit) {
    Text(
        "Can't install it? Report it",
        Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onReport)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
        color = Tt.colors.text3,
        textDecoration = TextDecoration.Underline,
    )
}

/** Pick why it would not install; "Something else" asks for the reason in the tester's words. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallReportDialog(
    appName: String,
    onSend: (reason: String, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val other = reason == "other"
    val ready = reason != null && (!other || note.trim().length >= NOTE_MIN)
    Ask(
        title = "Can't install $appName?",
        body =
            "The developer is told why, without your name. The slot goes back and nothing counts against you. " +
                "When two testers report it, it comes off Circle until the developer fixes it.",
        confirm = "Send report",
        confirmEnabled = ready,
        onConfirm = { reason?.let { onSend(it, note.trim()) } },
        onDismiss = onDismiss,
        content = {
            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                OutlinedTextField(
                    value = installReasons.firstOrNull { it.first == reason }?.second ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("What went wrong") },
                    placeholder = { Text("Choose one") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    installReasons.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                reason = code
                                open = false
                            },
                        )
                    }
                }
            }
            if (other) {
                Spacer(Modifier.height(12.dp))
                DialogField(
                    value = note,
                    onValueChange = { note = it.take(NOTE_MAX) },
                    label = "Tell the developer what happened",
                    placeholder = "Play shows the page, but Install is greyed out",
                    singleLine = false,
                    minHeight = 72.dp,
                    maxLength = NOTE_MAX,
                    error = if (note.isNotBlank() && note.trim().length < NOTE_MIN) "A little more, so they know what to fix." else null,
                )
            }
        },
    )
}

/** On the owner's request: why testers could not install it, and what that did. */
@Composable
internal fun InstallReports(request: Request) {
    Notice(if (request.unlisted) Status.missed else Status.short, Lucide.TriangleAlert) {
        Text(
            if (request.unlisted) {
                "Off Circle: ${request.reports.size} testers couldn't install it"
            } else if (request.reports.size == 1) {
                "1 tester couldn't install it"
            } else {
                "${request.reports.size} testers couldn't install it"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        request.reports.forEach { r ->
            Text(
                if (r.note.isBlank()) r.label else "${r.label}: ${r.note}",
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            if (request.unlisted) {
                "Check that ${Config.GROUP_EMAIL} is on your closed track's testers and a release is rolled out, then relist it."
            } else {
                "Two reports take it off Circle until you fix it and relist it."
            },
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.text3,
        )
    }
}
