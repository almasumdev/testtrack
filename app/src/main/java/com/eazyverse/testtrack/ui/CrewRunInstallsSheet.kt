package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lucide

/** The Run updates' install count opens just these apps, with the existing Play action. */
@Composable
internal fun CrewRunInstallsSheet(
    apps: List<CrewApp>,
    runs: List<Crew>,
    actions: CrewActions,
    onDismiss: () -> Unit,
) {
    PickerSheet(
        title = "Apps to install",
        onDismiss = onDismiss,
        leading = { IconTile(Lucide.Download, 36.dp, corner = 12.dp, glyph = 18.dp, tint = Tone.ACCENT.ink()) },
        skipHalf = true,
        fitMax = 0.85f,
    ) { close ->
        LazyColumn(
            Modifier.weight(1f, fill = false).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (apps.isEmpty()) item { Text("All these apps are ready. You can continue tonight's testing.") }
            items(apps, key = { it.id }) { app ->
                val seat = runs.asSequence().flatMap { it.seats.asSequence() }.firstOrNull { it.id == app.id }
                val install = {
                    close {
                        if (seat != null) actions.onInstall(seat) else actions.onUpdateApp(app)
                    }
                }
                HairlineRow(
                    leading = { AppMark(app.packageName, app.label, seat?.icon.orEmpty(), size = 44.dp) },
                    title = app.label,
                    note =
                        AnnotatedString(
                            if (actions.isInstalled(app.packageName)) "Install the Google Play copy" else "Not on your phone yet",
                        ),
                    onClick = install,
                    trailing = { RowPill("Install", PillKind.SOFT, onClick = install) },
                )
            }
        }
    }
}
