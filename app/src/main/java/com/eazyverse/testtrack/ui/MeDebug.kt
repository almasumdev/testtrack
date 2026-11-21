package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.Wifi
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * Debug builds only, at the foot of You: pretend the account is outside the testers group, to walk
 * the Crew Open join steps from a phone that is in it, and show each kind of snack in turn. The
 * caller draws it only when the build is a debug one.
 */
@Composable
internal fun MeDebug(
    noGroup: Boolean,
    onNoGroup: (Boolean) -> Unit,
) {
    var shown by remember { mutableIntStateOf(0) }
    Column {
        SectionHeading("Debug", top = 16.dp, style = TextStyles.SectionTitleStyle)
        TileGroup(count = 2) { index, place ->
            if (index == 0) {
                TileRow(
                    Lucide.Users,
                    "Force not in testers group",
                    place,
                    subtitle = "Ignores the remembered yes",
                    trailing = { DebugSwitch(noGroup, onNoGroup) },
                    onClick = { onNoGroup(!noGroup) },
                )
            } else {
                TileRow(
                    Lucide.Layers,
                    "Show a snack",
                    place,
                    subtitle = "Each tap shows the next kind",
                    onClick = {
                        Snacks.show(DebugSnacks[shown % DebugSnacks.size])
                        shown++
                    },
                )
            }
        }
    }
}

/** Every tone, as the design page shows them: offline stays until Back online replaces it. */
private val DebugSnacks =
    listOf(
        Snack("Refused. Three members will decide it."),
        Snack("Accepted. Tania has been paid.", SnackTone.DONE),
        Snack("Couldn't add it. Check your connection.", SnackTone.ERROR, SnackAction(Snacks.TRY_AGAIN) {}),
        Snack("You're offline. The chat catches up once you're back.", SnackTone.OFFLINE, sticky = true, key = "debug-offline"),
        Snack("Back online.", SnackTone.DONE, key = "debug-offline", icon = Lucide.Wifi),
        Snack("Your new screenshot replaced the one before. Hide anything personal again."),
    )

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
