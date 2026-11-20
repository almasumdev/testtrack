package com.eazyverse.testtrack.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleHelp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.eazyverse.testtrack.ui.theme.Tt

/** The group-join sheet's frame and transitions, with an explicit fallback before changing flow. */
@Composable
internal fun NoPlayOptionSheet(
    job: ReviewJob,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    val t = Tt.colors
    var fallback by rememberSaveable(job.post.id, job.review.id) { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(fallback) { scroll.scrollTo(0) }
    PickerSheet(
        title = if (fallback) "Send feedback here" else "No review option on Play?",
        onDismiss = onDismiss,
        leading = { IconTile(if (fallback) Lucide.MessageSquare else Lucide.CircleHelp) },
        containerColor = t.sheet,
        topLine = t.sheetLine,
        handleGap = 16.dp,
        headerGap = 12.dp,
        titleLiveRegion = true,
        skipHalf = true,
        fitMax = 0.9f,
    ) { close ->
        Column(Modifier.weight(1f, fill = false).verticalScroll(scroll).padding(bottom = 20.dp)) {
            AnimatedContent(
                targetState = fallback,
                transitionSpec = {
                    (
                        fadeIn(tween(durationMillis = 240, delayMillis = 70)) +
                            slideInVertically(tween(durationMillis = 300, delayMillis = 110)) { it / 5 }
                    ).togetherWith(fadeOut(tween(durationMillis = 150)))
                        .using(SizeTransform(clip = false))
                },
                label = "no-play-review",
            ) { showFallback ->
                NoPlayOptionBody(
                    job = job,
                    fallback = showFallback,
                    closing = closing,
                    onFallback = { fallback = true },
                    onFixes = { fallback = false },
                    onClose = { close {} },
                    onContinue = {
                        if (!closing) {
                            closing = true
                            close(onContinue)
                        }
                    },
                )
            }
        }
    }
}

/** Supplied state keeps both faces independent of review services and account state. */
@Composable
internal fun NoPlayOptionBody(
    job: ReviewJob,
    fallback: Boolean,
    closing: Boolean,
    onFallback: () -> Unit,
    onFixes: () -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit,
) {
    val reward = job.post.reward / 2
    val needs = maxOf(FeedRules.NO_BOX_MIN_CHARS, job.needsChars)
    Column(Modifier.fillMaxWidth()) {
        if (fallback) {
            Why("Your feedback goes to the developer here. No review is posted on Play.")
            Spacer(Modifier.height(16.dp))
            ReviewBullet(body = "Pays $reward tix (half reward), if accepted.", highlights = listOf("$reward tix", "half reward"))
            Spacer(Modifier.height(12.dp))
            NoPlayFix(1, "Screenshot", "Play opens with a camera button. Capture the page showing no review option.")
            NoPlayFix(2, "Written feedback", "Write at least $needs characters for the developer.")
            Actions { Primary("Capture on Play", busy = closing, busyKeepsFill = true, onClick = onContinue) }
            InlineAction("Back to fixes", Modifier.padding(top = 6.dp), enabled = !closing, fullWidth = true, onClick = onFixes)
        } else {
            Why("Try these fixes before sending feedback here.")
            Spacer(Modifier.height(12.dp))
            NoPlayFix(1, "Google account", "Use the account you installed this app with.")
            NoPlayFix(2, "Tester status", "If Play lists you as a tester, leave the test and wait a few minutes.")
            NoPlayFix(3, "Existing review", "\"Edit your review\" means you reviewed it before. It does not count as a new review.")
            NoPlayFix(4, "Play Store update", "Update the Play Store, then reopen the app's page.")
            Actions { Primary("Still no review option", onClick = onFallback) }
            InlineAction("Keep trying on Play", Modifier.padding(top = 6.dp), fullWidth = true, onClick = onClose)
        }
    }
}

/** The join flow's numbered rows, with wrapped supporting text under each title. */
@Composable
private fun NoPlayFix(
    number: Int,
    title: String,
    detail: String,
) {
    StepRow(number, title, now = false)
    Text(
        detail,
        Modifier.padding(start = 34.dp, bottom = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = Tt.colors.text2,
    )
}
