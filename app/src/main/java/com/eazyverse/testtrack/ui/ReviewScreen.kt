package com.eazyverse.testtrack.ui

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircleQuestion
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular

/**
 * The guided review: what somebody actually does to earn the tix.
 *
 * A stepper rather than a text box, because the job is not writing a paragraph. It is installing
 * the app from Play, spending real time in it, leaving a review where it counts, and proving it.
 * Each of those is a different claim the phone can check, so each gets its own step. See
 * docs/feedback.md.
 *
 * Nothing here decides whether a step is done. The phone reports what it sees and the server says
 * what that adds up to, which is why the screen simply redraws whatever comes back.
 *
 * This half is the wiring: the view model, the lifecycle, the intents. Everything drawn lives in
 * [ReviewBody], which is handed plain values and so can be opened by a test at any step, without
 * an account, a network or Play. That split is not tidiness. The stepper shipped once with every
 * step but the last already ticked, for want of a test that could open it.
 */
@Composable
fun ReviewScreen(
    post: ApiPost,
    onClose: () -> Unit,
    vm: ReviewViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }

    LaunchedEffect(post.id) { vm.start(context, post) }

    // Every return to this screen is a measurement. Coming back from the app under review is the
    // whole interaction, so resume is where the stepper moves rather than a button.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watcher =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) vm.refresh(context)
            }
        owner.lifecycle.addObserver(watcher)
        onDispose { owner.lifecycle.removeObserver(watcher) }
    }

    LaunchedEffect(vm.message) {
        val message = vm.message ?: return@LaunchedEffect
        snackbars.showSnackbar(message)
        vm.clearMessage()
    }

    ReviewBody(
        post = post,
        job = vm.job,
        sideloaded = vm.sideloaded,
        busy = vm.busy,
        snackbars = snackbars,
        onClose = onClose,
        onLeave = { step ->
            when (step) {
                ReviewStep.INSTALL -> openPlay(context, post.packageName)
                ReviewStep.USE -> openApp(context, post.packageName)
                // Going to Play is also the only signal that the review may now exist, so the
                // proof step opens on the way out rather than waiting for a state that cannot come.
                ReviewStep.LEAVE -> {
                    openPlay(context, post.packageName)
                    vm.markLeft()
                }
                else -> Unit
            }
        },
        onSend = { proof, written -> vm.submit(context, proof, written) },
    )
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
    snackbars: SnackbarHostState,
    onClose: () -> Unit,
    onLeave: (ReviewStep) -> Unit,
    onSend: (proof: Uri?, written: String) -> Unit,
) {
    var proving by remember { mutableStateOf(false) }
    val t = Tt.colors

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text("Review", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Lucide.ChevronLeft, "Back") } },
                actions = {
                    Box(Modifier.padding(end = Gutter)) {
                        Pill("+${post.reward} tix", t.tileInk, t.tile)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            if (job != null && !job.settled) {
                Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp)) {
                    Primary(stepAction(job.step, job), tall = true) {
                        // Writing happens here. Every other step hands the reviewer to Play or to
                        // the app itself, which is somebody else's screen.
                        val step = job.step
                        val writing = step == ReviewStep.PROVE || (step == ReviewStep.LEAVE && !post.isLive)
                        if (writing) proving = true else onLeave(step)
                    }
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // Everything scales off the height left, so the whole step fits on a small phone.
            val room = maxHeight
            val dial = (room * 0.30f).coerceIn(108.dp, 168.dp)
            val gap = (room * 0.03f).coerceIn(10.dp, 22.dp)

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            ) {
                ReviewAppRow(post)
                if (job == null) {
                    Blank("Getting this ready.")
                    return@Column
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
                    Text(
                        if (job.settled) "Sent" else stepTitle(job),
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 23.sp, lineHeight = 27.sp),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (job.settled) reviewOutcome(job.review.state) else stepLine(job),
                        Modifier.widthIn(max = 300.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        color = t.text2,
                        textAlign = TextAlign.Center,
                    )
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
                    val asked = post.ask.trim()
                    if (!job.settled && asked.isNotEmpty() && asked != GENERAL_ASK) {
                        Spacer(Modifier.height(gap))
                        DeveloperAsks(asked)
                    }
                }
            }
        }
    }

    if (proving) {
        ReviewProofDialog(
            live = post.isLive,
            busy = busy,
            onSend = { proof, written ->
                proving = false
                onSend(proof, written)
            },
            onDismiss = { proving = false },
        )
    }
}

/** What a post carries when the developer asked nothing in particular; not worth showing. */
private const val GENERAL_ASK = "General feedback on the app experience."

private val ReviewSteps = ReviewStep.entries.filter { it != ReviewStep.WAIT }

/** The app under review: its mark, its name, and where it is. */
@Composable
private fun ReviewAppRow(post: ApiPost) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AppTile(post.appName, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                post.appName,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                (if (post.isLive) "Live on Google Play" else "Closed testing") +
                    if (post.ownerName.isNotBlank()) " · by ${post.ownerName}" else "",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = Tt.colors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Four rounded segments round a ring, one per step.
 *
 * Done steps fill in the accent, the current one is tinted, and while the app is being used its
 * segment fills as the thirty seconds pass, so the dial is the clock as well as the progress.
 */
@Composable
private fun StepDial(
    job: ReviewJob,
    size: Dp,
) {
    val t = Tt.colors
    val here = if (job.settled) ReviewSteps.size else ReviewSteps.indexOf(job.step).coerceAtLeast(0)
    val used by animateFloatAsState(job.usedFraction, tween(500), label = "used")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.toPx() * 0.072f
            val inset = stroke / 2f
            val ring = Size(this.size.width - stroke, this.size.height - stroke)
            val gapDeg = 14f
            val sweep = 90f - gapDeg
            for (i in 0 until 4) {
                val start = -90f + i * 90f + gapDeg / 2f
                val segment: (Color, Float) -> Unit = { color, fraction ->
                    drawArc(
                        color = color,
                        startAngle = start,
                        sweepAngle = sweep * fraction,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = ring,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                segment(t.glass2, 1f)
                when {
                    i < here -> segment(t.accent, 1f)
                    i == here && ReviewSteps[i] == ReviewStep.USE -> if (used > 0f) segment(t.accent, used)
                    i == here -> segment(t.accentInk.copy(alpha = 0.35f), 1f)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                job.review.state == "approved" -> {
                    Text("+${job.post.reward}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = t.ok)
                    Text("tix added", style = MaterialTheme.typography.labelMedium, color = t.text3)
                }
                job.settled -> {
                    // A tick rather than the word: the title under the dial already says "Sent".
                    Icon(Lucide.Check, null, Modifier.size(size * 0.2f), tint = t.accentInk)
                    Text("with the developer", style = MaterialTheme.typography.labelMedium, color = t.text3)
                }
                job.step == ReviewStep.USE -> {
                    val needed = (job.review.neededMs / 1000).toInt()
                    Text(
                        "${(needed - job.secondsLeft).coerceAtLeast(0)}",
                        style =
                            MaterialTheme.typography.headlineMedium
                                .tabular()
                                .copy(fontSize = (size.value * 0.24f).sp),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("of $needed seconds", style = MaterialTheme.typography.labelMedium, color = t.text3)
                }
                else -> {
                    Text("Step ${here + 1}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("of ${ReviewSteps.size}", style = MaterialTheme.typography.labelMedium, color = t.text3)
                }
            }
        }
    }
}

/** The four steps by name under the dial, ticked as they are done. */
@Composable
private fun StepLabels(job: ReviewJob) {
    val t = Tt.colors
    val here = if (job.settled) ReviewSteps.size else ReviewSteps.indexOf(job.step).coerceAtLeast(0)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ReviewSteps.forEachIndexed { i, step ->
            Text(
                (if (i < here) "✓ " else "") + stepShort(step, job),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                fontWeight = FontWeight.SemiBold,
                color =
                    if (i < here) {
                        t.text2
                    } else if (i == here) {
                        t.accentInk
                    } else {
                        t.text4
                    },
            )
        }
    }
}

/** The developer's question: an icon in line with the label, the question full width under it. */
@Composable
private fun DeveloperAsks(asked: String) {
    val t = Tt.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.glass)
            .border(1.dp, t.glassBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.MessageCircleQuestion, null, Modifier.size(15.dp), tint = t.accentInk)
            Spacer(Modifier.width(6.dp))
            Text(
                "The developer asks",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                fontWeight = FontWeight.SemiBold,
                color = t.text3,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(asked, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
    }
}

private fun stepShort(
    step: ReviewStep,
    job: ReviewJob,
): String =
    when (step) {
        ReviewStep.INSTALL -> "Install"
        ReviewStep.USE -> "Use"
        ReviewStep.LEAVE -> if (job.post.isLive) "Review" else "Write"
        ReviewStep.PROVE -> "Prove"
        ReviewStep.WAIT -> ""
    }

private fun stepTitle(job: ReviewJob): String =
    when (job.step) {
        ReviewStep.INSTALL -> "Install ${job.post.appName}"
        ReviewStep.USE -> "Try the app"
        ReviewStep.LEAVE -> if (job.post.isLive) "Review it on Play" else "Write your feedback"
        ReviewStep.PROVE -> "Send your proof"
        ReviewStep.WAIT -> "Sent"
    }

private fun stepLine(job: ReviewJob): String =
    when (job.step) {
        ReviewStep.INSTALL -> "Get it from Google Play so it counts."
        ReviewStep.USE ->
            if (job.secondsLeft == 0) "Long enough. Leave your review next." else "${job.secondsLeft} more seconds in the app"
        ReviewStep.LEAVE ->
            if (job.post.isLive) "Rate it on Play, then come back with a screenshot." else "Say what worked and what did not."
        ReviewStep.PROVE -> "A screenshot of the review you left on Play."
        ReviewStep.WAIT -> ""
    }

private fun stepAction(
    step: ReviewStep,
    job: ReviewJob,
): String =
    when (step) {
        ReviewStep.INSTALL -> "Get it on Play"
        ReviewStep.USE -> "Open the app"
        ReviewStep.LEAVE -> if (job.post.isLive) "Open Play to review" else "Write it"
        ReviewStep.PROVE -> "Send the proof"
        ReviewStep.WAIT -> ""
    }
