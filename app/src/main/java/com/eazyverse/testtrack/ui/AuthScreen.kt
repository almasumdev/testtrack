package com.eazyverse.testtrack.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.data.*
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
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            "TestTrack",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in with the Gmail you use for closed testing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

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

        Spacer(Modifier.weight(1f))

        vm.message?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    it,
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = { vm.signIn(activity, onSignedIn) },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (vm.busy) "Signing in…" else "Continue with Google") }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Reason(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
