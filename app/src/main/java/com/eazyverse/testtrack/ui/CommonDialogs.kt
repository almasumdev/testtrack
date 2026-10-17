package com.eazyverse.testtrack.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * The one dialog shape this app uses.
 *
 * A card in the panel's own colour with the robot looking over its top right corner: its head
 * behind the card, its mitts holding the edge. Compact by design, since most of these are a
 * sentence and a choice: a 16sp title, 13.5sp words, and two short buttons at the right, the
 * answer filled and the way out outlined, both 32dp with small corners. See [DialogRobotHead].
 *
 * Destructive actions are named rather than coloured red by default. "Sign out" is not a warning,
 * and a dialog where the confirm button is always alarming teaches people to ignore the alarm, so
 * [destructive] is opt-in, for the ones that genuinely take something away.
 *
 * [body] covers the ordinary case of a sentence or two. [content] is for the few that need a field,
 * steps or an image, and both may be given: the prose comes first. A null [confirm] leaves only
 * the way out, for a dialog that is waiting on something; [looking] has the robot's eyes move
 * while it does.
 */
@Composable
fun Ask(
    title: String,
    confirm: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    body: String? = null,
    dismiss: String? = "Cancel",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    /** A mark after the confirm label, such as the arrow for one that leaves the app. */
    confirmIcon: ImageVector? = null,
    looking: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val t = Tt.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .padding(horizontal = 22.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(top = DialogRobotRise),
        ) {
            // Behind the card, so only the face shows above its edge.
            DialogRobotHead(looking, Modifier.align(Alignment.TopEnd).padding(end = 10.dp).offset(y = -DialogRobotRise))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(DialogShape)
                    .background(t.glass)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 14.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 21.sp),
                    fontWeight = FontWeight.Bold,
                    color = t.text,
                )
                body?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = DialogBody, color = t.text2)
                }
                content?.let {
                    Spacer(Modifier.height(if (body != null) 12.dp else 8.dp))
                    it()
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismiss?.let { DialogButton(it, DialogButtonKind.OUTLINE, onClick = onDismiss) }
                    confirm?.let {
                        DialogButton(
                            it,
                            if (destructive) DialogButtonKind.DANGER else DialogButtonKind.FILL,
                            enabled = confirmEnabled,
                            icon = confirmIcon,
                            onClick = onConfirm,
                        )
                    }
                }
            }
            // In front of the card: the mitts over its edge.
            DialogRobotHands(Modifier.align(Alignment.TopEnd).padding(end = 10.dp).offset(y = -DialogRobotRise))
        }
    }
}

/** What a dialog button is: the answer, the answer that takes something away, or the way out. */
enum class DialogButtonKind { FILL, DANGER, OUTLINE }

/** A dialog's button: 32dp, small corners. Also for a dialog's own extra action. */
@Composable
fun DialogButton(
    label: String,
    kind: DialogButtonKind,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    val dismissKeyboard = rememberDismissKeyboard()
    val click = {
        dismissKeyboard()
        onClick()
    }
    val shape = RoundedCornerShape(8.dp)
    val padding = PaddingValues(horizontal = 12.dp)
    val inner: @Composable RowScope.() -> Unit = {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        icon?.let {
            Spacer(Modifier.width(5.dp))
            Icon(it, null, Modifier.size(13.dp))
        }
    }
    if (kind == DialogButtonKind.OUTLINE) {
        OutlinedButton(
            onClick = click,
            enabled = enabled,
            shape = shape,
            border = BorderStroke(1.dp, t.hair),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = t.accentInk),
            contentPadding = padding,
            modifier = modifier.height(32.dp),
            content = inner,
        )
    } else {
        val fill = if (kind == DialogButtonKind.DANGER) t.bad else t.accent
        Button(
            onClick = click,
            enabled = enabled,
            shape = shape,
            elevation = null,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = fill,
                    contentColor = if (kind == DialogButtonKind.DANGER) t.onBad else t.onAccent,
                    disabledContainerColor = fill.copy(alpha = 0.45f),
                    disabledContentColor = t.onAccent.copy(alpha = 0.85f),
                ),
            contentPadding = padding,
            modifier = modifier.height(32.dp),
            content = inner,
        )
    }
}

/**
 * Numbered steps inside a dialog, one short line each. [circled] rings the numbers, for steps
 * somebody is about to go and do rather than read.
 */
@Composable
fun DialogSteps(
    steps: List<AnnotatedString>,
    circled: Boolean = false,
) {
    val t = Tt.colors
    Column(verticalArrangement = Arrangement.spacedBy(if (circled) 7.dp else 4.dp)) {
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                if (circled) {
                    // Drawn rather than set as text in a ring: a glyph sits on its baseline, not
                    // in the middle of its box, and the figure came out low and to the right.
                    val ink = t.accent
                    val figure =
                        rememberTextMeasurer().measure(
                            "${index + 1}",
                            MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = ink),
                        )
                    Canvas(Modifier.padding(top = 1.dp).size(18.dp)) {
                        drawCircle(ink, radius = size.minDimension / 2 - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                        drawText(
                            figure,
                            topLeft =
                                Offset(
                                    (size.width - figure.size.width) / 2f,
                                    (size.height - figure.size.height) / 2f,
                                ),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                } else {
                    Text(
                        "${index + 1}",
                        Modifier.widthIn(min = 12.dp),
                        style = DialogBody,
                        fontWeight = FontWeight.SemiBold,
                        color = t.text4,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(step, style = DialogBody, color = t.text2)
            }
        }
    }
}

/** A small grey line under a dialog's main words. */
@Composable
fun DialogNote(text: String) {
    Text(text, style = DialogBody.copy(fontSize = 12.sp, lineHeight = 16.sp), color = Tt.colors.text4)
}

/** The words of a dialog, in the app's own face. */
val DialogBody: TextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 19.5.sp)

private val DialogShape = RoundedCornerShape(18.dp)

/** A field inside [Ask], matching the shape the rest of the app uses for one. */
@Composable
fun DialogField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    error: String? = null,
    isError: Boolean = error != null,
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
    /**
     * What the keyboard's own key does. Search, Go and Send mean nothing without it; Done puts the
     * keyboard away and then runs it if given; Next moves to the field below unless it is given.
     */
    onImeAction: (() -> Unit)? = null,
) {
    val focus = LocalFocusManager.current
    val dismissKeyboard = rememberDismissKeyboard()
    val finish = {
        dismissKeyboard()
        onImeAction?.invoke()
        Unit
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            fontWeight = FontWeight.SemiBold,
            color = Tt.colors.text4,
        )
        Spacer(Modifier.height(4.dp))
        // The hint is in the faintest ink, well apart from typed text: a hint that looked typed was
        // being read as a value already filled in.
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder =
                placeholder?.let {
                    { Text(it, style = DialogBody, color = Tt.colors.text4.copy(alpha = 0.8f)) }
                },
            singleLine = singleLine,
            isError = isError,
            textStyle = DialogBody.copy(color = Tt.colors.text),
            shape = RoundedCornerShape(8.dp),
            // A keyboard that says Done and inserts a newline is lying about its own button, so a
            // field taking more than one line does not get to name the action.
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = if (singleLine) imeAction else ImeAction.Default,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = { finish() },
                    onSearch = { finish() },
                    onGo = { finish() },
                    onSend = { finish() },
                    onNext = { onImeAction?.invoke() ?: focus.moveFocus(FocusDirection.Down) },
                ),
            modifier = Modifier.fillMaxWidth().heightIn(min = minHeight),
        )
        // Ours rather than the field's own supporting slot, which reserves the height whether or
        // not anything is in it and pushes whatever follows down over nothing.
        if (error != null || supporting != null || maxLength != null) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    (error ?: supporting).orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
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
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
