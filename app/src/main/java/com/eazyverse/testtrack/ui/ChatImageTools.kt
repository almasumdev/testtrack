package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Send
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The picture editor's controls: the round buttons over the picture, the pen's colours and size,
 * the crop's shapes, typing words onto the picture, and the caption with its send button.
 */

/** A round button on the picture: on [active] it turns white with dark ink, as a tool in use does. */
@Composable
internal fun EditorButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    val ink =
        when {
            active -> t.photo
            enabled -> t.onPhoto
            else -> t.onPhoto.copy(alpha = 0.35f)
        }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) t.onPhoto else t.photoScrim)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, Modifier.size(21.dp), tint = ink) }
}

/** The colours to pick from, in a row; the picked one is ringed and a size bigger. */
@Composable
internal fun PenColours(
    picked: Color,
    onPick: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        t.pens.forEach { colour ->
            val on = colour == picked
            Box(
                Modifier
                    .size(if (on) 32.dp else 26.dp)
                    .clip(CircleShape)
                    .background(colour)
                    .border(if (on) 3.dp else 1.5.dp, t.onPhoto, CircleShape)
                    .clickable(onClickLabel = "Pick this colour") { onPick(colour) },
            )
        }
    }
}

/** While drawing: the colours, and how thick the pen is, with a dot that shows it. */
@Composable
internal fun PenBar(
    pen: Color,
    width: Float,
    onPen: (Color) -> Unit,
    onWidth: (Float) -> Unit,
) {
    val t = Tt.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        PenColours(pen, onPen, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width.dp.coerceAtMost(30.dp)).clip(CircleShape).background(pen))
            }
            Spacer(Modifier.width(8.dp))
            Slider(
                value = width,
                onValueChange = onWidth,
                valueRange = PEN_MIN..PEN_MAX,
                modifier = Modifier.weight(1f),
                colors =
                    SliderDefaults.colors(
                        thumbColor = t.onPhoto,
                        activeTrackColor = t.onPhoto,
                        inactiveTrackColor = t.onPhoto.copy(alpha = 0.3f),
                    ),
            )
        }
    }
}

/** The pen's thinnest and thickest, in dp on the screen. */
internal const val PEN_MIN = 2f
internal const val PEN_MAX = 28f

/** While cropping: turn, the crop's shapes, and done. */
@Composable
internal fun CropBar(
    shape: CropShape,
    onShape: (CropShape) -> Unit,
    onTurn: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorButton(Lucide.RotateCw, "Turn a quarter", onClick = onTurn)
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { CropShape.entries.forEach { ShapeChip(it.label, it == shape) { onShape(it) } } }
        Spacer(Modifier.width(10.dp))
        EditorButton(Lucide.Check, "Done cropping", active = true, onClick = onDone)
    }
}

@Composable
private fun ShapeChip(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (on) t.onPhoto else t.photoScrim)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (on) t.photo else t.onPhoto,
        )
    }
}

/**
 * Typing words onto the picture, over it with the picture shaded: the colours, Done, and the words
 * big in the middle. Done with nothing written takes the words away.
 */
@Composable
internal fun WordsEntry(
    text: String,
    colour: Color,
    onDone: (String, Color) -> Unit,
) {
    val t = Tt.colors
    var value by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    var ink by remember { mutableStateOf(colour) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        Modifier
            .fillMaxSize()
            .background(t.photoScrim)
            .clickable(interactionSource = null, indication = null, onClickLabel = "Done") { onDone(value.text, ink) }
            .safeDrawingPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PenColours(ink, { ink = it }, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(t.onPhoto)
                    .clickable { onDone(value.text, ink) }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) { Text("Done", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = t.photo) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                textStyle =
                    MaterialTheme.typography.headlineMedium.copy(
                        color = ink,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                cursorBrush = SolidColor(t.onPhoto),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
    }
}

/**
 * The caption under the picture, with the chat's own rules for words, and the round send button.
 * [problem] is said over it when what is written cannot go.
 */
@Composable
internal fun CaptionBar(
    value: TextFieldValue,
    onValue: (TextFieldValue) -> Unit,
    problem: String?,
    canSend: Boolean,
    busy: Boolean,
    onSend: () -> Unit,
) {
    val t = Tt.colors
    // While the picture is made ready the button stays lit and turns, and takes no more taps.
    val lit = canSend || busy
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
        if (problem != null) {
            Row(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                Icon(Lucide.CircleAlert, null, Modifier.padding(top = 1.dp).size(15.dp), tint = t.photoBad)
                Spacer(Modifier.width(6.dp))
                Text(problem, style = MaterialTheme.typography.bodySmall, color = t.photoBad)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = value,
                onValueChange = onValue,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = t.onPhoto, fontSize = 15.sp, lineHeight = 20.sp),
                cursorBrush = SolidColor(t.onPhoto),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(t.onPhoto.copy(alpha = 0.14f))
                            .border(1.dp, if (problem != null) t.photoBad else t.onPhoto.copy(alpha = 0f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                    ) {
                        if (value.text.isEmpty()) {
                            Text(
                                "Add a caption...",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
                                color = t.onPhoto.copy(alpha = 0.6f),
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (lit) t.accent else t.onPhoto.copy(alpha = 0.18f))
                    .clickable(enabled = canSend && !busy, onClickLabel = "Send", onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.onAccent)
                } else {
                    Icon(
                        Lucide.Send,
                        "Send",
                        Modifier.size(20.dp),
                        tint = if (canSend) t.onAccent else t.onPhoto.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}
