package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.eazyverse.testtrack.ui.theme.Tt

/** Plain app marks in a stable order; per-app completion belongs in the crew view. */
@Composable
internal fun CrewRunAppIcons(crew: Crew) {
    if (crew.apps.isEmpty()) return
    val apps = crew.apps
    val seatsById = remember(crew.seats) { crew.seats.associateBy { it.id } }
    val seatsByPackage = remember(crew.seats) { crew.seats.filter { it.packageName.isNotBlank() }.associateBy { it.packageName } }
    val opened = apps.count { it.done }
    val reported = apps.count { it.reported && !it.done }
    CrewAppIconStrip(apps.size, "Tonight's apps: $opened opened, $reported reported, ${crew.owed} left.") { side ->
        apps.forEach { app ->
            key(app.id) {
                val seat = seatsById[app.id] ?: seatsByPackage[app.packageName]
                Box(Modifier.size(side)) {
                    Box(Modifier.alpha(if (app.reported && !app.done) 0.4f else 1f)) {
                        AppMark(
                            app.packageName,
                            app.label,
                            seat?.icon.orEmpty(),
                            size = side,
                        )
                    }
                    if (app.reported && !app.done) {
                        val ink = Tone.WARN.ink()
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size((side * 0.48f).coerceAtMost(10.dp))
                                .clip(CircleShape)
                                .background(ink),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Lucide.Minus, null, Modifier.size(8.dp), tint = Tt.colors.inkOn(ink)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CrewRunAppIconsLoading() {
    CrewAppIconStrip(CrewRules.NIGHTLY) { side ->
        repeat(CrewRules.NIGHTLY) { Skeleton(width = side, height = side, corner = side * 0.3f) }
    }
}
