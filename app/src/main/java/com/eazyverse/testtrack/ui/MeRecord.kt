package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquareText
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Trophy
import com.composables.icons.lucide.Users
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
 * What this account has done, as a two-column grid of small stat cards.
 *
 * Each card is one fact read the same way: a tinted icon on the left, the figure and what it counts
 * on the right. Six of them in three rows scan in one look, which a big number with a pill and a
 * sentence under it did not; and the crews sit in the same grid instead of a second block of their
 * own. The rank is in gold, the one medal colour, and opens the Ranks tab.
 */
@Composable
internal fun RecordGrid(
    points: Int,
    place: Int,
    opens: Int,
    reviews: Int,
    crewsRunning: Int,
    /** Crews that have filled: a crew still filling owes nothing a night yet. */
    crewsFilled: Int = crewsRunning,
    onOpenRanks: () -> Unit = {},
) {
    val t = Tt.colors
    val ranked = points > 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                Lucide.Trophy,
                if (ranked) "#$place" else "–",
                if (ranked) "Rank" else "Not ranked yet",
                tint = t.gold,
                onClick = if (ranked) onOpenRanks else null,
            )
            StatCard(Lucide.Star, "$points", "Points", tint = t.accentInk)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(Lucide.Smartphone, "$opens", if (opens == 1) "App opened" else "Apps opened")
            StatCard(Lucide.MessageSquareText, "$reviews", if (reviews == 1) "Review accepted" else "Reviews accepted")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(Lucide.Users, "$crewsRunning", if (crewsRunning == 1) "Crew going on" else "Crews going on")
            StatCard(Lucide.Moon, "${CrewRules.nightly(crewsFilled)}", "Apps a night")
        }
        if (!ranked) {
            Text(
                "Open the apps in your crew, or get a review accepted in Circle, and this starts " +
                    "counting.",
                Modifier.padding(top = 4.dp, start = 2.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                color = t.text3,
            )
        }
    }
}

/**
 * [RecordGrid] before the figures have arrived. The cards, their icons and what each one counts
 * are the same for everybody, so they are drawn for real; only the figures shimmer.
 */
@Composable
internal fun RecordGridLoading() {
    val t = Tt.colors
    Column(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "Loading your record" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(Lucide.Trophy, null, "Rank", tint = t.gold)
            StatCard(Lucide.Star, null, "Points", tint = t.accentInk)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(Lucide.Smartphone, null, "Apps opened")
            StatCard(Lucide.MessageSquareText, null, "Reviews accepted")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(Lucide.Users, null, "Crews going on")
            StatCard(Lucide.Moon, null, "Apps a night")
        }
    }
}

/**
 * One stat: a tinted icon, the figure, and its label, side by side on a card. A null [value] is
 * one still on its way, drawn as a bar the figure's height.
 *
 * [tint] colours the icon and its tile; left alone it is the palette's own tile blue. A card with
 * [onClick] shows a chevron so it reads as something that goes somewhere.
 */
@Composable
private fun RowScope.StatCard(
    icon: ImageVector,
    value: String?,
    label: String,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(CardRadius)
    val ink = tint ?: t.tileInk
    Row(
        Modifier
            .weight(1f)
            .clip(shape)
            .background(t.glass)
            .border(1.dp, t.glassBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (tint == null) t.tile else tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(19.dp), tint = ink)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (value == null) {
                Box(Modifier.height(22.dp), contentAlignment = Alignment.CenterStart) {
                    Skeleton(width = 44.dp, height = 18.dp)
                }
            } else {
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 22.sp, fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = t.text,
                    maxLines = 1,
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
                color = t.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Icon(Lucide.ChevronRight, "Open", Modifier.size(16.dp), tint = t.text4)
        }
    }
}

/** [Who] before the profile has arrived: the face's ring, then the name, address and facts as bars. */
@Composable
internal fun WhoLoading() {
    val t = Tt.colors
    Column(Modifier.padding(horizontal = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(FaceSize)
                    .clip(CircleShape)
                    .background(t.glass)
                    .border(1.dp, t.glassBorder, CircleShape)
                    .padding(3.dp),
            ) {
                Skeleton(width = FaceSize - 6.dp, height = FaceSize - 6.dp, corner = FaceSize / 2)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Skeleton(width = 140.dp, height = 18.dp)
                Spacer(Modifier.height(8.dp))
                Skeleton(width = 190.dp, height = 12.dp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Skeleton(width = 104.dp, height = 22.dp, corner = 11.dp)
            Skeleton(width = 92.dp, height = 22.dp, corner = 11.dp)
        }
    }
}
