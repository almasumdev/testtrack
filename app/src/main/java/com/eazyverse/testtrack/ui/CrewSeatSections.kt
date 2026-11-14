package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The seat page's own sections: the testers group (add it to the closed track and ask Play), and the
 * two boxes only the app's owner can confirm, which stand right above the submit button.
 */

/**
 * The two boxes every member's install depends on, each with what goes wrong and where to fix it
 * while it is unticked. Both lines go once it is ticked.
 */
@Composable
internal fun CrewSeatBoxes(
    boxes: SeatBoxes,
    onChange: (SeatBoxes) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        BoxRow(SeatBoxCopy.COUNTRIES, boxes.countries, SeatBoxCopy.COUNTRIES_WHY, SeatBoxCopy.COUNTRIES_HOW) {
            onChange(boxes.copy(countries = it))
        }
        BoxRow(SeatBoxCopy.DEVICES, boxes.devices, SeatBoxCopy.DEVICES_WHY, SeatBoxCopy.DEVICES_HOW) {
            onChange(boxes.copy(devices = it))
        }
    }
}

@Composable
private fun BoxRow(
    label: String,
    on: Boolean,
    why: String,
    how: Array<String>,
    onChange: (Boolean) -> Unit,
) {
    val t = Tt.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = on, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = RoundedCornerShape(6.dp)
        Box(
            Modifier
                .size(22.dp)
                .clip(shape)
                .then(if (on) Modifier.background(t.accent) else Modifier.border(1.5.dp, t.glassBorderStrong, shape)),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon(Lucide.Check, null, Modifier.size(14.dp), tint = t.onAccent)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = tileStyle(15f, 20f, FontWeight.Medium), color = t.text)
    }
    if (!on) {
        // Lined up with the label: the box and its gap are 34dp.
        Row(Modifier.padding(start = 34.dp), verticalAlignment = Alignment.Top) {
            Icon(Lucide.TriangleAlert, null, Modifier.padding(top = 2.dp).size(13.dp), tint = t.warn)
            Spacer(Modifier.width(6.dp))
            Text(why, style = SmallLine, color = t.warn)
        }
        Text(boldParts(*how), Modifier.padding(start = 34.dp, top = 2.dp), style = SmallLine, color = t.text3)
        Spacer(Modifier.height(8.dp))
    }
}

private val SmallLine
    @Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp)

private val BodyLine
    @Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)

/** What the testers group section needs to draw and to ask. */
internal class SeatGroupState(
    /** Play does not show the app to the group yet, or a seat was just refused for it. */
    val open: Boolean,
    /** The line under the address: when Play was last asked and what it said. */
    val checkLine: String,
    val checkFailed: Boolean,
    val checking: Boolean,
    /** A failure that is not a check's answer, such as no connection; null when none. */
    val error: String?,
)

/** Testers group: what to add in Play Console and the check, or only its heading once Play shows the app to the group. */
@Composable
internal fun CrewSeatGroup(
    state: SeatGroupState,
    onVerify: () -> Unit,
) {
    val t = Tt.colors
    if (!state.open) {
        SeatHeading("Testers group", SeatStatus.ok("Play shows it"), gap = 0.dp)
        return
    }
    SeatHeading("Testers group", if (state.checkFailed) SeatStatus.warn("Not shown yet") else null)
    Text(
        "Crew members install your app through this Google Group. Play has to show your app to it first.",
        style = BodyLine,
        color = t.text2,
    )
    Spacer(Modifier.height(4.dp))
    GroupAddress(flat = true)
    GroupSteps("Roll out a release to that track.", textStyle = BodyLine)
    Spacer(Modifier.height(16.dp))
    if (state.checkFailed) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Lucide.TriangleAlert, null, Modifier.padding(top = 1.5.dp).size(14.dp), tint = t.warn)
            Spacer(Modifier.width(6.dp))
            Text(state.checkLine, style = SmallLine, color = t.warn)
        }
    } else {
        Text(state.checkLine, style = SmallLine, color = t.text3)
    }
    state.error?.let { Text(it, Modifier.padding(top = 8.dp), style = SmallLine, color = t.bad) }
    Spacer(Modifier.height(8.dp))
    SeatBar(
        if (state.checking) {
            "Asking Play"
        } else if (state.checkFailed) {
            "Check again"
        } else {
            "Verify on Play"
        },
        Lucide.RefreshCw,
        busy = state.checking,
        enabled = !state.checking,
        onClick = onVerify,
    )
}

/** Before you submit: the two boxes only the app's owner can confirm, and the server's words if it refused for them. */
@Composable
internal fun CrewSeatConfirm(
    boxes: SeatBoxes,
    boxesMessage: String?,
    onBoxes: (SeatBoxes) -> Unit,
) {
    val t = Tt.colors
    SeatHeading("Before you submit")
    Text(
        "You set both in Play Console. Every member needs them to install your app.",
        style = BodyLine,
        color = t.text2,
    )
    Spacer(Modifier.height(8.dp))
    CrewSeatBoxes(boxes, onBoxes)
    boxesMessage?.let { Text(it, Modifier.padding(top = 8.dp), style = SmallLine, color = t.warn) }
}

/**
 * The bar under a step: the Feed's Post bar, inline, in the sheet's own colour. Quiet while it is
 * not yet allowed, and busy while Play is being asked.
 */
@Composable
internal fun SeatBar(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(14.dp)
    val ink = if (enabled || busy) t.tabSelected else t.text4
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, t.cardLine, shape)
            .then(if (enabled && !busy) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ink)
            } else {
                Icon(icon, null, Modifier.size(20.dp), tint = ink)
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ink)
        }
    }
}
