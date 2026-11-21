package com.eazyverse.testtrack.ui

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * Picking one of a short fixed list, in a field's box: the app's one dropdown.
 *
 * The box is an [OutlinedField] in its picker mode: 48 high, the label in its notch, the picked
 * words on one line (or "Choose one"), and a chevron that turns over while the list is open. The
 * list opens 4dp under the box at its width, in the sheet's colour with a hairline and a soft
 * shadow. Rows wrap to two lines; the picked one has the soft accent wash, bold words and a check.
 * A long list stops at [MenuMax] and scrolls, opening on the picked row.
 *
 * [options] are the value each row stands for and its words; [picked] is the value picked, or null.
 */
@Composable
internal fun <T> DropdownField(
    label: String,
    options: List<Pair<T, String>>,
    picked: T?,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Choose one",
) {
    val t = Tt.colors
    var open by rememberSaveable { mutableStateOf(false) }
    // The tap that closes the list by landing on the box also reaches the box; it must not reopen it.
    var closedAt by remember { mutableLongStateOf(0L) }
    var fieldWidth by remember { mutableIntStateOf(0) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    val words = options.firstOrNull { it.first == picked }?.second.orEmpty()
    // The box alone (no hint or count under it), so the list's gap is measured from its edge.
    Box(modifier.fillMaxWidth().onSizeChanged { fieldWidth = it.width }) {
        OutlinedField(
            value = words,
            onValueChange = {},
            label = label,
            placeholder = placeholder,
            onTap = { open = !open && SystemClock.uptimeMillis() - closedAt > REOPEN_GUARD_MS },
            active = open,
            trailing = {
                Icon(Lucide.ChevronDown, null, Modifier.size(20.dp).rotate(turn), tint = t.text3)
            },
        )
        if (open) {
            val gap = with(LocalDensity.current) { MenuGap.roundToPx() }
            Popup(
                popupPositionProvider = remember(gap) { BelowAnchor(gap) },
                onDismissRequest = {
                    open = false
                    closedAt = SystemClock.uptimeMillis()
                },
                properties = PopupProperties(focusable = true),
            ) {
                val width = with(LocalDensity.current) { fieldWidth.toDp() }
                val start = (options.indexOfFirst { it.first == picked }).coerceAtLeast(0)
                // Opens with the picked row in view, a couple of rows down so what is above it shows too.
                val list = rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0))
                LazyColumn(
                    Modifier
                        .width(width)
                        .heightIn(max = MenuMax)
                        .shadow(8.dp, MenuShape, clip = false)
                        .clip(MenuShape)
                        .background(t.sheet)
                        .border(1.dp, t.glassBorder, MenuShape),
                    state = list,
                    contentPadding = PaddingValues(4.dp),
                ) {
                    itemsIndexed(options) { _, (value, text) ->
                        MenuRow(text, value == picked) {
                            onPick(value)
                            open = false
                        }
                    }
                }
            }
        }
    }
}

/** One row of the list: at least 44 high, 14 on 18, up to two lines; the picked one washed and ticked. */
@Composable
private fun MenuRow(
    text: String,
    picked: Boolean,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RowShape)
            .then(if (picked) Modifier.background(t.leafAccentSoft) else Modifier)
            .semantics { selected = picked }
            .clickable(role = Role.DropdownList, onClick = onClick)
            .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            Modifier.weight(1f),
            style = RowStyle,
            fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium,
            color = if (picked) t.tabSelected else t.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (picked) {
            Spacer(Modifier.width(8.dp))
            Icon(Lucide.Check, null, Modifier.size(18.dp), tint = t.tabSelected)
        }
    }
}

/** Under the box at its start, [gap] below it; above it when there is no room underneath. */
private class BelowAnchor(
    private val gap: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val below = anchorBounds.top + anchorBounds.height + gap
        val y =
            if (below + popupContentSize.height <= windowSize.height) {
                below
            } else {
                (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(0)
            }
        return IntOffset(anchorBounds.left, y)
    }
}

private const val REOPEN_GUARD_MS = 300L
private val MenuShape = RoundedCornerShape(10.dp)
private val RowShape = RoundedCornerShape(8.dp)
private val MenuGap = 4.dp

/** Six rows and a bit, so a longer list shows that it scrolls. */
private val MenuMax = 264.dp
private val RowStyle = tileStyle(14f, 18f, FontWeight.Medium)
