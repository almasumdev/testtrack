package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * Asking the circle to look at one of your apps.
 *
 * Always an app on My apps, picked from Circle or opened from its own page, so it arrives with its
 * name, icon and track. This page is how many reviews, what each has and pays, what testers should
 * look at, and the price; the one button scrolls with the page. See docs/design/ask/ask-final.html.
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
    /** My apps could not be read, so a missing app may not be gone at all. */
    offline: Boolean = false,
    onRetry: () -> Unit = {},
    /** Short of tix: back to the shell on the Circle tab, where reviews earn them. */
    onOpenCircle: () -> Unit = {},
    vm: AskViewModel = viewModel(),
) {
    LaunchedEffect(app?.id) { app?.let(vm::useApp) }
    WhileShown(onShow = vm::startLive, onHide = vm::stopLive)
    Scaffold(
        containerColor = Color.Transparent,
        // The navigation route already applies safeDrawingPadding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                PageBar("Ask for feedback", onBack)
                PageMessage(vm.pageMessage, onDone = vm::clearMessage, error = true)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // The skeleton and the not-found state stand outside the scroll, so they know the
            // page's height and fill it; a filling state inside a scroll has no height and crashes.
            when {
                app == null && !loading ->
                    Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Gutter)) {
                        AppNotThere(offline, onRetry, onBack)
                    }
                app == null || vm.appId != app.id ->
                    AskPageSkeleton(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = Gutter)
                            .padding(top = 8.dp),
                    )
                else -> AskBody(app, vm, onDone, onOpenCircle)
            }
        }
    }
    if (vm.needsSignIn) {
        SignInSheet(
            reason = SignInReason("Sign in to continue", "Your session ran out. Sign in again to send your request."),
            onDismiss = vm::dismissSignIn,
            onSignedIn = vm::signedIn,
            vm = viewModel(key = "ask-signin-${vm.authPromptId}"),
        )
    }
}

@Composable
private fun ColumnScope.AskBody(
    app: AppEntry,
    vm: AskViewModel,
    onDone: () -> Unit,
    onOpenCircle: () -> Unit,
) {
    val dismissKeyboard = rememberDismissKeyboard()
    val scope = rememberCoroutineScope()
    val priceSpot = remember { BringIntoViewRequester() }
    // Goes up each time the hint asks for the ready reviews, which scrolls to the first problem.
    var readyAsk by remember { mutableIntStateOf(0) }
    val enabled = !vm.posting
    val detailed = vm.feedback == FeedRules.Feedback.DETAILED
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .dismissKeyboardOnTap(dismissKeyboard)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gutter),
    ) {
        Spacer(Modifier.height(8.dp))
        AskAppHead(app)
        Spacer(Modifier.height(16.dp))
        AskTrackLines(live = vm.isLive, verified = !vm.unchecked)

        Spacer(Modifier.height(SectionGap))
        AskCount(vm.reviewCount, enabled, vm::changeReviewCount)

        Spacer(Modifier.height(SectionGap))
        SectionHeading("Each review has")
        AskLevelTiles(vm.feedback, enabled, vm::chooseFeedback)

        Spacer(Modifier.height(SectionGap))
        AskPay(vm.reward, vm.feedback.least, enabled, vm::changeReward)

        Spacer(Modifier.height(SectionGap))
        SectionHeading("For testers", tag = "Optional")
        Spacer(Modifier.height(6.dp))
        OutlinedField(
            value = vm.ask,
            onValueChange = { vm.ask = it.take(FeedRules.ASK_MAX) },
            label = "What to look at",
            placeholder = "Example: is the monthly total easy to read?",
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            maxLength = FeedRules.ASK_MAX,
            enabled = enabled,
        )
        if (detailed) {
            AskReadyTexts(
                texts = vm.readyTexts,
                errors = vm.readyErrors,
                max = vm.reviewCount,
                enabled = enabled,
                spotAsk = readyAsk,
                onAdd = vm::addReady,
                onEdit = vm::editReady,
                onRemove = vm::removeReady,
            )
        }

        Spacer(Modifier.height(SectionGap))
        AskPrice(
            reviews = vm.reviewCount,
            cost = vm.cost,
            tix = vm.tix,
            onOpenCircle = onOpenCircle,
            modifier = Modifier.bringIntoViewRequester(priceSpot),
        )

        // The hint shows only while something stops the request, and never while it is going up.
        val block = vm.blockReason.takeIf { !vm.posting }
        if (block != null) {
            Spacer(Modifier.height(20.dp))
            AskHint(block, vm.shortBy) {
                when (block) {
                    AskBlock.SHORT -> scope.launch { priceSpot.bringIntoView() }
                    AskBlock.READY -> readyAsk++
                }
            }
            Spacer(Modifier.height(10.dp))
        } else {
            Spacer(Modifier.height(SectionGap))
        }
        Primary(
            "Ask for feedback",
            busy = vm.posting,
            enabled = vm.canPost,
            onClick = { vm.post(onDone) },
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** The gap above each part's heading. */
private val SectionGap = 24.dp

/**
 * The page before its app has arrived, in the page's own shapes from top to bottom: the head, the
 * lines, the count and its chips, the three levels, the slider, the note box, the price and the
 * button.
 */
@Composable
private fun AskPageSkeleton(modifier: Modifier = Modifier) {
    Box(modifier.clipToBounds().semantics { contentDescription = "Loading the request page" }) {
        Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)) {
            AppHeadSkeleton(name = 170.dp, sub = 90.dp)
            Spacer(Modifier.height(18.dp))
            Skeleton(height = 12.dp)
            Spacer(Modifier.height(7.dp))
            Box(Modifier.fillMaxWidth(0.72f)) { Skeleton(height = 12.dp) }
            SkeletonHeading(80.dp, end = 112.dp, endHeight = 36.dp)
            Skeleton(height = 36.dp, corner = 18.dp)
            SkeletonHeading(120.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { Box(Modifier.weight(1f)) { Skeleton(height = 78.dp, corner = TileRadius) } }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(0.6f)) { Skeleton(height = 12.dp) }
            SkeletonHeading(130.dp, end = 60.dp)
            Spacer(Modifier.height(8.dp))
            Skeleton(height = 4.dp, corner = 2.dp)
            Spacer(Modifier.height(30.dp))
            Skeleton(height = 86.dp, corner = 10.dp)
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Skeleton(width = 40.dp, height = 40.dp, corner = 12.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Skeleton(width = 150.dp, height = 15.dp)
                    Spacer(Modifier.height(6.dp))
                    Skeleton(width = 100.dp, height = 11.dp)
                }
            }
            Spacer(Modifier.height(24.dp))
            Skeleton(height = 52.dp, corner = 26.dp)
        }
    }
}
