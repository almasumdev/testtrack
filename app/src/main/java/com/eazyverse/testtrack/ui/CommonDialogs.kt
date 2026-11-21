package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazyverse.testtrack.ui.TextStyles.DialogBody
import com.eazyverse.testtrack.ui.TextStyles.DialogNote
import com.eazyverse.testtrack.ui.TextStyles.DialogTitle
import com.eazyverse.testtrack.ui.TextStyles.StepNumber
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * The one dialog this app uses.
 *
 * A card on the sheet colour with the robot looking over its top right corner: its head behind
 * the card, its mitts holding the edge (see [DialogRobotHead]). Compact by design, since most of
 * these are a sentence and a choice: a 16sp title, 13.5sp words, and [AskButtons] at the end, the
 * answer a small filled block and the way out a text button beside it.
 *
 * The title and the buttons stay put; the middle ([body], then [content], then [note]) scrolls
 * when it does not fit. [lead] replaces the title for a dialog that is about someone, such as a
 * member's profile, so [title] may be null only with a [lead].
 *
 * [icon] sits inline before the title, only on the asks for a permission and on warnings, in the
 * [tone]'s ink: the accent's, or [Tone.WARN]'s amber.
 *
 * Destructive actions are named rather than coloured red by default. "Sign out" is not a warning,
 * and a dialog where the answer is always alarming teaches people to ignore the alarm, so
 * [destructive] is opt-in, for the ones that genuinely take something away.
 *
 * A null [confirm] leaves only the way out, for a dialog that is waiting on something; [looking]
 * has the robot's eyes move while it does. [busy] shows a spinner in the answer and holds both
 * buttons until it is done. [alt] is a second, lesser answer, at the start of the button row.
 */
@Composable
fun Ask(
    title: String?,
    onDismiss: () -> Unit,
    icon: ImageVector? = null,
    tone: Tone = Tone.ACCENT,
    destructive: Boolean = false,
    body: String? = null,
    note: String? = null,
    confirm: String? = null,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    busy: Boolean = false,
    dismiss: String? = "Cancel",
    alt: String? = null,
    onAlt: () -> Unit = {},
    /** The [alt] in the quiet ink, for a way out rather than an answer, such as "Don't ask again". */
    altQuiet: Boolean = false,
    lead: (@Composable () -> Unit)? = null,
    looking: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    require(title != null || lead != null) { "A dialog needs a title or a lead" }
    val t = Tt.colors
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .padding(horizontal = 22.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .padding(top = DialogRobotRise),
        ) {
            // Behind the card, so only the face shows above its edge.
            DialogRobotHead(looking, Modifier.align(Alignment.TopEnd).padding(end = 10.dp).offset(y = -DialogRobotRise))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(DialogShape)
                    .background(t.sheet)
                    // The buttons are 40 drawn in a 48 target; the 4 either side comes off here.
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp - TargetSlack),
            ) {
                if (lead != null) lead() else AskTitle(title.orEmpty(), icon, tone)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    body?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = DialogBody, color = t.text2)
                    }
                    content?.let {
                        Spacer(Modifier.height(12.dp))
                        it()
                    }
                    note?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = DialogNote, color = t.text4)
                    }
                }
                Spacer(Modifier.height(12.dp - TargetSlack))
                AskButtons(
                    confirm = confirm,
                    onConfirm = onConfirm,
                    confirmEnabled = confirmEnabled,
                    destructive = destructive,
                    busy = busy,
                    dismiss = dismiss,
                    onDismiss = onDismiss,
                    alt = alt,
                    onAlt = onAlt,
                    altQuiet = altQuiet,
                )
            }
            // In front of the card: the mitts over its edge.
            DialogRobotHands(Modifier.align(Alignment.TopEnd).padding(end = 10.dp).offset(y = -DialogRobotRise))
        }
    }
}

/** The title, with its [icon] inline before the first word so a second line starts under the icon. */
@Composable
private fun AskTitle(
    title: String,
    icon: ImageVector?,
    tone: Tone,
) {
    val t = Tt.colors
    if (icon == null) {
        Text(title, style = DialogTitle, color = t.text)
        return
    }
    val ink = if (tone == Tone.WARN) t.warn else t.accentInk
    // In dp, so the mark stays 18 at any font size, with 7 after it.
    val density = LocalDensity.current
    val (width, height) = with(density) { (TitleIcon + TitleIconGap).toSp() to TitleIcon.toSp() }
    val text =
        buildAnnotatedString {
            appendInlineContent(ICON_ID, "")
            append(title)
        }
    val inline =
        mapOf(
            ICON_ID to
                InlineTextContent(Placeholder(width, height, PlaceholderVerticalAlign.TextCenter)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Icon(icon, null, Modifier.size(TitleIcon), tint = ink)
                    }
                },
        )
    Text(text, style = DialogTitle, color = t.text, inlineContent = inline)
}

/**
 * A dialog's buttons, at its end: [alt] as a text button at the start, then the way out as a text
 * button, then the answer as a small filled block ([Primary] with `small`), red when [destructive].
 *
 * When the three do not fit on one line, [alt] takes a line of its own above the other two; when
 * those two still do not fit, everything stacks at the end, the answer on top.
 */
@Composable
internal fun AskButtons(
    confirm: String?,
    onConfirm: () -> Unit,
    dismiss: String?,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    busy: Boolean = false,
    alt: String? = null,
    onAlt: () -> Unit = {},
    altQuiet: Boolean = false,
) {
    val t = Tt.colors
    Layout(
        content = {
            alt?.let {
                InlineAction(
                    it,
                    Modifier.layoutId(SLOT_ALT),
                    enabled = !busy,
                    color = if (altQuiet) t.text3 else t.accentInk,
                    dialog = true,
                    onClick = onAlt,
                )
            }
            dismiss?.let {
                InlineAction(it, Modifier.layoutId(SLOT_DISMISS), enabled = !busy, dialog = true, onClick = onDismiss)
            }
            confirm?.let {
                Primary(
                    it,
                    Modifier.layoutId(SLOT_CONFIRM),
                    busy = busy,
                    enabled = confirmEnabled,
                    destructive = destructive,
                    busyKeepsFill = true,
                    small = true,
                    onClick = onConfirm,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val placeables = measurables.associate { it.layoutId to it.measure(loose) }
        val altP = placeables[SLOT_ALT]
        val dismissP = placeables[SLOT_DISMISS]
        val confirmP = placeables[SLOT_CONFIRM]
        val pairGap = PairGap.roundToPx()
        // The alt's words line up with the dialog's words, its padding out in the margin.
        val altShift = AltShift.roundToPx()
        val pair = listOfNotNull(dismissP, confirmP)
        val pairWidth = pair.sumOf { it.width } + if (pair.size == 2) pairGap else 0
        val pairHeight = pair.maxOfOrNull { it.height } ?: 0
        val oneLine = altP == null || altP.width - altShift + AltGap.roundToPx() + pairWidth <= width
        when {
            oneLine -> {
                val height = maxOf(pairHeight, altP?.height ?: 0)
                layout(width, height) {
                    altP?.placeRelative(-altShift, (height - altP.height) / 2)
                    placePair(dismissP, confirmP, width, pairGap, 0, height)
                }
            }
            pairWidth <= width -> {
                val altHeight = altP.height
                layout(width, altHeight + pairHeight) {
                    altP.placeRelative(-altShift, 0)
                    placePair(dismissP, confirmP, width, pairGap, altHeight, pairHeight)
                }
            }
            else -> {
                // Stacked at the end: the answer, the way out, then the alt.
                val stack = listOfNotNull(confirmP, dismissP, altP)
                layout(width, stack.sumOf { it.height }) {
                    var y = 0
                    stack.forEach {
                        it.placeRelative(width - it.width, y)
                        y += it.height
                    }
                }
            }
        }
    }
}

/** The way out and the answer at the end of a line [top] down, centred on its [height]. */
private fun androidx.compose.ui.layout.Placeable.PlacementScope.placePair(
    dismiss: androidx.compose.ui.layout.Placeable?,
    confirm: androidx.compose.ui.layout.Placeable?,
    width: Int,
    gap: Int,
    top: Int,
    height: Int,
) {
    var x = width
    confirm?.let {
        x -= it.width
        it.placeRelative(x, top + (height - it.height) / 2)
        x -= gap
    }
    dismiss?.let {
        x -= it.width
        it.placeRelative(x, top + (height - it.height) / 2)
    }
}

/**
 * Numbered steps inside a dialog, one short line each, every number in a filled 22dp circle in
 * the tile's colours.
 */
@Composable
fun DialogSteps(steps: List<AnnotatedString>) {
    val t = Tt.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(StepCircle).clip(CircleShape).background(t.tile), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = StepNumber, color = t.tileInk)
                }
                Spacer(Modifier.width(8.dp))
                // The 19 line centred on the 22 circle.
                Text(step, Modifier.padding(top = 1.5.dp), style = DialogBody, color = t.text2)
            }
        }
    }
}

private val DialogShape = RoundedCornerShape(12.dp)
private val TitleIcon = 18.dp
private val TitleIconGap = 7.dp
private val StepCircle = 22.dp

/** How much of a button's 48dp touch target is outside its 40dp shape, top and bottom. */
private val TargetSlack = 4.dp
private val PairGap = 8.dp
private val AltGap = 4.dp
private val AltShift = 10.dp

private const val ICON_ID = "icon"
private const val SLOT_ALT = "alt"
private const val SLOT_DISMISS = "dismiss"
private const val SLOT_CONFIRM = "confirm"
