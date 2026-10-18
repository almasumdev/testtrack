package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
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

/** Where people find it: on a closed test, or live on Play. */
@Composable
internal fun SourceTag(live: Boolean) {
    val shape = RoundedCornerShape(percent = 50)
    Text(
        if (live) "Live" else "Closed test",
        Modifier
            .clip(shape)
            .border(1.dp, Tt.colors.glassBorderStrong, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
        fontWeight = FontWeight.SemiBold,
        color = Tt.colors.text2,
    )
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
    CrewCard(modifier, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppMark(app.pkg, app.name, app.icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    app.developer.ifBlank { app.pkg },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            SourceTag(app.live)
        }
        Spacer(Modifier.height(12.dp))
        val badges = app.badges()
        if (badges.isEmpty()) {
            Text(
                "Nothing running. Open it to put it in a crew or ask for feedback.",
                style = MaterialTheme.typography.bodySmall,
                color = Tt.colors.text3,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                badges.forEach { Badge(it) }
            }
        }
    }
}
