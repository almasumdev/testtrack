package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.eazyverse.testtrack.domain.MentionOption
import com.eazyverse.testtrack.ui.theme.Tt

/** Room kept round the list inside its window, so its shadow is not cut off. */
internal val MentionEdge = 6.dp

private val MentionRow = 48.dp
private const val MENTION_ROWS_SHOWN = 4

/**
 * Who "@word" may mean, as a card of rows over the field: a face or app icon, the name, and a
 * quiet note of the other half (an app's owner, a member's app). About four rows show; the rest scroll.
 * [width] is the card's own width, the field's.
 */
@Composable
internal fun MentionList(
    options: List<MentionOption>,
    width: Dp,
    onPick: (MentionOption) -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(TileRadius)
    Box(Modifier.padding(MentionEdge)) {
        LazyColumn(
            Modifier
                .width(width)
                .heightIn(max = MentionRow * MENTION_ROWS_SHOWN)
                .shadow(5.dp, shape, ambientColor = t.shadow.copy(alpha = 0.3f), spotColor = t.shadow.copy(alpha = 0.3f))
                .clip(shape)
                .background(t.glass2)
                .border(1.dp, t.glassBorder, shape),
        ) {
            items(options, key = { "${if (it.app) "app" else "seat"}-${it.person.seatId}" }) { option ->
                MentionRow(option) { onPick(option) }
            }
        }
    }
}

@Composable
private fun MentionRow(
    option: MentionOption,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    val person = option.person
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MentionRow)
            .clickable(onClickLabel = "Mention ${option.name}", onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (option.app) {
            AppMark(person.packageName, person.appName, person.appIcon, size = 30.dp)
        } else {
            Avatar(person.photo, initialOf(person.name), size = 30.dp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (option.app) shortAppName(person.appName) else person.name,
            Modifier.weight(1f, fill = false),
            style = chatStyle(15f, 20f),
            fontWeight = FontWeight.Medium,
            color = t.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val hint = if (option.app) person.name else shortAppName(person.appName)
        if (hint.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(
                hint,
                Modifier.weight(1f, fill = false),
                style = chatStyle(12.5f, 16f),
                color = t.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Puts the list just over the field, its edges (and the room kept for its shadow) lined up with the
 * field's. The field is the popup's anchor, so the keyboard coming up moves the anchor and the list
 * follows it; the list never goes off the top or the sides of the window.
 */
internal class MentionAnchor(
    private val gap: Int,
    private val edge: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset =
        IntOffset(
            (anchorBounds.left - edge).coerceAtMost(windowSize.width - popupContentSize.width).coerceAtLeast(0),
            (anchorBounds.top - popupContentSize.height - gap + edge).coerceAtLeast(0),
        )
}
