package com.eazyverse.testtrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazyverse.testtrack.domain.Waiting
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiPost

/**
 * What each list adds above its first card.
 *
 * Half a gutter rather than a whole one, and the missing half is not missing: the header row keeps
 * its own space below the pill, and the two together are the single gutter that shows on screen.
 * Both tabs use it, so switching between them does not move the first card.
 */
private val ListTop = 8.dp

/**
 * Circle: the community front page.
 *
 * Two halves under one heading rather than two places in the bar, because they answer the same
 * question at different speeds: Feedback is what somebody wants from you now, Activity is what the
 * group has done lately. Splitting them across the bottom nav would have made a five tab app a six
 * tab one, and made the quieter of the two into somewhere nobody goes.
 *
 * Everything here runs against [FeedViewModel]'s in-memory list. See its own note for why that is
 * deliberate rather than unfinished.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(vm: FeedViewModel = viewModel()) {
    val snackbars = remember { SnackbarHostState() }

    // Cleared here rather than on a timer in the view model, so a message cannot be spent by a
    // rotation that happens between the action and the snackbar appearing.
    LaunchedEffect(vm.message) {
        val message = vm.message ?: return@LaunchedEffect
        snackbars.showSnackbar(message)
        vm.clearMessage()
    }

    /** The post being reviewed, which takes over the whole screen while it is open. */
    var reviewing by remember { mutableStateOf<ApiPost?>(null) }
    var posting by remember { mutableStateOf(false) }

    /** The review being refused, which needs a reason before anything is charged. */
    var refusing by remember { mutableStateOf<Waiting?>(null) }
    val context = LocalContext.current

    // Reloaded on every arrival rather than once. Coming back from a review is the commonest way
    // to reach this screen, and the counts on it have just changed.
    LaunchedEffect(Unit) { vm.load(context) }

    reviewing?.let { post ->
        ReviewScreen(
            post = post,
            onClose = {
                reviewing = null
                vm.load(context)
            },
        )
        return
    }

    // One per tab rather than one shared between them. A single state carries its scroll position
    // across the switch, so Activity opened at whatever row Feedback happened to be resting on.
    val feedbackList = rememberLazyListState()
    val activityList = rememberLazyListState()

    /**
     * The dashed button at the top of Feedback has scrolled off.
     *
     * Read through [derivedStateOf] so this recomposes when the answer changes rather than on
     * every frame of the scroll that produced it.
     */
    val askOffScreen by remember { derivedStateOf { feedbackList.firstVisibleItemIndex > 0 } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbars) },
        // Scaffold's own slot rather than a Box over the list, so a snackbar is laid out above it
        // instead of across it. Only on Feedback: Activity is written by the app, and there is
        // nothing to post to it.
        //
        // Centred rather than at the end, which for something that fills the width is the only
        // position that means anything: the slot measures its content loosely, so the bar takes
        // the whole width and carries its own gutter in from both edges.
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            AnimatedVisibility(
                visible = vm.tab == FeedTab.FEEDBACK && askOffScreen,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                FeedAskBar { posting = true }
            }
        },
        // Two tabs at the left and a balance at the right, sitting on the page rather than in a
        // bar. No title, because the bottom bar already says Circle and highlights it; no rule,
        // because there is nothing here to divide the page from. What separates this from the
        // first card is space.
        //
        // It is in the topBar slot only so it stays put while the list moves under it, which is
        // the one thing chrome is genuinely for. Everything that made it look like chrome is
        // gone. The ground it paints is load bearing for that: without it the cards would scroll
        // through the words.
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    // The row keeps its own space below the pill rather than sitting flush on its
                    // own bottom edge. What comes off to hold the total to one gutter is the
                    // list's inset above the first card. See [ListTop].
                    // Two of these three used to be the top of the screen. The header above now
                    // brings its own space below itself and the tabs bring their own touch
                    // padding, so this row adds almost nothing of its own or the three stack into
                    // a gap the size of a card.
                    // Nothing of its own. The header above brings its space below itself and
                    // the tabs bring their own, which is their touch target and cannot be given
                    // up; anything here is a third helping on top of those two.
                    .padding(start = Gutter, end = Gutter),
                // The control takes only the width its two words need and the space between the
                // two of them does the rest, so neither is given a weight to stretch into.
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The balance used to sit here. It is in the shell's header now, where it is on
                // every screen rather than on this one, so what is left is the control.
                FeedTabs(
                    selected = vm.tab,
                    waiting = vm.waiting.size + vm.disputes.size,
                ) { vm.show(it) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (vm.tab) {
                FeedTab.FEEDBACK ->
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = feedbackList,
                        // Room under the last card for the floating button, which is only ever
                        // on screen once you are far enough down for this to be the padding
                        // that matters.
                        contentPadding =
                            PaddingValues(
                                start = Gutter,
                                top = ListTop,
                                end = Gutter,
                                bottom = Gutter + 64.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "ask") { FeedAskButton { posting = true } }
                        if (vm.offline) {
                            item {
                                Blank(
                                    "Sign in to see what people are asking for. " +
                                        "Circle needs an account.",
                                )
                            }
                        } else if (vm.loading) {
                            item { Blank("Loading.") }
                        } else if (vm.posts.isEmpty()) {
                            item { Blank("Nobody is asking for anything just now.") }
                        }
                        items(vm.posts, key = { it.id }) { post ->
                            FeedRequestCard(post) { reviewing = post }
                        }
                    }

                // The same gutter, the same edge and the same air between cards as Feedback.
                // Nothing about this tab is a different kind of list, so nothing about it is
                // measured differently: what tells the two apart is what is inside a card.
                FeedTab.ACTIVITY ->
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = activityList,
                        contentPadding =
                            PaddingValues(
                                start = Gutter,
                                top = ListTop,
                                end = Gutter,
                                bottom = Gutter,
                            ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(vm.events, key = { it.id }) { event -> FeedEventCard(event) }
                    }

                FeedTab.WAITING ->
                    WaitingBody(
                        reviews = vm.waiting,
                        disputes = vm.disputes,
                        loading = vm.loading,
                        offline = vm.offline,
                        busy = vm.busy,
                        proof = { ref -> ProofImage(ref) },
                        onApprove = { vm.approve(context, it) },
                        onReject = { refusing = it },
                        onVote = { dispute, forReviewer -> vm.vote(context, dispute, forReviewer) },
                    )
            }
        }
    }

    refusing?.let { review ->
        FeedRefuseDialog(
            appName = review.appName,
            onRefuse = { reason ->
                refusing = null
                vm.reject(context, review, reason)
            },
            onDismiss = { refusing = null },
        )
    }

    if (posting) {
        FeedPostDialog(
            tix = vm.tix,
            onPost = { name, pkg, ask, reward, isLive ->
                posting = false
                vm.post(context, name, pkg, ask, reward, isLive)
            },
            onDismiss = { posting = false },
        )
    }
}

/**
 * A proof screenshot, fetched with the session on it.
 *
 * Served by our API and refused without a token, so it cannot be an ordinary image URL. The
 * loader borrows the same OkHttp client every other call uses, which is what puts the token on
 * the request and refreshes it when it has expired.
 */
@Composable
fun ProofImage(ref: String) {
    val context = LocalContext.current
    val loader = ImageLoader.Builder(context).okHttpClient { Api.http }.build()

    AsyncImage(
        model =
            ImageRequest
                .Builder(context)
                .data(Api.proofUrl(ref))
                .crossfade(true)
                .build(),
        contentDescription = "The screenshot sent as proof",
        imageLoader = loader,
        // Tall, because a phone screenshot is. Cropped rather than letterboxed: the part that
        // matters is the review at the top, and a shrunk-to-fit screenshot is unreadable.
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(10.dp)),
        contentScale = ContentScale.Crop,
    )
}
