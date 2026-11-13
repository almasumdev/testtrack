package com.eazyverse.testtrack.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.viewmodel.HudViewModel
import com.eazyverse.testtrack.viewmodel.TestingDriveViewModel

/**
 * What the Open tab's join steps need, read again on every return to TestTrack: the switches are
 * turned on in Android's settings, the group is joined in a browser and Drive is granted on a
 * Google screen, and coming back should show the step gone.
 *
 * [myApps] is null until My apps has answered, which keeps the "add an app" step from flashing
 * for somebody who has one.
 */
@Composable
internal fun rememberCrewGuide(
    vm: CrewViewModel,
    myApps: List<AppEntry>?,
    onSignIn: () -> Unit,
    onAddApp: () -> Unit,
    onOpenCircle: () -> Unit,
): CrewGuide {
    val context = LocalContext.current
    val membership = rememberMembership()
    var resumes by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watcher = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumes++ }
        owner.lifecycle.addObserver(watcher)
        onDispose { owner.lifecycle.removeObserver(watcher) }
    }
    val permissions = remember(resumes) { vm.permissionsReady(context) }
    // Asked of Google without showing anything, on every return, since the grant is the account's.
    val drive: TestingDriveViewModel = viewModel()
    val activity = context.findActivity()
    LaunchedEffect(resumes, vm.signedOut) { if (!vm.signedOut) drive.check(activity) }
    val driveSheet =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            drive.consentResult(activity, it.data) {}
        }
    // The header's balance, the same one the seat sheet reads: unknown until the server has answered.
    val hud: HudViewModel = viewModel()
    return CrewGuide(
        join =
            CrewJoinState(
                signedOut = vm.signedOut,
                member = membership.member,
                // A closed app is any that is not live on Play; a live one goes to Feedback.
                closedApp = myApps?.any { !it.live },
                drive = drive.connected,
                tix = if (hud.level != null) hud.tix else null,
                permissions = permissions,
            ),
        onSignIn = onSignIn,
        onJoin = { membership.show() },
        onAddApp = onAddApp,
        onDrive = { drive.connect(activity, driveSheet::launch) {} },
        onCircle = onOpenCircle,
        onAllow = { vm.allowPermissions(activity) },
    )
}
