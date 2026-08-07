package com.eazyverse.testtrack.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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

    /**
     * Abandons the account and hands back to navigate.
     *
     * Setup is where a tester lands on the wrong Gmail — the checklist is keyed to the account,
     * and every step below the first fails for an address that is not in the group. Without this
     * the only way back to the picker was to uninstall.
     */
    fun signOut(context: Context, then: () -> Unit) {
        viewModelScope.launch {
            releaseSession(context)
            then()
        }
    }

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
                message = e.friendly("We couldn't check your group membership just now. Try again in a moment.")
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
                message = e.friendly("Google didn't complete the Drive connection. Try it again.")
            }
            connectingDrive = false
        }
    }
}

@Composable
fun SetupScreen(
    onFinished: () -> Unit,
    onSignOut: () -> Unit,
    vm: SetupViewModel = viewModel()
) {
    val activity = LocalContext.current.findActivity()
    var confirmSignOut by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val token = AuthRepo.tokenFromConsent(activity, result.data)
        Session.updateDriveConnected(token != null)
        if (token == null) vm.message = "Drive access was not granted"
    }

    // Usage access and notifications both live in system settings and can be switched off behind
    // our back, so they are re-read every time this screen comes forward rather than trusted from
    // a stored flag.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Session.refreshUsageAccess(activity)
                Session.refreshNotifications(activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The channel exists before the prompt, so the name the tester sees in system settings is the
    // one they will be looking for if they ever go turning it off.
    LaunchedEffect(Unit) { PushRepo.ensureChannel(activity) }

    /**
     * Android answers the notification prompt exactly once per install.
     *
     * A second request after a refusal returns denied without showing anything, so the button has
     * to change into one that opens system settings instead — otherwise it becomes a control that
     * visibly does nothing.
     */
    var promptSpent by rememberSaveable { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        promptSpent = true
        Session.refreshNotifications(activity)
        Session.updateRemindersAsked()
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
        Session.usageAccessGranted,
        Session.remindersSettled
    )

    /**
     * Only the first outstanding step is live; the rest are inert until it clears. Each depends on
     * the one before, so a control that cannot succeed yet is worse than no control.
     *
     * A finished step then stays shut. Only sign-in reopens, and only because the wrong Gmail is
     * the one failure the phone cannot detect for itself. Everything else is re-read when this
     * screen comes forward, so a revoked grant or a switch turned off in Settings drops its step
     * back to outstanding and opens it without being asked.
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
                "Five things to do once. Take them in order.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
          Panel {

            // The only step that reopens. The rest repair themselves: a revoked Drive grant or a
            // usage switch turned off in Settings is re-read when this screen comes forward, and
            // the step goes back to outstanding and opens on its own. Signing in is the one thing
            // the phone cannot notice going wrong, because the wrong Gmail is not a fault, and
            // every step below it fails without saying so.
            Step(
                title = "Sign in",
                result = Session.email ?: "",
                done = done[0],
                live = live(0),
                onReopen = { reopened = if (live(0)) null else 0 }.takeIf { done[0] }
            ) {
                Text(
                    "You're signed in as ${Session.email ?: "this account"}. If that's the wrong " +
                        "Gmail, sign out and pick another one. Your place in the group and your " +
                        "Drive both belong to the account, so the steps below will start again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { confirmSignOut = true },
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
            }

            Step(
                title = "Join the testers group",
                result = "You're in the group",
                detail = "App owners can only add you to their closed test if you're in this " +
                    "group. Open it, join with the same Gmail you signed in with, then come back " +
                    "and verify.",
                done = done[1],
                live = live(1),
                onReopen = null
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
                detail = "Your daily screenshots go to your own Google Drive, so the proof stays " +
                    "yours. We can only see the files we put there and nothing else in it.",
                done = done[2],
                live = live(2),
                onReopen = null
            ) {
                Primary("Connect Drive", busy = vm.connectingDrive) {
                    vm.connectDrive(activity) { consentLauncher.launch(it) }
                }
            }

            Step(
                title = "Allow usage access",
                result = "Time in each app is recorded",
                detail = "Your daily report shows how long you actually spent in each app, not " +
                    "just that you opened it. Android only shares that if you switch on usage " +
                    "access, and there's no pop-up to ask for it, so you'll need to find " +
                    "TestTrack in the list and turn it on yourself.",
                done = done[3],
                live = live(3),
                onReopen = null
            ) {
                Primary("Open usage access settings") { UsageRepo.openSettings(activity) }

                // The most common way this step fails, and the one a tester cannot solve by
                // trying harder: the switch is there, they tap it, and Android refuses without
                // explaining that the cause is how the app was installed rather than anything
                // they did. Written out in full because the menu that unblocks it stays hidden
                // until the refusal has been seen once, so nobody finds it by looking.
                Spacer(Modifier.height(20.dp))
                Text(
                    "If the switch won't turn on",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Some phones, Samsung especially, block this for apps installed from a " +
                        "browser instead of the Play Store. You'll see a message saying the " +
                        "setting is restricted. Nothing is wrong with your phone or with " +
                        "TestTrack, and you can turn it on yourself:\n\n" +
                        "1. Tap the switch once and close the message that appears\n" +
                        "2. Open Settings, then Apps, then TestTrack\n" +
                        "3. Tap the three dot menu in the top right corner\n" +
                        "4. Choose Allow restricted settings\n" +
                        "5. Confirm with your PIN, pattern or fingerprint\n" +
                        "6. Come back here and the switch will work\n\n" +
                        "The menu in step 3 only appears after you've tapped the switch once, so " +
                        "don't skip the first step.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.5f
                )
            }

            Step(
                title = "Turn on reminders",
                result = if (Session.notificationsGranted) "You'll be nudged if a day is slipping"
                         else "Off, so you'll need to remember on your own",
                detail = "A run is fourteen days with no gaps, and one missed day resets the " +
                    "clock for everyone in your group, not just for you. We'll only nudge you " +
                    "when apps are still waiting, never to say well done.",
                done = done[4],
                live = live(4),
                last = true,
                onReopen = null
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !promptSpent) {
                    Primary("Turn on reminders") {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    // Either the prompt is spent, or this is Android 12 and below where there was
                    // never a prompt to spend — both lead to the same place.
                    Primary("Open notification settings") {
                        activity.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                        )
                        Session.updateRemindersAsked()
                    }
                }
                TextButton(
                    onClick = { Session.updateRemindersAsked() },
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) { Text("Not now") }
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

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    "You'll come back to the account picker and can sign in with a different " +
                        "Gmail. Nothing you've already reported is lost."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    // Setup is reachable from home, where a round may still be running. A no-op
                    // when there is nothing to end.
                    CaptureService.endSession(activity)
                    vm.signOut(activity) {
                        Cache.clear()
                        onSignOut()
                    }
                }) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            }
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
