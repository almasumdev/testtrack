package com.eazyverse.testtrack.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.domain.Case
import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.domain.Waiting
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiActivityEvent
import com.eazyverse.testtrack.net.ApiAuth
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.ApiReview
import com.eazyverse.testtrack.net.ApiSession
import com.eazyverse.testtrack.viewmodel.LiveReload
import com.eazyverse.testtrack.viewmodel.PhoneChecks
import com.eazyverse.testtrack.viewmodel.caseOf
import com.eazyverse.testtrack.viewmodel.millis
import com.eazyverse.testtrack.viewmodel.requestOf
import kotlinx.coroutines.launch

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

    /** Loads started, and the newest of them that has finished, answered or failed. */
    var loadsStarted = 0
        private set
    var loadsFinished by mutableIntStateOf(0)
        private set

    /**
     * Whether a fetch from the server has finished since the app started, answered or failed.
     *
     * Saved data with something in it shows at once, but saved data that is empty is only last
     * time's answer: each tab waits for this before it says it has nothing, so "nothing has
     * happened yet" is never shown from memory and then contradicted.
     */
    var answered by mutableStateOf(false)
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

    /** The Activity tab: what happened in the community lately, from the server. */
    var events by mutableStateOf<List<FeedEvent>>(emptyList())
        private set

    /** The Activity tab's own load failed, whatever happened to the rest. */
    var activityFailed by mutableStateOf(false)
        private set

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

    /**
     * What every tab showed last time, from the phone, so Circle opens on it rather than on
     * placeholders. The account's own parts only while somebody is signed in; they are wiped with
     * the session anyway.
     */
    private fun seedFromPhone() {
        // Kept requests show at once. None kept means the tab waits for the server, so it never
        // says "nobody's asking" from memory and then has a card arrive under the words.
        Api.lastPosts()?.takeIf { it.posts.isNotEmpty() }?.let {
            posts = it.posts
            cursor = it.nextCursor
            loading = false
        }
        Api.lastActivity()?.let { events = it.events.mapNotNull(::eventOf) }
        if (!ApiSession.signedIn) return
        Api.lastMyReviews()?.let { mine = it.map(::myReview) }
        Api.lastWaiting()?.let { waiting = it.map(::waitingOf) }
        Api.lastDisputes()?.let { disputes = it.map(::caseOf) }
        Api.lastMyPosts()?.let { requests = it.map(::requestOf) }
    }

    init {
        seedFromPhone()
    }

    /** Everything the tab shows, fetched together. Safe to call on every resume. */
    fun load(context: Context) {
        val load = ++loadsStarted
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
                pagedPast = false
                // Readable signed out too, like the posts. Its own failure is its own.
                try {
                    events = Api.activity().events.mapNotNull(::eventOf)
                    activityFailed = false
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    activityFailed = true
                }
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
                part {
                    val reviews = Api.myReviews()
                    mine = reviews.map(::myReview)
                    PhoneChecks.report(context.applicationContext, reviews)
                }
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
                answered = true
                if (load > loadsFinished) loadsFinished = load
            }
        }
    }

    // The feed's own events, and yours: reviews of your posts, verdicts on yours, tix.
    private val live =
        LiveReload("circle", viewModelScope) { it.channel == "feed" || it.channel.startsWith("user:") }

    /**
     * The same news for a review, case or request opened on its own page, which borrows this
     * model. Its own listener rather than Circle's, so leaving one page and arriving at the other
     * cannot stop the one that has just started.
     */
    private val pageLive =
        LiveReload("check", viewModelScope) { it.channel == "feed" || it.channel.startsWith("user:") }

    fun startPageLive(context: Context) {
        if (!signedOut) pageLive.start { load(context) }
    }

    fun stopPageLive() = pageLive.stop()

    /** Somebody scrolled past the first page; a live refetch would cut the list back under them. */
    private var pagedPast = false

    fun startLive(context: Context) =
        live.start {
            if (!pagedPast) load(context)
        }

    fun stopLive() = live.stop()

    /** The next page of posts, when the list reaches its end. */
    fun loadMore() {
        pagedPast = true
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
                MyAppsChanged.tell()
            } catch (e: ApiError) {
                message = e.message
            } catch (_: Exception) {
                message = "We could not reach TestTrack. Check your connection."
            } finally {
                busy = false
            }
        }
    }

    /** Put a request two testers could not install back in the feed, once it is fixed. */
    fun relistRequest(
        context: Context,
        id: String,
    ) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                Api.relistPost(id)
                message = "Back in the feed. Testers can take it again."
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

    private fun eventOf(it: ApiActivityEvent): FeedEvent? {
        val kind = FeedEventKind.of(it.kind) ?: return null
        return FeedEvent(
            id = it.id,
            kind = kind,
            headline = it.headline,
            detail = it.detail,
            happenedAt = millis(it.happenedAt) ?: return null,
            faces = it.faces,
            moreFaces = it.moreFaces,
            tix = it.tix,
        )
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
            icon = it.icon,
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
            kept = it.kept,
            icon = it.icon,
        )
    }

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
                MyAppsChanged.tell()
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

/** How long an owner has to answer, as the server's rule; only used when it did not say. */
private const val TWO_DAYS = 48 * 3_600_000L
