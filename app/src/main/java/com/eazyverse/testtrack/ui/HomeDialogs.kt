package com.eazyverse.testtrack.ui

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.data.*

/**
 * The way into an app, written before anybody has been asked to open it.
 *
 * Not destructive, and an empty field saved is a real answer rather than a slip to guard against:
 * clearing the field is the only way to take down a login that has stopped working.
 */
@Composable
fun HomeNotesDialog(
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
                    "Anything they need to get past the front door. Everyone in your " +
                        "crew and the admins can read this, so use a throwaway account, never your " +
                        "own password.",
                singleLine = false,
                // The same floor the submit form gives it. A field asking for a sign-in note is
                // asking for a couple of lines, and one line high says it wants a word.
                minHeight = 96.dp,
            )
        },
    )
}
