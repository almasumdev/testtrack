package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.composables.icons.lucide.FlaskConical
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * An app on My apps, drawn: its icon, where people find it, and the badges that say what is
 * happening to it. One flat list of these; the badges do the grouping a heading used to.
 */

/**
 * The app's icon: the one on this phone, else Play's, else its initial. The phone's comes first
 * because it is the picture every other screen draws for this app, wherever its owner is.
 */
@Composable
internal fun AppMark(
    pkg: String,
    name: String,
    icon: String,
    size: Dp = 44.dp,
) {
    AppIcon(pkg, name, size = size) {
        if (icon.isBlank()) {
            AppTile(name, size)
        } else {
            SubcomposeAsyncImage(
                model = icon,
                contentDescription = null,
                modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)),
                loading = { AppTile(name, size) },
                error = { AppTile(name, size) },
            )
        }
    }
}

/** A badge's colours: running in the accent, done green, needing a look amber, a problem red. */
@Composable
internal fun badgeColors(tone: BadgeTone): Pair<Color, Color> =
    when (tone) {
        BadgeTone.RUNNING -> Tt.colors.accentInk to Tt.colors.tile
        BadgeTone.DONE -> Status.posted to Status.postedSoft
        BadgeTone.ATTENTION -> Status.short to Status.shortSoft
        BadgeTone.PROBLEM -> Status.missed to Status.missedSoft
        BadgeTone.QUIET -> MaterialTheme.colorScheme.onSurfaceVariant to Status.neutralSoft
    }

@Composable
internal fun Badge(badge: AppBadge) {
    val (tone, background) = badgeColors(badge.tone)
    Text(
        badge.text,
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.5.sp),
        fontWeight = FontWeight.SemiBold,
        color = tone,
        maxLines = 1,
    )
}

/** One app on My apps. Tapping it opens its page, where crews and feedback start. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppCard(
    app: AppEntry,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    CrewCard(modifier, padding = PaddingValues(0.dp), onClick = onOpen) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppMark(app.pkg, app.name, app.icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val title = MaterialTheme.typography.titleSmall
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.name,
                        Modifier.weight(1f, fill = false),
                        style = title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    SourceIcon(app.live, size = with(LocalDensity.current) { title.fontSize.toDp() })
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    app.developer.ifBlank { app.pkg },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // What's happening, on a tinted band along the card's foot: set apart by colour, not a line.
        FlowRow(
            Modifier
                .fillMaxWidth()
                .background(Tt.colors.tile.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            app.tileBadges().forEach { Badge(it) }
        }
    }
}

/** Where people find it, as a mark beside the title: a flask for a closed test, a globe for live. */
@Composable
private fun SourceIcon(
    live: Boolean,
    size: Dp,
) {
    Icon(
        if (live) Lucide.Globe else Lucide.FlaskConical,
        contentDescription = if (live) "Live" else "Closed test",
        modifier = Modifier.size(size),
        tint = Tt.colors.text4,
    )
}
