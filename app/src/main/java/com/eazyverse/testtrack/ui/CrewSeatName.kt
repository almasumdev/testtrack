package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Tag
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The name the crew sees, as a row of the seat page rather than a boxed field: a tag tile, the name
 * over what it is, and a pencil. Tapping the row edits it in place, at the same height.
 */

private val LabelStyle = tileStyle(12.5f, 14f, FontWeight.Medium)
private val LabelEditStyle = tileStyle(12.5f, 14f, FontWeight.SemiBold)
private val CountStyle = tileStyle(11.5f, 16f, FontWeight.Normal)

/**
 * The name row. Resting, it shows the name and a pencil; editing, the name is a one-line field with
 * a 2dp underline and the count at the end. Done or leaving the field saves it and goes back to
 * resting; a blank name stays open, with the app's own name as its hint. [focusAsk] goes up each
 * time the page's hint asks for the name, which puts the cursor in it.
 */
@Composable
internal fun SeatNameRow(
    name: String,
    placeholder: String,
    onRename: (String) -> Unit,
    focusAsk: Int,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    var editing by rememberSaveable { mutableStateOf(false) }
    val open = editing || name.isBlank()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var field by remember { mutableStateOf(TextFieldValue(name, TextRange(name.length))) }
    var wantFocus by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(name) {
        if (field.text != name) field = TextFieldValue(name, TextRange(name.length))
    }
    // Only a new ask focuses it, not the count it already had when the row came back on screen.
    var asked by remember { mutableIntStateOf(focusAsk) }
    LaunchedEffect(focusAsk) {
        if (focusAsk != asked) {
            asked = focusAsk
            editing = true
            wantFocus = true
        }
    }
    LaunchedEffect(open, wantFocus) {
        if (open && wantFocus) {
            wantFocus = false
            runCatching { focus.requestFocus() }
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .then(
                if (open) {
                    Modifier
                } else {
                    Modifier.clickable(onClickLabel = "Change the name") {
                        editing = true
                        wantFocus = true
                    }
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(t.tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Tag, null, Modifier.size(20.dp), tint = t.tileInk)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (open) {
                val line = t.primary
                BasicTextField(
                    value = field,
                    onValueChange = { typed ->
                        val text = typed.text.take(FeedRules.APP_NAME_MAX)
                        field = if (text == typed.text) typed else TextFieldValue(text, TextRange(text.length))
                        onRename(text)
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    hadFocus = true
                                } else if (hadFocus) {
                                    // Leaving the field saves it, trimmed, and goes back to resting.
                                    hadFocus = false
                                    onRename(field.text.trim())
                                    editing = false
                                }
                            }.drawBehind {
                                val high = 2.dp.toPx()
                                drawRect(line, Offset(0f, size.height - high), Size(size.width, high))
                            }.padding(bottom = 4.dp),
                    singleLine = true,
                    textStyle = AppTileName.copy(color = t.text),
                    cursorBrush = SolidColor(t.primary),
                    keyboardOptions =
                        KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    decorationBox = { inner ->
                        Box {
                            if (field.text.isEmpty()) {
                                Text(placeholder, style = AppTileName, color = t.text4, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            inner()
                        }
                    },
                )
                Spacer(Modifier.height(2.dp))
                Text("Name the crew sees", style = LabelEditStyle, color = t.accentInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(name, style = AppTileName, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("Name the crew sees", style = LabelStyle, color = t.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (open) {
                Text("${field.text.length}/${FeedRules.APP_NAME_MAX}", style = CountStyle, color = t.text4, maxLines = 1, softWrap = false)
            } else {
                Icon(Lucide.Pencil, null, Modifier.size(18.dp), tint = t.text4)
            }
        }
    }
}
