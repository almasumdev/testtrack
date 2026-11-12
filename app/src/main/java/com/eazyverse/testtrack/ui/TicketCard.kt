package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.ui.theme.Barlow
import com.eazyverse.testtrack.ui.theme.Tt

// The text block (name up to two lines, plus the note) is 19 + 19 + 14 = 52sp, the height of the icon
// beside it, so a long name never makes the card taller than a short one. Trimmed line heights keep
// the font's own padding from adding hidden height.
private val TicketLineHeight =
    LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)

@Suppress("DEPRECATION")
private val TicketTitle =
    TextStyle(
        fontFamily = Barlow,
        fontSize = 16.sp,
        lineHeight = 19.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = TicketLineHeight,
    )

@Suppress("DEPRECATION")
private val TicketNote =
    TextStyle(
        fontFamily = Barlow,
        fontSize = 12.5.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Normal,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = TicketLineHeight,
    )

/**
 * The ticket every Circle tab draws its list in: a notched card on the page ground with a 1dp
 * line, the app's mark and two lines of text, and a stub on the right behind a tear line.
 *
 * [leading] is the 52dp mark, [stub] is one of the TicketStub pieces, and the whole card is the
 * tap target when [onClick] is given. The note may carry a small [noteIcon], and both are drawn in
 * [noteTone].
 */
@Composable
internal fun TicketCard(
    leading: @Composable () -> Unit,
    title: String,
    note: String,
    modifier: Modifier = Modifier,
    noteIcon: ImageVector? = null,
    noteTone: Color = Tt.colors.text3,
    onClick: (() -> Unit)? = null,
    stub: @Composable () -> Unit,
) {
    val t = Tt.colors
    TicketFrame(modifier, onClick, leading, stub) {
        Text(
            title,
            style = TicketTitle,
            fontWeight = FontWeight.SemiBold,
            color = t.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (noteIcon != null) {
                Icon(noteIcon, null, Modifier.size(12.dp), tint = noteTone)
                Spacer(Modifier.width(4.dp))
            }
            Text(note, style = TicketNote, color = noteTone, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The ticket's outline and layout, with its text block left to the caller (the loading card fills it with bars). */
@Composable
internal fun TicketFrame(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    leading: @Composable () -> Unit,
    stub: @Composable () -> Unit,
    text: @Composable ColumnScope.() -> Unit,
) {
    val t = Tt.colors
    val shape = remember { TicketShape() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(t.bg)
            .border(1.dp, t.cardLine, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            Modifier.weight(1f).padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), content = text)
        }
        stub()
    }
}
