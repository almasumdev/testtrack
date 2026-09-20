package com.eazyverse.testtrack.net

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eazyverse.testtrack.data.Device
import com.eazyverse.testtrack.ui.ReviewBody
import com.eazyverse.testtrack.ui.ReviewJob
import com.eazyverse.testtrack.ui.ReviewStep
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * The whole job, from this phone, against the real server.
 *
 * Every other test in the app stops at a boundary. The unit tests stop at what the app computes,
 * and ReviewBodyTest stops at what it draws from values this repository wrote by hand. Neither
 * would notice the server renaming a field, changing when it refuses something, or answering a
 * shape the models cannot read. This runs the real Api object, over the network, and then draws
 * the real screen from what actually came back.
 *
 * **Signing in.** The app signs in through Google, which a test cannot do. So the session is
 * minted out of band by scripts/seed_e2e.py in the backend repository, which also creates the
 * people and posts this exercises, and the tokens arrive as instrumentation arguments. Nothing
 * about that path exists in the app or in the API: there is no test mode to leave switched on.
 * Without those arguments every test here skips rather than fails, because a red suite that only
 * means "you did not seed" teaches people to ignore red suites.
 *
 *     scripts/e2e.sh          (app repo)  seeds, runs this, then removes what it seeded
 *
 * **What it deliberately does not do.** The install and usage steps are reports about the device,
 * and on an emulator the seeded package is not installed and has no foreground time. Faking that
 * would mean a switch in ReviewViewModel that says "pretend", which is the one thing that must
 * never exist in a build somebody can install. So this drives those two reports through the API
 * directly, exactly as the view model does, and checks the server's answer. What is proved is the
 * contract and the rules; what is not proved is UsageStatsManager, which has its own test.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ReviewJourneyTest {
    @get:Rule
    val compose = createComposeRule()

    private val args = InstrumentationRegistry.getArguments()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val reviewerAccess = args.getString("reviewerAccess").orEmpty()
    private val reviewerRefresh = args.getString("reviewerRefresh").orEmpty()
    private val ownerAccess = args.getString("ownerAccess").orEmpty()
    private val ownerRefresh = args.getString("ownerRefresh").orEmpty()
    private val livePostId = args.getString("livePostId").orEmpty()
    private val closedPostId = args.getString("closedPostId").orEmpty()

    @Before
    fun requireASeededWorld() {
        assumeTrue(
            "No seeded session. Run scripts/e2e.sh rather than this class on its own.",
            reviewerAccess.isNotEmpty() && livePostId.isNotEmpty(),
        )
        ApiSession.load(context)
        beReviewer()
    }

    private fun beReviewer() = ApiSession.remember(Tokens(reviewerAccess, reviewerRefresh, 900))

    private fun beOwner() = ApiSession.remember(Tokens(ownerAccess, ownerRefresh, 900))

    private fun fingerprint() = requireNotNull(Device.fingerprint(context)) { "no ANDROID_ID" }

    /**
     * A real PNG, built here rather than shipped as a test asset.
     *
     * The server checks the type it was told and stores the bytes it was given, so the bytes
     * ought to be an image: a test that uploads the word "proof" proves only that nothing looks.
     */
    private fun onePixelPng(): ByteArray {
        fun crc(bytes: ByteArray): Int {
            var c = 0xFFFFFFFFu
            for (b in bytes) {
                c = c xor (b.toUInt() and 0xFFu)
                repeat(8) { c = if (c and 1u != 0u) (c shr 1) xor 0xEDB88320u else c shr 1 }
            }
            return (c xor 0xFFFFFFFFu).toInt()
        }

        fun chunk(
            type: String,
            data: ByteArray,
        ): ByteArray {
            val body = type.toByteArray() + data
            return java.nio.ByteBuffer
                .allocate(12 + data.size)
                .putInt(data.size)
                .put(body)
                .putInt(crc(body))
                .array()
        }

        val header = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        val ihdr =
            java.nio.ByteBuffer
                .allocate(13)
                .putInt(1)
                .putInt(1)
                .put(8)
                .put(2)
                .put(0)
                .put(0)
                .put(0)
                .array()
        val pixel =
            java.util.zip.Deflater().let { d ->
                d.setInput(byteArrayOf(0, -1, -1, -1))
                d.finish()
                val out = ByteArray(64)
                val n = d.deflate(out)
                out.copyOf(n)
            }
        return header + chunk("IHDR", ihdr) + chunk("IDAT", pixel) + chunk("IEND", ByteArray(0))
    }

    // ------------------------------------------------------------------ the journey

    /**
     * Claim, install, use, submit, approve, and the tix landing at the end.
     *
     * One test rather than six because they are one sequence: each step needs the state the last
     * one left, and six tests sharing a mutable world on a live server is how a suite becomes
     * order-dependent without admitting it. The name pins it first; the rest only read.
     */
    @Test
    fun t1_a_live_review_runs_all_the_way_to_being_paid() =
        runBlocking {
            val before = Api.me().tix

            val claimed = Api.claim(livePostId, fingerprint())
            assertEquals("claimed", claimed.state)
            assertEquals(30_000L, claimed.neededMs)
            assertEquals(0L, claimed.usedMs)

            val installed = Api.installed(claimed.id)
            assertEquals("installed", installed.state)

            // Short of the thirty seconds. The server has to hold the line here or the whole
            // measurement is decoration.
            val partly = Api.used(claimed.id, 11_000)
            assertEquals("installed", partly.state)
            assertEquals(11_000L, partly.usedMs)
            try {
                Api.submit(claimed.id, proofRef = "shot-too-early")
                fail("Submitting eleven seconds in should have been refused.")
            } catch (e: ApiError) {
                assertEquals(409, e.status)
            }

            val used = Api.used(claimed.id, 31_000)
            assertEquals("used", used.state)

            // A live post wants the screenshot, and says so rather than accepting an empty one.
            try {
                Api.submit(claimed.id, proofRef = "")
                fail("A live post should not accept a submission with no proof.")
            } catch (e: ApiError) {
                assertTrue("Wrong refusal: ${e.message}", e.status in 400..422)
            }

            // The proof is a real file this time, sent the way the app sends it. Until there was
            // an upload endpoint the dialog asked for a screenshot with nowhere to put one, so a
            // live review could be worked all the way through and never submitted.
            val shot = Api.upload(onePixelPng(), "image/png")
            assertTrue("An upload answers with what it stored", shot.size > 0)

            val submitted = Api.submit(claimed.id, proofRef = shot.id)
            assertEquals("submitted", submitted.state)
            assertNotNull(submitted.submittedAt)

            // Nothing is paid while it is still with the developer.
            assertEquals(before, Api.me().tix)

            beOwner()
            val waiting = Api.waiting()
            assertTrue(
                "The owner cannot see the review they are supposed to judge.",
                waiting.any { it.id == claimed.id },
            )
            assertEquals("approved", Api.approve(claimed.id).state)

            beReviewer()
            assertEquals("The reward did not land.", before + 40, Api.me().tix)
        }

    /** The same package, same device, twice. The rule the whole economy rests on. */
    @Test
    fun t2_the_same_device_cannot_review_one_app_twice() =
        runBlocking {
            try {
                Api.claim(livePostId, fingerprint())
                fail("A second claim on the same post from the same device should be refused.")
            } catch (e: ApiError) {
                assertTrue("Wrong refusal: ${e.status} ${e.message}", e.status in 400..409)
            }
        }

    /** A closed test has no listing, so it wants words and refuses a screenshot on its own. */
    @Test
    fun t3_a_closed_test_is_submitted_with_words_not_a_screenshot() =
        runBlocking {
            val claimed = Api.claim(closedPostId, fingerprint())
            Api.installed(claimed.id)
            Api.used(claimed.id, 31_000)

            try {
                Api.submit(claimed.id, proofRef = "a-screenshot-of-nothing")
                fail("A closed test has no Play review to screenshot, so this is not a review.")
            } catch (e: ApiError) {
                assertTrue("Wrong refusal: ${e.message}", e.status in 400..422)
            }

            val words = "The stop search found my stop first try, but the keyboard covered the list."
            assertEquals("submitted", Api.submit(claimed.id, written = words).state)
        }

    // ------------------------------------------------------------------ the screen, on real data

    /**
     * The stepper, drawn from what the server actually sent.
     *
     * ReviewBodyTest asks the same questions of an ApiReview this repository made up. This asks
     * them of one the server made, which is the only version that can go wrong in production. If
     * a field is renamed or a state is spelled differently, the model deserialises to something
     * the stepper does not recognise and this is where it shows.
     */
    @Test
    fun t4_the_screen_reads_a_real_server_review_at_every_step() =
        runBlocking {
            val posts = Api.posts().posts
            val post =
                posts.firstOrNull { it.id == livePostId }
                    ?: error("The seeded post is not in the feed the app fetches.")

            assertEquals("Nikosh Budget", post.appName)
            assertEquals(40, post.reward)
            assertTrue(post.isLive)
            assertTrue("A reviewed post should come back marked as mine to review.", !post.mine)

            val mine =
                Api.myReviews().firstOrNull { it.postId == livePostId }
                    ?: error("The review just approved is missing from /reviews/mine.")

            // Approved, so the screen should be settled and offering nothing to press.
            val job = ReviewJob(post, mine)
            assertEquals(ReviewStep.WAIT, job.step)
            assertTrue(job.settled)

            compose.setContent {
                TestTrackTheme {
                    ReviewBody(
                        post = post,
                        job = job,
                        sideloaded = false,
                        snackbars = SnackbarHostState(),
                        onClose = {},
                        onLeave = {},
                        onSend = { _, _ -> },
                    )
                }
            }

            compose.onNodeWithText("Sent").assertIsDisplayed()
            compose.onNodeWithText("Approved. The tix are yours.").assertIsDisplayed()
            compose.onNodeWithText("Get it on Play").assertDoesNotExist()
            compose.onNodeWithText("Send the proof").assertDoesNotExist()
        }
}
