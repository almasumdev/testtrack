package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * An app's icon on its own page and on the way in: the nudge for one that has none, and the tap
 * that changes it.
 */

/**
 * An app the server has no icon for, so everybody else sees a letter: one tap to give it one. The
 * icon it has on this phone where it is installed here, else a picture.
 */
@Composable
internal fun IconNudge(
    name: String,
    installedHere: Boolean,
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
                if (installedHere) "We'll use the icon it has on this phone." else "Choose a square picture for it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Secondary(if (installedHere) "Use it" else "Choose", onClick = onAdd)
    }
}

/**
 * Tapping an app's icon to change it, without the ripple. The tap area is the square round the
 * icon and its pencil, so a ripple flashed a hard grey square over a rounded icon; what changes
 * next, the gallery opening, is the answer to the tap.
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
