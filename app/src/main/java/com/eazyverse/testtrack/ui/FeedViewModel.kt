package com.eazyverse.testtrack.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.domain.Case
import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.domain.RequestReview
import com.eazyverse.testtrack.domain.Waiting
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiAuth
import com.eazyverse.testtrack.net.ApiDispute
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiMyPost
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.ApiReview
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Circle, against the API.
 *
 * There is no sample data behind this any more. Everything on the screen is a row on the server,
 * which is what makes the tix real: a balance the phone could compute is a balance the phone could
 * be talked into computing differently.
 */
class FeedViewModel : ViewModel() {
    var tab by mutableStateOf(FeedTab.FEEDBACK)
        private set

    var posts by mutableStateOf<List<ApiPost>>(emptyList())
        private set

    /**
     * The Feedback list: open posts, less any this account has already sent a review for. A sent
     * review is done from the reviewer's side, and it is followed from the Mine tab instead.
     */
    val openPosts: List<ApiPost> get() = posts.filter { it.myState !in SentStates }

    /**
     * The post to open one of my reviews through. The live one when it is still on the feed;
     * otherwise rebuilt from the review itself, since a post that has had all its reviews is gone
     * from the feed but its reviews still need opening. Either way it names the review, so the
     * page opens that one rather than claiming a new one.
     */
    fun postFor(review: MyReview): ApiPost =
        posts.firstOrNull { it.id == review.postId }?.copy(myReviewId = review.id, myState = review.state)
            ?: ApiPost(
                id = review.postId,
                appName = review.appName,
                packageName = review.packageName,
                ask = "",
                isLive = review.isLive,
                reward = review.reward,
                reviewsBought = 0,
                reviewsDone = 0,
                ownerName = "",
                mine = false,
                myReviewId = review.id,
                myState = review.state,
                createdAt = "",
            )

    /** This account's own reviews, for the Mine tab. */
    var mine by mutableStateOf<List<MyReview>>(emptyList())
        private set

    var tix by mutableStateOf(0)
        private set

    var loading by mutableStateOf(true)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    /** Set once the API has refused us for good, so the screen can say so rather than spin. */
    var offline by mutableStateOf(false)
        private set

    /**
     * Nobody is signed in, so the three personal halves of this tab are empty.
     *
     * The posts are not: they read without an account, and they are the whole reason somebody
     * without one is allowed in here at all.
     */
    var signedOut by mutableStateOf(false)
        private set

    val events = SampleFeed.events()

    fun show(next: FeedTab) {
        tab = next
    }

    /** The review or case last decided, by id, so its page knows to close. */
    var decided by mutableStateOf<String?>(null)
        private set

    /** Reviews of this account's apps, waiting to be accepted or refused. */
    var waiting by mutableStateOf<List<Waiting>>(emptyList())
        private set

    /** Cases this account is eligible to help decide. */
    var disputes by mutableStateOf<List<Case>>(emptyList())
        private set

    var busy by mutableStateOf(false)
        private set

    /** How many decisions are owed, for the badge on the tab. */
    val owed: Int get() = waiting.size + disputes.size

    /** Which app a waiting review is of, since a review only carries the post's id. */
    fun appNameFor(postId: String): String = posts.firstOrNull { it.id == postId }?.appName ?: "Your app"

    fun clearMessage() {
        message = null
    }

    /** This account's own requests for feedback, with what each has had. */
    var requests by mutableStateOf<List<Request>>(emptyList())
        private set

    /** Where the next page of posts starts, or null when there is none. */
    private var cursor: String? = null
    private var paging = false

    /** Everything the tab shows, fetched together. Safe to call on every resume. */
    fun load(context: Context) {
        viewModelScope.launch {
            try {
                // The posts come either way. Everything after them is about one account, so a
                // stranger gets the feed and nothing else, rather than an error page in front of
                // content the server was perfectly willing to hand over.
                val named = ApiAuth.ensure(context)
                signedOut = !named
                val page = Api.posts()
                posts = page.posts
                cursor = page.nextCursor
                if (!named) {
                    tix = 0
                    mine = emptyList()
                    waiting = emptyList()
                    disputes = emptyList()
                    requests = emptyList()
                    offline = false
                    return@launch
                }
                tix = Api.me().tix
                // Each part on its own: one that fails keeps what it had, rather than turning the
                // whole tab into an offline page when the rest came back fine.
                part { mine = Api.myReviews().map(::myReview) }
                // Both duties, fetched with the feed rather than when the tab is opened: the
                // badge has to be right before anybody looks at it.
                part { waiting = Api.waiting().map(::waitingOf) }
                part { disputes = Api.disputes().map(::caseOf) }
                part { requests = Api.myPosts().map(::requestOf) }
                offline = false
            } catch (e: ApiError) {
                message = e.message
                if (e.isAuth) signedOut = true else offline = true
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
            }
        }
    }

    /** The next page of posts, when the list reaches its end. */
    fun loadMore() {
        val from = cursor ?: return
        if (paging) return
        paging = true
        viewModelScope.launch {
            try {
                val page = Api.posts(before = from)
                val seen = posts.map { it.id }.toSet()
                posts = posts + page.posts.filter { it.id !in seen }
                cursor = page.nextCursor
            } catch (_: Exception) {
                // The next scroll to the end asks again.
            } finally {
                paging = false
            }
        }
    }

    /** Close one of your requests and take back what was held for the slots nobody took. */
    fun closeRequest(
        context: Context,
        id: String,
    ) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val closed = Api.closePost(id)
                message =
                    if (closed.refunded > 0) {
                        "Closed. ${closed.refunded} tix came back to you."
                    } else {
                        "Closed. Reviews already under way can still finish."
                    }
                load(context)
            } catch (e: ApiError) {
                message = e.message
            } catch (_: Exception) {
                message = "We could not reach TestTrack. Check your connection."
            } finally {
                busy = false
            }
        }
    }

    /** One personal part of the load. An auth refusal still ends the whole load. */
    private suspend fun part(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: ApiError) {
            if (e.isAuth) throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Kept as it was; the next load tries again.
        }
    }

    private fun myReview(it: ApiReview) =
        MyReview(
            id = it.id,
            postId = it.postId,
            appName = it.appName.ifBlank { appNameFor(it.postId) },
            state = it.state,
            reward = it.reward,
            reason = it.reason,
            packageName = it.packageName,
            isLive = it.isLive,
        )

    // Mapped here rather than drawn raw: a post id means nothing on a card, and the screen should
    // not have to know what the database calls things.
    private fun waitingOf(it: ApiReview): Waiting {
        // From an older server these come blank; the post, when it is on the feed, has them too.
        val post = posts.firstOrNull { p -> p.id == it.postId }
        return Waiting(
            id = it.id,
            appName = it.appName.ifBlank { appNameFor(it.postId) },
            written = it.written,
            proofRef = it.proofRef,
            reviewerName = it.reviewerName,
            reward = it.reward.takeIf { r -> r > 0 } ?: post?.reward ?: 0,
            isLive = if (it.appName.isBlank()) post?.isLive ?: it.isLive else it.isLive,
            decideBy = millis(it.decideBy) ?: millis(it.submittedAt)?.plus(TWO_DAYS),
            packageName = it.packageName.ifBlank { post?.packageName.orEmpty() },
        )
    }

    private fun caseOf(it: ApiDispute) =
        Case(
            id = it.id,
            appName = it.appName,
            reason = it.reason,
            written = it.written,
            proofRef = it.proofRef,
            stake = it.stake,
            votesCast = it.votesCast,
            votesNeeded = it.votesNeeded,
            packageName = it.packageName,
            reward = it.reward,
            isLive = it.isLive,
        )

    private fun requestOf(it: ApiMyPost) =
        Request(
            id = it.id,
            appName = it.appName,
            packageName = it.packageName,
            isLive = it.isLive,
            reward = it.reward,
            bought = it.reviewsBought,
            done = it.reviewsDone,
            open = it.slotsOpen,
            closed = it.closed,
            refundable = it.refundable,
            reviews =
                it.reviews.map { r ->
                    RequestReview(
                        id = r.id,
                        state = r.state,
                        reviewerName = r.reviewerName,
                        proofRef = r.proofRef,
                        written = r.written,
                        reason = r.reason,
                        outcome = r.outcome,
                        decideBy = millis(r.decideBy),
                    )
                },
        )

    /** Accept a review of your app. The reviewer is paid and the post is one closer to done. */
    fun approve(
        context: Context,
        review: Waiting,
    ) = decide(context, review.id, "Accepted. The reviewer has been paid.") { Api.approve(review.id) }

    /**
     * Refuse one, which is not free.
     *
     * Three members decide it and the loser funds them, so the screen says the stake before the
     * button is pressed rather than after the tix have gone.
     */
    fun reject(
        context: Context,
        review: Waiting,
        reason: String,
    ) = decide(context, review.id, "Refused. Three members will decide it.") { Api.reject(review.id, reason) }

    /** Come down on one side of a case between two other people. */
    fun vote(
        context: Context,
        dispute: Case,
        forReviewer: Boolean,
    ) = decide(context, dispute.id, "Counted. Thank you.") { Api.vote(dispute.id, forReviewer) }

    /**
     * One decision, one reload, one place failure is handled.
     *
     * Reloaded rather than patched, because a vote can close a case and a case closing moves
     * somebody's tix, and neither of those is one row on this screen.
     */
    private fun decide(
        context: Context,
        id: String,
        done: String,
        block: suspend () -> Unit,
    ) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
                message = done
                // Decided, so its page has nothing left to ask; it closes on this.
                decided = id
                load(context)
            } catch (e: ApiError) {
                message = e.message
            } catch (_: Exception) {
                message = "We could not reach TestTrack. Check your connection."
            } finally {
                busy = false
            }
        }
    }
}

/** An ISO time from the server as epoch milliseconds, or null when absent or unreadable. */
private fun millis(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

/** How long an owner has to answer, as the server's rule; only used when it did not say. */
private const val TWO_DAYS = 48 * 3_600_000L
