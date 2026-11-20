package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleDashed
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * One app in the seat sheet's list. 66dp at least: a 44dp icon with 11dp above and below. The text
 * beside it is 19 + 5 + 14 = 38dp, never taller than the icon, so a long name or line cannot make
 * one tile taller than the next.
 */

private val SeatNameStyle = AppTileName
private val SeatLineStyle = tileStyle(12.5f, 14f, FontWeight.Medium)
private val SeatLineBold = tileStyle(12.5f, 14f, FontWeight.SemiBold)

/**
 * An app that can go in is tapped; one that can't ([blocked]) is drawn flat and quiet, with its
 * icon in grey, and does nothing.
 */
@Composable
internal fun SeatAppTile(
    app: AppEntry,
    line: String,
    mark: SeatMark,
    blocked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(TileRadius)
    val ink =
        when (mark) {
            SeatMark.READY -> t.ok
            SeatMark.TODO -> t.text2
            SeatMark.LOCK, SeatMark.WAIT -> t.text3
        }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clip(shape)
            .background(if (blocked) Color.Transparent else t.bg)
            .border(1.dp, t.cardLine, shape)
            .then(if (blocked) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(if (blocked) Modifier.greyedOut() else Modifier) {
            AppMark(app.pkg, app.name, app.icon, size = 44.dp, ownIcon = true)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.name,
                style = SeatNameStyle,
                color = if (blocked) t.text3 else t.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    markIcon(mark),
                    null,
                    Modifier.size(12.dp),
                    tint =
                        if (mark ==
                            SeatMark.TODO
                        ) {
                            t.accentInk
                        } else if (mark == SeatMark.READY) {
                            t.ok
                        } else {
                            t.text4
                        },
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    line,
                    style = if (mark == SeatMark.READY) SeatLineBold else SeatLineStyle,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when {
                blocked -> Unit
                else -> Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = t.text4)
            }
        }
    }
}

private fun markIcon(mark: SeatMark): ImageVector =
    when (mark) {
        SeatMark.READY -> Lucide.CircleCheck
        SeatMark.TODO -> Lucide.CircleDashed
        SeatMark.LOCK -> Lucide.Lock
        SeatMark.WAIT -> Lucide.Hourglass
    }

/** Draws what is inside in grey (no colour at all) at 45% strength. */
private fun Modifier.greyedOut(): Modifier =
    drawWithContent {
        val paint =
            Paint().apply {
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                alpha = 0.45f
            }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
            drawContent()
            canvas.restore()
        }
    }

/** A tile still to come: the frame, a 44dp square and two bars. */
@Composable
internal fun SeatTileSkeleton(
    nameWidth: Dp,
    lineWidth: Dp,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .tileFrame()
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Skeleton(width = 44.dp, height = 44.dp, corner = 13.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Skeleton(width = nameWidth, height = 14.dp)
            Spacer(Modifier.height(8.dp))
            Skeleton(width = lineWidth, height = 11.dp)
        }
    }
}
