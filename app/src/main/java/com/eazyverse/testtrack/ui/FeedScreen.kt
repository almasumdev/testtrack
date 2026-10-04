package com.eazyverse.testtrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Megaphone
import com.eazyverse.testtrack.net.Api
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * What each list adds above its first card.
 *
 * Half a gutter rather than a whole one, and the missing half is not missing: the header row keeps
 * its own space below the pill, and the two together are the single gutter that shows on screen.
 * Both tabs use it, so switching between them does not move the first card.
 */
private val ListTop = 16.dp

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
fun FeedScreen(
    onAsk: () -> Unit = {},
    onReview: (String) -> Unit = {},
    onSignIn: () -> Unit = {},
    /** Open a review of your app, or a case, on a page of its own, by id. */
    onCheck: (String) -> Unit = {},
    vm: FeedViewModel = viewModel(),
) {
    val context = LocalContext.current

    // Reloaded on every arrival rather than once. Coming back from a review is the commonest way
    // to reach this screen, and the counts on it have just changed.
    LaunchedEffect(Unit) { vm.load(context) }

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

    // Mine and Waiting are this account's own; signed out there is no account, so no tabs for them.
    val shownTabs = if (vm.signedOut) listOf(FeedTab.FEEDBACK, FeedTab.ACTIVITY) else FeedTab.entries
    val tab = if (vm.tab in shownTabs) vm.tab else FeedTab.FEEDBACK

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // Scaffold's own slot rather than a Box over the list, so it is laid out above the list
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
                Box(Modifier.padding(bottom = dockRoom())) { FeedAskBar(onAsk) }
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
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
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
                    // The count is the one thing on this screen genuinely owed: reviews of this
                    // account's apps, and cases it can help decide. It shows from whichever tab you
                    // are on rather than only from Waiting, because it is an alert, and an alert you
                    // have to go looking for is not one.
                    val owed = vm.waiting.size + vm.disputes.size
                    WordTabs(
                        labels = shownTabs.map { it.label },
                        selected = shownTabs.indexOf(tab),
                        counts = shownTabs.map { if (it == FeedTab.WAITING) owed else 0 },
                        gap = 14,
                    ) { vm.show(shownTabs[it]) }
                }
                PageMessage(vm.message, vm::clearMessage)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                FeedTab.FEEDBACK -> {
                    // Not a sign in prompt. The posts come back for anybody, so nothing here and
                    // offline means the server could not be got at.
                    val unreached = vm.offline && vm.posts.isEmpty()
                    val none = !vm.loading && !unreached && vm.openPosts.isEmpty()
                    if (unreached) {
                        RobotOffline("Circle didn't load", onRetry = { vm.load(context) })
                    } else if (none) {
                        RobotState(
                            Lucide.Megaphone,
                            "Nobody's asking right now",
                            "When someone wants feedback on their app, it shows up here. You can ask for some yourself.",
                            action = "Ask for feedback",
                            onAction = onAsk,
                        )
                    } else {
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
                                    bottom = Gutter + 64.dp + dockRoom(),
                                ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item(key = "ask") { FeedAskButton(onPost = onAsk) }
                            if (vm.loading && vm.posts.isEmpty()) {
                                items(3) { FeedLoadingCard() }
                            }
                            items(vm.openPosts, key = { it.id }) { post ->
                                FeedRequestCard(post) { onReview(Json.encodeToString(post)) }
                            }
                        }
                    }
                }

                // The same gutter, the same edge and the same air between cards as Feedback.
                // Nothing about this tab is a different kind of list, so nothing about it is
                // measured differently: what tells the two apart is what is inside a card.
                FeedTab.ACTIVITY ->
                    if (!vm.loading && vm.events.isEmpty()) {
                        if (vm.offline) {
                            RobotOffline("Activity didn't load", onRetry = { vm.load(context) })
                        } else {
                            RobotState(
                                Lucide.Activity,
                                "Nothing has happened yet",
                                "New posts, reviews and payouts in your circle show up here as they happen.",
                            )
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            state = activityList,
                            contentPadding =
                                PaddingValues(
                                    start = Gutter,
                                    top = ListTop,
                                    end = Gutter,
                                    bottom = Gutter + dockRoom(),
                                ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (vm.loading && vm.events.isEmpty()) {
                                items(4) { FeedLoadingCard(compact = true) }
                            }
                            items(vm.events, key = { it.id }) { event -> FeedEventCard(event) }
                        }
                    }

                FeedTab.MINE ->
                    MineBody(
                        reviews = vm.mine,
                        loading = vm.loading,
                        signedOut = vm.signedOut,
                        onSignIn = onSignIn,
                        offline = vm.offline,
                        onRetry = { vm.load(context) },
                        onFind = { vm.show(FeedTab.FEEDBACK) },
                        onOpen = { review -> onReview(Json.encodeToString(vm.postFor(review))) },
                    )

                FeedTab.WAITING ->
                    WaitingBody(
                        reviews = vm.waiting,
                        disputes = vm.disputes,
                        loading = vm.loading,
                        offline = vm.offline,
                        onRetry = { vm.load(context) },
                        signedOut = vm.signedOut,
                        onOpenReview = { onCheck(it.id) },
                        onOpenCase = { onCheck(it.id) },
                        onSignIn = onSignIn,
                    )
            }
        }
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
fun ProofImage(
    ref: String,
    whole: Boolean = false,
) {
    val context = LocalContext.current
    // The token-carrying client, and one loader rather than a new one on every recomposition.
    // Drive needs no token: the file is shared by link.
    val loader = remember { ImageLoader.Builder(context).okHttpClient { Api.images }.build() }

    AsyncImage(
        model =
            ImageRequest
                .Builder(context)
                .data(proofSource(ref))
                .crossfade(true)
                .build(),
        contentDescription = "The screenshot sent as proof",
        imageLoader = loader,
        // On a review's own page it is shown whole, at the width of the page. Anywhere smaller it
        // is cropped to its top, where the review is, since a shrunk-to-fit screenshot is
        // unreadable.
        modifier =
            if (whole) {
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(10.dp))
            },
        contentScale = if (whole) ContentScale.FillWidth else ContentScale.Crop,
    )
}
