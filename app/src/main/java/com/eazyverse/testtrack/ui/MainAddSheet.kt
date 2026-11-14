package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Users

/** The shell's Add menu, in the same frame and rows as the app and crew pickers. */
@Composable
internal fun MainAddSheet(
    onAddApp: () -> Unit,
    onAsk: () -> Unit,
    onTakeSeat: () -> Unit,
    onDismiss: () -> Unit,
) {
    PickerSheet(title = "Add", onDismiss = onDismiss, skipHalf = true) { close ->
        TileGroup(count = 3) { index, place ->
            when (index) {
                0 -> TileRow(Lucide.Plus, "Add an app", place, subtitle = "From this phone or Play") { close(onAddApp) }
                1 ->
                    TileRow(
                        Lucide.MessageSquare,
                        "Ask for feedback",
                        place,
                        subtitle = "On one of your apps, paid in tix",
                    ) { close(onAsk) }
                else ->
                    TileRow(
                        Lucide.Users,
                        "Take a seat",
                        place,
                        subtitle = "See the crew and what you need to join",
                    ) { close(onTakeSeat) }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
