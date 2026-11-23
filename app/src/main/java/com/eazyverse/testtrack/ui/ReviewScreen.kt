package com.eazyverse.testtrack.ui

import android.net.Uri
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
import com.eazyverse.testtrack.viewmodel.dropStrayButton
import com.eazyverse.testtrack.viewmodel.openUsageSettings
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
    // Play often finishes after the return, so the app landing is a measurement too.
    WatchInstalls(post.packageName) { vm.refresh(context, waits = true) }

    var givingUp by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    val capture = rememberReviewCapture(vm, post.packageName, post.isLive)
    val dismissKeyboard = rememberDismissKeyboard()

    val activity = context.findActivity()
    val membership = rememberMembership()
    val requireMembership: (() -> Unit) -> Unit = { action ->
        if (post.isLive) action() else membership.require(action)
    }

    ReviewBody(
        post = post,
        job = vm.pageJob,
        proof = vm.proof,
        replacing = vm.replacing,
        sending = vm.sending,
        // The screenshot already got stays until a new one arrives, so a capture abandoned half
        // way does not cost the one there was.
        onCapture = {
            dismissKeyboard()
            capture()
        },
        onPicked = { vm.keepPicked(context, it) },
        onRemoveProof = vm::removeProof,
        note = vm.note,
        onNote = vm::editNote,
        sideloaded = vm.sideloaded,
        busy = vm.busy,
        usageAccess = vm.usageAccess,
        onAllowUsage = { if (vm.requirePlayInstall(context)) vm.openUsageSettings(context) },
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
        onNoBox = {
            if (vm.markNoBox()) {
                dismissKeyboard()
                capture()
            }
        },
        onReply = { text, picture -> vm.reply(activity, text, picture) },
        // What is sent is the screenshot kept at that moment (see ReviewViewModel.submit), not the address shown.
        onSend = { _, note ->
            requireMembership {
                if (vm.requirePlayInstall(context)) vm.submit(activity, note)
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
    offline: Boolean = false,
    failure: String? = null,
    onRetry: () -> Unit = {},
    onClose: () -> Unit,
    onLeave: (ReviewStep) -> Unit,
    onSend: (proof: Uri, note: String) -> Unit,
    proof: Uri? = null,
    /** A new version of [proof] is still being kept: Send waits. */
    replacing: Boolean = false,
    /** The screenshot is going to TestTrack with the review. */
    sending: Boolean = false,
    onCapture: () -> Unit = {},
    /** Keeps a picture from the editor; returns once it is kept and throws when it can't be. */
    onPicked: suspend (Uri) -> Unit = {},
    onRemoveProof: () -> Unit = {},
    note: String = "",
    onNote: (String) -> Unit = {},
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
    var answering by remember { mutableStateOf(false) }
    val galleryPick = rememberImagePicker { uri -> if (uri != null) editingProof = uri }
    val shown = proof
    // A spinner only for work that takes a moment: a quick check on coming back stays still.
    var spinning by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        if (busy) delay(SPIN_AFTER_MS)
        spinning = busy
    }

    // The Send and Waiting pages sit their foot 8dp under the page, as the thread pages do, and
    // their thread fades out above it.
    val threadFoot = job != null && (job.settled || job.step == ReviewStep.PROVE)
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageBar(if (post.isLive) "Review" else "Feedback", onClose) {
                val reward = job?.reward ?: post.reward
                StatusChip(if (job?.noBox == true) "Half pay, +$reward tix" else "+$reward tix", Tone.TILE, size = ChipSize.LARGE)
            }
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .snackFloor()
                    .padding(start = Gutter, end = Gutter, top = if (threadFoot) 8.dp else 12.dp, bottom = 18.dp),
            ) {
                if (job == null && failure != null) {
                    Primary("Try again", busy = spinning, onClick = onRetry)
                } else if (job == null) {
                    Spacer(Modifier.height(52.dp))
                } else if (job.settled) {
                    WaitFoot(job, busy = spinning, onDone = onClose, onAnswer = { answering = true })
                } else {
                    val asking = job.step == ReviewStep.USE && !usageAccess
                    // Step four sends once there is a screenshot, and the text the request asks for.
                    val waitingForProof = job.step == ReviewStep.PROVE && (shown == null || replacing || !job.textEnough(note))
                    // Waiting on a second day: opening the app today would not count, so the button
                    // says when instead. From the next calendar day it opens the app again.
                    val opens = job.opensAt()?.takeIf { job.step == ReviewStep.USE && !job.secondDayNow(now) }
                    val notYet = opens != null && opens.isAfter(now)
                    Primary(
                        when {
                            sending && job.step == ReviewStep.PROVE -> "Uploading picture"
                            needsAgreement && !rulesAccepted -> "Read and agree to start"
                            needsAgreement && job.onPhone -> "Start review"
                            needsAgreement -> "Start and open Google Play"
                            asking -> "Allow usage access"
                            // Timing 2 waits first for the second visit, then for the review.
                            notYet ->
                                (if (job.paced && !job.secondVisitDone) "Back " else "Opens ") +
                                    opensWhen(opens, now).removePrefix("today ")
                            job.waitingForDay && job.usedOnTwoDays -> "Continue to review"
                            else -> stepAction(job.step, job)
                        },
                        busy = spinning || (sending && job.step == ReviewStep.PROVE),
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
                // Keep the route separate from the app's identity. A thread starts nearer the app:
                // its day label brings its own room, and the Send page's route sits 18dp under it.
                val robot = job?.review?.state == "abandoned" || job?.review?.state == "expired"
                Spacer(
                    Modifier.height(
                        when {
                            page == ReviewPage.DONE && !robot -> 10.dp
                            page == ReviewPage.STEP && job?.step == ReviewStep.PROVE -> 18.dp
                            else -> ReviewSectionGap
                        },
                    ),
                )
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
                        ReviewDone(job, room)
                        return@Crossfade
                    }
                    if (job.step == ReviewStep.PROVE) {
                        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                            ReviewStepGuide(job, usageAccess, now)
                            if (sideloaded) {
                                Spacer(Modifier.height(ReviewSectionGap))
                                SideloadNotice()
                            }
                            Spacer(Modifier.height(10.dp))
                            SendProof(
                                job = job,
                                shown = shown,
                                note = note,
                                onNote = onNote,
                                onCapture = onCapture,
                                // Showing it again needs a picture taken now, on Play, not one picked.
                                onGallery = galleryPick.takeUnless { job.rechecking },
                                onRemove = onRemoveProof,
                                onEdit = { editingProof = it },
                                working =
                                    when {
                                        sending -> "Uploading picture"
                                        replacing -> "Saving picture"
                                        else -> null
                                    },
                            )
                        }
                        return@Crossfade
                    }
                    // Keep each task at the top; longer instructions remain scrollable.
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(ReviewSectionGap),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ReviewLineGap)) {
                            ReviewStepGuide(job, usageAccess, now)
                            if (job.step == ReviewStep.LEAVE) SelfShotLink { onLeave(ReviewStep.LEAVE) }
                        }
                        if (needsAgreement) {
                            ReviewStartRules(job)
                        }
                        if (sideloaded) SideloadNotice()
                        // On the send step the developer's ask and ready review are their messages, under the box.
                        if (!job.rechecking && !job.noBox) DeveloperAsks(job)
                        if (needsAgreement) {
                            ReviewRulesAgreement(rulesAccepted) { rulesAccepted = it }
                        }
                        WrittenAsk(job, onNoBox)
                        // What losing a dispute costs, said on the first step, before any work is done.
                        if (!job.settled && job.step == ReviewStep.INSTALL && !needsAgreement) {
                            DisputeNote()
                        }
                    }
                }
            }
            if (threadFoot) FootFade(Modifier.align(Alignment.BottomCenter))
        }
    }
    editingProof?.let { uri ->
        ProofPictureEditor(uri, onKeep = onPicked, onClose = { editingProof = null })
    }
    if (answering && job?.canReply == true) {
        AnswerSheet(onSend = onReply, onDismiss = { answering = false })
    }
}

/** Under the route, for an app that did not come from Google Play. */
@Composable
private fun SideloadNotice() {
    Notice(Status.short, Lucide.TriangleAlert) {
        Text(
            "Install the Google Play version to continue.",
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.text,
        )
    }
}

private enum class ReviewPage { LOADING, FAILED, STEP, DONE }

private const val SPIN_AFTER_MS = 250L
