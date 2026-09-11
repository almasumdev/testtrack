package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.net.ApiPost

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
 */
@Composable
fun ReviewScreen(
    post: ApiPost,
    onClose: () -> Unit,
    vm: ReviewViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbars = remember { SnackbarHostState() }
    var proving by remember { mutableStateOf(false) }

    LaunchedEffect(post.id) { vm.start(context, post) }

    // Every return to this screen is a measurement. Coming back from the app under review is the
    // whole interaction, so resume is where the stepper moves rather than a button.
    val owner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = 6.dp, end = Gutter, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(onClick = onClose)
                        .padding(10.dp),
                ) {
                    Icon(Lucide.ArrowLeft, "Back", Modifier.size(20.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    post.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Pill(
                    "+${post.reward}",
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    MaterialTheme.colorScheme.primaryContainer,
                )
            }
        },
    ) { padding ->
        val job = vm.job
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (job == null) {
                Blank("Getting this ready.")
                return@Column
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = Gutter),
            ) {
                item {
                    Box(Modifier.padding(horizontal = Gutter)) {
                        CrewCard(padding = PaddingValues(16.dp)) {
                            Text(
                                if (job.settled) "Sent" else job.step.label,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (job.settled) reviewOutcome(job.review.state) else post.ask,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!job.settled) {
                                Spacer(Modifier.height(16.dp))
                                StepTrack(job.step)
                            }
                        }
                    }
                }

                if (vm.sideloaded) {
                    item {
                        Box(Modifier.padding(horizontal = Gutter, vertical = 12.dp)) {
                            Notice(Status.short, Lucide.TriangleAlert) {
                                Text(
                                    "That copy did not come from Play, so it cannot count. " +
                                        "Install it through the button below instead.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                item { CrewHeading("The steps") }
                ReviewStep.entries.filter { it != ReviewStep.WAIT }.forEachIndexed { index, step ->
                    item {
                        if (index > 0) RowDivider()
                        StepRow(
                            step = step,
                            job = job,
                            onAct = {
                                when (step) {
                                    ReviewStep.INSTALL -> openPlay(context, post.packageName)
                                    ReviewStep.USE -> openApp(context, post.packageName)
                                    ReviewStep.LEAVE ->
                                        if (post.isLive) {
                                            openPlay(context, post.packageName)
                                        } else {
                                            proving = true
                                        }
                                    ReviewStep.PROVE -> proving = true
                                    ReviewStep.WAIT -> Unit
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (proving) {
        ReviewProofDialog(
            live = post.isLive,
            onSend = { proof, written ->
                proving = false
                vm.submit(proof, written)
            },
            onDismiss = { proving = false },
        )
    }
}

/**
 * How far along, as four marks.
 *
 * The same shape the crew screens use for a fortnight, at a different scale. One control learned
 * once.
 */
@Composable
private fun StepTrack(step: ReviewStep) {
    val steps = ReviewStep.entries.filter { it != ReviewStep.WAIT }
    val reached = steps.indexOf(step).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        steps.forEachIndexed { index, _ ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            index < reached -> Status.posted
                            index == reached -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            )
        }
    }
}

/**
 * One step, and the only control on it.
 *
 * A step that is behind you shows a tick and nothing to press. A step that is ahead shows nothing
 * either: offering a button for something that cannot be done yet is how a stepper stops meaning
 * anything.
 */
@Composable
private fun StepRow(
    step: ReviewStep,
    job: ReviewJob,
    onAct: () -> Unit,
) {
    val steps = ReviewStep.entries.filter { it != ReviewStep.WAIT }
    val here = steps.indexOf(job.step).coerceAtLeast(steps.size - 1)
    val mine = steps.indexOf(step)
    val done = job.settled || mine < here
    val current = !job.settled && mine == here

    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (done) Status.postedSoft else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(Lucide.Check, null, Modifier.size(15.dp), tint = Status.posted)
                } else {
                    Text(
                        "${mine + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (current) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    step.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                    color =
                        if (done || current) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stepDetail(step, job),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (current) {
            if (step == ReviewStep.USE) {
                Spacer(Modifier.height(12.dp))
                UseProgress(job)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip(
                    stepAction(step, job),
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    MaterialTheme.colorScheme.primaryContainer,
                    onAct,
                )
            }
        }
    }
}

/** The thirty seconds, drawn, because a number counting up is the only thing that shows progress. */
@Composable
private fun UseProgress(job: ReviewJob) {
    val fill by animateFloatAsState(job.usedFraction, tween(500), label = "used")
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fill)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (job.secondsLeft == 0) {
                "Long enough. Leave your review next."
            } else {
                "${job.secondsLeft} more seconds in the app"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun stepDetail(
    step: ReviewStep,
    job: ReviewJob,
): String =
    when (step) {
        ReviewStep.INSTALL -> "From Play, so it counts. A copy from anywhere else does not."
        ReviewStep.USE -> "Stay as long as you like. We only check you were there."
        ReviewStep.LEAVE ->
            if (job.post.isLive) {
                "A public review on Play, which is the thing that actually helps."
            } else {
                "This one is still in closed testing, so write what you found."
            }
        ReviewStep.PROVE -> "A screenshot of what you left. Nobody else can see a Play review."
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
