package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

internal val CrewUpdatePadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)

/** Compact update content inside the shared bordered tile. Supporting text starts flush left. */
@Composable
internal fun CrewUpdateRow(
    title: String,
    icon: ImageVector,
    tone: Tone = Tone.QUIET,
    note: AnnotatedString? = null,
    explanation: String? = null,
    action: String? = null,
    enabled: Boolean = true,
    onAction: () -> Unit = {},
) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ink = if (tone == Tone.QUIET) Tt.colors.text else tone.ink()
                    Icon(icon, null, Modifier.size(15.dp), tint = if (tone == Tone.QUIET) Tt.colors.text3 else ink)
                    Spacer(Modifier.width(6.dp))
                    Text(title, Modifier.weight(1f, fill = false), style = tileStyle(13f, 18f, FontWeight.Medium), color = ink)
                    explanation?.let { UpdateInfo(title, it) }
                }
                note?.let { Text(it, style = tileStyle(12f, 17f, FontWeight.Normal), color = Tt.colors.text3) }
            }
            if (!largeText && action != null) InlineAction(action, enabled = enabled, onClick = onAction)
        }
        if (largeText && action != null) InlineAction(action, Modifier.align(Alignment.End), enabled = enabled, onClick = onAction)
    }
}

@Composable
private fun UpdateInfo(
    title: String,
    explanation: String,
) {
    var open by remember(title) { mutableStateOf(false) }
    Box {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            IconButton(onClick = { open = !open }, modifier = Modifier.size(24.dp)) {
                Icon(Lucide.Info, "About $title", Modifier.size(14.dp), tint = Tt.colors.text3)
            }
        }
        Callout(open, { open = false }, title, iconInset = 5.dp) {
            Text(explanation, style = tileStyle(13f, 18f, FontWeight.Normal), color = Tt.colors.text2)
        }
    }
}
