package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One question a step raises, the answer to it, and sometimes a picture.
 *
 * [art] is for the one case where words are the wrong tool: a page whose layout is the problem.
 * Drawn rather than photographed, and deliberately not a lookalike. A screenshot of somebody
 * else's page goes stale the week they restyle it, and a convincing copy of a Google page is a
 * thing nobody should be shipping. A diagram in this app's own colours can only be read as a
 * diagram, which is what it is.
 */
data class QA(
    val q: String,
    val a: String,
    val art: (@Composable () -> Unit)? = null,
    /**
     * The one sentence that would do on its own, said louder than the rest.
     *
     * For the answers where somebody is already lost and reading fast. Everything under it is
     * still worth having, and none of it is worth having first.
     */
    val lead: String? = null,
)

/**
 * One node on the rail.
 *
 * A finished step keeps only what it produced: the address, the outcome. The live step is the only
 * one showing instructions, controls or answers. Everything ahead is the title alone, dimmed: it
 * says what is coming without offering anything to press.
 *
 * The dot carries the state so the screen can be read without reading it. Done is a tick on the
 * same green a kept day is drawn in everywhere else in the app, live is the number on the iris the
 * buttons use, ahead is the number outlined and faint. The connector between two finished steps is
 * green as well, which is how far you got, said without a word.
 *
 * [IntrinsicSize.Min] is what lets the connector run the height of whatever the body turns out to
 * be, which for step four with its walkthrough open is most of a screen.
 */
@Composable
fun Step(
    number: Int,
    title: String,
    result: String,
    done: Boolean,
    live: Boolean,
    onToggle: (() -> Unit)?,
    /**
     * Whether [action] still means anything once the step is finished.
     *
     * False for the ones that perform the step. Opening a finished step to read how it was done
     * and being handed Connect Drive is the screen asking for something it already has, and the
     * button is worse than useless: pressing it sends somebody back through a Google consent
     * sheet to arrive exactly where they started.
     *
     * True for the two where the control is not "do this step" but "change what it produced".
     * Signing out is the only fix for the wrong Gmail. Reminders count as settled when somebody
     * declines them, so a finished reminders step is often a switched-off one, and hiding the way
     * to turn it on would strand anybody who changed their mind.
     *
     * Nothing is lost by hiding the rest. Every one of them is re-read when this screen comes
     * forward, so a Drive grant revoked or a usage switch turned off drops its step back to
     * outstanding on its own, action and all.
     */
    actionOutlivesTheStep: Boolean = false,
    detail: String? = null,
    last: Boolean = false,
    answers: List<QA> = emptyList(),
    action: @Composable (ColumnScope.() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // Always, and in both directions. It used to be tappable only when it was closed
            // and only on the one step that could reopen, so a step you had opened out of
            // curiosity could not be put away again.
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = Gutter)
            .height(IntrinsicSize.Min),
    ) {
        Column(
            Modifier.width(24.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Node(number, done, live)
            if (!last) {
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .background(
                            if (done) Status.posted else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f).padding(bottom = if (last) 8.dp else 22.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal,
                lineHeight = 24.sp,
                color =
                    when {
                        live || done -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
            )

            if (done && result.isNotBlank()) {
                Text(
                    result,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (live) {
                detail?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (action != null && (!done || actionOutlivesTheStep)) {
                    Spacer(Modifier.height(16.dp))
                    action()
                }
                Answers(answers)
            }
        }
    }
}

/** Done, doing, or waiting its turn. */
@Composable
private fun Node(
    number: Int,
    done: Boolean,
    live: Boolean,
) {
    val fill =
        when {
            done -> Status.posted
            live -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        }

    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(fill)
            .then(
                if (done || live) {
                    Modifier
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            // The page ground, which is dark behind the light green of the dark theme and light
            // behind the deep green of the light one. One value, legible on both.
            Icon(
                Icons.Default.Check,
                null,
                Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.background,
            )
        } else {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color =
                    if (live) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    },
            )
        }
    }
}

/**
 * What people ask when they get stuck on this step.
 *
 * The first one open, the rest closed, one at a time. These are not documentation moved closer:
 * without them a tester wondering what lands in their Drive has nowhere to look, so they guess,
 * and the ones who guess wrong stop setting up and never say why.
 *
 * Which question somebody opened is deliberately not remembered across a launch. It is a glance,
 * not a place in a document.
 */
@Composable
private fun Answers(items: List<QA>) {
    if (items.isEmpty()) return
    // Nothing open to begin with. One of them being open by default made the step taller than it
    // needed to be and answered a question the reader had not asked yet, which is the opposite of
    // what a list of questions is for.
    var open by remember { mutableStateOf(-1) }

    Column(Modifier.padding(top = 18.dp)) {
        Dashes()
        items.forEachIndexed { index, item ->
            val shown = open == index
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = if (shown) -1 else index }
                    .padding(vertical = 11.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        item.q,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (shown) "\u2013" else "+",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (shown) {
                    item.lead?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.4f,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.a,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.5f,
                    )
                    item.art?.let {
                        Spacer(Modifier.height(12.dp))
                        it()
                    }
                }
            }
            Dashes()
        }
    }
}

/** The console's dashed rule, which is what separates rows in a list over there. */
@Composable
private fun Dashes() {
    val ink = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = ink,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.5.dp.toPx(), 3.dp.toPx())),
        )
    }
}
