package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val t = Tt.colors
    // The stub is leafAccentSoft already, so a ticket in the same fill would vanish; card lifts it.
    val mark = rememberTicketMark(t.card)
    Column(
        Modifier
            .width(TICKET_STUB)
            .fillMaxHeight()
            .background(t.leafAccentSoft)
            .tearLine(t.glassBorder)
            .then(if (mine) Modifier else Modifier.clearAndSetSemantics { contentDescription = "$tix tix" }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (mine) {
            Text("Yours", style = StubNote, fontWeight = FontWeight.Medium, color = t.text3, maxLines = 1)
        } else {
            Image(mark, null, Modifier.size(16.dp).rotate(-12f))
            Spacer(Modifier.height(3.dp))
            Text(
                grouped(tix),
                style =
                    TextStyle(
                        fontFamily = Barlow,
                        fontSize = if (tix >= 1000) 15.sp else 18.sp,
                        lineHeight = 20.sp,
                        fontFeatureSettings = "tnum",
                    ),
                fontWeight = FontWeight.SemiBold,
                color = t.tabSelected,
                maxLines = 1,
            )
        }
    }
}
