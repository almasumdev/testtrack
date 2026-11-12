package com.eazyverse.testtrack.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.viewmodel.proofSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * What each list adds above its first card.
 *
 * Half a gutter rather than a whole one, and the missing half is not missing: the tab row keeps
 * its own space below the pill, and the two together are the single gutter that shows on screen.
 * Every list here uses it, so switching tabs does not move the first card. Signed out there is no
 * tab row, and the first card sits this far below the app bar.
 */
private val ListTop = 12.dp

/**
 * Circle: the community front page.
 *
 * Feedback, your own reviews and what is waiting on you sit under one heading rather than as places
 * in the bar, so the bottom nav stays at five tabs.
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
    /** Signed out and tapped Review on a card: asked for an account with that reason. */
    onSignInToReview: () -> Unit = onSignIn,
    /** Open a review of your app, or a case, on a page of its own, by id. */
    onCheck: (String) -> Unit = {},
    vm: FeedViewModel = viewModel(),
) {
    val membership = rememberMembership()
    val context = LocalContext.current

    // Reloaded on every arrival rather than once. Coming back from a review is the commonest way
    // to reach this screen, and the counts on it have just changed.
    LaunchedEffect(Unit) { vm.load(context) }
    // New requests, reviews of yours and verdicts arrive while the tab is open.
    WhileShown(onShow = { vm.startLive(context) }, onHide = vm::stopLive)

    val feedbackList = rememberLazyListState()

    /**
     * The ask row at the top of Feedback has scrolled off.
     *
     * Read through [derivedStateOf] so this recomposes when the answer changes rather than on
     * every frame of the scroll that produced it.
     */
    val askOffScreen by remember { derivedStateOf { feedbackList.firstVisibleItemIndex > 0 } }
    // Near the end of the list, the next page: the first twenty were all there ever was.
    val nearEnd by remember {
        derivedStateOf {
            val info = feedbackList.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

    // Notifications, offered once and only once there is something of this account's in
    // Feedback to hear about: a request out, or a review taken on. Offered in our own words
    // first, and the system's question only follows an Allow: a prompt out of nowhere is a
    // permission for nothing in particular, which is the one people refuse.
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val involved = vm.requests.isNotEmpty() || vm.mine.isNotEmpty()
    var offeringNotify by remember { mutableStateOf(false) }
    LaunchedEffect(involved) {
        if (!involved || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val prefs = context.getSharedPreferences("feedback_notify", Context.MODE_PRIVATE)
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted && !prefs.getBoolean("asked", false)) {
            prefs.edit().putBoolean("asked", true).apply()
            offeringNotify = true
        }
    }
    // Circle's how-to card, under the Ask button: until this account has a review or a request of
    // its own, by when it has seen how Circle goes, or until it is closed. Gone for good either way.
    var howTo by remember { mutableStateOf(!HowToCards.circleGone(context)) }
    LaunchedEffect(involved) {
        if (involved && howTo) {
            HowToCards.hideCircle(context)
            howTo = false
        }
    }

    if (offeringNotify) {
        Ask(
            title = "Turn on notifications?",
            body = "We let you know when a review of your app comes in or a review you sent is accepted.",
            confirm = "Allow",
            dismiss = "Not now",
            onConfirm = {
                offeringNotify = false
                notify.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = { offeringNotify = false },
        )
    }

    // Mine and Waiting are this account's own; signed out there is no account, so no tabs for them.
    val shownTabs = if (vm.signedOut) listOf(FeedTab.FEEDBACK) else FeedTab.entries
    val tab = if (vm.tab in shownTabs) vm.tab else FeedTab.FEEDBACK

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // Scaffold's own slot rather than a Box over the list, so it is laid out above the list
        // instead of across it. Only on Feedback, the one tab with something to post.
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
        // The tabs sit on the page rather than in a bar, with no title and no rule: the bottom bar
        // already says Circle, and space is what separates them from the first card. They are in
        // the topBar slot only so they stay put while the list moves under them.
        topBar = {
            Column {
                // The count is the one thing on this screen genuinely owed: reviews of this
                // account's apps, and cases it can help decide. It shows from whichever tab you
                // are on rather than only from Waiting, because it is an alert, and an alert you
                // have to go looking for is not one. The tabs bring their own gutter and touch
                // padding, so nothing wraps them.
                val owed = vm.waiting.size + vm.disputes.size
                // Signed out there is only Feedback, and one lone segment is not a choice: no row.
                if (shownTabs.size > 1) {
                    SegmentedTabs(
                        labels = shownTabs.map { it.label },
                        selected = shownTabs.indexOf(tab),
                        counts =
                            shownTabs.map {
                                when (it) {
                                    FeedTab.WAITING -> owed
                                    // Taken on and not yet sent, claimed included: each one is the reader's own next step.
                                    FeedTab.MINE -> vm.mine.count { r -> r.state in InProgressStates }
                                    else -> 0
                                }
                            },
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
                    val waiting = DebugLooks.holdLoading || (vm.loading && vm.posts.isEmpty())
                    LoadSwap(waiting) { hold ->
                        val unreached = !hold && vm.offline && vm.posts.isEmpty()
                        val none = !hold && !vm.loading && !unreached && vm.openPosts.isEmpty()
                        if (unreached) {
                            RobotOffline("Circle didn't load", onRetry = { vm.load(context) })
                        } else if (none) {
                            RobotState(
                                Prop.Music,
                                "Nobody's asking right now",
                                "When someone wants feedback on their app, it shows up here. You can ask for some yourself.",
                                action = "Ask for feedback",
                                onAction = onAsk,
                            )
                        } else {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                state = if (hold) rememberLazyListState() else feedbackList,
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
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // 4dp more under it than between cards, so it sits apart from the list.
                                item(key = "ask") { FeedAskRow(Modifier.padding(bottom = 4.dp), onPost = onAsk) }
                                // Only once the server has answered: saved lists can be last time's,
                                // and a card shown from them would vanish as the real ones arrive.
                                if (!hold && howTo && vm.answered && !involved) {
                                    item(key = "howto") {
                                        Box(Modifier.animateItem()) {
                                            FeedCard {
                                                HowToBody(HowTo.CIRCLE, onHide = {
                                                    HowToCards.hideCircle(context)
                                                    howTo = false
                                                })
                                            }
                                        }
                                    }
                                }
                                if (hold) items(SKELETON_ROWS) { FeedRequestLoadingCard() }
                                items(if (hold) emptyList() else vm.openPosts, key = { it.id }) { post ->
                                    // Fades in, fades out and glides to its new place when the list changes.
                                    Box(Modifier.animateItem()) {
                                        // Group membership is required for closed testing only.
                                        FeedRequestCard(post) {
                                            // Signed out, a review cannot be claimed: ask for the account first.
                                            if (vm.signedOut) {
                                                onSignInToReview()
                                            } else if (post.isLive) {
                                                onReview(Json.encodeToString(post))
                                            } else {
                                                membership.require { onReview(Json.encodeToString(post)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                FeedTab.MINE ->
                    LoadSwap(DebugLooks.holdLoading || (!vm.answered && vm.mine.isEmpty() && !vm.signedOut)) { waiting ->
                        if (waiting) {
                            TicketLoadingList()
                            return@LoadSwap
                        }
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
                    }

                FeedTab.WAITING ->
                    LoadSwap(
                        DebugLooks.holdLoading ||
                            (!vm.answered && vm.waiting.isEmpty() && vm.disputes.isEmpty() && !vm.signedOut),
                    ) { waiting ->
                        if (waiting) {
                            TicketLoadingList()
                            return@LoadSwap
                        }
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
                            requests = vm.requests,
                            onOpenRequest = { onCheck(it.id) },
                        )
                    }
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

    SubcomposeAsyncImage(
        model =
            ImageRequest
                .Builder(context)
                .data(proofSource(ref))
                .crossfade(true)
                .build(),
        contentDescription = "The screenshot sent as proof",
        imageLoader = loader,
        // A screenshot from Drive takes a moment on mobile data: its shape shimmers meanwhile.
        loading = {
            if (whole) {
                Skeleton(height = 320.dp, corner = 14.dp)
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) { Skeleton(maxWidth, maxHeight, 10.dp) }
            }
        },
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
