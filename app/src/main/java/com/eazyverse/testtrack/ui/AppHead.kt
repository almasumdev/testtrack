package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The app a page or a row is about, drawn one way everywhere: its icon with where people find it on
 * the corner, its name on up to two lines and one quiet line under it.
 */

/**
 * An app's icon with its source mark on the bottom right corner, hanging 5dp past the icon: the
 * Play logo for a live app, the flask for a closed test. [live] null draws the icon alone.
 */
@Composable
internal fun SourcedAppMark(
    pkg: String,
    name: String,
    icon: String,
    live: Boolean?,
    size: Dp = AppTileIcon,
    /** The owner's own screens, where the icon they chose wins over the copy on this phone. */
    ownIcon: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(size)) {
        AppMark(pkg, name, icon, size = size, ownIcon = ownIcon)
        if (live != null) SourceMark(live, Modifier.align(Alignment.BottomEnd).offset(5.dp, 5.dp))
    }
}

/** The room [AppHead] keeps under its icon for the source mark when it has a press shape. */
internal val AppHeadMarkRoom = 6.dp

/** The line under an app's name in [AppHead]. */
private val AppHeadSub = tileStyle(12.5f, 15f, FontWeight.Medium)

/**
 * The app a page is about: [SourcedAppMark], the name on up to two lines and [sub] under it. The
 * icon grows with the font size so the two lines beside it keep fitting.
 *
 * With [onClick] the whole head is one rounded press shape, with room inside it for the source
 * mark that hangs under the icon; without one it takes no room it does not need. [trailing] sits
 * at the far end, such as a count or a mark.
 *
 * [subGap] and [subStyle] set the line under the name: 2dp under it at 12.5 on 15 unless given,
 * such as the review page's and the picker's package, flush under the name at 12.5 on 14.
 */
@Composable
internal fun AppHead(
    pkg: String,
    name: String,
    icon: String,
    live: Boolean?,
    sub: AnnotatedString,
    modifier: Modifier = Modifier,
    ownIcon: Boolean = false,
    onClick: (() -> Unit)? = null,
    /** What TalkBack says a tap does, such as "Open Cherish on Google Play". */
    onClickLabel: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    subGap: Dp = 2.dp,
    subStyle: TextStyle = AppHeadSub,
) {
    val t = Tt.colors
    val mark = AppTileIcon * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClickLabel = onClickLabel, onClick = onClick)
                        .padding(bottom = AppHeadMarkRoom)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourcedAppMark(pkg, name, icon, live, size = mark, ownIcon = ownIcon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = AppTileName, color = t.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(subGap))
                Text(sub, style = subStyle, color = t.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** [AppHead] with a plain line under the name. */
@Composable
internal fun AppHead(
    pkg: String,
    name: String,
    icon: String,
    live: Boolean?,
    sub: String,
    modifier: Modifier = Modifier,
    ownIcon: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    subGap: Dp = 2.dp,
    subStyle: TextStyle = AppHeadSub,
) = AppHead(pkg, name, icon, live, AnnotatedString(sub), modifier, ownIcon, onClick, onClickLabel, trailing, subGap, subStyle)
