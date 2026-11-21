package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import com.eazyverse.testtrack.viewmodel.AppNights
import com.eazyverse.testtrack.viewmodel.MemberNight
import com.eazyverse.testtrack.viewmodel.MemberNights
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The run's new cards and rows on a real screen: what each says, and that each button reaches the
 * action it promises. The actions are recorded rather than run, since what they send is the
 * server's to judge and is tested there.
 */
@RunWith(AndroidJUnit4::class)
class CrewRunCardsTest {
    @get:Rule
    val compose = createComposeRule()

    private val did = mutableListOf<String>()

    private fun actions(
        sending: Boolean = false,
        stuck: String? = null,
        blocked: String? = null,
        localVersion: Long? = null,
    ) = CrewActions(
        onOpen = { did += "open ${it.label}" },
        onOpenAll = {},
        onInstall = { did += "install ${it.appName}" },
        onReport = {},
        onLeave = {},
        onTakeSeat = {},
        onPickPicture = { did += "pick picture ${it.label}" },
        sendingPicture = { sending },
        picturesStuck = stuck,
        onRetryPictures = { did += "retry" },
        localVersion = { localVersion },
        onUpdateApp = { did += "update ${it.label}" },
        onFeedback = { did += "feedback ${it.label}" },
        onPickFeedback = { did += "pick feedback ${it.label}" },
        onTellUpdate = { did += "tell ${it.appName}" },
        onMarkDown = { did += "down ${it.appName}" },
        updateBlocked = { blocked },
    )

    private val steps = CrewApp(id = "s1", label = "Steps Daily", packageName = "com.example.steps", owner = "Kenji", pictureDue = true)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) =
        compose.setContent { TestTrackTheme { Column { content() } } }

    @Test
    fun aMissingPictureOffersBothWaysToFixIt() {
        show { PicturesDueCard(listOf(steps), actions()) }
        compose.onNodeWithText("Steps Daily needs a picture").assertIsDisplayed()
        compose.onNode(hasText("Open it again") and hasClickAction()).performClick()
        compose.onNode(hasText("Pick a picture") and hasClickAction()).performClick()
        assertEquals(listOf("open Steps Daily", "pick picture Steps Daily"), did)
    }

    @Test
    fun aPictureStuckSendingSaysWhyAndTriesAgain() {
        show { PicturesDueCard(listOf(steps), actions(sending = true, stuck = "We could not reach TestTrack.")) }
        compose.onNodeWithText("Sending 1 picture").assertIsDisplayed()
        compose.onNodeWithText("We could not reach TestTrack.").assertIsDisplayed()
        compose.onNode(hasText("Try again") and hasClickAction()).performClick()
        assertEquals(listOf("retry"), did)
    }

    @Test
    fun theOwnersUpdateSaysWhyWhenItCannotGoYet() {
        val mine =
            CrewSeat(
                id = "m",
                appName = "Focus Flow",
                owner = "You",
                packageName = "com.example.focus",
                mine = true,
                versionCode = 41,
                versionName = "1.4.0",
                downsLeft = 0,
            )
        show { OwnerToolsCard(mine, actions(blocked = "Install the new version from Google Play on this phone first.")) }
        compose
            .onNodeWithText(
                "They have version 1.4.0. After you tap Update they get 24 hours to install the new one.",
            ).assertIsDisplayed()
        compose.onNodeWithText("Install the new version from Google Play on this phone to tell them.").assertIsDisplayed()
        compose.onNode(hasText("Update") and hasClickAction()).performClick()
        compose.onNode(hasText("Down tonight") and hasClickAction()).assertIsNotEnabled()
        assertEquals(listOf("tell Focus Flow"), did)
    }

    @Test
    fun theTrackingGridNamesTheNightsShortOfGoogles12() {
        val testers =
            (0 until 12).map { i ->
                MemberNights(
                    seatId = "t$i",
                    name = "Tester $i",
                    day = 4,
                    out = false,
                    // Everybody on nights 1 and 3; night 2 is missed by one, so it is short of 12.
                    nights =
                        (1..3)
                            .filter { d ->
                                !(d == 2 && i == 0)
                            }.map { d -> MemberNight(d, "", 11_000, "picture-$i-$d", false, true) },
                )
            }
        show { TrackingGrid(AppNights("Focus Flow", 14, testers)) }
        compose.onNodeWithText("Short on night 2").assertIsDisplayed()
    }
}
