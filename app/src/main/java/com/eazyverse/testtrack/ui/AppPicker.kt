package com.eazyverse.testtrack.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private enum class AppPickerPhase { OPENING, LOADING, READY }

/**
 * Choosing which app, from Circle's Ask for feedback. Crew's Take a seat has its own sheet,
 * [CrewSeatSheet], which also walks the app through what it needs.
 *
 * Every app lives on My apps now, so the button asks which one rather than for a link: the apps
 * that can go there, then a way to add a new one, which opens the add page.
 */
@Composable
internal fun AppPickerSheet(
    apps: List<AppEntry>,
    /** My apps has not answered yet, so an empty list says nothing about what there is. */
    loading: Boolean,
    onPick: (AppEntry) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fits = apps.filter { it.canFeedback }
    val waiting = loading && apps.isEmpty()
    val waitingNow by rememberUpdatedState(waiting)
    var phase by remember { mutableStateOf(if (waiting) AppPickerPhase.OPENING else AppPickerPhase.READY) }
    // Fast replies replace the quiet opening frame immediately, without ever starting shimmer.
    LaunchedEffect(waiting) {
        if (!waiting && phase == AppPickerPhase.OPENING) phase = AppPickerPhase.READY
    }
    LaunchedEffect(Unit) {
        if (phase == AppPickerPhase.READY) return@LaunchedEffect
        delay(180)
        if (!waitingNow || phase == AppPickerPhase.READY) return@LaunchedEffect
        phase = AppPickerPhase.LOADING
        // Once visible, don't flash the skeleton for only a frame or two.
        delay(300)
        snapshotFlow { waitingNow }.first { !it }
        phase = AppPickerPhase.READY
    }
    PickerSheet(
        title = "Feedback on which app?",
        subtitle =
            when {
                phase != AppPickerPhase.READY || fits.isNotEmpty() -> "Your apps that can go now."
                apps.isEmpty() -> "You haven't added an app yet."
                else -> "Each of your apps already has a request open."
            },
        onDismiss = onDismiss,
        skipHalf = true,
    ) { close ->
        Crossfade(
            targetState = phase,
            modifier = Modifier.fillMaxWidth().animateContentSize(tween(180)).heightIn(max = 560.dp),
            animationSpec = tween(180),
            label = "app-picker-load",
        ) { shown ->
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when (shown) {
                    AppPickerPhase.OPENING -> Spacer(Modifier.height(180.dp))
                    AppPickerPhase.LOADING -> {
                        // A short picker, not a whole page of placeholders that abruptly shrinks.
                        repeat(3) {
                            Skeleton(height = AppTileIcon + AppTilePadding * 2, corner = TileRadius)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    AppPickerPhase.READY -> {
                        fits.forEach { app ->
                            PickRow(app) { close { onPick(app) } }
                            Spacer(Modifier.height(8.dp))
                        }
                        Outlined(
                            "Add a new app",
                            Modifier.fillMaxWidth(),
                            icon = { Icon(Lucide.Plus, null, Modifier.size(18.dp)) },
                        ) { close(onAdd) }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** The same outline and source mark as My apps, with the package ID in place of its status. */
@Composable
private fun PickRow(
    app: AppEntry,
    onClick: () -> Unit,
) {
    // Two 19sp title lines and one 14sp package line fit the icon, also at larger text sizes.
    AppHead(
        app.pkg,
        app.name,
        app.icon,
        app.live,
        app.pkg,
        Modifier
            .appTileFrame()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(AppTilePadding),
        ownIcon = true,
        trailing = { Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = Tt.colors.text4) },
        subGap = 0.dp,
        subStyle = TextStyles.DeveloperStyle,
    )
}
