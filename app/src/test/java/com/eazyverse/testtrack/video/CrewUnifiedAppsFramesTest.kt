package com.eazyverse.testtrack.video

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.*
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import com.eazyverse.testtrack.ui.theme.Tt
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(VideoFramesRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Phone.SDK], qualifiers = Phone.QUALIFIERS, application = Application::class)
class CrewUnifiedAppsFramesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val frame = Frame(compose, "crew")
    private val did = mutableListOf<String>()
    private val listings =
        listOf(
            "Auto Silent: Volume Scheduler" to "com.eazyverse.soundscheduler",
            "Astra Match" to "com.shahadat.astramatch",
            "Baka Run" to "com.markuljdev.bakarun",
            "Bluefy: BT Keyboard & Mouse" to "com.eazyverse.bluefy",
            "Cherish – Remember People" to "app.cherish.people",
            "EditMe: Photo Editor & Presets" to "com.arisoftware.editme",
            "Gaan Music Player" to "com.arisoftware.gaan",
        )
    private val apps =
        listings.mapIndexed { i, (name, pkg) ->
            CrewApp(
                "app$i",
                name,
                pkg,
                "Tester $i",
                day = 5,
                feedbackDueDay = 7,
                done = i == 1,
                reported = i == 2,
                feedbackSent = i == 1 || i == 4,
                versionCode = if (i == 4) 12 else null,
                updateRequired = i == 4,
                feedbackLate = i == 6,
            )
        }
    private val seats =
        apps.map { app ->
            CrewSeat(
                app.id,
                app.label,
                app.owner,
                app.packageName,
                icon = "https://icons.testtrack.invalid/circle_actual_${app.packageName.replace('.', '_')}.png",
                reported = app.reported,
            )
        }
    private val owner = CrewSeat("mine", "Metro Map", "You", "dev.seedapps.readyfb", mine = true)
    private val crew = Crew("c", "Crew 58", CrewPhase.RUN, apps = apps, seats = seats + owner, day = 5)

    @Before fun onlyWhenAsked() = assumeTrue("set -PvideoFrames", videoFramesOn)

    private fun actions() =
        CrewActions(
            onOpen = { did += "open ${it.id}" },
            onOpenAll = {},
            onInstall = { did += "install ${it.id}" },
            onReport = { did += "report ${it.id}" },
            onLeave = {},
            onTakeSeat = {},
            onFeedback = { did += "feedback ${it.id}" },
            onPickFeedback = { did += "pick ${it.id}" },
            onUpdateApp = { did += "update ${it.id}" },
            isInstalled = { it != apps[3].packageName },
            localVersion = { 1L },
            sendingPicture = { it.id == "app5" },
            picturesStuck = "Offline",
            onRetryPictures = { did += "retry" },
            onWithdrawReport = { did += "withdraw ${it.id}" },
        )

    private fun control(
        id: String,
        text: String,
    ) = compose.onNode(
        hasAnyAncestor(hasTestTag("crew-run-app-$id")) and hasText(text) and hasClickAction(),
    )

    @Test fun unifiedPageKeepsHeaderAndOwner() {
        frame.show { Pushed { CrewRunPhase(crew, actions()) } }
        frame.settle(800)
        compose.onNodeWithText("Day 5 of 14").assertExists()
        compose.onNodeWithText("Private feedback on Play").assertDoesNotExist()
        frame.write("unifiedDetailTop")
        compose.mainClock.autoAdvance = true
        val list = compose.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasText("Your app"))
        compose.onNodeWithText("Your app").assertExists()
        list.performScrollToNode(hasText("Tell your crew"))
        compose.onNodeWithText("Update").assertExists()
        compose.onNodeWithText("Down tonight").assertExists()
        list.performScrollToNode(hasText("Apps"))
        frame.settle(800)
        frame.write("unifiedDetailApps")
        compose.onNodeWithText("Tonight").assertDoesNotExist()
        compose.onNodeWithText("Done").assertDoesNotExist()
        apps.forEach { app ->
            list.performScrollToNode(hasTestTag("crew-run-app-${app.id}"))
            assertEquals(1, compose.onAllNodesWithTag("crew-run-app-${app.id}").fetchSemanticsNodes().size)
        }
    }

    @Test fun appActionsAndFeedbackBadge() {
        val actions = actions()
        frame.show {
            Pushed {
                TestTrackTheme(darkTheme = true) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(Tt.colors.bg)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        apps.forEachIndexed { i, app -> CrewRunAppTile(app, seats[i], actions, {}) }
                    }
                }
            }
        }
        frame.settle(800)
        frame.write("unifiedAppStates")
        compose.mainClock.autoAdvance = true
        control("app0", "Open").performScrollTo().performClick()
        control("app0", "Report").performScrollTo().performClick()
        control("app0", "Write").performScrollTo().performClick()
        control("app2", "Withdraw").performScrollTo().performClick()
        control("app3", "Install").performScrollTo().performClick()
        control("app4", "Update").performScrollTo().performClick()
        control("app5", "Retry").performScrollTo().performClick()
        control("app6", "Write").performScrollTo().performClick()
        assertEquals(
            listOf(
                "open app0",
                "report app0",
                "feedback app0",
                "withdraw app2",
                "install app3",
                "update app4",
                "retry",
                "feedback app6",
            ),
            did,
        )
        control("app1", "Write").assertDoesNotExist()
        compose.onNodeWithContentDescription("Feedback sent for ${apps[1].label}", useUnmergedTree = true).assertDoesNotExist()
        control("app2", "Reported").assertDoesNotExist()
        val iconSize = with(compose.density) { 44.dp.toPx() }
        apps.forEach { app ->
            compose.onNodeWithTag("crew-run-app-${app.id}").performScrollTo()
            val icon = compose.onNodeWithTag("crew-app-icon-${app.id}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val text = compose.onNodeWithTag("crew-app-text-${app.id}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("Fixed icon height for ${app.id}", iconSize, icon.height, 1f)
            assertEquals(text.center.y, icon.center.y, 1f)
        }
        compose.onNodeWithContentDescription("About Feedback overdue").performScrollTo().performClick()
        frame.settle(300)
        compose.onNodeWithText("Give private feedback on Google Play before testing can count.").assertExists()
        frame.write("unifiedFeedbackCallout")
    }

    @Test fun missingScreenshotOffersCaptureAndSavedPicture() {
        val app = apps[0].copy(pictureDue = true)
        var picked = false
        val actions = CrewActions({}, {}, {}, {}, {}, {}, onPickPicture = { picked = true })
        frame.show { Pushed { Column(Modifier.padding(16.dp)) { CrewRunAppTile(app, seats[0], actions, {}) } } }
        frame.settle(800)
        compose.mainClock.autoAdvance = true
        control(app.id, "Add screenshot").performClick()
        compose.onNodeWithText("Your time is saved. Add one so this test counts.").assertExists()
        compose.onNodeWithText("Retake").assertExists()
        compose.onNodeWithText("Use saved").performClick()
        org.junit.Assert.assertTrue(picked)
        compose.onNodeWithText("Add a screenshot").assertDoesNotExist()
    }

    @Test fun largeTextKeepsIconFixed() {
        val app = apps[0]
        frame.show {
            Pushed {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) { CrewRunAppTile(app, seats[0], actions(), {}) }
                }
            }
        }
        frame.settle(800)
        frame.write("unifiedAppLargeText")
        val icon = compose.onNodeWithTag("crew-app-icon-${app.id}", true).fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithTag("crew-app-text-${app.id}", true).fetchSemanticsNode().boundsInRoot
        assertEquals(with(compose.density) { 44.dp.toPx() }, icon.height, 1f)
        org.junit.Assert.assertTrue("Text grows for accessibility without enlarging the icon", text.height > icon.height)
    }
}
