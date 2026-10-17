package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Users
import kotlinx.coroutines.launch

/*
 * Putting an app up, from the tab that lists them.
 *
 * An app goes up one of two ways, a crew seat or a Circle request, and they live on the Crew tab
 * and on Circle. My apps lists what both sent and had no way to send anything, so the one tab
 * named after your apps was the one place you could not add one. This asks which way, once, and
 * hands over to the screen that already does it.
 */

/** The two ways an app goes up, each in a line, and on to the one picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddAppSheet(
    onCrew: () -> Unit,
    onFeedback: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // Hidden first, then left: a sheet removed while open vanishes on one frame.
    fun go(next: () -> Unit) {
        scope.launch {
            state.hide()
            onDismiss()
            next()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Gutter).navigationBarsPadding()) {
            Text("Add an app", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick what you need it for.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            TileGroup(count = 2) { index, place ->
                if (index == 0) {
                    TileRow(
                        Lucide.Users,
                        "Test it with a crew",
                        place,
                        subtitle = "14 testers for 14 days. Free.",
                    ) { go(onCrew) }
                } else {
                    TileRow(
                        Lucide.MessageSquare,
                        "Ask for feedback",
                        place,
                        subtitle = "Private reviews. From 20 tix.",
                    ) { go(onFeedback) }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** The way in on a list that already has apps: a pill over the bottom of the list. */
@Composable
internal fun AddAppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Primary("Add app", modifier, mark = rememberVectorPainter(Lucide.Plus), onClick = onClick)
}
