package com.eazyverse.testtrack.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.Config
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.MembershipViewModel

/** The one membership check for the whole app, whichever screen asks. */
@Composable
fun rememberMembership(): MembershipViewModel = viewModel(LocalContext.current.findActivity() as ComponentActivity)

/**
 * Checking the testers group, and what to do when this account is not on it.
 *
 * Three faces: the check in progress, a no, and a check that could not be made. The no says
 * exactly why it matters and the one thing to do, and coming back from the group page checks
 * again by itself. A check that failed is said as that, never as "you are not a member": telling
 * somebody who joined an hour ago they are not in the group is the answer most likely to make
 * them give up.
 */
@Composable
fun JoinGroupDialog(vm: MembershipViewModel = rememberMembership()) {
    if (!vm.asking) return
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watcher = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.returned() }
        owner.lifecycle.addObserver(watcher)
        onDispose { owner.lifecycle.removeObserver(watcher) }
    }
    JoinGroupAsk(
        email = vm.email,
        checking = vm.checking,
        outside = vm.outside,
        failure = vm.failure,
        onCheck = vm::check,
        onOpenGroup = {
            vm.openedGroup()
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.GROUP_URL)))
        },
        onClose = vm::close,
    )
}

/** The dialog, drawn from plain values so each of its faces can be put in front of a test. */
@Composable
fun JoinGroupAsk(
    email: String,
    checking: Boolean,
    outside: Boolean,
    failure: String?,
    onCheck: () -> Unit,
    onOpenGroup: () -> Unit,
    onClose: () -> Unit,
) {
    val account = email.ifBlank { "this Google account" }
    when {
        failure != null ->
            Ask(
                title = "Couldn't check the testers group",
                body = "$failure\n\nReviews and crew testing need this check first.",
                confirm = if (checking) "Checking…" else "Try again",
                confirmEnabled = !checking,
                onConfirm = onCheck,
                onDismiss = onClose,
                dismiss = "Not now",
            )
        outside ->
            Ask(
                title = "Join the testers group",
                body =
                    "$account isn't in the TestTrack testers group yet. Owners can only add you " +
                        "to their closed tests once it is, so reviews and crew testing stay locked " +
                        "until then.\n\nOpen the group with this same account, tap Join group, then " +
                        "come back. If the page says you don't have permission, ignore that and tap Join.",
                confirm = "Open the group",
                onConfirm = onOpenGroup,
                onDismiss = onClose,
                dismiss = "Not now",
            ) {
                TextButton(onClick = onCheck, enabled = !checking) {
                    Text(
                        if (checking) "Checking…" else "I've joined, check again",
                        fontWeight = FontWeight.SemiBold,
                        color = Tt.colors.accentInk,
                    )
                }
            }
        else ->
            Ask(
                title = "Checking the testers group",
                body = "Making sure $account can be added to closed tests.",
                confirm = "Checking…",
                confirmEnabled = false,
                onConfirm = {},
                onDismiss = onClose,
                dismiss = "Cancel",
            ) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
            }
    }
}
