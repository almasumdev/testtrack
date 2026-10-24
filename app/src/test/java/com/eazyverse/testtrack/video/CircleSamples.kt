package com.eazyverse.testtrack.video

import com.eazyverse.testtrack.domain.Case
import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.domain.RequestReview
import com.eazyverse.testtrack.domain.Waiting
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.ApiReview
import com.eazyverse.testtrack.ui.AppEntry

/**
 * The people and apps the Circle video is told with. Neutral names, plain numbers.
 *
 * Icons are addresses the test image loader answers from store/art (see [FrameImages]); no request
 * leaves the machine.
 */
internal object CircleSamples {
    const val BALANCE = 150
    const val LEVEL = 3
    const val INTO_LEVEL = 45
    const val FOR_NEXT = 120

    const val REVIEWER = "Nadia Karim"
    const val NOTE = "Loved the garden. The reminder fired twice today."
    const val REFUSAL = "The screenshot is of a different app's listing."

    private const val CREATED = "2026-10-20T09:00:00+00:00"

    private fun icon(name: String) = "https://icons.testtrack.invalid/$name.png"

    val weather =
        ApiPost(
            id = "post-weather",
            appName = "Pixel Weather",
            packageName = "com.example.pixelweather",
            icon = icon("icon_weather"),
            ask = "Is the hourly chart readable at a glance?",
            isLive = true,
            reward = 30,
            reviewsBought = 3,
            reviewsDone = 1,
            slotsOpen = 2,
            ownerName = "Zara Ahmed",
            mine = false,
            createdAt = CREATED,
        )

    val habit =
        ApiPost(
            id = "post-habit",
            appName = "Habit Garden",
            packageName = "com.example.habitgarden",
            icon = icon("icon_habit"),
            ask = "Does the garden make you want to come back tomorrow?",
            isLive = false,
            reward = 40,
            reviewsBought = 3,
            reviewsDone = 0,
            slotsOpen = 3,
            ownerName = "Lucas Moreau",
            mine = false,
            createdAt = CREATED,
        )

    val lift =
        ApiPost(
            id = "post-lift",
            appName = "Lift Log",
            packageName = "com.example.liftlog",
            icon = icon("icon_lift"),
            ask = "Is logging a set fast enough mid-workout?",
            isLive = true,
            reward = 50,
            reviewsBought = 2,
            reviewsDone = 1,
            slotsOpen = 1,
            ownerName = "Grace Liu",
            mine = false,
            createdAt = CREATED,
        )

    val feed = listOf(weather, habit, lift)

    /** Habit Garden's review at each step: claimed, installed, used, then sent. */
    fun habitReview(
        state: String,
        usedMs: Long = 0,
    ) = ApiReview(
        id = "review-habit",
        postId = habit.id,
        state = state,
        usedMs = usedMs,
        neededMs = 120_000,
        proofRef = if (state == "submitted") "drive:habit-proof" else "",
        written = if (state == "submitted") NOTE else "",
        reason = "",
        createdAt = CREATED,
        appName = habit.appName,
        packageName = habit.packageName,
        icon = habit.icon,
        isLive = false,
    )

    val focus =
        AppEntry(
            id = "app-focus",
            pkg = "com.example.focusflow",
            name = "Focus Flow",
            icon = icon("icon_focus"),
            developer = "You",
            live = false,
            verifiedBy = "play",
            canCrew = true,
            canFeedback = true,
        )

    /** Your own live app, for the page's live notice. */
    val liftMine =
        AppEntry(
            id = "app-lift",
            pkg = lift.packageName,
            name = lift.appName,
            icon = lift.icon,
            developer = "You",
            live = true,
            verifiedBy = "play",
            canFeedback = true,
        )

    /** A Pixel Weather review by Nadia, waiting on its owner. */
    fun weatherCheck(now: Long) =
        Waiting(
            id = "check-weather",
            appName = weather.appName,
            written = "The hourly chart reads well. The rain line is hard to see in dark mode.",
            proofRef = "drive:weather-proof",
            reviewerName = REVIEWER,
            reward = weather.reward,
            isLive = true,
            decideBy = now + 41 * HOUR,
            packageName = weather.packageName,
            icon = weather.icon,
        )

    val case =
        Case(
            id = "case-lift",
            appName = lift.appName,
            reason = "The review is about a different workout app, not Lift Log.",
            written = "Logged three sets. The timer between sets is great.",
            proofRef = "drive:lift-proof",
            stake = 15,
            votesCast = 1,
            votesNeeded = 3,
            packageName = lift.packageName,
            reward = lift.reward,
            isLive = true,
            icon = lift.icon,
        )

    fun focusRequest(now: Long) =
        Request(
            id = "request-focus",
            appName = focus.name,
            packageName = focus.pkg,
            isLive = false,
            reward = 30,
            bought = 5,
            done = 1,
            open = 2,
            refundable = 60,
            icon = focus.icon,
            reviews =
                listOf(
                    RequestReview(id = "rr-1", state = "approved", reviewerName = "Nadia Karim", written = "Clear and calm."),
                    RequestReview(id = "rr-2", state = "submitted", reviewerName = "Omar Haddad", decideBy = now + 30 * HOUR),
                    RequestReview(id = "rr-3", state = "installed", reviewerName = "Mei Tanaka"),
                ),
        )

    private const val HOUR = 3_600_000L
}
