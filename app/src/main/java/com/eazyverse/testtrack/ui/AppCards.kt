package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * An app on My apps, drawn: its icon, its name and one line of what is happening to it. One flat
 * list of these; the pieces of a tile are in AppTileParts.
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
    /**
     * The owner's own screens: the icon chosen for the app wins over the copy on this phone, or an
     * owner who sets a picture keeps seeing the old icon and thinks it did not take. The installed
     * one still shows while the chosen one loads, and stands in if it cannot.
     */
    ownIcon: Boolean = false,
) {
    if (ownIcon && icon.isNotBlank()) {
        ShapedIconImage(
            model = icon,
            size = size,
            loading = { Skeleton(size, size, size * 0.3f) },
            error = { AppIcon(pkg, name, size = size) { AppTile(name, size) } },
        )
        return
    }
    AppIcon(pkg, name, size = size) {
        if (icon.isBlank()) {
            AppTile(name, size)
        } else {
            ShapedIconImage(
                model = icon,
                size = size,
                loading = { Skeleton(size, size, size * 0.3f) },
                error = { AppTile(name, size) },
            )
        }
    }
}

/**
 * One app on My apps: its icon with a mark for where people find it, its name on up to two lines
 * over one status line, and on the right a count of reviews to check or a mark for a problem.
 * Tapping the tile opens its page, where crews and feedback start.
 */
@Composable
internal fun AppCard(
    app: AppEntry,
    modifier: Modifier = Modifier,
    /** Whether this tile's problem callout is open, and how its mark opens or closes it. */
    problemOpen: Boolean = false,
    onProblemOpenChange: (Boolean) -> Unit = {},
    onOpen: () -> Unit,
) {
    val t = Tt.colors
    val status = app.statusLine()
    val problems = app.problems()
    val toCheck = app.toCheck
    // Scales with the text, but only up to 1.3x, so the column beside it keeps fitting.
    val icon = AppTileIcon * LocalDensity.current.fontScale.coerceIn(1f, 1.3f)
    Row(
        modifier
            .testTag(APP_TILE_TAG)
            .appTileFrame()
            .clickable(onClick = onOpen)
            .padding(AppTilePadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(icon)) {
            AppMark(app.pkg, app.name, app.icon, size = icon, ownIcon = true)
            SourceMark(app.live, Modifier.align(Alignment.BottomEnd).offset(5.dp, 5.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).testTag(APP_TEXT_TAG)) {
            Text(
                app.name,
                style = AppTileName,
                color = t.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            StatusRow(status)
        }
        if (problems.isNotEmpty() || toCheck > 0) {
            Spacer(Modifier.width(10.dp))
            if (problems.isNotEmpty()) {
                // Its 32dp target reaches 6dp into the padding, so the icon itself sits on the gutter.
                ProblemMark(app, problems, problemOpen, onProblemOpenChange, onOpen, Modifier.offset(x = 6.dp))
            } else {
                CountBadge(toCheck, cap = 99)
            }
        }
    }
}
