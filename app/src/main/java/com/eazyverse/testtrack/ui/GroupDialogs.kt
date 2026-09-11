package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.data.*

/**
 * The way into an app, written by the person who has it and read by everyone who needs it.
 *
 * Not destructive, and an empty field saved is a real answer rather than a slip to guard against:
 * an app that needed a test account last week may not need one now, and clearing the field is the
 * only way to take a stale login down once it stops working.
 */
@Composable
fun NotesDialog(
    app: TestApp,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(app.packageName) { mutableStateOf(app.notes) }

    Ask(
        title = "Notes for your testers",
        confirm = "Save",
        onConfirm = { onSave(text.trim()) },
        onDismiss = onDismiss,
        content = {
            DialogField(
                value = text,
                // Held to the cap as they type rather than checked on save, because the limit is
                // the document's. Refusing it afterwards would throw away whatever they had
                // already written past the end.
                onValueChange = { text = it.take(NOTES_MAX) },
                label = "Notes",
                placeholder = "Test account: demo@example.com / pass1234",
                supporting =
                    "Everyone in your crew and the admins can read this. Use a " +
                        "throwaway account, never your real password.",
                singleLine = false,
                // The same floor the other input dialogs give it, so a field asking for a
                // sign-in note reads as wanting a couple of lines rather than one word.
                minHeight = 120.dp,
                maxLength = NOTES_MAX,
            )
        },
    )
}

/**
 * What the developer wrote down so you could get past their front door.
 *
 * Whose app it is stays on the row, where it is read; this is only the part that gets *used*. A
 * test login is typed into another app's sign-in screen, and it was on the row until now in the
 * worst possible form: cut off after two lines, and not selectable, which is the one thing a
 * password is for.
 *
 * Selectable, not editable. A text field would offer a keyboard and a cursor for text nobody here
 * is allowed to change, and a value that looks editable gets typed into anyway. Long press picks
 * it up, which is the gesture people already use on a message.
 */
@Composable
fun AppDetails(
    app: TestApp,
    onDismiss: () -> Unit,
) {
    Ask(
        title = app.label,
        confirm = "Close",
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        // Nothing is being decided here, so a Cancel next to Close would be two words for one door.
        dismiss = null,
        content = {
            // No label over it. The dialog is titled with the app and holds one thing, so a
            // heading would only be a word explaining the only word on screen.
            SelectionContainer {
                Text(
                    app.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // Notes run to 500 characters and the dialog would otherwise grow until
                            // the buttons went off the bottom of the screen.
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                            .clip(RoundedCornerShape(10.dp))
                            .background(Status.neutralSoft)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        },
    )
}
