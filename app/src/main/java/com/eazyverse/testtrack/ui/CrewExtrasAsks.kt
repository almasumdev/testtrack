package com.eazyverse.testtrack.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquareText
import com.composables.icons.lucide.TriangleAlert
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
    val membership = rememberMembership()
    val extras: CrewExtrasViewModel = viewModel()
    val pickPicture =
        rememberImagePicker { uri ->
            if (uri != null) extras.pick(uri) else extras.cancelFeedback()
        }
    // Hidden ask: Write goes straight to Android's own, from the tap itself, never from a state change.
    val askCapture =
        rememberCaptureConsent(
            onGranted = { code, data -> extras.startCapture(context.findActivity(), code, data) },
            onRefused = { extras.cancelFeedback() },
        )
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
        busySeat = base.busySeat,
        onPickPicture = base.onPickPicture,
        sendingPicture = base.sendingPicture,
        waitingPicture = base.waitingPicture,
        latePicture = base.latePicture,
        picturesStuck = base.picturesStuck,
        onRetryPictures = base.onRetryPictures,
        localVersion = { pkg -> vm.copyOf(context, pkg).takeIf { it.installed }?.versionCode },
        fromPlay = { pkg -> vm.copyOf(context, pkg).let { !it.installed || it.fromPlay } },
        onUpdateApp = { app -> membership.require { openOnPlay(context, app.packageName) } },
        onFeedback = { app ->
            membership.require {
                if (extras.askHidden) {
                    extras.askFeedback(app.id, app.label, app.packageName, asking = false)
                    askCapture()
                } else {
                    extras.askFeedback(app.id, app.label, app.packageName)
                }
            }
        },
        feedbackUpload = { id -> extras.uploads[id] },
        onRetryFeedback = { app -> extras.retryFeedback(context, app.id) { vm.load(context.applicationContext) } },
        onPickFeedback = { app ->
            extras.askFeedback(app.id, app.label, app.packageName, asking = false)
            pickPicture()
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
        onAppeal = { seat -> extras.askAppeal(seat.id, seat.appName) },
        onUninstall = { pkg -> uninstall(context, pkg) },
        onLeaveVolunteer = base.onLeaveVolunteer,
        onFindCrew = base.onFindCrew,
        privateLine = base.privateLine,
        onPrivateTesters = base.onPrivateTesters,
        onWithdrawReport = base.onWithdrawReport,
    )
}

/** The asks those actions raise. Shown wherever [CrewDialogsHost] is. */
@Composable
internal fun CrewExtrasAsks(vm: CrewViewModel) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val extras: CrewExtrasViewModel = viewModel()
    val pickPicture =
        rememberImagePicker { uri ->
            if (uri != null) extras.pick(uri) else extras.cancelFeedback()
        }

    // Back from Play with the camera's picture: it opens in the editor to be checked, then goes up.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watcher = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) extras.takeCaptured(activity) }
        owner.lifecycle.addObserver(watcher)
        onDispose { owner.lifecycle.removeObserver(watcher) }
    }

    // A sent picture's tile stays as sent until the crews read that the seat no longer owes feedback.
    LaunchedEffect(vm.crews) {
        if (vm.crews.isEmpty()) return@LaunchedEffect
        val seats = vm.crews.flatMap { it.apps + it.optional + it.left }
        val owing = seats.filter { !it.feedbackSent }.mapTo(HashSet()) { it.id }
        extras.clearSentUploads { it in owing }
    }

    val askCapture =
        rememberCaptureConsent(
            onGranted = { code, data -> extras.startCapture(activity, code, data) },
            onRefused = { extras.cancelFeedback() },
        )

    extras.feedbackFor?.takeIf { !it.capturing && it.asking }?.let { job ->
        var hide by remember { mutableStateOf(false) }
        Ask(
            title = "Private feedback",
            icon = Lucide.MessageSquareText,
            note = "Once a run per app. Only our team sees the picture.",
            confirm = "Start",
            dismiss = "Not now",
            onConfirm = {
                if (hide) extras.hideAsk()
                askCapture()
            },
            onDismiss = { extras.cancelFeedback() },
            alt = "Use saved",
            onAlt = {
                extras.askFeedback(job.seatId, job.label, job.packageName, asking = false)
                pickPicture()
            },
        ) {
            DialogSteps(
                listOf(
                    "Tap Start and allow screen sharing.",
                    "On Play, tap Send private feedback and write it.",
                    "Before sending, tap the round camera button.",
                ).map { AnnotatedString(it) },
            )
            AskCheck("Don't show again", hide) { hide = it }
        }
    }

    extras.editing?.let { picture ->
        ProofPictureEditor(
            picture,
            onKeep = { edited -> extras.sendEdited(context, edited) { vm.load(context.applicationContext) } },
            onClose = { extras.closeEditor() },
            title = "Check your picture",
            copy = "Only the TestTrack team sees it. Hide anything personal first.",
            primaryLabel = "Send",
            failText = "The edited picture could not be saved. Try again.",
        )
    }

    extras.explain?.let { why ->
        Ask(
            title = "Can't tell the crew yet",
            icon = Lucide.TriangleAlert,
            tone = Tone.WARN,
            body = why,
            confirm = "OK",
            dismiss = null,
            onConfirm = { extras.explain = null },
            onDismiss = { extras.explain = null },
        )
    }

    extras.appealFor?.let { (seatId, appName) ->
        AppealSheet(
            appName = appName,
            busy = extras.busy,
            error = extras.appealError,
            onSend = { text, sent -> extras.appeal(seatId, text, reload = { vm.load(context) }, sent = sent) },
            onDismiss = { extras.closeAppeal() },
        )
    }

    extras.confirmDown?.let { seatId ->
        Ask(
            title = "Mark your app down tonight?",
            body = "Nobody owes it tonight. It costs you one of Google's 14 nights. You have ${CrewRules.DOWN_NIGHTS} a run.",
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

/**
 * Android's own "uninstall this app?" for one package. TestTrack removes nothing by itself: the
 * phone asks, and the person says yes or no.
 */
internal fun uninstall(
    context: Context,
    packageName: String,
): Boolean =
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

/**
 * The owner's appeal, in their own words: once per app, read by the team. It closes only once
 * the server has it; a failure is said under the words, which stay for another try.
 */
@Composable
internal fun AppealSheet(
    appName: String,
    busy: Boolean,
    /** The words and how to close the sheet once they are in. */
    onSend: (String, () -> Unit) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
) {
    var text by rememberSaveable { mutableStateOf("") }
    PickerSheet(
        title = "Ask to get $appName back",
        subtitle = "Tell the team what happened on the nights you missed. They usually answer within a day.",
        onDismiss = onDismiss,
    ) { close ->
        OutlinedField(
            value = text,
            onValueChange = { text = it.take(CrewRules.APPEAL_MAX) },
            label = "What happened",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            maxLength = CrewRules.APPEAL_MAX,
            error = error,
        )
        Spacer(Modifier.height(16.dp))
        Primary("Send", busy = busy, enabled = text.isNotBlank()) { onSend(text.trim()) { close {} } }
        Spacer(Modifier.height(20.dp))
    }
}
