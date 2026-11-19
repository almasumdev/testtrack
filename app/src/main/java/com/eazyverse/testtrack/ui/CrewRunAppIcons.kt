package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * The crew's other apps in a stable order, each marked for tonight: a check when opened, a dash
 * when reported, plain while still owed, and faint when tonight owes it nothing. [quiet] fades the
 * whole strip, for a reader whose own app is out.
 */
@Composable
internal fun CrewRunAppIcons(
    crew: Crew,
    quiet: Boolean = false,
) {
    val apps = crew.strip
    if (apps.isEmpty()) return
    val owedIds = remember(crew.apps) { crew.apps.mapTo(HashSet()) { it.id } }
    val seatsById = remember(crew.seats) { crew.seats.associateBy { it.id } }
    val seatsByPackage = remember(crew.seats) { crew.seats.filter { it.packageName.isNotBlank() }.associateBy { it.packageName } }
    val opened = apps.count { it.done }
    val reported = apps.count { it.reported && !it.done }
    Box(Modifier.alpha(if (quiet) 0.45f else 1f)) {
        CrewAppIconStrip(apps.size, "Tonight's apps: $opened opened, $reported reported, ${crew.owed} left.") { side ->
            apps.forEach { app ->
                key(app.id) {
                    val seat = seatsById[app.id] ?: seatsByPackage[app.packageName]
                    val faint = !app.done && (app.reported || app.id !in owedIds)
                    Box(Modifier.size(side)) {
                        Box(Modifier.alpha(if (faint) 0.4f else 1f)) {
                            AppMark(app.packageName, app.label, seat?.icon.orEmpty(), size = side)
                        }
                        if (app.done) {
                            Corner(Lucide.Check, Tone.OK.ink(), side)
                        } else if (app.reported) {
                            Corner(Lucide.Minus, Tone.WARN.ink(), side)
                        }
                    }
                }
            }
        }
    }
}

/** A small round mark on an app's bottom-right corner. */
@Composable
private fun BoxScope.Corner(
    icon: ImageVector,
    ink: Color,
    side: Dp,
) {
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .size((side * 0.48f).coerceAtMost(10.dp))
            .clip(CircleShape)
            .background(ink),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(8.dp), tint = Tt.colors.inkOn(ink)) }
}

@Composable
internal fun CrewRunAppIconsLoading() {
    CrewAppIconStrip(CrewRules.NIGHTLY) { side ->
        repeat(CrewRules.NIGHTLY) { Skeleton(width = side, height = side, corner = side * 0.3f) }
    }
}
