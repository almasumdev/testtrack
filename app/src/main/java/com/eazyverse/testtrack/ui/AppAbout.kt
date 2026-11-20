package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AskFailure

/*
 * What an app is, in Play's two parts: read from its listing when Play has one, written by its
 * owner when it was picked from the phone, and changed later from the app's page.
 */

private val ShortStyle = tileStyle(15f, 21f, FontWeight.Medium)
private val FullStyle = tileStyle(14f, 20f, FontWeight.Normal)

/**
 * About the app as Play has it: both texts in full, nothing cut, the full one in its own
 * paragraphs. Nothing at all when Play gave neither.
 */
@Composable
internal fun PlayAbout(
    short: String,
    full: String,
) {
    if (short.isBlank() && full.isBlank()) return
    val t = Tt.colors
    SectionHeading("About the app", tag = "From Play", top = 24.dp, tagBeside = true)
    if (short.isNotBlank()) {
        SmallHeading("Short description", top = 0.dp, bottom = 4.dp)
        Text(short.trim(), style = ShortStyle, color = t.text)
    }
    if (full.isNotBlank()) {
        SmallHeading("Full description", top = if (short.isNotBlank()) 16.dp else 0.dp, bottom = 4.dp)
        paragraphs(full).forEachIndexed { index, paragraph ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            Text(paragraph, style = FullStyle, color = t.text)
        }
    }
}

/** The full description's paragraphs: split at blank lines, single line breaks kept inside them. */
internal fun paragraphs(text: String): List<String> =
    text
        .trim()
        .split(Regex("\\n\\s*\\n"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

/**
 * The two descriptions as fields: the short one on one line that wraps as it grows, the full one
 * three lines at first and growing with the text, never a box that scrolls inside.
 */
@Composable
internal fun AboutFields(
    short: String,
    onShort: (String) -> Unit,
    full: String,
    onFull: (String) -> Unit,
    enabled: Boolean = true,
    /** Run after the full description is typed to its very end, for a page to scroll there. */
    onFullEndEdit: (() -> Unit)? = null,
) {
    OutlinedField(
        value = short,
        onValueChange = onShort,
        label = "Short description",
        singleLine = false,
        maxLength = FeedRules.SHORT_DESC_MAX,
        capitalization = KeyboardCapitalization.Sentences,
        imeAction = ImeAction.Next,
        enabled = enabled,
    )
    Spacer(Modifier.height(14.dp))
    OutlinedField(
        value = full,
        onValueChange = onFull,
        label = "Full description",
        singleLine = false,
        minLines = 3,
        // No cap: it grows with the text and the page scrolls.
        maxLines = Int.MAX_VALUE,
        onEndEdit = onFullEndEdit,
        maxLength = FeedRules.FULL_DESC_MAX,
        capitalization = KeyboardCapitalization.Sentences,
        // A new line, not Done: the full description has paragraphs.
        imeAction = ImeAction.Default,
        enabled = enabled,
    )
}

/**
 * The app page's Edit details: the name and both descriptions in one sheet, and Save. [onSave]
 * gets what to save and what to run once it is saved, which closes the sheet. [error] is why the
 * last save did not happen.
 */
@Composable
internal fun AppDetailsSheet(
    app: AppEntry,
    busy: Boolean,
    error: String?,
    onSave: (name: String, short: String, full: String, saved: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(app.id) { mutableStateOf(app.name) }
    var short by rememberSaveable(app.id) { mutableStateOf(app.shortDescription) }
    var full by rememberSaveable(app.id) { mutableStateOf(app.fullDescription) }
    // A name from Play can be longer than one typed here; it is kept, not cut, until it is edited.
    val nameMax = maxOf(FeedRules.APP_NAME_MAX, app.name.length)
    val changed = name.trim() != app.name || short.trim() != app.shortDescription || full.trim() != app.fullDescription
    PickerSheet(title = "Edit details", onDismiss = onDismiss, skipHalf = true, fitMax = 0.92f) { close ->
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(6.dp))
            OutlinedField(
                value = name,
                onValueChange = { name = it.take(nameMax) },
                label = "Name",
                maxLength = FeedRules.APP_NAME_MAX,
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
                enabled = !busy,
            )
            Spacer(Modifier.height(14.dp))
            AboutFields(
                short = short,
                onShort = { short = it.replace('\n', ' ').take(FeedRules.SHORT_DESC_MAX) },
                full = full,
                onFull = { full = it.take(FeedRules.FULL_DESC_MAX) },
                enabled = !busy,
            )
            error?.let {
                Spacer(Modifier.height(12.dp))
                FailureLine(AskFailure("Couldn't save", it))
            }
        }
        Spacer(Modifier.height(18.dp))
        Primary("Save", busy = busy, enabled = name.isNotBlank() && changed) {
            onSave(name.trim(), short.trim(), full.trim()) { close {} }
        }
        Spacer(Modifier.height(16.dp))
    }
}
