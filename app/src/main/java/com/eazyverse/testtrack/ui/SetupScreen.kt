package com.eazyverse.testtrack.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.Config
import com.eazyverse.testtrack.data.*
import com.eazyverse.testtrack.findActivity
import kotlinx.coroutines.launch

class SetupViewModel : ViewModel() {
    var checkingGroup by mutableStateOf(false)
        private set
    var connectingDrive by mutableStateOf(false)
        private set
    var submitting by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    var packageInput by mutableStateOf("")
    var nameInput by mutableStateOf("")
    var confirmed by mutableStateOf(false)

    /** Rough Android package shape: at least two dot-separated segments starting with a letter. */
    val packageValid: Boolean
        get() = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$").matches(packageInput)

    val canSubmit: Boolean get() = packageValid && nameInput.isNotBlank() && confirmed && !submitting

    /**
     * Sends the package for admin review.
     *
     * Not machine-checked: reading Play track state needs developer-level authorization from
     * every owner, which costs more than it is worth here. An admin confirms instead, and
     * approval deliberately does not block the owner from using the app meanwhile.
     */
    fun submitTrack() {
        if (!canSubmit) return
        submitting = true
        message = null
        viewModelScope.launch {
            val uid = AuthRepo.uid
            val email = Session.email
            if (uid == null || email == null) {
                message = "Not signed in"
            } else {
                runCatching { Repo.submitApp(uid, email, packageInput, nameInput.trim()) }
                    .onSuccess { Session.submitTrack(packageInput) }
                    .onFailure { message = it.message ?: "Could not submit" }
            }
            submitting = false
        }
    }

    /** Re-runs the server-side membership check for the account we already hold a token for. */
    fun verifyGroup(activity: Activity) {
        checkingGroup = true
        message = null
        viewModelScope.launch {
            try {
                // A fresh sign-in is the only way to get a fresh ID token; it is silent when the
                // account was picked before, so this costs the tester nothing.
                val (account, gate) = AuthRepo.signInAndVerify(activity)
                Session.updateEmail(account.email)
                Session.updateMember(gate is GateResult.Member)
                message = when (gate) {
                    is GateResult.Member -> null
                    is GateResult.NotMember -> "${gate.email} isn't in the group yet."
                    is GateResult.Failed -> gate.reason
                }
            } catch (e: Exception) {
                message = e.message ?: "Check failed"
            }
            checkingGroup = false
        }
    }

    fun connectDrive(activity: Activity, launchConsent: (IntentSenderRequest) -> Unit) {
        connectingDrive = true
        message = null
        viewModelScope.launch {
            try {
                val result = AuthRepo.authorizeDrive(activity)
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    launchConsent(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    Session.updateDriveConnected(result.accessToken != null)
                }
            } catch (e: Exception) {
                message = e.message ?: "Drive authorization failed"
            }
            connectingDrive = false
        }
    }
}

@Composable
fun SetupScreen(onFinished: () -> Unit, vm: SetupViewModel = viewModel()) {
    val activity = LocalContext.current.findActivity()

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val token = AuthRepo.tokenFromConsent(activity, result.data)
        Session.updateDriveConnected(token != null)
        if (token == null) vm.message = "Drive access was not granted"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Finish setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "A few one-time steps before you can start posting daily proof.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))

        Step(1, "Signed in", Session.email ?: "", Session.signedIn)

        Step(
            number = 2,
            title = "Join the testers Google Group",
            body = "Owners can only add you to their closed test if you're in the group. " +
                "Join with the same Gmail you signed in with.",
            done = Session.isGroupMember
        ) {
            OutlinedButton(
                onClick = {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.GROUP_URL)))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open the group") }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.verifyGroup(activity) },
                enabled = !vm.checkingGroup,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (vm.checkingGroup) "Checking…" else "Verify membership") }
        }

        Step(
            number = 3,
            title = "Connect Google Drive",
            body = "Your daily screenshots are stored in your own Drive. TestTrack can only see " +
                "files it creates.",
            done = Session.driveConnected
        ) {
            Button(
                onClick = { vm.connectDrive(activity) { consentLauncher.launch(it) } },
                enabled = !vm.connectingDrive,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (vm.connectingDrive) "Connecting…" else "Connect Drive") }
        }

        Step(
            number = 4,
            title = "Your app on closed testing",
            body = if (Session.trackSubmitted)
                "${Session.packageName}\n\nSubmitted — an admin will confirm your track shortly. " +
                    "You can carry on in the meantime."
            else
                "Publish a release to closed testing and add the group as testers, then submit " +
                    "your package name for approval.",
            done = Session.trackSubmitted
        ) {
            OutlinedTextField(
                value = vm.nameInput,
                onValueChange = { vm.nameInput = it },
                label = { Text("App name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vm.packageInput,
                onValueChange = { vm.packageInput = it.trim() },
                label = { Text("Package name") },
                placeholder = { Text("com.example.myapp") },
                singleLine = true,
                isError = vm.packageInput.isNotEmpty() && !vm.packageValid,
                supportingText = {
                    Text(
                        if (vm.packageInput.isNotEmpty() && !vm.packageValid)
                            "Looks wrong — expected something like com.example.myapp"
                        else
                            "Play Console → your app → App information"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { vm.confirmed = !vm.confirmed }
            ) {
                Checkbox(checked = vm.confirmed, onCheckedChange = { vm.confirmed = it })
                Spacer(Modifier.width(4.dp))
                Text(
                    "My app is live on a closed testing track and the group is added as testers.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.submitTrack() },
                enabled = vm.canSubmit,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (vm.submitting) "Submitting…" else "Submit for approval") }
        }

        vm.message?.let {
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    it,
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onFinished,
            enabled = Session.setupComplete,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (Session.setupComplete) "Continue" else "Complete all steps to continue") }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Step(
    number: Int,
    title: String,
    body: String,
    done: Boolean,
    action: @Composable (ColumnScope.() -> Unit)? = null
) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(16.dp)) {
            Surface(
                shape = CircleShape,
                color = if (done) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    if (done) {
                        Icon(
                            Icons.Default.Check, null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text("$number", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (body.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (action != null && !done) {
                    Spacer(Modifier.height(12.dp))
                    Column { action() }
                }
            }
        }
    }
}
