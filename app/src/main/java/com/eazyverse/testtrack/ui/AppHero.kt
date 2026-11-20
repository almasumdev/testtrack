package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.FlaskConical
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The app at the top of its own page, and of the page that adds it: one hero for both.
 */

/** The icon's size in the hero; its tap area is 8dp wider, for the pencil on its corner. */
internal val AppHeroIcon = 88.dp

/**
 * The app, centred: its [icon] (drawn [AppHeroIcon] square), name, package and chips for where it
 * is found, with [chips] after them for what else is true of it. Tapping the icon changes it; the
 * pencil on its corner says so. With [onRename] the name is tappable too and carries a pencil.
 * [iconBusy] puts a loader on the icon while a new one is being read and sent. [below] is whatever
 * sits under the chips, such as the pill that says a closed test is live now.
 */
@Composable
internal fun AppHero(
    name: String,
    pkg: String,
    live: Boolean,
    onChangeIcon: () -> Unit,
    modifier: Modifier = Modifier,
    iconBusy: Boolean = false,
    onRename: (() -> Unit)? = null,
    icon: @Composable () -> Unit,
    chips: @Composable RowScope.() -> Unit,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(AppHeroIcon + 8.dp).iconTap(onChangeIcon)) {
            icon()
            if (iconBusy) {
                Box(
                    Modifier
                        .size(AppHeroIcon)
                        .clip(RoundedCornerShape(AppHeroIcon * 0.3f))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp, color = Tt.colors.accentInk) }
            }
            EditBadge(Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.height(12.dp))
        AppHeroName(name, onRename)
        Spacer(Modifier.height(4.dp))
        Text(
            pkg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip(
                if (live) "Live" else "Closed test",
                Tone.QUIET,
                if (live) Lucide.Globe else Lucide.FlaskConical,
                size = ChipSize.FACT,
            )
            chips()
        }
        below?.invoke(this)
    }
}

/** The app's name at 22sp, and with [onRename] a press shape round it with a pencil after it. */
@Composable
private fun AppHeroName(
    name: String,
    onRename: (() -> Unit)?,
) {
    val style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 26.sp)
    if (onRename == null) {
        Text(name, style = style, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        return
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Rename", onClick = onRename)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = style,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        Icon(Lucide.Pencil, null, Modifier.size(16.dp), tint = Tt.colors.accentInk)
    }
}

/** The pencil in a round on an icon's corner: this can be changed by tapping it. */
@Composable
internal fun EditBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Tt.colors.glassBorder, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(Lucide.Pencil, null, Modifier.size(15.dp), tint = Tt.colors.accentInk) }
}
