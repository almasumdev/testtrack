package com.eazyverse.testtrack.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * An outlined field whose label sits inside while it is empty and moves up into a notch in the top
 * border once it has focus or a value. 48dp tall rather than [DialogField]'s 56, which is why it is
 * built on [BasicTextField] and the outlined decoration instead of OutlinedTextField and its floor.
 *
 * Under the box, only when there is something to say: the error at the start, and "used/max" at the
 * end for a field with a [maxLength]. Like [DialogField], the cap is the caller's to enforce.
 *
 * A [trailing] action, such as an X that removes the field, sits in the box's top end corner and
 * the text keeps clear of it. A field that is not [enabled] takes no typing and is drawn faded.
 */
@Composable
internal fun OutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    /** Only for a multi-line box: it starts this tall, grows to [maxLines], then scrolls inside. */
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    /** Hidden behind dots, with an eye to show it. */
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLength: Int? = null,
    enabled: Boolean = true,
    /** A 36dp action in the top end corner of the box. */
    trailing: (@Composable () -> Unit)? = null,
) {
    val t = Tt.colors
    val focus = LocalFocusManager.current
    val dismissKeyboard = rememberDismissKeyboard()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var shown by rememberSaveable { mutableStateOf(false) }
    val isError = error != null
    val transformation =
        if (password && !shown) PasswordVisualTransformation() else VisualTransformation.None
    val colors =
        OutlinedTextFieldDefaults.colors(
            focusedTextColor = t.text,
            unfocusedTextColor = t.text,
            errorTextColor = t.text,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            cursorColor = t.primary,
            errorCursorColor = t.primary,
            focusedBorderColor = t.primary,
            unfocusedBorderColor = t.glassBorderStrong,
            errorBorderColor = t.bad,
            focusedLabelColor = t.accentInk,
            unfocusedLabelColor = t.text3,
            errorLabelColor = t.bad,
            focusedPlaceholderColor = t.text4,
            unfocusedPlaceholderColor = t.text4,
            errorPlaceholderColor = t.text4,
            focusedTrailingIconColor = t.text4,
            unfocusedTrailingIconColor = t.text4,
            errorTrailingIconColor = t.text4,
        )
    // The decoration grows the label from bodySmall (in the notch) to bodyLarge (inside the box), so
    // those two are what set its sizes here: 12/16 SemiBold up, 15/20 down.
    val typography = MaterialTheme.typography
    val fieldTypography =
        typography.copy(
            bodyLarge = typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
            bodySmall = typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
        )
    val labelDown = !focused && value.isEmpty()
    Column(modifier.fillMaxWidth().then(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA))) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            textStyle = typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = t.text),
            cursorBrush = SolidColor(t.primary),
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            visualTransformation = transformation,
            interactionSource = interaction,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = capitalization,
                    keyboardType = if (password) KeyboardType.Password else keyboardType,
                    imeAction = imeAction,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = { dismissKeyboard() },
                    onNext = { focus.moveFocus(FocusDirection.Down) },
                ),
            decorationBox = { inner ->
                Box {
                    MaterialTheme(typography = fieldTypography) {
                        OutlinedTextFieldDefaults.DecorationBox(
                            value = value,
                            innerTextField = inner,
                            enabled = true,
                            singleLine = singleLine,
                            visualTransformation = transformation,
                            interactionSource = interaction,
                            isError = isError,
                            // Down in the box it is the faintest ink, the hint; up in the notch the label's.
                            label = {
                                Text(
                                    label,
                                    color =
                                        when {
                                            isError -> t.bad
                                            labelDown -> t.text4
                                            focused -> t.accentInk
                                            else -> t.text3
                                        },
                                )
                            },
                            placeholder = placeholder?.let { { Text(it, color = t.text4) } },
                            trailingIcon =
                                if (password) {
                                    {
                                        IconButton(onClick = { shown = !shown }, modifier = Modifier.size(40.dp)) {
                                            Icon(
                                                if (shown) Lucide.EyeOff else Lucide.Eye,
                                                contentDescription = if (shown) "Hide password" else "Show password",
                                                modifier = Modifier.size(20.dp),
                                                tint = t.text4,
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                            colors = colors,
                            contentPadding =
                                OutlinedTextFieldDefaults.contentPadding(
                                    start = 14.dp,
                                    end = if (trailing != null) TrailingRoom else 4.dp,
                                    top = 12.dp,
                                    bottom = 12.dp,
                                ),
                            container = {
                                OutlinedTextFieldDefaults.Container(
                                    enabled = true,
                                    isError = isError,
                                    interactionSource = interaction,
                                    colors = colors,
                                    shape = RoundedCornerShape(10.dp),
                                    focusedBorderThickness = 2.dp,
                                    unfocusedBorderThickness = 1.dp,
                                )
                            },
                        )
                    }
                    if (trailing != null) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 4.dp).size(36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            trailing()
                        }
                    }
                }
            },
        )
        if (error != null || maxLength != null) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (error != null) {
                    IconLine(
                        Lucide.CircleAlert,
                        t.bad,
                        AnnotatedString(error),
                        typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
                        t.bad,
                        Modifier.weight(1f),
                        iconSize = 13.dp,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (maxLength != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${value.length}/$maxLength",
                        style = typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 14.sp),
                        color = t.text4,
                    )
                }
            }
        }
    }
}

/** What a [trailing] action keeps clear at the box's end: its 36dp and 4dp on either side. */
private val TrailingRoom = 40.dp

/** How faded a field is while it takes no typing, as the page's other controls are. */
internal const val DISABLED_ALPHA = 0.45f
