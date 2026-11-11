package com.eazyverse.testtrack.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.launch

/** The title's line height, which a mark beside the title is sized from so it scales with the font. */
internal val PickerTitleLineHeight = 22.sp

/**
 * The one frame every picker sheet in the app is drawn in: the sheet surface with top corners of
 * 22, a 38 by 4 handle, the title, and the body under it, never wider than 560.
 *
 * [tall] is for a long list with search: the sheet takes 82% of the screen at once, rather than
 * growing and shrinking as the search narrows the list. [skipHalf] opens a short sheet fully,
 * with no half-open stop. [leading] sits before the title, such as the app a sheet is about.
 * [content] gets `close`, which hides the sheet first and then runs what it is given, since a
 * sheet removed while open vanishes on one frame.
 *
 * The rest are for the one sheet that needs a different look or a lock, and every one defaults to
 * what the other sheets use. [containerColor] and [topLine] (a hairline along the top edge) change
 * the surface; [handleGap] and [headerGap] are the space under the handle and under the title row;
 * [titleLiveRegion] makes a screen reader read the title again when it changes. [dismissible]
 * false holds the sheet open: no swipe down, no tap outside, no Back. The caller says when to let go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PickerSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    tall: Boolean = false,
    skipHalf: Boolean = tall,
    leading: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    topLine: Color? = null,
    handleGap: Dp = 14.dp,
    headerGap: Dp = 14.dp,
    titleLiveRegion: Boolean = false,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.(close: (() -> Unit) -> Unit) -> Unit,
) {
    // Read when asked rather than when the state was made, so letting go of the lock takes effect
    // on the sheet that is already open.
    val canDismiss by rememberUpdatedState(dismissible)
    val state =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = skipHalf,
            confirmValueChange = { it != SheetValue.Hidden || canDismiss },
        )
    val scope = rememberCoroutineScope()
    val close: (() -> Unit) -> Unit = { next ->
        // A locked sheet is not closed from inside either: a button that is still on screen from
        // the state before the lock must not remove it. And what follows runs only once the sheet
        // really is hidden, since the hide can be refused after the check.
        if (canDismiss) {
            scope.launch {
                state.hide()
                if (!state.isVisible) {
                    onDismiss()
                    next()
                }
            }
        }
    }
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    val density = LocalDensity.current
    ModalBottomSheet(
        // The scrim tap and a swipe past the bottom both end up here.
        onDismissRequest = { if (canDismiss) onDismiss() },
        modifier =
            if (topLine == null) {
                Modifier
            } else {
                Modifier.drawWithContent {
                    drawContent()
                    // A stroke twice the hairline, centred on the edge and cut to the top corners,
                    // so one dp shows above the sheet and nothing runs down its sides.
                    clipRect(top = -4.dp.toPx(), bottom = 24.dp.toPx()) {
                        drawOutline(
                            shape.createOutline(size, layoutDirection, density),
                            topLine,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            },
        sheetState = state,
        shape = shape,
        containerColor = containerColor,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = handleGap)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Tt.colors.text4.copy(alpha = 0.5f)),
            )
        },
    ) {
        // The sheet's own back handling is switched off above while locked; this swallows the press
        // so it cannot fall through to the screen underneath either.
        BackHandler(enabled = !dismissible) {}
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .then(if (tall) Modifier.fillMaxHeight(0.82f) else Modifier)
                    .padding(horizontal = Gutter)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leading != null) {
                        leading()
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            modifier =
                                if (titleLiveRegion) {
                                    Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                } else {
                                    Modifier
                                },
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 17.sp,
                                    lineHeight = PickerTitleLineHeight,
                                ),
                            fontWeight = FontWeight.Bold,
                            color = Tt.colors.text,
                        )
                        if (subtitle != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tt.colors.text3)
                        }
                    }
                }
                Spacer(Modifier.height(headerGap))
                content(close)
            }
        }
    }
}

/**
 * A picker's search: 48 high, a pill on the sunken ground with a search mark before the words, a
 * 1.2 line that turns the text ink at 1.6 while focused, and a round clear button once something
 * is typed.
 */
@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = CircleShape
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = source,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.text),
        cursorBrush = SolidColor(t.accentInk),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(shape)
                    .background(t.field)
                    .border(if (focused) 1.6.dp else 1.2.dp, if (focused) t.text else t.fieldBorder, shape)
                    .padding(start = 14.dp, end = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Search, null, Modifier.size(18.dp), tint = t.text3)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                            color = t.text4,
                            maxLines = 1,
                        )
                    }
                    field()
                }
                if (value.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(t.glass2)
                            .clickable(onClickLabel = "Clear search") { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Lucide.X, "Clear search", Modifier.size(14.dp), tint = t.text2) }
                }
            }
        },
    )
}
