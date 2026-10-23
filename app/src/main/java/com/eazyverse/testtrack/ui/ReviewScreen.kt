package com.eazyverse.testtrack.ui

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.TestingDriveViewModel
import kotlinx.coroutines.delay

/**
 * Guided installation, app usage, private feedback and proof. The server accepts transitions.
 * [ReviewBody] draws supplied state independently of account/network services.
 */
@Composable
fun ReviewScreen(
    post: ApiPost,
    onClose: () -> Unit,
    vm: ReviewViewModel = viewModel(),
) {
    val context = LocalContext.current

    LaunchedEffect(post.id) { vm.start(context, post) }
    WhileShown(onShow = vm::startLive, onHide = vm::stopLive)

    // Every return to this screen is a measurement. Coming back from the app under review is the
    // whole interaction, so resume is where the stepper moves rather than a button.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watcher =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    vm.dropStrayButton(context)
                    vm.refresh(context)
                }
            }
        owner.lifecycle.addObserver(watcher)
        onDispose { owner.lifecycle.removeObserver(watcher) }
    }

    var guiding by remember { mutableStateOf(false) }
    var askingOverlay by remember { mutableStateOf(false) }
    var givingUp by remember { mutableStateOf(false) }
    val consent =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                vm.startCapture(context, result.resultCode, data, post.packageName)
            } else {
                openPlay(context, post.packageName)
            }
        }
    val toCapture: () -> Unit = {
        if (!vm.canFloat(context)) {
            askingOverlay = true
        } else {
            val projections = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            // The whole screen, asked for by name: on Android 14 and later the default is "share one
            // app", which adds an app picker to a one-tap screenshot.
            val ask =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    projections.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                } else {
                    projections.createScreenCaptureIntent()
                }
            consent.launch(ask)
        }
    }

    // The proof goes to the reviewer's own Drive. Asked of Google quietly once step four is up;
    // connecting is one sheet, and a Send that was waiting on it goes ahead as soon as it is done.
    val activity = context.findActivity()
    val drive: TestingDriveViewModel = viewModel()
    var sendAfterDrive by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    val connected = {
        sendAfterDrive?.let { (proof, note) -> vm.submit(activity, proof, note) }
        sendAfterDrive = null
    }
    val driveSheet =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            drive.consentResult(activity, it.data, connected)
            if (!vm.driveConnected) sendAfterDrive = null
        }
    val membership = rememberMembership()
    val requireMembership: (() -> Unit) -> Unit = { action ->
        if (post.isLive) action() else membership.require(action)
    }
    val atProof = vm.job?.step == ReviewStep.PROVE
    LaunchedEffect(atProof) { if (atProof) vm.checkDrive(activity) }

    ReviewBody(
        post = post,
        job = vm.job,
        proof = vm.proof,
        // The screenshot already got stays until a new one arrives, so a capture abandoned half
        // way does not cost the one there was.
        onCapture = { if (vm.guideSeen(context)) toCapture() else guiding = true },
        onPicked = { vm.keepPicked(context, it) },
        note = vm.note,
        onNote = vm::editNote,
        sideloaded = vm.sideloaded,
        busy = vm.busy,
        usageAccess = vm.usageAccess,
        onAllowUsage = { vm.openUsageSettings(context) },
        message = drive.message ?: vm.message,
        onMessageDone = {
            drive.clearMessage()
            vm.clearMessage()
        },
        driveConnected = vm.driveConnected,
        connectingDrive = drive.busy,
        onConnectDrive = { drive.connect(activity, driveSheet::launch, connected) },
        offline = vm.offline,
        failure = vm.failure,
        onRetry = { vm.retry(context, post) },
        onClose = onClose,
        // Closed tests need group membership; publicly available apps do not.
        onLeave = { step ->
            requireMembership {
                when (step) {
                    ReviewStep.INSTALL -> openPlay(context, post.packageName)
                    ReviewStep.USE -> openApp(context, post.packageName)
                    ReviewStep.LEAVE -> vm.markLeft()
                    else -> Unit
                }
            }
        },
        onGiveUp = { givingUp = true },
        onSend = { proof, note ->
            requireMembership {
                if (vm.driveConnected) {
                    vm.submit(activity, proof, note)
                } else {
                    sendAfterDrive = proof to note
                    drive.connect(activity, driveSheet::launch, connected)
                }
            }
        },
    )

    if (givingUp) {
        Ask(
            title = "Give this review up?",
            body = "The slot goes back for somebody else, and you can't take this one again.",
            confirm = "Give it up",
            destructive = true,
            onConfirm = {
                givingUp = false
                vm.abandon(onClose)
            },
            onDismiss = { givingUp = false },
        )
    }

    if (guiding) {
        ReviewGuideDialog(
            live = post.isLive,
            onOk = {
                guiding = false
                vm.markGuideSeen(context)
                toCapture()
            },
            onDismiss = { guiding = false },
        )
    }
    if (askingOverlay) {
        OverlayPermissionDialog(
            onOpen = {
                askingOverlay = false
                vm.openOverlaySettings(context)
            },
            onDismiss = { askingOverlay = false },
        )
    }
}

/**
 * The review, drawn: one step at a time around a dial.
 *
 * The app at the top, the button at the bottom, and between them only the step you are on: a dial
 * of four segments that says how far through you are (and is the thirty second clock while you
 * use the app), what to do now, and what the developer asked. It is centred in the space between,
 * and sized off the screen's height so a small phone shows all of it without scrolling.
 *
 * Takes what it shows rather than fetching it, so any step can be put in front of a test.
 * [onLeave] covers the steps that hand somebody to another app; writing and the proof open a
 * dialog this owns, because that is where the words and the picture are given.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewBody(
    post: ApiPost,
    job: ReviewJob?,
    sideloaded: Boolean,
    busy: Boolean = false,
    usageAccess: Boolean = true,
    onAllowUsage: () -> Unit = {},
    message: String? = null,
    onMessageDone: () -> Unit = {},
    offline: Boolean = false,
    failure: String? = null,
    onRetry: () -> Unit = {},
    onClose: () -> Unit,
    onLeave: (ReviewStep) -> Unit,
    onSend: (proof: Uri, note: String) -> Unit,
    proof: Uri? = null,
    onCapture: () -> Unit = {},
    onPicked: (Uri) -> Unit = {},
    note: String = "",
    onNote: (String) -> Unit = {},
    driveConnected: Boolean = true,
    connectingDrive: Boolean = false,
    onConnectDrive: () -> Unit = {},
    onGiveUp: (() -> Unit)? = null,
) {
    var editingProof by remember { mutableStateOf<Uri?>(null) }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) editingProof = uri
        }
    val shown = proof
    val t = Tt.colors
    // A spinner only for work that takes a moment: a quick check on coming back stays still.
    var spinning by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        if (busy) delay(SPIN_AFTER_MS)
        spinning = busy
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                PageBar("Review", onClose) {
                    Pill("+${post.reward} tix", t.tileInk, t.tile)
                }
                PageMessage(message, onMessageDone)
            }
        },
        bottomBar = {
            // Always there, even while loading and once sent, so the space the page is centred in
            // never changes size under it.
            Box(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 18.dp)) {
                if (job == null && failure != null) {
                    Primary("Try again", busy = spinning, onClick = onRetry)
                } else if (job == null) {
                    Spacer(Modifier.height(52.dp))
                } else if (job.settled) {
                    Primary("Done", onClick = onClose)
                } else {
                    val asking = job.step == ReviewStep.USE && !usageAccess
                    // Step four cannot send until there is a screenshot to send; the note is optional.
                    val waitingForProof = job.step == ReviewStep.PROVE && shown == null
                    val needsDrive = job.step == ReviewStep.PROVE && !driveConnected
                    Primary(
                        when {
                            asking -> "Allow usage access"
                            needsDrive -> "Connect Drive and send"
                            else -> stepAction(job.step, job)
                        },
                        busy = spinning,
                        enabled = !waitingForProof,
                    ) {
                        if (asking) {
                            onAllowUsage()
                            return@Primary
                        }
                        // The proof is picked here. Leaving it on Play opens Play under the camera
                        // button; every other step hands the reviewer to Play or to the app itself.
                        val step = job.step
                        when {
                            step == ReviewStep.LEAVE -> onCapture()
                            step != ReviewStep.PROVE -> onLeave(step)
                            shown != null -> onSend(shown, note.trim())
                            else -> Unit
                        }
                    }
                }
            }
        },
    ) { padding ->
        // A tap on the page, away from the note, puts the keyboard away.
        val dismissKeyboard = rememberDismissKeyboard()
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).dismissKeyboardOnTap(dismissKeyboard)) {
            // Everything scales off the height left, so the whole step fits on a small phone.
            val room = maxHeight
            val dial = (room * 0.30f).coerceIn(108.dp, 168.dp)
            val gap = (room * 0.03f).coerceIn(10.dp, 22.dp)

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter),
            ) {
                ReviewAppRow(post.appName, post.packageName, post.isLive, post.ownerName, offline, icon = post.icon)
                // The dial is centred in the room under this row; when a step is taller than that
                // room it starts at the top instead, and without this it sat flush against the row.
                Spacer(Modifier.height(gap))
                // Which page this is, rather than the job itself: keyed on the job, every answer
                // from the server faded the whole page out and back in. Now only a real change of
                // page does: loading to the step, the step to sent.
                val page =
                    when {
                        job == null && failure != null -> ReviewPage.FAILED
                        job == null -> ReviewPage.LOADING
                        job.settled -> ReviewPage.DONE
                        else -> ReviewPage.STEP
                    }
                Crossfade(page, label = "review") { shownPage ->
                    if (shownPage == ReviewPage.FAILED) {
                        ReviewFailed(failure.orEmpty(), room)
                        return@Crossfade
                    }
                    if (job == null || shownPage == ReviewPage.LOADING) {
                        ReviewLoading(room, dial, gap)
                        return@Crossfade
                    }
                    if (shownPage == ReviewPage.DONE) {
                        ReviewDone(job, room)
                        return@Crossfade
                    }
                    // Centred in what is left under the app row; scrolls only if a phone is smaller
                    // than anything this was sized for.
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = room - 64.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        StepDial(job, dial)
                        Spacer(Modifier.height(8.dp))
                        StepLabels(job)
                        Spacer(Modifier.height(gap))
                        val stepCopy =
                            (
                                when {
                                    job.settled -> "Sent"
                                    job.step == ReviewStep.USE && !usageAccess -> "Allow usage time"
                                    else -> stepTitle(job)
                                }
                            ) to (
                                when {
                                    job.settled -> reviewOutcome(job.review.state, job.live)
                                    job.step == ReviewStep.USE && !usageAccess ->
                                        "Allow Usage access for TestTrack, then return. Time already spent still counts."
                                    job.step == ReviewStep.PROVE && shown != null ->
                                        "Check the screenshot and hide personal details before " +
                                            "sending."
                                    else -> stepLine(job)
                                }
                            )
                        AnimatedContent(
                            targetState = stepCopy,
                            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) using SizeTransform(clip = false) },
                            label = "step",
                        ) { copy ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    copy.first,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    copy.second,
                                    Modifier.widthIn(max = 300.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = t.text2,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        if (!job.settled && job.step == ReviewStep.LEAVE) SelfShotLink { onLeave(ReviewStep.LEAVE) }
                        if (sideloaded) {
                            Spacer(Modifier.height(gap))
                            Notice(Status.short, Lucide.TriangleAlert) {
                                Text(
                                    "That copy did not come from Play, so it cannot count. " +
                                        "Install it through the button below instead.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        if (!job.settled && job.step == ReviewStep.PROVE) {
                            Spacer(Modifier.height(gap))
                            ProofArea(
                                shown = shown,
                                note = note,
                                onNote = onNote,
                                onCapture = onCapture,
                                onGallery = {
                                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                onInspect = { editingProof = it },
                            )
                            if (!driveConnected) {
                                Spacer(Modifier.height(12.dp))
                                DriveConnectCard(connectingDrive, onConnectDrive)
                            }
                        }
                        val asked = post.ask.trim()
                        if (!job.settled && job.step != ReviewStep.PROVE && asked.isNotEmpty() && asked != GENERAL_ASK) {
                            Spacer(Modifier.height(gap))
                            DeveloperAsks(asked)
                        }
                        // The way out, quiet and at the end: somebody who cannot finish gives the
                        // slot back rather than holding it until it runs out.
                        // What losing a dispute costs, said on the first step, before any work is done.
                        if (!job.settled && job.step == ReviewStep.INSTALL) {
                            Spacer(Modifier.height(gap))
                            DisputeNote()
                        }
                        if (!job.settled && onGiveUp != null) {
                            Spacer(Modifier.height(gap))
                            GiveUpLink(onGiveUp)
                        }
                    }
                }
            }
        }
    }
    editingProof?.let { uri ->
        ProofEditor(uri, onKeep = onPicked, onClose = { editingProof = null })
    }
}

private enum class ReviewPage { LOADING, FAILED, STEP, DONE }

private const val SPIN_AFTER_MS = 250L

/** What a post carries when the developer asked nothing in particular; not worth showing. */
private const val GENERAL_ASK = "General feedback on the app experience."

internal val ReviewSteps = ReviewStep.entries.filter { it != ReviewStep.WAIT }
