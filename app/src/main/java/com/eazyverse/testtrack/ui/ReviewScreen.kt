package com.eazyverse.testtrack.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.eazyverse.testtrack.viewmodel.requirePlayInstall
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

    var givingUp by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    val capture = rememberReviewCapture(vm, post.packageName, post.isLive)

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
        onCapture = capture,
        onPicked = { vm.keepPicked(context, it) },
        note = vm.note,
        onNote = vm::editNote,
        sideloaded = vm.sideloaded,
        busy = vm.busy,
        usageAccess = vm.usageAccess,
        onAllowUsage = { if (vm.requirePlayInstall(context)) vm.openUsageSettings(context) },
        message = drive.message ?: vm.message,
        onMessageDone = {
            drive.clearMessage()
            vm.clearMessage()
        },
        driveConnected = vm.driveConnected,
        connectingDrive = drive.busy,
        onConnectDrive = { if (vm.requirePlayInstall(context)) drive.connect(activity, driveSheet::launch, connected) },
        offline = vm.offline,
        failure = vm.failure,
        onRetry = { vm.start(context, post) },
        onClose = onClose,
        // Closed tests need group membership; publicly available apps do not.
        onLeave = { step ->
            requireMembership {
                when (step) {
                    ReviewStep.INSTALL -> vm.install(context) { openPlay(context, post.packageName) }
                    ReviewStep.USE -> {
                        if (vm.requirePlayInstall(context)) {
                            val current = vm.job
                            if (current != null && current.waitingForDay && current.usedOnTwoDays) {
                                vm.refresh(context)
                            } else {
                                openApp(context, post.packageName)
                            }
                        }
                    }
                    ReviewStep.LEAVE -> vm.markLeft()
                    else -> Unit
                }
            }
        },
        // Availability comes from the current review state.
        onGiveUp = { givingUp = true },
        onReport = { reporting = true },
        onNoBox = { if (vm.markNoBox()) capture() },
        onReply = { text, picture -> vm.reply(activity, text, picture) },
        onSend = { proof, note ->
            requireMembership {
                if (vm.requirePlayInstall(context)) {
                    if (vm.driveConnected) {
                        vm.submit(activity, proof, note)
                    } else {
                        sendAfterDrive = proof to note
                        drive.connect(activity, driveSheet::launch, connected)
                    }
                }
            }
        },
    )
    if (reporting && vm.job?.canReportInstall == true) {
        InstallReportDialog(
            appName = post.appName,
            onSend = { reason, note ->
                reporting = false
                vm.report(reason, note, onClose)
            },
            onDismiss = { reporting = false },
        )
    }

    if (givingUp && vm.job?.canGiveUp == true) {
        GiveUpDialog(
            onGiveUp = {
                givingUp = false
                vm.abandon(onClose)
            },
            onDismiss = { givingUp = false },
        )
    }
}

/**
 * The review, drawn: a labelled four-step route, the current task and its completion check.
 * New reviews require an explicit agreement before the start action can run.
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
    onReport: (() -> Unit)? = null,
    onNoBox: () -> Unit = {},
    onReply: (String, Uri?) -> Unit = { _, _ -> },
) {
    var rulesAccepted by rememberSaveable(post.id, post.feedback, post.needsChars, post.ask) { mutableStateOf(false) }
    val needsAgreement = job?.step == ReviewStep.INSTALL && job.review.id.isEmpty()
    val now by rememberReviewNow(job)
    val scroll = rememberScrollState()
    LaunchedEffect(post.id, job?.step) { scroll.scrollTo(0) }
    var editingProof by remember { mutableStateOf<Uri?>(null) }
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) editingProof = uri
        }
    val shown = proof
    val t = Tt.colors
    // A spinner only for work that takes a moment: a quick check on coming back stays still.
    var spinning by remember { mutableStateOf(false) }
    val galleryPick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    LaunchedEffect(busy) {
        if (busy) delay(SPIN_AFTER_MS)
        spinning = busy
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                PageBar(if (post.isLive) "Review" else "Feedback", onClose) {
                    val reward = job?.reward ?: post.reward
                    Pill(if (job?.noBox == true) "Half · +$reward tix" else "+$reward tix", t.tileInk, t.tile)
                }
                PageMessage(message, onMessageDone)
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 18.dp)) {
                if (job == null && failure != null) {
                    Primary("Try again", busy = spinning, onClick = onRetry)
                } else if (job == null) {
                    Spacer(Modifier.height(52.dp))
                } else if (job.settled) {
                    Primary("Done", onClick = onClose)
                } else {
                    val asking = job.step == ReviewStep.USE && !usageAccess
                    // Step four sends once there is a screenshot, and the text the request asks for.
                    val waitingForProof = job.step == ReviewStep.PROVE && (shown == null || !job.textEnough(note))
                    val needsDrive = job.step == ReviewStep.PROVE && !driveConnected
                    // Waiting on a second day: opening the app today would not count, so the button
                    // says when instead. From the next calendar day it opens the app again.
                    val opens = job.opensAt()?.takeIf { job.step == ReviewStep.USE && !job.secondDayNow(now) }
                    val notYet = opens != null && opens.isAfter(now)
                    Primary(
                        when {
                            needsAgreement && !rulesAccepted -> "Read and agree to start"
                            needsAgreement && job.onPhone -> "Start review"
                            needsAgreement -> "Start and open Google Play"
                            asking -> "Allow usage access"
                            needsDrive -> "Connect Drive and send"
                            notYet -> "Opens " + opensWhen(opens, now).removePrefix("today ")
                            job.waitingForDay && job.usedOnTwoDays -> "Continue to review"
                            else -> stepAction(job.step, job)
                        },
                        busy = spinning,
                        enabled = !waitingForProof && !notYet && (!needsAgreement || rulesAccepted),
                    ) {
                        if (needsAgreement && !rulesAccepted) return@Primary
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
                if (job != null) ReviewRecoveryHelp(job, busy, onReport, onGiveUp)
            }
        },
    ) { padding ->
        // A tap on the page, away from the note, puts the keyboard away.
        val dismissKeyboard = rememberDismissKeyboard()
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).dismissKeyboardOnTap(dismissKeyboard)) {
            // The page scrolls when instructions, permissions or proof need more room.
            val room = maxHeight

            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = Gutter),
            ) {
                // Anonymous: whose app it is is not shown to the reviewer.
                ReviewAppRow(post.appName, post.packageName, post.isLive, if (post.mine) post.ownerName else "", offline, icon = post.icon)
                // Keep the route separate from the app's identity.
                Spacer(Modifier.height(ReviewSectionGap))
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
                        ReviewLoading()
                        return@Crossfade
                    }
                    if (shownPage == ReviewPage.DONE) {
                        ReviewDone(job, room, onReply)
                        return@Crossfade
                    }
                    // Keep each task at the top; longer instructions remain scrollable.
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(ReviewSectionGap),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ReviewLineGap)) {
                            ReviewStepGuide(job, usageAccess, shown != null, now)
                            if (job.step == ReviewStep.LEAVE) SelfShotLink { onLeave(ReviewStep.LEAVE) }
                        }
                        if (needsAgreement) {
                            ReviewStartRules(job)
                        }
                        if (sideloaded) {
                            Notice(Status.short, Lucide.TriangleAlert) {
                                Text(
                                    "Install the Google Play version to continue.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        if (!job.settled && !job.rechecking && !job.noBox) {
                            DeveloperAsks(job)
                        }
                        if (needsAgreement) {
                            ReviewRulesAgreement(rulesAccepted) { rulesAccepted = it }
                        }
                        WrittenAsk(job, onNote, onNoBox)
                        if (!job.settled && job.step == ReviewStep.PROVE) {
                            ProofArea(
                                shown = shown,
                                note = note.takeUnless { job.rechecking },
                                needs = job.needsChars,
                                onNote = onNote,
                                onCapture = onCapture,
                                // Showing it again needs a picture taken now, on Play, not one picked.
                                onGallery = galleryPick.takeUnless { job.rechecking },
                                onInspect = { editingProof = it },
                            )
                            if (!driveConnected) {
                                DriveConnectCard(connectingDrive, onConnectDrive)
                            }
                        }
                        // What losing a dispute costs, said on the first step, before any work is done.
                        if (!job.settled && job.step == ReviewStep.INSTALL && !needsAgreement) {
                            DisputeNote()
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
