package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * The crew's other apps in a stable order, each marked for tonight: a check when opened, dimmed
 * with a dash when reported, plain while still owed, and faint when tonight owes it nothing. Apps
 * taken out of the run come after, faint with a cross. [quiet] fades every icon the same way, for
 * a reader whose own app is out; the marks stay full.
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
    // Apps taken out are drawn but never counted.
    val inRun = apps.filter { !it.out }
    val opened = inRun.count { it.done }
    val reported = inRun.count { it.reported && !it.done }
    val out = apps.size - inRun.size
    val said = "Tonight's apps: $opened opened, $reported reported, ${crew.owed} left" + if (out > 0) ", $out out." else "."
    CrewAppIconStrip(apps.size, said) { side ->
        apps.forEach { app ->
            key(app.id) {
                val seat = seatsById[app.id] ?: seatsByPackage[app.packageName]
                // One fade, never two: an out reader owes nothing, so stacking [quiet] on the
                // not-owed fade left the icons at under a fifth and the strip read as missing.
                // An app taken out takes the same single fade, so it stays legible under [quiet].
                val faint = quiet || app.out || (!app.done && (app.reported || app.id !in owedIds))
                Box(Modifier.size(side)) {
                    Box(Modifier.alpha(if (faint) FAINT else 1f)) {
                        AppMark(app.packageName, app.label, seat?.icon.orEmpty(), size = side)
                    }
                    when {
                        app.out -> Corner(Lucide.X, Tt.colors.text3)
                        app.done -> Corner(Lucide.Check, Tt.colors.ok)
                        app.reported -> Corner(Lucide.Minus, Tt.colors.warn)
                    }
                }
            }
        }
    }
}

/**
 * A small round mark on an app's bottom-end corner, set 2dp past the icon, with a ring in the
 * card's colour so it reads apart from the art under it.
 */
@Composable
private fun BoxScope.Corner(
    icon: ImageVector,
    fill: Color,
) {
    val t = Tt.colors
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            // The ring sits outside the mark, so the mark itself ends 2dp past the icon.
            .offset(x = MarkPast + MarkRing, y = MarkPast + MarkRing)
            .size(MarkSize + MarkRing * 2)
            .clip(CircleShape)
            .background(t.bg)
            .padding(MarkRing)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(8.dp), tint = t.inkOn(fill)) }
}

private const val FAINT = 0.45f
private val MarkSize = 11.dp
private val MarkRing = 1.5.dp
private val MarkPast = 2.dp

@Composable
internal fun CrewRunAppIconsLoading() {
    CrewAppIconStrip(CrewRules.NIGHTLY) { side ->
        repeat(CrewRules.NIGHTLY) { Skeleton(width = side, height = side, corner = side * 0.3f) }
    }
}
