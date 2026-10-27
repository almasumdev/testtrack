package com.eazyverse.testtrack.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.viewmodel.CrewExtrasViewModel

/*
 * The crew's acts beyond opening apps, wired once for the tab and for a crew's page: the owner's
 * Update and Down tonight, and a member's private feedback on Play with its picture. The rules are
 * docs/crew.md's; the server holds them, and this only asks, explains and sends.
 */

/** The new crew actions, added to [base]: what [crewActions] hands every row. */
@Composable
internal fun withCrewExtras(
    vm: CrewViewModel,
    base: CrewActions,
): CrewActions {
    val context = LocalContext.current
    val extras: CrewExtrasViewModel = viewModel()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) extras.pick(context.findActivity(), uri) else extras.cancelFeedback()
        }
    return CrewActions(
        onOpen = base.onOpen,
        onOpenAll = base.onOpenAll,
        onInstall = base.onInstall,
        onReport = base.onReport,
        onLeave = base.onLeave,
        onTakeSeat = base.onTakeSeat,
        onVote = base.onVote,
        isInstalled = base.isInstalled,
        onMembers = base.onMembers,
        busySeat = base.busySeat ?: (if (extras.busy) extras.feedbackFor?.seatId else null),
        onPickPicture = base.onPickPicture,
        sendingPicture = base.sendingPicture,
        picturesStuck = base.picturesStuck,
        onRetryPictures = base.onRetryPictures,
        localVersion = { pkg -> extras.copy(context, pkg).takeIf { it.installed }?.versionCode },
        onUpdateApp = { app -> openOnPlay(context, app.packageName) },
        onFeedback = { app -> extras.askFeedback(app.id, app.label, app.packageName) },
        onPickFeedback = { app ->
            extras.askFeedback(app.id, app.label, app.packageName, asking = false)
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onTellUpdate = { seat ->
            val why = extras.updateBlocked(context, seat.packageName, seat.versionCode)
            if (why != null) {
                extras.explain = why
            } else {
                extras.tellUpdate(context.findActivity(), seat.id, seat.packageName) { vm.load(context) }
            }
        },
        onMarkDown = { seat -> extras.confirmDown = seat.id },
        updateBlocked = { seat -> extras.updateBlocked(context, seat.packageName, seat.versionCode) },
    )
}

/** The asks those actions raise. Shown wherever [CrewDialogsHost] is. */
@Composable
internal fun CrewExtrasAsks(vm: CrewViewModel) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val extras: CrewExtrasViewModel = viewModel()

    // Its lines go where the Crew page says everything else.
    LaunchedEffect(extras.message) {
        extras.message?.let {
            vm.pictures.tell(it)
            extras.clearMessage()
        }
    }
    // Back from Play with the camera's picture: it goes to Drive and onto the feedback.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watcher = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) extras.takeCaptured(activity) }
        owner.lifecycle.addObserver(watcher)
        onDispose { owner.lifecycle.removeObserver(watcher) }
    }

    val consent =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                extras.startCapture(activity, result.resultCode, data)
            } else {
                extras.cancelFeedback()
            }
        }

    extras.feedbackFor?.takeIf { !it.capturing && it.asking }?.let { job ->
        Ask(
            title = "Private feedback for ${job.label}",
            body =
                "1. Tap Start and allow screen sharing.\n" +
                    "2. On its Play page, tap \"Send private feedback\" and write what you found.\n" +
                    "3. Before you send it, tap TestTrack's round camera button to take its picture.\n" +
                    "Once a run per app. Only the TestTrack team sees the picture.",
            confirm = "Start",
            dismiss = "Not now",
            onConfirm = { consent.launch(wholeScreenCapture(context)) },
            onDismiss = { extras.cancelFeedback() },
        )
    }

    extras.explain?.let { why ->
        Ask(
            title = "Update can't be told yet",
            body = why,
            confirm = "OK",
            dismiss = null,
            onConfirm = { extras.explain = null },
            onDismiss = { extras.explain = null },
        )
    }

    extras.confirmDown?.let { seatId ->
        Ask(
            title = "Mark your app down tonight?",
            body =
                "Nobody in the crew owes it tonight, and nobody is marked down for it. It costs you a " +
                    "night towards Google's 14, and you can do it ${CrewRules.DOWN_NIGHTS} times a run.",
            confirm = "Mark it down",
            dismiss = "Keep it",
            onConfirm = {
                extras.confirmDown = null
                extras.markDown(seatId) { vm.load(context) }
            },
            onDismiss = { extras.confirmDown = null },
        )
    }
}
