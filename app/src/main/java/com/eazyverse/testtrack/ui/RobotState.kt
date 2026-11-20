package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.ReviewMoment

/**
 * A page with nothing to list: empty, or not loaded. The robot holds that case's cartoon
 * [Prop], with a title, one line, and the way out.
 *
 * Empty pages are in the accent with a filled button, since there is something to go and do.
 * A page that did not load is grey with an outlined "Try again", so it reads as a pause rather
 * than as something still loading or broken.
 *
 * [fill] false lays it out at its own height, for the top of a page that has more under it, such as
 * You signed out with its settings below. A fixed share of the screen was tried there and cut the
 * button in half on a short phone.
 */
@Composable
fun RobotState(
    prop: Prop,
    title: String,
    line: String,
    modifier: Modifier = Modifier,
    action: String = "",
    onAction: () -> Unit = {},
    failed: Boolean = false,
    /** Something quieter under the way out, such as the how-to video's links. */
    footer: (@Composable () -> Unit)? = null,
    fill: Boolean = true,
    /** The robot's size: smaller where the page has a lot under it. */
    robot: Dp = 112.dp,
) {
    val t = Tt.colors
    val body: @Composable ColumnScope.() -> Unit = {
        RobotHolding(prop, size = robot, description = title)
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 25.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            line,
            Modifier.widthIn(max = 300.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = t.text2,
            textAlign = TextAlign.Center,
        )
        if (action.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            if (failed) {
                Outlined(action, onClick = onAction)
            } else {
                Primary(action, Modifier.widthIn(max = 240.dp), onClick = onAction)
            }
        }
        if (footer != null) {
            Spacer(Modifier.height(10.dp))
            footer()
        }
    }
    if (fill) {
        // Fills the page and sits in the middle of what is visible: between the tabs and the dock
        // that floats over the bottom of the page.
        CenteredState(modifier, content = body)
    } else {
        Column(
            modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = body,
        )
    }
}

/**
 * The one offline page: every "did not load" takes this shape, so they all say it the same way.
 * Use it rather than a [RobotState] with [Prop.Plug] of your own.
 */
@Composable
fun RobotOffline(
    title: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Something did not load: no moment to ask for a rating for the rest of this session.
    LaunchedEffect(Unit) { ReviewMoment.noteTrouble() }
    RobotOfflineState(title, onRetry, modifier)
}

@Composable
private fun RobotOfflineState(
    title: String,
    onRetry: () -> Unit,
    modifier: Modifier,
) = RobotState(
    Prop.Plug,
    title,
    "Check your connection, then try again.",
    modifier,
    action = "Try again",
    onAction = onRetry,
    failed = true,
)
