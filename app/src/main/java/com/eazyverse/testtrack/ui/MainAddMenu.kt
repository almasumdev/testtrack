package com.eazyverse.testtrack.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.SquarePlus
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.ui.theme.Tt

/** Three small actions grow out of the centre button, above the bar rather than in a sheet. */
@Composable
internal fun MainAddMenu(
    visible: Boolean,
    bottom: Dp,
    onAddApp: () -> Unit,
    onAsk: () -> Unit,
    onTakeSeat: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val transition = updateTransition(visible, label = "add-menu")
    val reveal by transition.animateFloat(
        transitionSpec = {
            if (targetState) spring(dampingRatio = 0.78f, stiffness = 450f) else tween(160)
        },
        label = "add-menu-reveal",
    ) { if (it) 1f else 0f }
    BackHandler(enabled = visible, onBack = onDismiss)

    // Let the buttons return to the plus before opening a picker or leaving the shell.
    LaunchedEffect(transition.isRunning, transition.currentState, transition.targetState) {
        if (!transition.isRunning && pending != null) {
            val action = pending
            pending = null
            onDismiss()
            action?.invoke()
        }
    }
    if (!transition.currentState && !transition.targetState) return
    val choose: (() -> Unit) -> Unit = { action ->
        if (visible && pending == null) {
            pending = action
            onDismiss()
        }
    }
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Close Add menu",
                    onClick = onDismiss,
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottom)
                .fillMaxWidth()
                .height(164.dp)
                .graphicsLayer { alpha = reveal.coerceIn(0f, 1f) }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            t.scrim.copy(alpha = 0f),
                            t.scrim.copy(alpha = t.scrim.alpha * 0.55f),
                        ),
                    ),
                ),
        )
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottom + 12.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(horizontal = Gutter)
                .semantics {
                    paneTitle = "Add"
                    isTraversalGroup = true
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            val actions =
                listOf(
                    Triple(Lucide.MessageSquare, "Feedback", onAsk),
                    Triple(Lucide.SquarePlus, "Add app", onAddApp),
                    Triple(Lucide.Users, "Take seat", onTakeSeat),
                )
            actions.forEachIndexed { index, (icon, label, action) ->
                MainAddAction(
                    icon = icon,
                    label = label,
                    description =
                        when (index) {
                            0 -> "Ask for feedback"
                            1 -> "Add an app"
                            else -> "Take a seat"
                        },
                    enabled = visible && pending == null,
                    onClick = { choose(action) },
                    modifier =
                        Modifier.weight(1f).graphicsLayer {
                            val open = reveal.coerceIn(0f, 1f)
                            // Side actions fan out from the middle as all three rise from the plus.
                            translationX = direction * (1 - index) * (size.width + 8.dp.toPx()) * (1f - open)
                            translationY = 62.dp.toPx() * (1f - open)
                            scaleX = 0.4f + 0.6f * reveal
                            scaleY = 0.4f + 0.6f * reveal
                            alpha = open
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        },
                )
            }
        }
    }
}

@Composable
private fun MainAddAction(
    icon: ImageVector,
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(8.dp)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f),
        label = "add-action-press",
    )
    Row(
        modifier
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }.background(t.card, shape)
            .border(1.dp, t.cardLine, shape)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ).semantics(mergeDescendants = true) { contentDescription = description }
            .heightIn(min = 48.dp)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = t.accentInk)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = t.text2,
            textAlign = TextAlign.Center,
        )
    }
}
