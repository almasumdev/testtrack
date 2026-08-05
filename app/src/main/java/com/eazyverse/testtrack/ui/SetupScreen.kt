package com.eazyverse.testtrack.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    var message by mutableStateOf<String?>(null)

    /** Re-runs the server-side membership check for the account we already hold a token for. */
    fun verifyGroup(activity: Activity) {
        checkingGroup = true
        message = null
        viewModelScope.launch {
            try {
                val (account, gate) = AuthRepo.recheckGroup(activity)
                account?.let { Session.updateEmail(it.email) }
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

    // Usage access lives in Settings and can be switched off behind our back, so it is re-read
    // every time this screen comes forward rather than trusted from a stored flag.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) Session.refreshUsageAccess(activity)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Same for Drive: the grant is the account's, so ask Google rather than trusting a local flag
    // that sign-out cleared. Silent when it is already there.
    LaunchedEffect(Unit) {
        if (!Session.driveConnected) {
            runCatching { Session.updateDriveConnected(AuthRepo.hasDriveAccess(activity)) }
        }
    }

    val done = listOf(
        Session.signedIn,
        Session.isGroupMember,
        Session.driveConnected,
        Session.usageAccessGranted
    )

    /**
     * Only the first outstanding step is live; the rest are inert until it clears. Each depends
     * on the one before, so a control that cannot succeed yet is worse than no control. A
     * finished step reopens on tap — re-verifying and reconnecting Drive are both real needs.
     */
    val outstanding = done.indexOfFirst { !it }
    var reopened by rememberSaveable { mutableStateOf<Int?>(null) }
    fun live(index: Int) = if (reopened != null) reopened == index else index == outstanding

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        Column(Modifier.padding(start = Gutter, end = Gutter, top = 36.dp, bottom = 24.dp)) {
            Text(
                "Set up TestTrack",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Four things to do once, in order.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
          Panel {

            Step(
                title = "Sign in",
                result = Session.email ?: "",
                done = done[0],
                live = false,
                onReopen = null
            )

            Step(
                title = "Join the testers group",
                result = "You're in the group",
                detail = "Owners can only add you to their closed test if you're in the group. " +
                    "Join with the Gmail you signed in with.",
                done = done[1],
                live = live(1),
                onReopen = { reopened = if (live(1)) null else 1 }.takeIf { done[1] }
            ) {
                Primary("Verify membership", busy = vm.checkingGroup) { vm.verifyGroup(activity) }
                TextButton(
                    onClick = {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.GROUP_URL)))
                    },
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) { Text("Open the group") }
            }

            Step(
                title = "Connect Google Drive",
                result = "Proof will be saved to your Drive",
                detail = "Your daily screenshots go to your own Drive. TestTrack can only see " +
                    "files it creates there.",
                done = done[2],
                live = live(2),
                onReopen = { reopened = if (live(2)) null else 2 }.takeIf { done[2] }
            ) {
                Primary("Connect Drive", busy = vm.connectingDrive) {
                    vm.connectDrive(activity) { consentLauncher.launch(it) }
                }
            }

            Step(
                title = "Allow usage access",
                result = "Time in each app is recorded",
                detail = "Your daily report records how long you actually spent in each app, not " +
                    "just that you opened it. Android only reports that with usage access, and " +
                    "it can't be asked for in a pop-up — find TestTrack in the list and turn it on.",
                done = done[3],
                live = live(3),
                last = true,
                onReopen = { reopened = if (live(3)) null else 3 }.takeIf { done[3] }
            ) {
                Primary("Open usage access settings") { UsageRepo.openSettings(activity) }
            }
          }

            vm.message?.let { Failure(it) }
            Spacer(Modifier.height(24.dp))
        }

        Primary(
            "Start testing",
            Modifier.padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 24.dp),
            enabled = Session.setupComplete,
            tall = true,
            onClick = onFinished
        )
    }
}

/**
 * One step.
 *
 * A finished step keeps only what it produced — the address, the outcome. The live step is the
 * only one showing instructions or controls. Everything ahead is the title alone, greyed: it says
 * what is coming without offering anything to press.
 */
@Composable
private fun Step(
    title: String,
    result: String,
    done: Boolean,
    live: Boolean,
    onReopen: (() -> Unit)?,
    detail: String? = null,
    last: Boolean = false,
    action: @Composable (ColumnScope.() -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onReopen != null && !live) Modifier.clickable(onClick = onReopen) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.CenterStart) {
                if (done) {
                    Icon(
                        Icons.Default.Check, null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    live || done -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
        }

        Column(Modifier.padding(start = 22.dp)) {
            if (done && !live && result.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    result,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (live) {
                detail?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (action != null) {
                    Spacer(Modifier.height(18.dp))
                    action()
                }
            }
        }
    }

}
