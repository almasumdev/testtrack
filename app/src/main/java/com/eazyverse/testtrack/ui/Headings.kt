package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * Headings over the parts of a page, and the few text styles every page line is set in.
 */

/**
 * The page text styles shared by every screen. Kept in one object so a page can import each by
 * name (`import com.eazyverse.testtrack.ui.TextStyles.PageLine`) in place of its own copy.
 */
object TextStyles {
    /** An ordinary line on a page: 13 on 18. */
    val PageLine = tileStyle(13f, 18f, FontWeight.Normal)

    /** The quiet line under an app's name: its developer or its package. */
    val DeveloperStyle = tileStyle(12.5f, 14f, FontWeight.Medium)

    /** The hint over a page's button. */
    val HintStyle = tileStyle(12.5f, 16f, FontWeight.SemiBold)

    /** A section's heading: 16 on 19, bold. */
    val HeadingStyle = tileStyle(16f, 19f, FontWeight.Bold)
}

/** How tall a [SectionHeading]'s words are, which the count badge is centred on. */
private val HeadingLine = 19.dp

/**
 * A heading over a part of a page, sentence case, in [TextStyles.HeadingStyle].
 *
 * [count] sits in a badge right beside the words it counts. [tag] is the small quiet tag at the
 * far end, such as "Optional"; [end] is anything else there, such as a status or an action. [top]
 * is the space over it; it keeps 10dp under it. Padding for the gutter goes in [modifier].
 *
 * With [tagTall] the tag keeps its own 20dp height and the words are centred beside it, so the
 * heading is 20dp tall, as the Ask page's "For testers" always was. Off, the tag is centred on the
 * words and adds no height.
 */
@Composable
internal fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    tag: String? = null,
    top: Dp = 0.dp,
    tagTall: Boolean = false,
    end: (@Composable RowScope.() -> Unit)? = null,
) {
    val align = if (tagTall) Alignment.CenterVertically else Alignment.Top
    Row(
        modifier.fillMaxWidth().heightIn(min = if (tagTall) HeadingLine else 0.dp).padding(top = top, bottom = 10.dp),
        verticalAlignment = align,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = align) {
            Text(
                text,
                Modifier.weight(1f, fill = false),
                style = TextStyles.HeadingStyle,
                color = Tt.colors.text,
            )
            if (count != null) {
                Spacer(Modifier.width(5.dp))
                // Centred on the words and given no height of its own, so the heading is exactly as
                // tall as its words and they start where the first card on any other tab starts.
                Box(Modifier.height(HeadingLine).wrapContentHeight(Alignment.CenterVertically, unbounded = true)) {
                    CountBadge(count)
                }
            }
        }
        if (!tag.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            if (tagTall) {
                HeadingTag(tag)
            } else {
                Box(Modifier.height(HeadingLine).wrapContentHeight(Alignment.CenterVertically, unbounded = true)) {
                    HeadingTag(tag)
                }
            }
        }
        if (end != null) {
            Spacer(Modifier.width(8.dp))
            end()
        }
    }
}

/** A small heading's words: 12 on 16, bold, in the quiet ink. */
private val SmallHeadingStyle = tileStyle(12f, 16f, FontWeight.Bold)

/**
 * A small quiet heading over a part of a page, [top] under what is above it, with an (i) that says
 * more when there is [info] to say.
 */
@Composable
internal fun SmallHeading(
    text: String,
    modifier: Modifier = Modifier,
    info: String? = null,
    top: Dp = 18.dp,
) {
    val t = Tt.colors
    Row(modifier.fillMaxWidth().padding(top = top, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            Modifier.weight(1f, fill = false),
            style = SmallHeadingStyle,
            color = t.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (info != null) {
            Spacer(Modifier.width(4.dp))
            InfoDot(text, t.text3, glyph = 14.dp) { CalloutLine(AnnotatedString(info)) }
        }
    }
}
