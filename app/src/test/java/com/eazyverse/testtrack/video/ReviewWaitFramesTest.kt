package com.eazyverse.testtrack.video

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.FeedRequestCard
import com.eazyverse.testtrack.ui.FeedRequestLoadingCard
import com.eazyverse.testtrack.ui.Gutter
import com.eazyverse.testtrack.ui.ReviewBody
import com.eazyverse.testtrack.ui.ReviewJob
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * A live app's review waiting for its second day of use, from the app's own composables: the day
 * it was taken, with the time it opens, and the next day with only hours left. Written to
 * media/public/frames/review-wait, opt-in like the other frames.
 */
@RunWith(VideoFramesRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Phone.SDK], qualifiers = Phone.QUALIFIERS, application = Application::class)
class ReviewWaitFramesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val frame = Frame(compose, "review-wait")

    @Before
    fun onlyWhenAsked() = assumeTrue("set -PvideoFrames to draw the video frames", videoFramesOn)

    private val live = CircleSamples.habit.copy(isLive = true)

    private fun show(
        name: String,
        takenHoursAgo: Long,
        daysUsed: Int,
    ) {
        val now = Instant.now()
        val taken = now.minusSeconds(takenHoursAgo * 3600)
        val review =
            CircleSamples.habitReview("installed", usedMs = 300_000).copy(
                neededMs = 300_000,
                isLive = true,
                createdAt = taken.toString(),
                firstUsedAt = taken.plusSeconds(300).toString(),
                returnsAt = (if (daysUsed >= 2) taken.plusSeconds(20 * 3600) else taken.plusSeconds(300 + 20 * 3600)).toString(),
                daysUsed = daysUsed,
            )
        frame.show {
            Pushed {
                ReviewBody(
                    post = live,
                    job = ReviewJob(live, review),
                    sideloaded = false,
                    usageAccess = true,
                    onClose = {},
                    onLeave = {},
                    onSend = { _, _ -> },
                    onGiveUp = {},
                )
            }
        }
        frame.settle(700)
        frame.write(name)
    }

    @Test
    fun w1_today() = show("today", takenHoursAgo = 0, daysUsed = 1)

    @Test
    fun w2_next_day() = show("nextDay", takenHoursAgo = 17, daysUsed = 2)

    /** The Feedback list while it loads, shaped as the request cards that land. */
    @Test
    fun w3_feed_loading() {
        frame.show {
            Pushed {
                Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) { FeedRequestLoadingCard() }
                }
            }
        }
        frame.settle(700)
        frame.write("feedLoading")
    }

    /** The request cards' tickets over a few seconds, to see the light sweep across them. */
    @Test
    fun w4_ticket_shine() {
        frame.show {
            Pushed {
                Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircleSamples.feed.forEach { FeedRequestCard(it) {} }
                }
            }
        }
        repeat(14) { frame.write("shine${it.toString().padStart(2, '0')}") }
    }
}
