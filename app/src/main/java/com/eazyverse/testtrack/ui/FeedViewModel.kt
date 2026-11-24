package com.eazyverse.testtrack.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.domain.Case
import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.domain.Waiting
import com.eazyverse.testtrack.domain.firstName
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiAuth
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.ApiReview
import com.eazyverse.testtrack.net.ApiSession
import com.eazyverse.testtrack.net.acceptLater
import com.eazyverse.testtrack.net.check
import com.eazyverse.testtrack.viewmodel.LiveReload
import com.eazyverse.testtrack.viewmodel.PhoneChecks
import com.eazyverse.testtrack.viewmodel.caseOf
import com.eazyverse.testtrack.viewmodel.millis
import com.eazyverse.testtrack.viewmodel.rejectWithPicture
import com.eazyverse.testtrack.viewmodel.requestOf
import com.eazyverse.testtrack.viewmodel.withUpdate
import kotlinx.coroutines.launch

/** Circle's server-backed lists and actions. Reviews, verdicts and tix stay server-authoritative. */
class FeedViewModel : ViewModel() {
    var tab by mutableStateOf(FeedTab.FEEDBACK)
        private set

    var posts by mutableStateOf<List<ApiPost>>(emptyList())
        private set

    /**
     * The Feedback list: requests this account has not started, started meaning the app is
     * installed for it (step 1 done). Until then a request stays here, unchanged, whether or not
     * Install was tapped. One only taken is also in My reviews, which counts it as a step to do;
     * once installed it leaves here for My reviews alone. One given up or run out is offered again.
     */
    val openPosts: List<ApiPost> get() = posts.filter { it.myState == null || it.myState in notStarted }

    private val notStarted = setOf("claimed", "abandoned", "expired")

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

    /**
     * This account's own reviews, for the Mine tab, every one taken on, claimed included: a review
     * taken and not yet installed is a step this account still owes, so it is listed and counted
     * there as well as being offered on Feedback.
     */
    var mine by mutableStateOf<List<MyReview>>(emptyList())
        private set

    var tix by mutableStateOf(0)
        private set

    var loading by mutableStateOf(true)
        private set

    /** Only the reader's pull shows the refresh indicator; live reloads stay quiet. */
    var pulling by mutableStateOf(false)
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

    /** This account's own requests for feedback, with what each has had. */
    var requests by mutableStateOf<List<Request>>(emptyList())
        private set

    /** Where the next page of posts starts, or null when there is none. */
    private var cursor: String? = null
    private var paging = false

    /** Offline fallback only; fills empty lists without replacing a server answer. */
    private fun seedFromPhone(afterFailure: Boolean = false) {
        // Kept requests show at once. None kept means the tab waits for the server, so it never
        // says "nobody's asking" from memory and then has a card arrive under the words.
        if (posts.isEmpty()) {
            Api.lastPosts(afterFailure)?.takeIf { it.posts.isNotEmpty() }?.let {
                posts = it.posts
                cursor = it.nextCursor
                loading = false
            }
        }
        if (!ApiSession.signedIn) return
        if (mine.isEmpty()) Api.lastMyReviews(afterFailure)?.let { mine = it.map(::myReview) }
        if (waiting.isEmpty()) Api.lastWaiting(afterFailure)?.let { waiting = it.map(::waitingOf) }
        if (disputes.isEmpty()) Api.lastDisputes(afterFailure)?.let { disputes = it.map(::caseOf) }
        if (requests.isEmpty()) Api.lastMyPosts(afterFailure)?.let { requests = it.map(::requestOf) }
    }

    init {
        seedFromPhone()
    }

    /** Everything the tab shows, fetched together. Safe to call on every resume. */
    fun load(
        context: Context,
        fromPull: Boolean = false,
    ) {
        if (fromPull && pulling) return
        if (fromPull) {
            pulling = true
            seed = newFeedSeed()
        }
        val load = ++loadsStarted
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            try {
                // The posts come either way. Everything after them is about one account, so a
                // stranger gets the feed and nothing else, rather than an error page in front of
                // content the server was perfectly willing to hand over.
                val named = ApiAuth.ensure(context)
                signedOut = !named
                val page = Api.posts(seed)
                if (load != loadsStarted) return@launch
                posts = page.posts
                cursor = page.next ?: page.nextCursor
                shuffled = page.next != null || page.nextCursor == null
                pagedPast = false
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
                Snacks.error(e.message)
                if (e.isAuth) signedOut = true else offline = true
            } catch (e: Exception) {
                offline = true
                // No connection on the very first fetch: the kept copy stands in. Not for a server fault.
                if (e is java.io.IOException && !answered) seedFromPhone(afterFailure = true)
            } finally {
                loading = false
                answered = true
                if (load > loadsFinished) loadsFinished = load
                if (fromPull) {
                    holdShimmer(startedAt)
                    pulling = false
                }
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

    /** Stable through paging and live updates; an explicit pull asks for a fresh shuffle. */
    private var seed = newFeedSeed()

    /** The server shuffles and pages with `after`; false for one that still pages newest first. */
    private var shuffled = true

    /** Somebody scrolled past the first page; a live refetch would cut the list back under them. */
    private var pagedPast = false

    fun startLive(context: Context) =
        live.start {
            if (!pagedPast) load(context)
        }

    fun stopLive() = live.stop()

    /** The next page of posts, when the list reaches its end. */
    fun loadMore() {
        val from = cursor ?: return
        if (paging || pulling) return
        pagedPast = true
        val generation = loadsStarted
        paging = true
        viewModelScope.launch {
            try {
                val page = if (shuffled) Api.posts(seed, after = from) else Api.posts(seed, before = from)
                if (generation != loadsStarted) return@launch
                val seen = posts.map { it.id }.toSet()
                posts = posts + page.posts.filter { it.id !in seen }
                cursor = if (shuffled) page.next else page.nextCursor
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
                if (closed.refunded > 0) {
                    Snacks.done("Closed. ${closed.refunded} tix came back to you.")
                } else {
                    Snacks.info("Closed. Reviews already under way can still finish.")
                }
                load(context)
                MyAppsChanged.tell()
            } catch (e: ApiError) {
                Snacks.error(e.message)
            } catch (_: Exception) {
                unreached { closeRequest(context, id) }
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
                Snacks.done("Back in the feed. Testers can take it again.")
                load(context)
            } catch (e: ApiError) {
                Snacks.error(e.message)
            } catch (_: Exception) {
                unreached { relistRequest(context, id) }
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
        } catch (e: Exception) {
            // Kept as it was; the next load tries again. With no connection on the first load, the kept copy.
            if (e is java.io.IOException && !answered) seedFromPhone(afterFailure = true)
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
            icon = it.icon,
            daysUsed = it.daysUsed,
            usedEnough = it.usedMs >= it.neededMs,
            opensAt = it.returnsAt,
            noBox = it.noBox,
            checkBy = it.checkBy,
            decidedAt = it.decidedAt,
            submittedAt = it.submittedAt,
            timing = it.timing,
            firstVisitDone = it.step1DoneAt != null,
            secondVisitDone = it.step2NeededMs > 0 && it.step2Ms >= it.step2NeededMs,
            useAgainFrom = it.useAgainFrom,
            reviewFrom = it.reviewFrom,
        )

    // Mapped here, not drawn raw: a post id means nothing on a card. An older server leaves blanks the feed's post fills.
    private fun waitingOf(it: ApiReview): Waiting {
        val post = posts.firstOrNull { p -> p.id == it.postId }
        return Waiting(
            id = it.id,
            appName = it.appName.ifBlank { appNameFor(it.postId) },
            written = it.written,
            proofRef = it.proofRef,
            reviewerName = it.reviewerName,
            reward = it.reward.takeIf { r -> r > 0 } ?: post?.reward ?: 0,
            isLive = if (it.appName.isBlank()) post?.isLive ?: it.isLive else it.isLive,
            decideBy = millis(it.decideBy) ?: millis(it.submittedAt)?.plus(ONE_DAY),
            packageName = it.packageName.ifBlank { post?.packageName.orEmpty() },
            kept = it.kept,
            icon = it.icon,
            feedback = it.feedback,
            needsChars = it.needsChars,
            textUse = it.textUse,
            noBox = it.noBox,
            recheckedRef = it.recheckedRef,
            checked = it.checkedAt != null,
            sentAt = millis(it.submittedAt),
            askedAt = millis(it.checkedAt),
        ).withUpdate(it)
    }

    /** Accept a review of your app. The reviewer is paid and the post is one closer to done. */
    fun approve(
        context: Context,
        review: Waiting,
    ) = decide(context, review.id, Snack("Accepted. ${review.firstName} has been paid.", SnackTone.DONE)) { Api.approve(review.id) }

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
        picture: Uri? = null,
    ) = decide(context, review.id, Snack("Refused. Three members will decide it.")) {
        rejectWithPicture(context, review.id, reason, picture)
    }

    /** Can't find it on Play: ask its reviewer for a new screenshot. Once per review. */
    fun askAgain(
        context: Context,
        review: Waiting,
    ) = decide(context, review.id, Snack("Asked. They have ${FeedRules.RECHECK_HOURS} hours to send a new screenshot.")) {
        Api.check(review.id)
    }

    /** Accept a review you refused, while the members are still deciding. The reviewer is paid. */
    fun acceptLater(
        context: Context,
        reviewId: String,
    ) = decide(context, reviewId, Snack("Accepted. The reviewer has been paid.", SnackTone.DONE)) { Api.acceptLater(reviewId) }

    /** Come down on one side of a case between two other people. */
    fun vote(
        context: Context,
        dispute: Case,
        forReviewer: Boolean,
    ) = decide(context, dispute.id, Snack("Counted. Thank you.", SnackTone.DONE)) { Api.vote(dispute.id, forReviewer) }

    /**
     * One decision, one reload, one place failure is handled.
     *
     * Reloaded rather than patched, because a vote can close a case and a case closing moves
     * somebody's tix, and neither of those is one row on this screen.
     */
    private fun decide(
        context: Context,
        id: String,
        done: Snack,
        block: suspend () -> Unit,
    ) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
                Snacks.show(done)
                // Decided, so its page has nothing left to ask; it closes on this.
                decided = id
                load(context)
                MyAppsChanged.tell()
            } catch (e: ApiError) {
                Snacks.error(e.message)
            } catch (_: Exception) {
                unreached { decide(context, id, done, block) }
            } finally {
                busy = false
            }
        }
    }
}

/** No connection: said once, with the same call again on Try again. */
private fun unreached(retry: () -> Unit) = Snacks.error("Couldn't reach TestTrack. Check your connection.", retry)

/** How long an owner has to answer, as the server's rule; only used when it did not say. */
private const val ONE_DAY = 24 * 3_600_000L

private fun newFeedSeed(): String =
    java.util.UUID
        .randomUUID()
        .toString()
        .replace("-", "")
        .take(16)
