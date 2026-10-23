package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus

/** Which of the two things an app is being picked for. */
enum class PickFor { CREW, FEEDBACK }

/**
 * Choosing which app, from Crew's Take a seat or Circle's Ask for feedback.
 *
 * Every app lives on My apps now, so those buttons ask which one rather than for a link: the apps
 * that can go there, then a way to add a new one, which opens the add page.
 */
@Composable
internal fun AppPickerSheet(
    purpose: PickFor,
    apps: List<AppEntry>,
    /** My apps has not answered yet, so an empty list says nothing about what there is. */
    loading: Boolean,
    onPick: (AppEntry) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fits = apps.filter { if (purpose == PickFor.CREW) it.canCrew else it.canFeedback }
    val waiting = loading && fits.isEmpty()
    PickerSheet(
        title = if (purpose == PickFor.CREW) "Which app goes in?" else "Feedback on which app?",
        subtitle =
            when {
                waiting -> "Getting your apps."
                fits.isNotEmpty() -> "Your apps that can go now."
                apps.isEmpty() -> "You haven't added an app yet."
                purpose == PickFor.CREW ->
                    "None of your apps can go into a crew now: live apps can't, and one already in a crew waits."
                else -> "Each of your apps already has a request open."
            },
        onDismiss = onDismiss,
    ) { close ->
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            if (waiting) {
                repeat(2) {
                    Skeleton(height = 60.dp, corner = 16.dp)
                    Spacer(Modifier.height(3.dp))
                }
                Spacer(Modifier.height(20.dp))
                return@Column
            }
            TileGroup(count = fits.size + 1) { index, place ->
                if (index < fits.size) {
                    val app = fits[index]
                    PickRow(app, place) { close { onPick(app) } }
                } else {
                    TileRow(Lucide.Plus, "Add a new app", place, subtitle = "From this phone or Play") { close(onAdd) }
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
