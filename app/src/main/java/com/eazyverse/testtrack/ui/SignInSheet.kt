package com.eazyverse.testtrack.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.viewmodel.AuthViewModel

/**
 * Signing in, over whatever asked for it.
 *
 * There used to be a screen for this, and it was the first thing anybody saw: a logo, a three
 * point pitch and a button, before they had been shown a single thing the app does. It is a sheet
 * now, over the content that prompted it, and it comes up from one of two places: the button in
 * the header, or a screen that cannot answer without a name.
 *
 * That is the whole difference. Nobody is sent anywhere, nothing is lost behind them, and
 * dismissing puts them back exactly where they were rather than somewhere they have to navigate
 * out of. It also means there is no longer a screen whose only job is to ask, which is what the
 * old one had become once the pitch on it stopped being true.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInSheet(
    onDismiss: () -> Unit,
    onSignedIn: () -> Unit,
    vm: AuthViewModel = viewModel(),
) {
    val activity = LocalContext.current.findActivity()
    val state = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = Gutter, end = Gutter, bottom = 32.dp),
        ) {
            Text(
                "Sign in to TestTrack",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Your crews, your apps and what you have earned all belong to the account, so " +
                    "they follow you onto any phone you sign in on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Outlined rather than filled, the way every notice wider than a chip is in this app.
            // See the note on StatusColors: a filled band in a colour nothing else here is painted
            // in reads as a stain rather than as a warning.
            vm.message?.let { message ->
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                            RoundedCornerShape(14.dp),
                        ).padding(14.dp),
                ) {
                    Icon(
                        Lucide.CircleAlert,
                        null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            // Google asks for its own mark on a plain surface rather than a tinted button, which
            // also keeps it distinct from the app's own primary actions.
            Button(
                onClick = { vm.signIn(activity, onSignedIn) },
                enabled = !vm.busy,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = MaterialTheme.colorScheme.background,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (vm.busy) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    GoogleMark(Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    if (vm.busy) "Signing in" else "Continue with Google",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Gmail only, because Play Console ties closed testing to Google accounts.",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val GoogleBlue = Color(0xFF4285F4)
private val GoogleGreen = Color(0xFF34A853)
private val GoogleYellow = Color(0xFFFBBC05)
private val GoogleRed = Color(0xFFEA4335)

/**
 * The Google G, drawn as four arcs and the crossbar.
 *
 * Drawn rather than bundled: the mark is only ever needed at one size in one place, and a vector
 * asset would be a second copy of Google's artwork to keep in step. The blue arc stops at three
 * o'clock so the crossbar meets it flush.
 */
@Composable
fun GoogleMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.215f
        val radius = (size.minDimension - stroke) / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        val box = Offset(centre.x - radius, centre.y - radius)
        val arcSize = Size(radius * 2, radius * 2)

        fun arc(
            color: Color,
            start: Float,
            sweep: Float,
        ) = drawArc(
            color = color,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = box,
            size = arcSize,
            style = Stroke(stroke),
        )

        arc(GoogleBlue, 268f, 92f) // top right, running into the crossbar
        arc(GoogleGreen, 24f, 88f) // lower right and along the bottom
        arc(GoogleYellow, 112f, 64f) // bottom left
        arc(GoogleRed, 176f, 92f) // left and over the top

        drawRect(
            color = GoogleBlue,
            topLeft = Offset(centre.x, centre.y - stroke / 2f),
            size = Size(radius + stroke / 2f, stroke),
        )
    }
}
