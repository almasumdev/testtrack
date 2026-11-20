package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert

/** The compact summary's View action names each blocker and keeps its existing recovery action. */
@Composable
internal fun CrewRunAttentionSheet(
    attention: CrewRunAttention,
    runs: List<Crew>,
    actions: CrewActions,
    onOpenCrew: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    PickerSheet(
        title =
            when (attention.kind) {
                CrewRunAttentionKind.FEEDBACK -> "Apps needing feedback"
                CrewRunAttentionKind.UPDATE -> "Apps to update"
                CrewRunAttentionKind.MIXED -> "Apps needing attention"
            },
        onDismiss = onDismiss,
        leading = { IconTile(Lucide.TriangleAlert, 36.dp, Tone.WARN, corner = 12.dp, glyph = 18.dp, tint = Tone.WARN.ink()) },
        skipHalf = true,
        fitMax = 0.85f,
    ) { close ->
        LazyColumn(
            Modifier.weight(1f, fill = false).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (attention.apps.isEmpty()) item { Text("These apps are ready for testing.") }
            items(attention.apps, key = { it.id }) { app ->
                val crew = runs.firstOrNull { run -> run.apps.any { it.id == app.id } }
                val seat = crew?.seats?.firstOrNull { it.id == app.id } ?: CrewSeat(app.id, app.label, app.owner, app.packageName)
                val state = crewRunAppState(app, seat, CrewRunAppKind.REQUIRED, actions)
                val act = { close { if (state.action != null) state.run() else crew?.let { onOpenCrew(it.id) } } }
                HairlineRow(
                    leading = { AppMark(app.packageName, app.label, seat.icon, size = 44.dp) },
                    title = app.label,
                    note = AnnotatedString(state.text),
                    onClick = act,
                    trailing = { RowPill(state.action ?: "View crew", PillKind.SOFT, busy = actions.busySeat == app.id, onClick = act) },
                )
            }
        }
    }
}
