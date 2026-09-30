package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * The one dialog shape this app uses.
 *
 * Material's default container is `surfaceContainerHigh`, a grey nothing else here is painted in,
 * so every dialog arrived looking like a piece of another product dropped on top of this one. This
 * takes the panel's own surface and corner, which is what the rest of the app is built from.
 *
 * Destructive actions are named rather than coloured red by default. "Sign out" is not a warning,
 * and a dialog where the confirm button is always alarming teaches people to ignore the alarm — so
 * [destructive] is opt-in, for the ones that genuinely take something away.
 *
 * [body] covers the ordinary case of a sentence or two. [content] is for the few that need a field
 * or an image, and both may be given: the prose comes first.
 */
@Composable
fun Ask(
    title: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    body: String? = null,
    dismiss: String? = "Cancel",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(26.dp),
        containerColor = Tt.colors.glass,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), fontWeight = FontWeight.Bold)
        },
        text =
            if (body == null && content == null) {
                null
            } else {
                {
                    Column {
                        body?.let { Text(it, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp)) }
                        content?.let {
                            if (body != null) Spacer(Modifier.height(14.dp))
                            it()
                        }
                    }
                }
            },
        // Pills, as Value Guard's dialogs have them: the answer filled, in red when it takes
        // something away, and the way out a soft one beside it.
        confirmButton = {
            val t = Tt.colors
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                shape = RoundedCornerShape(percent = 50),
                elevation = null,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = if (destructive) t.bad else t.accent,
                        contentColor = if (destructive) t.onBad else t.onAccent,
                    ),
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Text(confirm, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton =
            dismiss?.let {
                {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(percent = 50),
                        elevation = null,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Tt.colors.glass2,
                                contentColor = Tt.colors.text,
                            ),
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Text(it, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
    )
}

/** A field inside [Ask], matching the shape the rest of the app uses for one. */
@Composable
fun DialogField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    error: String? = null,
    supporting: String? = null,
    singleLine: Boolean = true,
    /** Which key the keyboard offers. Ignored where the field takes more than one line. */
    imeAction: ImeAction = ImeAction.Done,
    /** Which keyboard. A field that only accepts digits should not open a letter one. */
    keyboardType: KeyboardType = KeyboardType.Text,
    /** A floor, for a field that expects a sentence or two rather than a word. */
    minHeight: Dp = 0.dp,
    /** The field's own cap, shown as "used/max" under it. Not enforced here — the caller already
     * has to hold typing to the limit, since this only draws what that number already is. */
    maxLength: Int? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder =
                placeholder?.let {
                    { Text(it, style = MaterialTheme.typography.bodyMedium) }
                },
            singleLine = singleLine,
            isError = error != null,
            shape = RoundedCornerShape(8.dp),
            // A keyboard that says Done and inserts a newline is lying about its own button, so a
            // field taking more than one line does not get to name the action.
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = if (singleLine) imeAction else ImeAction.Default,
                ),
            modifier = Modifier.fillMaxWidth().heightIn(min = minHeight),
        )
        // Ours rather than the field's own supporting slot, which reserves the height whether or
        // not anything is in it and pushes whatever follows down over nothing.
        if (error != null || supporting != null || maxLength != null) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    (error ?: supporting).orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.weight(1f),
                )
                if (maxLength != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${value.length}/$maxLength",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
