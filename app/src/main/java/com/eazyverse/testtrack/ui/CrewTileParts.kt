package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * Lobby and Run share the same crew heading and quiet detail. [trailing] sits after the detail, such
 * as the chat button on a page that leaves out the tile's app line.
 */
@Composable
internal fun CrewTileHeading(
    name: String,
    detail: String,
    detailTone: Tone? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), style = tileStyle(18f, 23f, FontWeight.SemiBold), color = Tt.colors.text)
        Spacer(Modifier.width(8.dp))
        Text(detail, style = TextStyles.SmallNote, color = detailTone?.ink() ?: Tt.colors.text3)
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** The owner's app has the same small footer in both crew tabs; [trailing] sits at its end, such as the chat badge. */
@Composable
internal fun CrewTileApp(
    seat: CrewSeat?,
    trailing: (@Composable () -> Unit)? = null,
) {
    if (seat == null) return
    CrewTileApp(seat.packageName, seat.appName, seat.icon, trailing = trailing)
}

/** The same footer from plain values; the Record tab keeps the name to [maxLines] 1. */
@Composable
internal fun CrewTileApp(
    packageName: String,
    appName: String,
    icon: String,
    maxLines: Int = 2,
    trailing: (@Composable () -> Unit)? = null,
) {
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppMark(packageName, appName, icon, size = 28.dp, ownIcon = true)
        Spacer(Modifier.width(8.dp))
        Text(
            appName,
            if (trailing != null) Modifier.weight(1f) else Modifier,
            style = TextStyles.PageLine,
            color = Tt.colors.text2,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
