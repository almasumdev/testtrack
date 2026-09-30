package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The profile's figures: the chips under the name, the points card and the crews strip.
 * Split from MeScreen.kt, which draws the page around them.
 */

/** A small fact under the name: an icon and a few words. The board place is lit in the accent. */
@Composable
internal fun ProfileChip(
    icon: ImageVector,
    text: String,
    lit: Boolean = false,
) {
    val t = Tt.colors
    val ink = if (lit) t.tileInk else t.text2
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (lit) t.tile else t.glass2)
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(12.dp), tint = ink)
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
            fontWeight = FontWeight.SemiBold,
            color = ink,
            maxLines = 1,
        )
    }
}

/**
 * The score, as the one big thing on the page.
 *
 * Points are what the board ranks and what the level is built from, so they lead: one large figure
 * with the place beside it, and the two kinds of work that add up to it written small underneath.
 * An account that has done nothing is not given a place; it is told how to earn one.
 */
@Composable
internal fun PointsHero(
    points: Int,
    place: Int,
    opens: Int,
    reviews: Int,
) {
    val t = Tt.colors
    // Plain on the page, no box: the figure is large enough to hold the space on its own, and a
    // card round it with the place in a second card inside was a box in a box.
    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                Text(
                    "$points",
                    Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 52.sp, fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = t.accentInk,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "points",
                    Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = t.text2,
                )
            }
            PlaceBadge(points, place)
        }
        Spacer(Modifier.height(10.dp))
        if (points == 0) {
            Text(
                "Open somebody's app on a testing night and this starts counting.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                color = t.text2,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Figure("$opens")
                Words(if (opens == 1) " app opened" else " apps opened")
                Words("  ·  ")
                Figure("$reviews")
                Words(if (reviews == 1) " review given" else " reviews given")
            }
        }
    }
}

/** Where the points put you, as plain words beside them. */
@Composable
private fun PlaceBadge(
    points: Int,
    place: Int,
) {
    val t = Tt.colors
    val ranked = points > 0
    Column(horizontalAlignment = Alignment.End) {
        Text(
            if (ranked) ordinal(place) else "–",
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = t.accentInk,
        )
        Text(
            if (ranked) "on the board" else "not ranked",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = t.text3,
        )
    }
}

@Composable
private fun Figure(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.Bold,
        color = Tt.colors.text,
    )
}

@Composable
private fun Words(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        color = Tt.colors.text2,
    )
}

/**
 * The crews, as one card of three figures side by side.
 *
 * Quieter than the points above it on purpose: the same card surface as everything else, figures in
 * plain ink, hairlines between them.
 */
@Composable
internal fun CrewStrip(
    running: Int,
    finished: Int,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(CardRadius)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(t.glass)
            .border(1.dp, t.glassBorder, shape)
            .padding(vertical = 14.dp),
    ) {
        listOf(
            "$running" to "going on",
            "$finished" to "finished",
            "${CrewRules.nightly(running)}" to "apps a night",
        ).forEachIndexed { index, (value, label) ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(t.hair),
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = t.text,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = t.text3,
                    maxLines = 1,
                )
            }
        }
    }
}
