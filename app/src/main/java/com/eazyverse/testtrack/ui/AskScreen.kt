package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Asking the circle to look at your app.
 *
 * A page rather than a dialog, because finding the app is its own step and it can fail in ways
 * worth explaining. A dialog that has to say "Play will not show us that app, check the testers
 * group is on the track" has outgrown being one.
 *
 * Two questions before anything else: where the app is, and its link. The name, icon and
 * publisher are read off Play and shown back to confirm. That used to be typed by hand, which is
 * two chances to make a typo nobody would catch until fifteen people failed to install the wrong
 * thing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    vm: AskViewModel = viewModel(),
) {
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
            AskIntro()
            Spacer(Modifier.height(20.dp))
            AskAvailability(vm.isLive, vm::choose)
            Spacer(Modifier.height(16.dp))
            if (vm.found == null) Finding(vm) else Found(vm)

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
            reason = SignInReason("Sign in to continue", "Sign in to find your app and request feedback."),
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

/** Before an app is found: one field and one button, and nothing else competing with them. */
@Composable
private fun Finding(vm: AskViewModel) {
    DialogField(
        value = vm.query,
        onValueChange = vm::type,
        label = "Your Google Play link",
        placeholder = "com.example.myapp",
        isError = vm.failure?.invalidLink == true,
        supporting =
            if (vm.isLive) {
                "Use the Google Play link or your app's package name."
            } else {
                "Add the testers group to your closed track before looking up your app."
            },
        singleLine = true,
        keyboardType = KeyboardType.Uri,
        imeAction = ImeAction.Search,
        onImeAction = vm::look,
    )
    vm.failure?.let {
        Spacer(Modifier.height(14.dp))
        AskMessage(it)
    }
    if (vm.byHand) {
        Spacer(Modifier.height(18.dp))
        ByHandForm(vm)
        return
    }
    Spacer(Modifier.height(16.dp))
    // Our check is down, not their app: the way on that does not need it.
    if (vm.failure?.byHand == true) {
        Secondary("Enter details yourself", Modifier.fillMaxWidth(), onClick = vm::enterByHand)
        Spacer(Modifier.height(10.dp))
    }
    Primary(
        if (vm.looking) {
            "Finding your app..."
        } else if (vm.failure != null) {
            "Try again"
        } else {
            "Find my app"
        },
        busy = vm.looking,
        enabled = vm.canLook,
        tall = true,
        onClick = vm::look,
    )
}

/** App details sit directly on the page. Tap the row to choose a different listing. */
@Composable
private fun Found(vm: AskViewModel) {
    val app = vm.found ?: return
    FoundApp(app.`package`, app.name, app.icon, app.developer, unchecked = vm.unchecked, onChange = vm::startOver)
}
