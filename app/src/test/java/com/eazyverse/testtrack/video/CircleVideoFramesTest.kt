package com.eazyverse.testtrack.video

import android.app.Application
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.eazyverse.testtrack.ui.AppPickerSheet
import com.eazyverse.testtrack.ui.AskScreen
import com.eazyverse.testtrack.ui.AskViewModel
import com.eazyverse.testtrack.ui.CaseCheck
import com.eazyverse.testtrack.ui.FeedRefuseDialog
import com.eazyverse.testtrack.ui.Gutter
import com.eazyverse.testtrack.ui.JoinGroupAsk
import com.eazyverse.testtrack.ui.PickFor
import com.eazyverse.testtrack.ui.RequestCheck
import com.eazyverse.testtrack.ui.ReviewBody
import com.eazyverse.testtrack.ui.ReviewCheck
import com.eazyverse.testtrack.ui.ReviewGuideDialog
import com.eazyverse.testtrack.ui.ReviewJob
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Circle tutorial's frames, from the app's own composables with sample data.
 *
 * Opt-in: `./gradlew :app:testDebugUnitTest -PvideoFrames --tests "*VideoFrames*"` writes each
 * frame as `<id>.png` with `<id>.json` beside it to media/public/frames/circle. Without the property
 * every test here is skipped, and [VideoFramesRunner] never starts Robolectric.
 *
 * A plain [Application] rather than the app's own, whose start-up reaches for Firebase and Play.
 */
@RunWith(VideoFramesRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Phone.SDK], qualifiers = Phone.QUALIFIERS, application = Application::class)
class CircleVideoFramesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val frame = Frame(compose)
    private val s = CircleSamples

    @Before
    fun onlyWhenAsked() = assumeTrue("set -PvideoFrames to draw the video frames", videoFramesOn)

    private fun text(
        value: String,
        clickable: Boolean = false,
    ) = if (clickable) compose.onNode(hasText(value) and hasClickAction()) else compose.onNodeWithText(value)

    /** The sample proof for one app: media/public/proof/<app>.png, beside store/art in the repo. */
    private fun shot(app: String): Uri = Uri.fromFile(FrameImages.art.resolve("../../media/public/proof/$app.png").canonicalFile)

    // ------------------------------------------------------------------ the feed

    @Test
    fun f01_feed() {
        val feed = feedWith(compose.activity, s.feed)
        frame.show { Ground { CircleShell(feed) } }
        frame.target("tix", compose.onNodeWithContentDescription("${s.BALANCE} tix"))
        frame.target("askButton", text("Ask for feedback", clickable = true))
        frame.target("cardHabit", compose.onNode(hasText(s.habit.appName) and hasClickAction()))
        frame.target("chipHabit", text("Review for ${s.habit.reward} tix", clickable = true))
        frame.target("pillClosed", compose.onNodeWithText("Closed test", useUnmergedTree = true))
        frame.write("feed")
    }

    @Test
    fun f02_group() {
        val feed = feedWith(compose.activity, s.feed)
        // Over the page, as MainActivity puts it, so it takes the app's theme like every dialog.
        frame.show {
            Ground {
                CircleShell(feed)
                JoinGroupAsk(
                    email = "sam.taylor@example.com",
                    checking = false,
                    outside = true,
                    failure = null,
                    onCheck = {},
                    onOpenGroup = {},
                    onClose = {},
                )
            }
        }
        frame.target("openGroup", text("Open group", clickable = true))
        frame.write("group")
    }

    // ------------------------------------------------------------------ reviewing Habit Garden

    private fun review(
        state: String,
        id: String,
        used: Long = 0,
        leftReview: Boolean = false,
        usageAccess: Boolean = true,
        proof: Uri? = null,
        note: String = "",
        guide: Boolean = false,
        targets: () -> Unit = {},
    ) {
        val job = ReviewJob(s.habit, s.habitReview(state, used), leftReview)
        frame.show {
            Pushed {
                ReviewBody(
                    post = s.habit,
                    job = job,
                    sideloaded = false,
                    usageAccess = usageAccess,
                    onClose = {},
                    onLeave = {},
                    onSend = { _, _ -> },
                    proof = proof,
                    note = note,
                    onGiveUp = {},
                )
                if (guide) ReviewGuideDialog(live = false, onOk = {}, onDismiss = {})
            }
        }
        targets()
        frame.write(id)
    }

    /** The dial is drawn, not labelled: its square is found from the words centred in it. */
    private fun dial(
        middle: String,
        under: String,
    ): Rect {
        val words = compose.onNodeWithText(middle).screenBounds()
        val labels = compose.onNodeWithText(under).screenBounds()
        val centre = words.center.y
        val half = labels.top - frame.px(8f) - centre
        return Rect(words.center.x - half, centre - half, words.center.x + half, centre + half)
    }

    @Test
    fun f03_review1() =
        review("claimed", "review1") {
            frame.target("stepTitle", compose.onNodeWithText("Install ${s.habit.appName}"))
            frame.target("getOnPlay", text("Get it on Play", clickable = true))
            frame.target("dial", dial("Step 1", "Install"))
        }

    @Test
    fun f04_review2() =
        review("installed", "review2", used = 80_000) {
            frame.target("stepTitle", compose.onNodeWithText("Try the app"))
            frame.target("openApp", text("Open the app", clickable = true))
            frame.target("dial", dial("80", "✓ Install"))
        }

    @Test
    fun f05_review2Allow() =
        review("installed", "review2Allow", used = 80_000, usageAccess = false) {
            frame.target("stepTitle", compose.onNodeWithText("Allow usage time"))
            frame.target("allowUsage", text("Allow usage access", clickable = true))
        }

    @Test
    fun f06_review3() =
        review("used", "review3") {
            frame.target("stepTitle", compose.onNodeWithText("Send feedback on Google Play"))
            frame.target("openPlay", text("Open Google Play", clickable = true))
            frame.target("selfShot", text("I'll take the screenshot myself", clickable = true))
        }

    @Test
    fun f07_playGuide() =
        review("used", "playGuide", guide = true) {
            frame.target("gotIt", text("Got it", clickable = true))
        }

    @Test
    fun f08_review4() =
        review("used", "review4", leftReview = true, proof = shot("habit"), note = s.NOTE) {
            frame.target("stepTitle", compose.onNodeWithText("Send your proof"))
            frame.target("sendProof", text("Send the proof", clickable = true))
            frame.target("note", compose.onNode(hasSetTextAction()))
        }

    @Test
    fun f09_sent() = review("submitted", "sent")

    // ------------------------------------------------------------------ asking for feedback

    @Test
    fun f10_askPicker() {
        val feed = feedWith(compose.activity, s.feed)
        frame.show {
            Ground {
                CircleShell(feed)
                AppPickerSheet(PickFor.FEEDBACK, listOf(s.focus), loading = false, onPick = {}, onAdd = {}, onDismiss = {})
            }
        }
        frame.target("pickFocus", text(s.focus.name, clickable = true))
        frame.write("askPicker")
    }

    private fun asking(
        app: com.eazyverse.testtrack.ui.AppEntry,
        reviews: Int,
        reward: Int,
    ): AskViewModel =
        AskViewModel().apply {
            useApp(app)
            changeReviewCount(reviews)
            changeReward(reward)
            // The balance is only ever the server's answer, read on the way in; given here instead.
            setState("tix", CircleSamples.BALANCE)
        }

    @Test
    fun f11_ask() {
        val vm = asking(s.focus, reviews = 5, reward = 30)
        frame.show { Pushed { AskScreen(onDone = {}, onBack = {}, app = s.focus, vm = vm) } }
        frame.scrollTo(text("Ask for feedback", clickable = true))
        frame.settle(600)
        frame.target("plus", compose.onNodeWithContentDescription("More reviews"))
        frame.target("rewardSlider", compose.onNodeWithContentDescription("Ticket reward per review"))
        frame.target("post", text("Ask for feedback", clickable = true))
        frame.write("ask")
    }

    @Test
    fun f12_askLive() {
        val vm = asking(s.liftMine, reviews = 3, reward = 30)
        frame.show { Pushed { AskScreen(onDone = {}, onBack = {}, app = s.liftMine, vm = vm) } }
        // Notice draws no semantics of its own: its box is its sentence plus its fixed padding,
        // across the page's width between the gutters.
        val words = compose.onNodeWithText("Testers rate and review your app", substring = true).screenBounds()
        val gutter = frame.px(Gutter.value)
        frame.target("liveNotice", Rect(gutter, words.top - frame.px(12f), Phone.WIDTH - gutter, words.bottom + frame.px(12f)))
        frame.write("askLive")
    }

    // ------------------------------------------------------------------ the owner's side

    @Test
    fun f13_check() {
        val review = s.weatherCheck(System.currentTimeMillis())
        frame.show { Pushed { ReviewCheck(review, proof = { LocalProof(shot("weather")) }) } }
        frame.target("accept", text("Accept and pay ${review.reward} tix", clickable = true))
        frame.target("refuse", text("Refuse", clickable = true))
        frame.write("check")
    }

    @Test
    fun f14_refuse() {
        val review = s.weatherCheck(System.currentTimeMillis())
        frame.show {
            Pushed {
                ReviewCheck(review, proof = { LocalProof(shot("weather")) })
                FeedRefuseDialog(appName = review.appName, onRefuse = {}, onDismiss = {})
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput(s.REFUSAL)
        frame.settle(800)
        frame.target("refuseIt", text("Refuse it", clickable = true))
        frame.write("refuse")
    }

    @Test
    fun f15_case() {
        frame.show { Pushed { CaseCheck(s.case, proof = { LocalProof(shot("lift")) }) } }
        frame.target("stands", text("The review stands", clickable = true))
        frame.target("devRight", text("The developer is right", clickable = true))
        frame.write("case")
    }

    @Test
    fun f16_request() {
        val request = s.focusRequest(System.currentTimeMillis())
        frame.show { Pushed { RequestCheck(request, proof = { LocalProof(shot("weather")) }) } }
        frame.target("close", text("Close and get ${request.refundable} tix back", clickable = true))
        frame.write("request")
    }
}
