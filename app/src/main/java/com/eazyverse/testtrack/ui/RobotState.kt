package com.eazyverse.testtrack.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.WifiOff
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * A page with nothing to list: empty, or not loaded. The robot holds that case's symbol, with a
 * title, one line, and the way out.
 *
 * Empty pages are in the accent with a filled button, since there is something to go and do.
 * A page that did not load is grey with an outlined "Try again", so it reads as a pause rather
 * than as something still loading or broken.
 */
@Composable
fun RobotState(
    icon: ImageVector,
    title: String,
    line: String,
    modifier: Modifier = Modifier,
    action: String = "",
    onAction: () -> Unit = {},
    failed: Boolean = false,
) {
    val t = Tt.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RobotHolding(icon, if (failed) t.text3 else t.accentInk, size = 104.dp, description = title)
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
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(percent = 50),
                    border = BorderStroke(1.5.dp, t.accentInk),
                    contentPadding = PaddingValues(horizontal = 22.dp),
                    modifier = Modifier.height(44.dp),
                ) {
                    Text(action, fontWeight = FontWeight.Bold, color = t.accentInk, fontSize = 14.5.sp)
                }
            } else {
                Primary(action, Modifier.widthIn(max = 240.dp), onClick = onAction)
            }
        }
    }
}

/** The one shape every "did not load" takes, so they all say it the same way. */
@Composable
fun RobotOffline(
    title: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) = RobotState(
    Lucide.WifiOff,
    title,
    "Check your connection, then try again.",
    modifier,
    action = "Try again",
    onAction = onRetry,
    failed = true,
)
