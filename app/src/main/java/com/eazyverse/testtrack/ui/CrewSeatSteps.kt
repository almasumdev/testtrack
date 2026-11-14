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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * An app's own steps inside the seat sheet: add the group to its closed track and ask Play, then
 * the two boxes only its owner can confirm. Plain rows with the step in hand open; the others are
 * one line each. The seat details are the step after, and have their own view.
 */

/**
 * The two boxes every member's install depends on, each with what goes wrong and where to fix it
 * while it is unticked. Both lines go once it is ticked. Shared by the seat sheet and the app's page.
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
    how: String,
    onChange: (Boolean) -> Unit,
) {
    val t = Tt.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .heightIn(min = 44.dp)
            .toggleable(value = on, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = RoundedCornerShape(6.dp)
        Box(
            Modifier
                .size(20.dp)
                .clip(shape)
                .then(if (on) Modifier.background(t.accent) else Modifier.border(1.5.dp, t.glassBorderStrong, shape)),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon(Lucide.Check, null, Modifier.size(13.dp), tint = t.onAccent)
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp), color = t.text)
    }
    if (!on) {
        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Top) {
            Icon(Lucide.TriangleAlert, null, Modifier.padding(top = 2.dp).size(13.dp), tint = t.warn)
            Spacer(Modifier.width(5.dp))
            Text(why, style = SmallLine, color = t.warn)
        }
        Text(how, Modifier.padding(top = 3.dp), style = SmallLine, color = t.text3)
    }
}

private val SmallLine
    @Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp)

/** What a steps view needs to draw and to ask. */
internal class SeatStepsState(
    val app: AppEntry,
    val boxes: SeatBoxes,
    /** Step one is the open one: Play does not show the app to the group yet, or a seat was just refused for it. */
    val groupOpen: Boolean,
    /** The line under the address: when Play was last asked and what it said. */
    val checkLine: String,
    val checkFailed: Boolean,
    val checking: Boolean,
    /** A failure that is not a check's answer, such as no connection; null when none. */
    val error: String?,
    /** The server's refusal of a seat for the boxes, said above the bar. */
    val boxesMessage: String?,
)

/** The steps for one app that is not ready, down to the bar that moves it on. */
@Composable
internal fun CrewSeatSteps(
    state: SeatStepsState,
    onVerify: () -> Unit,
    onBoxes: (SeatBoxes) -> Unit,
    onContinue: () -> Unit,
) {
    val t = Tt.colors
    if (state.groupOpen) {
        OpenStep(1, "Add the group to its closed track") {
            Text(
                "In Play Console, add this group as testers on your app's closed track, then roll out a release to it.",
                Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = t.text2,
            )
            Spacer(Modifier.height(10.dp))
            GroupAddress(flat = true)
            if (state.checkFailed) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Lucide.TriangleAlert, null, Modifier.padding(top = 1.dp).size(14.dp), tint = t.warn)
                    Spacer(Modifier.width(6.dp))
                    Text(state.checkLine, style = SmallLine, color = t.warn)
                }
            } else {
                Text(state.checkLine, Modifier.padding(top = 10.dp), style = SmallLine, color = t.text3)
            }
            state.error?.let { Text(it, Modifier.padding(top = 6.dp), style = SmallLine, color = t.bad) }
            Spacer(Modifier.height(12.dp))
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
        TodoStep(2, "Confirm two things in Play Console")
        TodoStep(3, "Seat details")
    } else {
        DoneStep("Play shows it to the group")
        OpenStep(2, "Confirm two things in Play Console") {
            Text(
                "Both are needed, so every member can install it.",
                Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = t.text2,
            )
            CrewSeatBoxes(state.boxes, onBoxes)
            state.boxesMessage?.let { Text(it, Modifier.padding(top = 8.dp), style = SmallLine, color = t.warn) }
            Spacer(Modifier.height(12.dp))
            SeatBar(
                if (state.boxes.both) "Continue" else "Tick both to continue",
                Lucide.ArrowRight,
                enabled = state.boxes.both,
                onClick = onContinue,
            )
        }
        TodoStep(3, "Seat details")
    }
}

/** The step in hand: its number lit, its words in ink, and its body under them in line with the words. */
@Composable
private fun OpenStep(
    number: Int,
    title: String,
    body: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(number.toString(), Tt.colors.accent, Tt.colors.onAccent)
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 19.sp),
                fontWeight = FontWeight.SemiBold,
                color = Tt.colors.text,
            )
        }
        // Under the title's own text: the dot's 22dp and the 12dp after it.
        Column(Modifier.fillMaxWidth().padding(start = 34.dp, bottom = 8.dp)) { body() }
    }
}

@Composable
private fun DoneStep(title: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Tt.colors.okBg), contentAlignment = Alignment.Center) {
            Icon(Lucide.Check, null, Modifier.size(13.dp), tint = Tt.colors.ok)
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = StepLine, color = Tt.colors.text2)
    }
}

@Composable
private fun TodoStep(
    number: Int,
    title: String,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).border(1.5.dp, Tt.colors.cardLine, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                fontWeight = FontWeight.Bold,
                color = Tt.colors.text4,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(title, style = StepLine, color = Tt.colors.text3)
    }
}

private val StepLine
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp)

@Composable
private fun Dot(
    text: String,
    background: Color,
    ink: Color,
) {
    Box(Modifier.size(22.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), fontWeight = FontWeight.Bold, color = ink)
    }
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
