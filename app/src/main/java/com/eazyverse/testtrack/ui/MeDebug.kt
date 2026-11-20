package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * Debug builds only, at the foot of You: pretend the account is outside the testers group, to walk
 * the Crew Open join steps from a phone that is in it. The caller draws it only when the build is
 * a debug one.
 */
@Composable
internal fun MeDebug(
    noGroup: Boolean,
    onNoGroup: (Boolean) -> Unit,
) {
    Column {
        SectionHeading("Debug", top = 16.dp)
        TileGroup(count = 1) { _, place ->
            TileRow(
                Lucide.Users,
                "Force not in testers group",
                place,
                subtitle = "Ignores the remembered yes",
                trailing = { DebugSwitch(noGroup, onNoGroup) },
                onClick = { onNoGroup(!noGroup) },
            )
        }
    }
}

@Composable
private fun DebugSwitch(
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val t = Tt.colors
    Switch(
        checked = on,
        onCheckedChange = onChange,
        colors =
            SwitchDefaults.colors(
                checkedThumbColor = t.onAccent,
                checkedTrackColor = t.accent,
                uncheckedThumbColor = t.text4,
                uncheckedTrackColor = t.track,
                uncheckedBorderColor = t.glassBorderStrong,
            ),
    )
}
