package com.eazyverse.testtrack.ui

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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.launch

/**
 * The one frame every picker sheet in the app is drawn in: the sheet surface with top corners of
 * 22, a 38 by 4 handle, the title, and the body under it, never wider than 560.
 *
 * [tall] is for a long list with search: the sheet takes 82% of the screen at once, rather than
 * growing and shrinking as the search narrows the list. [skipHalf] opens a short sheet fully,
 * with no half-open stop. [leading] sits before the title, such as the app a sheet is about.
 * [content] gets `close`, which hides the sheet first and then runs what it is given, since a
 * sheet removed while open vanishes on one frame.
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
    content: @Composable ColumnScope.(close: (() -> Unit) -> Unit) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipHalf)
    val scope = rememberCoroutineScope()
    val close: (() -> Unit) -> Unit = { next ->
        scope.launch {
            state.hide()
            onDismiss()
            next()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Tt.colors.text4.copy(alpha = 0.5f)),
            )
        },
    ) {
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
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
                            fontWeight = FontWeight.Bold,
                            color = Tt.colors.text,
                        )
                        if (subtitle != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tt.colors.text3)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
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
