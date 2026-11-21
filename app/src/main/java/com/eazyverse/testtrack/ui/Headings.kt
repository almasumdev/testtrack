package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Barlow
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.TtTypography
import com.eazyverse.testtrack.ui.theme.tabular

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

    /**
     * A block's heading on an app's page and the case page: 16 on 19, bold, on Material's title
     * with its letter spacing and line fit.
     */
    val BlockHeadingStyle = TtTypography.titleMedium.copy(fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold)

    /**
     * The Me tab's section headings: 16 bold on Material's title, whose 24 line gives the space
     * above and below the words.
     */
    val SectionTitleStyle = TtTypography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)

    /** The app page's small "Earlier" heading: 12.5 on 16, semibold, on Material's label. */
    val EarlierStyle = TtTypography.labelMedium.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)

    /** A dialog's title: 16 on 20, bold. */
    val DialogTitle = tileStyle(16f, 20f, FontWeight.Bold)

    /** A dialog's words: 13.5 on 19. */
    val DialogBody = tileStyle(13.5f, 19f, FontWeight.Normal)

    /** The small grey line under a dialog's words: 12.5 on 17, in text4. */
    val DialogNote = tileStyle(12.5f, 17f, FontWeight.Normal)

    /** The figure in a numbered step's circle: 12.5 on 14, bold. */
    val StepNumber = tileStyle(12.5f, 14f, FontWeight.Bold)

    /** A list row's title, such as a settings row or a video: 16 on 20, on Material's large body. */
    val RowTitle = TtTypography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp)

    /** The line under a [RowTitle]: 12.5 on 16, on Material's small body. */
    val RowLine = TtTypography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp)

    /** A small quiet label, such as the ends of a crew's progress bar: 12.5, on Material's small body. */
    val SmallLine = TtTypography.bodySmall.copy(fontSize = 12.5.sp)

    /** A main button's words and a done row's title: 15, on Material's small title. */
    val ButtonWords = TtTypography.titleSmall.copy(fontSize = 15.sp)

    /** A name in a picker list, such as a private tester's address: 15 on 19, on Material's large body. */
    val ListName = TtTypography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 19.sp)

    /** An app's name on a crew seat: 16 on 19, on Material's large body. */
    val SeatName = TtTypography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 19.sp)

    /** The About page's one line about the app: 14.5 on 21. */
    val AboutLead = TtTypography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 21.sp)

    /** The feedback card's title: 18 on 23, or 16 on 20 when compact. */
    val FeedbackTitle = TtTypography.titleMedium.copy(fontSize = 18.sp, lineHeight = 23.sp)
    val FeedbackTitleCompact = TtTypography.titleMedium.copy(fontSize = 16.sp, lineHeight = 20.sp)

    /** The You page's name, its letter in the face, and the address under it. */
    val ProfileName = TtTypography.titleLarge.copy(fontSize = 18.sp)
    val ProfileLetter = TtTypography.titleLarge.copy(fontSize = 23.sp)
    val ProfileEmail = TtTypography.bodyMedium.copy(fontSize = 13.sp)

    /** The figure in a small corner [CountBadge]: 10.5 on 13, tabular. */
    val SmallBadge = TtTypography.bodyMedium.copy(fontSize = 10.5.sp, lineHeight = 13.sp).tabular()

    /** A small photo chip's words: 12.5 on 16, bold. */
    val PhotoChipSmall = tileStyle(12.5f, 16f, FontWeight.Bold)

    /** The line under an app's name, its package or developer: 12.5 on 15, medium. */
    val AppSubLine = tileStyle(12.5f, 15f, FontWeight.Medium)

    /** A count to pick, such as how many reviews: 14 on 17, tabular. */
    val CountChip = tileStyle(14f, 17f, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum")

    /** The empty chat's title: 17 on 22, semibold. */
    val ChatEmptyTitle = TtTypography.bodyMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)

    /** A video's length on its thumbnail: 10 on 14. */
    val VideoLength = TtTypography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp)

    /** The crew rules card: a fact's figure, its unit and label, a rule's line, the list heading. */
    val RuleFigure =
        TextStyle(fontFamily = Barlow, fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
    val RuleUnit = TextStyle(fontFamily = Barlow, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
    val RuleLabel = TextStyle(fontFamily = Barlow, fontSize = 11.5.sp, lineHeight = 14.sp)
    val RuleLine = TextStyle(fontFamily = Barlow, fontSize = 13.sp, lineHeight = 17.sp)
    val RuleHeading = TextStyle(fontFamily = Barlow, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
}

/** How tall a [SectionHeading]'s words are, which the count badge is centred on. */
private val HeadingLine = 19.dp

/** A heading's short status words: 12.5 on 14, semibold. */
private val HeadingStatusStyle = tileStyle(12.5f, 14f, FontWeight.SemiBold)

/** The one short status at the far end of a heading: done (green) or something wrong (amber). */
internal class HeadingStatus(
    val text: String,
    val ok: Boolean,
) {
    companion object {
        fun warn(text: String) = HeadingStatus(text, false)
    }
}

/**
 * A heading over a part of a page, sentence case, in [style] ([TextStyles.HeadingStyle] unless
 * given).
 *
 * [count] sits in a badge right beside the words it counts. [tag] is the small quiet tag at the
 * far end, such as "Optional"; [status] is one short status there; [end] is anything else there,
 * such as an action. [top] is the space over it and [bottom] the space under it. Padding for the
 * gutter goes in [modifier].
 *
 * With [tagTall] the tag keeps its own 20dp height and the words are centred beside it, so the
 * heading is 20dp tall, as the Ask page's "For testers" always was. Off, the tag is centred on the
 * words and adds no height. [oneLine] is the seat page's way: the words on one line, cut short if
 * they must be, centred on what is at the end, the row at least 19dp tall.
 */
@Composable
internal fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    tag: String? = null,
    top: Dp = 0.dp,
    tagTall: Boolean = false,
    bottom: Dp = 10.dp,
    oneLine: Boolean = false,
    style: TextStyle = TextStyles.HeadingStyle,
    status: HeadingStatus? = null,
    /** The [tag] right after the words rather than at the far end. */
    tagBeside: Boolean = false,
    end: (@Composable RowScope.() -> Unit)? = null,
) {
    val t = Tt.colors
    val centred = tagTall || oneLine
    val align = if (centred) Alignment.CenterVertically else Alignment.Top
    Row(
        modifier.fillMaxWidth().heightIn(min = if (centred) HeadingLine else 0.dp).padding(top = top, bottom = bottom),
        verticalAlignment = align,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = align) {
            Text(
                text,
                Modifier.weight(1f, fill = false),
                style = style,
                color = t.text,
                maxLines = if (oneLine) 1 else Int.MAX_VALUE,
                overflow = if (oneLine) TextOverflow.Ellipsis else TextOverflow.Clip,
            )
            if (count != null) {
                Spacer(Modifier.width(5.dp))
                // Centred on the words and given no height of its own, so the heading is exactly as
                // tall as its words and they start where the first card on any other tab starts.
                Box(Modifier.height(HeadingLine).wrapContentHeight(Alignment.CenterVertically, unbounded = true)) {
                    CountBadge(count)
                }
            }
            if (tagBeside && !tag.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.height(HeadingLine).wrapContentHeight(Alignment.CenterVertically, unbounded = true)) {
                    HeadingTag(tag)
                }
            }
        }
        if (!tagBeside && !tag.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            if (tagTall) {
                HeadingTag(tag)
            } else {
                Box(Modifier.height(HeadingLine).wrapContentHeight(Alignment.CenterVertically, unbounded = true)) {
                    HeadingTag(tag)
                }
            }
        }
        if (status != null) {
            Spacer(Modifier.width(8.dp))
            val tone = if (status.ok) t.ok else t.warn
            Icon(if (status.ok) Lucide.CircleCheck else Lucide.CircleAlert, null, Modifier.size(14.dp), tint = tone)
            Spacer(Modifier.width(4.dp))
            Text(status.text, style = HeadingStatusStyle, color = tone, maxLines = 1, softWrap = false)
        }
        if (end != null) {
            Spacer(Modifier.width(8.dp))
            end()
        }
    }
}

/** The small quiet tag at a heading's far end, such as when a block is for or "Optional". */
@Composable
internal fun HeadingTag(tag: String) {
    val t = Tt.colors
    Text(
        tag,
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(t.glass2)
            .padding(horizontal = 9.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
        fontWeight = FontWeight.SemiBold,
        color = t.text3,
    )
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
    bottom: Dp = 8.dp,
    /** The words' style: [SmallHeadingStyle] unless given, such as the app page's "Earlier". */
    style: TextStyle = SmallHeadingStyle,
) {
    val t = Tt.colors
    Row(modifier.fillMaxWidth().padding(top = top, bottom = bottom), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            Modifier.weight(1f, fill = false),
            style = style,
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
