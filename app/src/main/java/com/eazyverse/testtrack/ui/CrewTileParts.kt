package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/** Lobby and Run share the same crew heading and quiet detail. */
@Composable
internal fun CrewTileHeading(
    name: String,
    detail: String,
    detailTone: Tone? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), style = tileStyle(18f, 23f, FontWeight.SemiBold), color = Tt.colors.text)
        Spacer(Modifier.width(8.dp))
        Text(detail, style = tileStyle(12f, 17f, FontWeight.Normal), color = detailTone?.ink() ?: Tt.colors.text3)
    }
}

/** The Lobby's seats, with a spoken summary and a marker for the owner's place. */
@Composable
internal fun CrewTileSquares(
    count: Int,
    description: String,
    marked: Int = -1,
    fill: @Composable (Int) -> Color,
) {
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(15.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(fill(index)),
                contentAlignment = Alignment.Center,
            ) {
                if (index == marked) Box(Modifier.size(4.dp).clip(CircleShape).background(Tt.colors.bg))
            }
        }
    }
}

/** The owner's app has the same small footer in both crew tabs. */
@Composable
internal fun CrewTileApp(seat: CrewSeat?) {
    if (seat == null) return
    CrewTileApp(seat.packageName, seat.appName, seat.icon)
}

/** The same footer from plain values; the Record tab keeps the name to [maxLines] 1. */
@Composable
internal fun CrewTileApp(
    packageName: String,
    appName: String,
    icon: String,
    maxLines: Int = 2,
) {
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppMark(packageName, appName, icon, size = 28.dp, ownIcon = true)
        Spacer(Modifier.width(8.dp))
        Text(
            appName,
            style = tileStyle(13f, 18f, FontWeight.Normal),
            color = Tt.colors.text2,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
