package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AskFailure

/**
 * Asking the circle to look at one of your apps.
 *
 * Always an app on My apps, picked from Circle or opened from its own page, so it arrives with its
 * name, icon and track. This page is only what to look at, how many reviews and what each pays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    /** The app from My apps this is for; null while My apps is still arriving, or if it is gone. */
    app: AppEntry?,
    /** My apps is still arriving, so a missing app is not gone yet. */
    loading: Boolean = false,
    vm: AskViewModel = viewModel(),
) {
    LaunchedEffect(app?.id) { app?.let(vm::useApp) }
    WhileShown(onShow = vm::startLive, onHide = vm::stopLive)
    val dismissKeyboard = rememberDismissKeyboard()
    Scaffold(
        containerColor = Color.Transparent,
        // The navigation route already applies safeDrawingPadding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageBar("Ask for feedback", onBack)
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .dismissKeyboardOnTap(dismissKeyboard)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter),
        ) {
            Spacer(Modifier.height(8.dp))
            if (app == null) {
                if (loading) {
                    CrewLobbyLoading()
                } else {
                    RobotState(
                        Prop.Notes,
                        "This app isn't on My apps",
                        "It may have been deleted on another phone.",
                        action = "Back",
                        onAction = onBack,
                    )
                }
                return@Column
            }
            AskIntro()
            Spacer(Modifier.height(20.dp))
            Found(vm, onChange = onBack)
            if (vm.found != null) {
                Spacer(Modifier.height(12.dp))
                TrackNote(vm.isLive)
            }

            if (vm.found != null) {
                Spacer(Modifier.height(16.dp))
                AskSection("Your feedback request", "Choose how many reviews to request") {
                    DialogField(
                        value = vm.ask,
                        onValueChange = { vm.ask = it.take(FeedRules.ASK_MAX) },
                        label = "Feedback focus (optional)",
                        placeholder = "Is the monthly total readable at a glance?",
                        supporting = "Leave blank for general feedback, or suggest something specific to try.",
                        singleLine = false,
                        minHeight = 96.dp,
                        maxLength = FeedRules.ASK_MAX,
                    )
                    Spacer(Modifier.height(16.dp))
                    ReviewInvestment(
                        reviews = vm.reviewCount,
                        reward = vm.reward,
                        enabled = !vm.posting,
                        onReviewsChange = vm::changeReviewCount,
                        onRewardChange = vm::changeReward,
                    )
                    Spacer(Modifier.height(20.dp))
                    AskBudget(cost = vm.cost, balance = vm.tix, reviews = vm.reviewCount)
                    Spacer(Modifier.height(16.dp))
                    vm.failure?.let { PostFailure(it) }
                    Primary(
                        "Post feedback request",
                        busy = vm.posting,
                        enabled = vm.canPost,
                        tall = true,
                        onClick = { vm.post(onDone) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
    if (vm.needsSignIn) {
        SignInSheet(
            reason = SignInReason("Sign in to continue", "Your session ran out. Sign in again to post your request."),
            onDismiss = vm::dismissSignIn,
            onSignedIn = vm::signedIn,
            vm = viewModel(key = "ask-signin-${vm.authPromptId}"),
        )
    }
}

@Composable
private fun PostFailure(failure: AskFailure) {
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(failure) {
        withFrameNanos { }
        bringIntoView.bringIntoView()
    }
    Column(Modifier.bringIntoViewRequester(bringIntoView)) {
        AskMessage(failure)
        Spacer(Modifier.height(16.dp))
    }
}

/** App details sit directly on the page. Tap the row to choose a different listing. */
@Composable
private fun Found(
    vm: AskViewModel,
    onChange: () -> Unit,
) {
    val app = vm.found ?: return
    FoundApp(app.`package`, app.name, app.icon, app.developer, unchecked = vm.unchecked, onChange = onChange)
}

/**
 * What testers will leave, and where, said before anything is posted. For a live app that includes
 * the part an owner should decide knowingly: Google does not allow rewarding reviews.
 */
@Composable
private fun TrackNote(live: Boolean) {
    Notice(if (live) Status.short else Tt.colors.accentInk, if (live) Lucide.TriangleAlert else Lucide.Info) {
        Text(
            if (live) {
                "Testers rate and review your app on Google Play in their own words, after using it on two days. " +
                    "Google does not allow rewarding reviews and can remove ones it doubts, so the reviews are " +
                    "paced like real users'. Posting this is your decision to make."
            } else {
                "Testers send you private feedback on Google Play after trying your app for two minutes. It reaches " +
                    "you in Play Console, and a screenshot of it comes here."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
