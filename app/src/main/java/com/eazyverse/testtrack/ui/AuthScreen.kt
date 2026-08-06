package com.eazyverse.testtrack.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.data.*
import com.eazyverse.testtrack.data.AdminEvents
import com.eazyverse.testtrack.findActivity
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    fun signIn(activity: Activity, onDone: () -> Unit) {
        busy = true
        message = null
        viewModelScope.launch {
            try {
                val (account, gate) = AuthRepo.signInAndVerify(activity)
                Session.updateEmail(account.email)
                Session.updateMember(gate is GateResult.Member)

                AuthRepo.uid?.let { uid ->
                    runCatching { Repo.upsertUser(uid, account.email, account.email.substringBefore('@')) }
                }

                // Everything that happened before this moment is history, not news. Without this
                // a returning tester is greeted by every placement decision ever made about them.
                AdminEvents.markCaughtUp(activity)

                // Drive was very likely authorised on a previous install or session — the grant
                // belongs to the account, not to this phone. Ask, so a returning tester is not
                // marched through a step they cleared months ago.
                Session.updateDriveConnected(AuthRepo.hasDriveAccess(activity))

                message = AuthRepo.firebaseError
                    ?: (gate as? GateResult.Failed)?.reason
                onDone()
            } catch (e: NotGmailException) {
                message = "${e.email} isn't a Gmail account. Sign in with the Gmail you use for testing."
            } catch (e: Exception) {
                message = e.message ?: "Sign-in failed"
            }
            busy = false
        }
    }
}

@Composable
fun AuthScreen(onSignedIn: () -> Unit, vm: AuthViewModel = viewModel()) {
    val activity = LocalContext.current.findActivity()

    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.9f))

        BrandMark()
        Spacer(Modifier.height(24.dp))

        Text(
            "TestTrack",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in with the Gmail you use for closed testing.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.weight(0.7f))

        Reason(
            Icons.Default.Group,
            "Proves you're in the group",
            "Checked on Google's servers, not on your phone — so it can't be faked."
        )
        Reason(
            Icons.Default.PhotoCamera,
            "Records your daily testing",
            "Open an app from your list and proof is captured automatically."
        )
        Reason(
            Icons.Default.CloudUpload,
            "Stores proof in your own Drive",
            "TestTrack can only ever see the files it creates there."
        )

        Spacer(Modifier.weight(0.7f))

        vm.message?.let {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(14.dp)
            ) {
                Icon(
                    Icons.Default.ErrorOutline, null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // Google asks for its own mark on a plain surface rather than a tinted button, which also
        // keeps it distinct from the app's own primary actions.
        Button(
            onClick = { vm.signIn(activity, onSignedIn) },
            enabled = !vm.busy,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (vm.busy) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                GoogleMark(Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(
                if (vm.busy) "Signing in…" else "Continue with Google",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Gmail only — Play Console closed testing is keyed to Google accounts.",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
    }
}

/** The app mark: a run of days, most of them kept. */
@Composable
private fun BrandMark() {
    val onMark = MaterialTheme.colorScheme.onPrimary
    val tint = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
    )

    Box(
        Modifier
            .size(78.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(tint)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(38.dp)) {
            val cell = size.width * 0.28f
            val gap = size.width * 0.08f
            repeat(3) { row ->
                repeat(3) { col ->
                    // The last cell is the day still to come.
                    val pending = row == 2 && col == 2
                    drawRoundRect(
                        color = onMark.copy(alpha = if (pending) 0.30f else 0.95f),
                        topLeft = Offset(col * (cell + gap), row * (cell + gap)),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(cell * 0.34f)
                    )
                }
            }
        }
    }
}

@Composable
private fun Reason(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.padding(top = 2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun GoogleMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.215f
        val radius = (size.minDimension - stroke) / 2f
        val centre = Offset(size.width / 2f, size.height / 2f)
        val box = Offset(centre.x - radius, centre.y - radius)
        val arcSize = Size(radius * 2, radius * 2)

        fun arc(color: Color, start: Float, sweep: Float) = drawArc(
            color = color,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = box,
            size = arcSize,
            style = Stroke(stroke)
        )

        arc(GoogleBlue, 268f, 92f)     // top right, running into the crossbar
        arc(GoogleGreen, 24f, 88f)     // lower right and along the bottom
        arc(GoogleYellow, 112f, 64f)   // bottom left
        arc(GoogleRed, 176f, 92f)      // left and over the top

        drawRect(
            color = GoogleBlue,
            topLeft = Offset(centre.x, centre.y - stroke / 2f),
            size = Size(radius + stroke / 2f, stroke)
        )
    }
}
