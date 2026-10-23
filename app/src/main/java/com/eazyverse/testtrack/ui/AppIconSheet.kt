package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Store

/**
 * Where an app's icon comes from: the one it has on this phone, a picture, or Play's.
 *
 * Offered while adding an app picked from the phone, and on the app's own page afterwards. The
 * phone's choice only where the app is installed here, Play's only where it is asked for.
 */
@Composable
internal fun AppIconSheet(
    name: String,
    installedHere: Boolean,
    onPhone: () -> Unit,
    onPicture: () -> Unit,
    onPlay: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val rows =
        buildList {
            if (installedHere) add(Source(Lucide.Smartphone, "Use the icon on this phone", "$name is installed here", onPhone))
            add(Source(Lucide.Image, "Choose a picture", "A square picture works best", onPicture))
            onPlay?.let { add(Source(Lucide.Store, "Use the icon from Play", "Once Play shows your app", it)) }
        }
    PickerSheet(title = "App icon", subtitle = "Shown to everyone who sees this app.", onDismiss = onDismiss) { close ->
        TileGroup(count = rows.size) { index, place ->
            val row = rows[index]
            TileRow(
                icon = row.icon,
                title = row.title,
                place = place,
                subtitle = row.subtitle,
                onClick = { close(row.action) },
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** One place an icon can come from, as a row of the sheet. */
private class Source(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val action: () -> Unit,
)

/** An app the server has no icon for, so everybody else sees a letter: the way to give it one. */
@Composable
internal fun IconNudge(
    name: String,
    onAdd: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Status.shortSoft)
            .border(1.dp, Status.short.copy(alpha = 0.28f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTile(name, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Add an icon", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "So testers know your app at a glance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Secondary("Add", onClick = onAdd)
    }
}

/**
 * Tapping an app's icon to change it, without the ripple. The tap area is the square round the
 * icon and its pencil, so a ripple flashed a hard grey square over a rounded icon; what changes
 * next, the gallery or the sheet, is the answer to the tap.
 */
@Composable
internal fun Modifier.iconTap(onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        role = Role.Button,
        onClickLabel = "Change icon",
        onClick = onClick,
    )
