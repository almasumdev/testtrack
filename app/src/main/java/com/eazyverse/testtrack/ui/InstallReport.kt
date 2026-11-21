package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.Config
import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.domain.pluralOf

/*
 * An app a tester cannot install, said once and in a form the developer can act on.
 *
 * The reasons are the server's own codes, so they are the same list in the app, the admin panel
 * and the developer's message. "Something else" asks for a sentence instead. Two reports from
 * different testers take the request off the feed until its owner fixes it and relists it.
 */

/**
 * The reasons, as the server takes them, in the words the tester picks from. The server still
 * takes the old "not_available", which this list no longer offers; what the owner reads comes
 * with its words from the server, so old reports keep reading as they did.
 */
val installReasons: List<Pair<String, String>> =
    listOf(
        "not_in_group" to "The testers link doesn't let me in",
        "no_install_button" to "I joined, but Play shows no Install button",
        "not_in_country" to "Not available in my country",
        "not_for_phone" to "Not available for my phone",
        "not_found" to "Play says the app can't be found",
        "device" to "Needs a newer Android version",
        "stuck_install" to "Install is greyed out or stuck on pending",
        "crashes" to "It installs but crashes when it opens",
        "sign_in" to "The sign-in details don't work",
        "other" to "Something else",
    )

private const val NOTE_MIN = 10
private const val NOTE_MAX = 280

/** Pick why it would not install; "Something else" asks for what happened in the tester's words. */
@Composable
fun InstallReportDialog(
    appName: String,
    onSend: (reason: String, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    val other = reason == "other"
    val ready = reason != null && (!other || note.trim().length >= NOTE_MIN)
    Ask(
        title = "Can't install $appName?",
        body = "The developer sees why, not your name. Nothing counts against you.",
        confirm = "Send",
        confirmEnabled = ready,
        onConfirm = { reason?.let { onSend(it, if (other) note.trim() else "") } },
        onDismiss = onDismiss,
    ) {
        DropdownField("Why", installReasons, reason, onPick = { reason = it })
        if (other) {
            Spacer(Modifier.height(14.dp))
            OutlinedField(
                value = note,
                onValueChange = { note = it.take(NOTE_MAX) },
                label = "What happened",
                placeholder = "Play shows the page, but Install is greyed out",
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                maxLength = NOTE_MAX,
                imeAction = ImeAction.Default,
            )
        }
    }
}

/** On the owner's request: why testers could not install it, and what that did. */
@Composable
internal fun InstallReports(request: Request) {
    val count = request.reports.size
    val text =
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(
                    if (request.unlisted) {
                        "Off Circle: $count testers couldn't install it"
                    } else {
                        "$count ${pluralOf(count, "tester")} couldn't install it"
                    },
                )
            }
            request.reports.forEach { r ->
                append("\n")
                append(if (r.note.isBlank()) r.label else "${r.label}: ${r.note}")
            }
            append("\n")
            append(
                if (request.unlisted) {
                    "Check that ${Config.GROUP_EMAIL} is on your closed track's testers and a release is rolled out, then relist it."
                } else {
                    "Two reports take it off Circle until you fix it and relist it."
                },
            )
        }
    InfoLine(text, tone = if (request.unlisted) Tone.BAD else Tone.WARN, icon = Lucide.TriangleAlert)
}
