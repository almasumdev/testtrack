package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** The small rounded badge on a ticket's stub: a card-coloured chip with a hairline edge. */
@Composable
internal fun Modifier.stubChip(): Modifier {
    val t = Tt.colors
    val chip = RoundedCornerShape(10.dp)
    return this
        .clip(chip)
        .background(t.card)
        .border(1.dp, t.glassBorder, chip)
        .padding(horizontal = 6.dp, vertical = 5.dp)
}

/** What a review pays, on the right of a ticket, behind the tear line. Your own post says so instead. */
@Composable
internal fun TicketStub(
    tix: Int,
    mine: Boolean,
) {
    val t = Tt.colors
    // The chip is card-coloured, so the ticket keeps its default soft fill and shows against it.
    val mark = rememberTicketMark()
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
        Column(Modifier.stubChip(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (mine) {
                Text("Yours", style = StubNote, fontWeight = FontWeight.Medium, color = t.text3, maxLines = 1)
            } else {
                Image(mark, null, Modifier.size(20.dp).rotate(-12f))
                Spacer(Modifier.height(2.dp))
                Text(
                    grouped(tix),
                    style =
                        TextStyle(
                            fontFamily = Barlow,
                            fontSize = if (tix >= 1000) 14.sp else 17.sp,
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
}
