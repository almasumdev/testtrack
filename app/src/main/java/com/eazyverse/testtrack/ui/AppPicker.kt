package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import kotlinx.coroutines.launch

/** Which of the two things an app is being picked for. */
enum class PickFor { CREW, FEEDBACK }

/**
 * Choosing which app, from Crew's Take a seat or Circle's Ask for feedback.
 *
 * Every app lives on My apps now, so those buttons ask which one rather than for a link: the apps
 * that can go there, then a way to add a new one, which opens the add page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppPickerSheet(
    purpose: PickFor,
    apps: List<AppEntry>,
    onPick: (AppEntry) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val fits = apps.filter { if (purpose == PickFor.CREW) it.canCrew else it.canFeedback }

    // Hidden first, then left: a sheet removed while open vanishes on one frame.
    fun go(next: () -> Unit) {
        scope.launch {
            state.hide()
            onDismiss()
            next()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter)
                .navigationBarsPadding(),
        ) {
            Text(
                if (purpose == PickFor.CREW) "Which app goes in?" else "Feedback on which app?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    fits.isNotEmpty() -> "Your apps that can go now."
                    purpose == PickFor.CREW ->
                        "None of your apps can go into a crew now: live apps can't, and one already in a crew waits."
                    else -> "Each of your apps already has a request open."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            TileGroup(count = fits.size + 1) { index, place ->
                if (index < fits.size) {
                    val app = fits[index]
                    PickRow(app, place) { go { onPick(app) } }
                } else {
                    TileRow(Lucide.Plus, "Add a new app", place, subtitle = "Find it on Play, then choose") { go(onAdd) }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** An app to pick, drawn as a settings row with its own icon. */
@Composable
private fun PickRow(
    app: AppEntry,
    place: TilePlace,
    onClick: () -> Unit,
) {
    TileRow(
        icon = Lucide.Plus,
        title = app.name,
        place = place,
        subtitle = if (app.live) "Live · ${app.pkg}" else "Closed test · ${app.pkg}",
        leading = { AppMark(app.pkg, app.name, app.icon, size = 36.dp, ownIcon = true) },
        onClick = onClick,
    )
}
