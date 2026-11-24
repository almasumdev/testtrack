package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import com.eazyverse.testtrack.domain.pluralOf
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The profile's figures: the stat cards of the record grid.
 * Split from MeScreen.kt, which draws the page around them.
 */

/**
 * What this account has done, as a two-column grid of small stat cards.
 *
 * Each card is one fact read the same way: a tinted icon on the left, the figure and what it counts
 * on the right. Six of them in three rows scan in one look, which a big number with a pill and a
 * sentence under it did not; and the crews sit in the same grid instead of a second block of their
 * own. The rank is in gold, the one medal colour, and for now only says Ranks are coming back; the
 * rest use the plain tile. The two cards of a row grow together when a label takes two lines at large text.
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
) {
    val t = Tt.colors
    val ranked = points > 0
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatPair {
            StatCard(
                Lucide.Trophy,
                if (ranked) "#$place" else "New",
                if (ranked) "Rank" else "Not ranked yet",
                tint = t.gold,
                onClick = { Snacks.info("Ranks are coming back in a later update.") },
                chevron = false,
            )
            StatCard(Lucide.Star, "$points", "Points")
        }
        StatPair {
            StatCard(Lucide.Smartphone, "$opens", "${pluralOf(opens, "App")} opened")
            StatCard(Lucide.MessageSquareText, "$reviews", "${pluralOf(reviews, "Review")} accepted")
        }
        StatPair {
            StatCard(Lucide.Users, "$crewsRunning", "${pluralOf(crewsRunning, "Crew")} running")
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
        StatPair {
            StatCard(Lucide.Trophy, null, "Rank", tint = t.gold)
            StatCard(Lucide.Star, null, "Points")
        }
        StatPair {
            StatCard(Lucide.Smartphone, null, "Apps opened")
            StatCard(Lucide.MessageSquareText, null, "Reviews accepted")
        }
        StatPair {
            StatCard(Lucide.Users, null, "Crews running")
            StatCard(Lucide.Moon, null, "Apps a night")
        }
    }
}

/** Two stat cards side by side, both as tall as the taller one. */
@Composable
private fun StatPair(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/**
 * One stat: a tinted icon, the figure, and its label, side by side on a card. A null [value] is
 * one still on its way, drawn as a bar the figure's height.
 *
 * [tint] colours the icon and its tile; left alone it is the palette's own tile blue. A card with
 * [onClick] shows a chevron so it reads as something that goes somewhere, unless [chevron] is off.
 */
@Composable
private fun RowScope.StatCard(
    icon: ImageVector,
    value: String?,
    label: String,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = onClick != null,
) {
    val t = Tt.colors
    val ink = tint ?: t.tileInk
    Row(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .tileFrame(onClick = onClick)
            .padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, 36.dp, corner = 11.dp, glyph = 18.dp, tint = ink, ground = tint?.copy(alpha = 0.16f))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (value == null) {
                Box(Modifier.height(22.dp), contentAlignment = Alignment.CenterStart) {
                    Skeleton(width = 44.dp, height = 18.dp)
                }
            } else {
                Text(value, style = TextStyles.RecordValue, color = t.text, maxLines = 1)
            }
            Text(
                label,
                style = TextStyles.RecordLabel,
                color = t.text3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(Lucide.ChevronRight, "Open", Modifier.size(18.dp), tint = t.text4)
        }
    }
}

/**
 * [Who] before the profile has arrived: the face's ring, then the name, address and facts as bars,
 * the facts in the column beside the face as they are once it loads.
 */
@Composable
internal fun WhoLoading() {
    val t = Tt.colors
    Row(Modifier.padding(start = 2.dp, top = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Spacer(Modifier.width(WhoGap))
        Column(Modifier.weight(1f)) {
            Skeleton(width = 150.dp, height = 18.dp)
            Spacer(Modifier.height(7.dp))
            Skeleton(width = 180.dp, height = 12.dp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Skeleton(width = 104.dp, height = 22.dp, corner = 11.dp)
                Skeleton(width = 90.dp, height = 22.dp, corner = 11.dp)
            }
        }
    }
}
