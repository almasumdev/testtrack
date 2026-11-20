package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
 * button in half on a short phone. [pad] is the space round it then.
 *
 * The rest set a page's own look where it was drawn that way: [robotGap] under the robot,
 * [titleStyle] and [titleColor], [titleGap] under the title, [lineColor] and [lineMax] for the line,
 * [footerGap] over the footer. [soft] draws the way out as a soft pill, such as a quiet "Try again".
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
    pad: PaddingValues = PaddingValues(top = 20.dp, bottom = 12.dp),
    robotGap: Dp = 14.dp,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold),
    titleColor: Color = Color.Unspecified,
    titleGap: Dp = 6.dp,
    lineColor: Color = Tt.colors.text2,
    lineMax: Dp = 300.dp,
    footerGap: Dp = 10.dp,
    soft: Boolean = false,
) {
    val body: @Composable ColumnScope.() -> Unit = {
        RobotHolding(prop, size = robot, description = title)
        Spacer(Modifier.height(robotGap))
        Text(
            title,
            style = titleStyle,
            color = titleColor,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(titleGap))
        Text(
            line,
            Modifier.widthIn(max = lineMax),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = lineColor,
            textAlign = TextAlign.Center,
        )
        if (action.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            when {
                soft -> RowPill(action, PillKind.SOFT, onClick = onAction)
                failed -> Outlined(action, onClick = onAction)
                else -> Primary(action, Modifier.widthIn(max = 240.dp), onClick = onAction)
            }
        }
        if (footer != null) {
            Spacer(Modifier.height(footerGap))
            footer()
        }
    }
    if (fill) {
        // Fills the page and sits in the middle of what is visible: between the tabs and the dock
        // that floats over the bottom of the page.
        CenteredState(modifier, content = body)
    } else {
        Column(
            modifier.fillMaxWidth().padding(pad),
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
