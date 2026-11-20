package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleHelp
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.Config
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.delay

/*
 * The seat page's own sections: the testers group (its address, the how-to and the check), and the
 * two boxes only the app's owner can confirm, which stand right above the submit button.
 */

/**
 * The two boxes every member's install depends on. Each has an info button whose callout says what
 * goes wrong and where to fix it; one callout is open at a time.
 */
@Composable
internal fun CrewSeatBoxes(
    boxes: SeatBoxes,
    onChange: (SeatBoxes) -> Unit,
    modifier: Modifier = Modifier,
) {
    var info by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth()) {
        BoxRow(
            SeatBoxCopy.COUNTRIES,
            boxes.countries,
            SeatBoxCopy.COUNTRIES_WHY,
            SeatBoxCopy.COUNTRIES_HOW,
            open = info == SeatBoxCopy.COUNTRIES,
            onInfo = { info = if (it) SeatBoxCopy.COUNTRIES else null },
        ) { onChange(boxes.copy(countries = it)) }
        BoxRow(
            SeatBoxCopy.DEVICES,
            boxes.devices,
            SeatBoxCopy.DEVICES_WHY,
            SeatBoxCopy.DEVICES_HOW,
            open = info == SeatBoxCopy.DEVICES,
            onInfo = { info = if (it) SeatBoxCopy.DEVICES else null },
        ) { onChange(boxes.copy(devices = it)) }
    }
}

@Composable
private fun BoxRow(
    label: String,
    on: Boolean,
    why: String,
    how: Array<String>,
    open: Boolean,
    onInfo: (Boolean) -> Unit,
    onChange: (Boolean) -> Unit,
) {
    val t = Tt.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        // Only the box is tapped: a 48dp target centred on it, spilling into the gutter, with a round ripple.
        // The label is its description, so the text itself is left out of what a screen reader reads.
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .requiredSize(48.dp)
                    .semantics { contentDescription = label }
                    .toggleable(
                        value = on,
                        interactionSource = null,
                        indication = ripple(bounded = false, radius = 20.dp),
                        role = Role.Checkbox,
                        onValueChange = onChange,
                    ),
                contentAlignment = Alignment.Center,
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
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            Modifier.weight(1f, fill = false).clearAndSetSemantics {},
            style = tileStyle(15f, 20f, FontWeight.Medium),
            color = t.text,
        )
        // The callout is anchored to this box: its 20dp icon sits 10dp inside the 40dp button.
        Box(Modifier.offset(x = (-6).dp)) {
            IconButton(onClick = { onInfo(!open) }, modifier = Modifier.size(40.dp)) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .then(if (open) Modifier.background(t.accentInk.copy(alpha = 0.1f)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.Info, "Why it matters", Modifier.size(20.dp), tint = if (open) t.accentInk else t.text4)
                }
            }
            Callout(open, onClose = { onInfo(false) }, paneTitle = label, iconInset = 10.dp, spacing = 6.dp) {
                Text(why, style = BodyLine, color = t.warn)
                Text(inkParts(how), style = BodyLine, color = t.text2)
            }
        }
    }
}

/** Plain and bold in turn, starting plain, with the bold parts in the main ink. */
@Composable
private fun inkParts(parts: Array<String>): AnnotatedString {
    val bold = SpanStyle(fontWeight = FontWeight.SemiBold, color = Tt.colors.text)
    return buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) withStyle(bold) { append(part) } else append(part)
        }
    }
}

private val SmallLine
    @Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp)

private val BodyLine
    @Composable get() = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)

/** What the testers group section needs to draw and to ask. */
internal class SeatGroupState(
    /** The amber line, only when the check failed: when Play was last asked and what it said. */
    val checkLine: String?,
    val checkFailed: Boolean,
    val checking: Boolean,
    /** A failure that is not a check's answer, such as no connection; null when none. */
    val error: String?,
) {
    /** The check's button: what it is doing, or what it does next. */
    val verifyLabel: String
        get() =
            when {
                checking -> "Asking Play"
                checkFailed -> "Check again"
                else -> "Verify on Play"
            }
}

/**
 * Testers group, while Play does not show the app to it yet: the address to copy, the way to the steps
 * and the check. Once it does, the page drops this part for a tile row in the details. The steps open
 * full screen, with a picture of each.
 */
@Composable
internal fun CrewSeatGroup(
    state: SeatGroupState,
    onVerify: () -> Unit,
) {
    val t = Tt.colors
    var howTo by rememberSaveable { mutableStateOf(false) }
    SectionHeading("Testers group", bottom = 8.dp, oneLine = true)
    // Once Play was asked and said no, the last check's own words take the plain line's place.
    val line = state.checkLine.takeIf { state.checkFailed } ?: "Play doesn't show your app to the group yet"
    IconLine(Lucide.TriangleAlert, t.warn, AnnotatedString(line), SmallLine, t.warn)
    Spacer(Modifier.height(6.dp))
    SeatAddressRow()
    Spacer(Modifier.height(12.dp))
    val pill = RoundedCornerShape(10.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RowPill(
            "How to add it",
            PillKind.SOFT,
            modifier = Modifier.weight(1f),
            leading = Lucide.CircleHelp,
            leadingSize = 16.dp,
            shape = pill,
        ) { howTo = true }
        RowPill(
            state.verifyLabel,
            PillKind.SOLID,
            busy = state.checking,
            modifier = Modifier.weight(1f),
            leading = Lucide.RefreshCw,
            leadingSize = 16.dp,
            shape = pill,
            onClick = onVerify,
        )
    }
    state.error?.let { Text(it, Modifier.padding(top = 8.dp), style = SmallLine, color = t.bad) }
    // Only while the group is open: once Play shows the app the steps close with it.
    if (howTo) GroupHowToDialog(state, onVerify, onDismiss = { howTo = false })
}

private val AddressStyle = tileStyle(15f, 18f, FontWeight.SemiBold)
private val AddressLabelStyle = tileStyle(12.5f, 14f, FontWeight.Medium)

/**
 * The group address on one line, shrinking to fit a narrow phone, over what it is, with copy at the
 * end. The whole row copies it.
 */
@Composable
internal fun SeatAddressRow() {
    val t = Tt.colors
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500)
            copied = false
        }
    }
    val copy = {
        clipboard.setText(AnnotatedString(Config.GROUP_EMAIL))
        copied = true
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(onClickLabel = "Copy the testers group address") { copy() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                Config.GROUP_EMAIL,
                style = AddressStyle.copy(color = t.accentInk),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 15.sp, stepSize = 0.5.sp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Testers group address",
                style = AddressLabelStyle,
                color = t.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { copy() }, modifier = Modifier.size(40.dp)) {
            Icon(
                if (copied) Lucide.Check else Lucide.Copy,
                if (copied) "Copied" else "Copy the address",
                Modifier.size(18.dp),
                tint = if (copied) t.ok else t.accentInk,
            )
        }
    }
}

/** Before you submit: the two boxes only the app's owner can confirm, and the server's words if it refused for them. */
@Composable
internal fun CrewSeatConfirm(
    boxes: SeatBoxes,
    boxesMessage: String?,
    onBoxes: (SeatBoxes) -> Unit,
) {
    val t = Tt.colors
    SectionHeading("Before you submit", bottom = 8.dp, oneLine = true)
    Text(
        "You set both in Play Console. Every member needs them to install your app.",
        style = BodyLine,
        color = t.text2,
    )
    Spacer(Modifier.height(8.dp))
    CrewSeatBoxes(boxes, onBoxes)
    boxesMessage?.let {
        IconLine(Lucide.TriangleAlert, t.warn, AnnotatedString(it), SmallLine, t.warn, Modifier.padding(top = 8.dp))
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
