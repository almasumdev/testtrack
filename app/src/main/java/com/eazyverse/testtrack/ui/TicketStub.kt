package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.domain.grouped
import com.eazyverse.testtrack.ui.theme.Barlow
import com.eazyverse.testtrack.ui.theme.Tt

private val StubNote = TextStyle(fontFamily = Barlow, fontSize = 11.sp, lineHeight = 13.sp)

/** What a review pays, on the right of a ticket, behind the tear line. Your own post says so instead. */
@Composable
internal fun TicketStub(
    tix: Int,
    mine: Boolean,
) {
    if (mine) {
        TicketStubFrame(null) {
            Text("Yours", style = StubNote, fontWeight = FontWeight.Medium, color = Tt.colors.text3, maxLines = 1)
        }
    } else {
        TicketStubFigure(grouped(tix), "$tix tix")
    }
}

/**
 * The stub's shell: the [fill] (the accent's soft tone unless the state asks for another), the
 * tear line in [tear] (none for a stub that has already been torn off), and [description] read
 * out as the whole stub. A null [description] leaves the content to describe itself.
 */
@Composable
internal fun TicketStubFrame(
    description: String?,
    fill: Color = Tt.colors.leafAccentSoft,
    tear: Color? = Tt.colors.glassBorder,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .width(TICKET_STUB)
            .fillMaxHeight()
            .background(fill)
            .then(if (tear == null) Modifier else Modifier.tearLine(tear))
            .then(if (description == null) Modifier else Modifier.clearAndSetSemantics { contentDescription = description }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/**
 * The ticket icon over one figure, struck through for a ticket that did not pay. With a [stamp]
 * (SENT, PAID, REFUSED...) the stamp stands where the icon was, above the figure, and an optional
 * [word] ("to earn", "half pay") sits under it.
 */
@Composable
internal fun TicketStubFigure(
    text: String,
    description: String,
    color: Color = Tt.colors.tabSelected,
    struck: Boolean = false,
    fill: Color = Tt.colors.leafAccentSoft,
    tear: Color? = Tt.colors.glassBorder,
    stamp: String? = null,
    stampColor: Color = color,
    word: String? = null,
    wordColor: Color = Tt.colors.text3,
) {
    // The stub is leafAccentSoft already, so a ticket in the same fill would vanish; card lifts it.
    val mark = rememberTicketMark(Tt.colors.card)
    TicketStubFrame(description, fill, tear) {
        if (stamp == null) {
            Image(mark, null, Modifier.size(16.dp).rotate(-12f))
            Spacer(Modifier.height(3.dp))
        } else {
            TicketStamp(stamp, stampColor)
            Spacer(Modifier.height(5.dp))
        }
        Text(
            text,
            style =
                TextStyle(
                    fontFamily = Barlow,
                    fontSize = if (text.length > 4) 15.sp else 18.sp,
                    lineHeight = 20.sp,
                    fontFeatureSettings = "tnum",
                ),
            fontWeight = FontWeight.SemiBold,
            color = color,
            textDecoration = if (struck) TextDecoration.LineThrough else null,
            maxLines = 1,
        )
        if (word != null) {
            Text(
                word,
                Modifier.padding(top = 1.dp),
                style = TextStyle(fontFamily = Barlow, fontSize = 10.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium),
                color = wordColor,
                maxLines = 1,
            )
        }
    }
}

/**
 * A rubber stamp: the word in capitals inside a thin rounded border of the same colour, turned a
 * little. Words longer than seven letters drop to 8.5sp so "GIVEN UP" stays inside the 58dp stub.
 */
@Composable
internal fun TicketStamp(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val long = text.length > 7
    Text(
        text.uppercase(),
        modifier
            .wrapContentWidth(unbounded = true)
            .rotate(-8f)
            .border(1.2.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = if (long) 3.dp else 4.dp),
        style =
            TextStyle(
                fontFamily = Barlow,
                fontSize = if (long) 8.5.sp else 9.5.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.06.em,
            ),
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

/** A stub with no figure to show, so one small icon stands in for it. */
@Composable
internal fun TicketStubIcon(
    icon: ImageVector,
    description: String,
    color: Color = Tt.colors.tabSelected,
) {
    TicketStubFrame(description) { Icon(icon, null, Modifier.size(22.dp), tint = color) }
}
