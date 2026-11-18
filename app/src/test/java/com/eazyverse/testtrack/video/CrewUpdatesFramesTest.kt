package com.eazyverse.testtrack.video

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
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
class CrewUpdatesFramesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val frame = Frame(compose, "crew")
    private val dark = mutableStateOf(true)
    private val did = mutableListOf<String>()
    private val app = CrewApp("app", "Auto Silent", "demo.app", "Tester", day = 8, feedbackDueDay = 7)
    private val crew =
        Crew(
            "crew",
            "Crew 66",
            CrewPhase.RUN,
            day = 8,
            apps = listOf(app),
            seats = listOf(CrewSeat(app.id, app.label, app.owner, app.packageName)),
        )

    @Before fun onlyWhenAsked() = assumeTrue("set -PvideoFrames", videoFramesOn)

    private fun actions() =
        CrewActions(
            onOpen = { did += "open ${it.id}" },
            onOpenAll = {},
            onInstall = { did += "install ${it.id}" },
            onReport = {},
            onLeave = {},
            onTakeSeat = {},
            onPickPicture = { did += "pick ${it.id}" },
            onFeedback = { did += "feedback ${it.id}" },
            onUpdateApp = { did += "update ${it.id}" },
            localVersion = { 1L },
        )

    private fun show(
        runs: List<Crew>,
        fontScale: Float = 1f,
        actionFactory: @Composable () -> CrewActions = { actions() },
    ) {
        frame.show {
            Pushed {
                TestTrackTheme(darkTheme = dark.value) {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .background(Tt.colors.bg)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 12.dp),
                        ) { CrewRunUpdates(runs, tonightIn(runs), actionFactory()) { did += "crew $it" } }
                    }
                }
            }
        }
        frame.settle(800)
        compose.mainClock.autoAdvance = true
    }

    @Test fun missedNightAndInstallRowsNavigateInBothThemes() {
        val risk = crew.copy(members = listOf(CrewMember("mine", "You", "Mine", mine = true, missedYesterday = true)))
        show(listOf(risk)) {
            CrewActions({}, {}, { did += "install ${it.id}" }, {}, {}, {}, isInstalled = { false })
        }
        compose.onNodeWithText("Missed yesterday").assertExists()
        compose.onNodeWithText("Crew 66 · 1 app left · Before midnight").assertExists()
        compose.onNodeWithText("View crew").performClick()
        assertEquals(listOf("crew crew"), did)
        frame.write("compactUpdatesRiskDark")
        compose.runOnIdle { dark.value = false }
        frame.settle(300)
        frame.write("compactUpdatesRiskLight")
        compose.onNodeWithText("View").performClick()
        frame.settle(500)
        compose.onNode(hasText("Install") and hasClickAction()).performClick()
        frame.settle(500)
        assertEquals(listOf("crew crew", "install app"), did)
    }

    @Test fun addScreenshotKeepsBothRecoveryPaths() {
        show(listOf(crew.copy(apps = listOf(app.copy(pictureDue = true)))))
        compose.onNodeWithText("Screenshot missing").assertExists()
        compose.onNodeWithText("Auto Silent · Test time saved").assertExists()
        frame.write("compactUpdatesMissing")
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Retake screenshot").performClick()
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Use saved app screenshot").performClick()
        assertEquals(listOf("open app", "pick app"), did)
    }

    @Test fun uploadStateAndRetryFollowThePhoneQueue() {
        val failed = mutableStateOf(false)
        val late = mutableStateOf(false)
        show(listOf(crew)) {
            CrewActions(
                {},
                {},
                {},
                {},
                {},
                {},
                sendingPicture = { true },
                picturesStuck = if (failed.value) "Offline" else null,
                latePicture = { late.value },
                onRetryPictures = { did += "retry" },
            )
        }
        compose.onNodeWithText("Uploading 1 screenshot").assertExists()
        compose.onNodeWithText("Retry").assertDoesNotExist()
        frame.write("compactUpdatesUploading")
        compose.runOnIdle { failed.value = true }
        compose.onNodeWithText("1 upload failed").assertExists()
        compose.onNodeWithText("Retry").performClick()
        frame.write("compactUpdatesFailed")
        compose.runOnIdle { late.value = true }
        compose.onNodeWithText("Upload waiting · 3h+").assertExists()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf("retry", "retry"), did)
        compose.onNodeWithContentDescription("About Upload waiting · 3h+").performClick()
        frame.settle(300)
        compose.onNodeWithText("These screenshots have waited over 3 hours.", substring = true).assertExists()
        frame.write("compactUpdatesLateInfo")
    }

    @Test fun mixedAttentionListsAppsAndDispatchesEachAction() {
        val feedback = app.copy(id = "feedback", feedbackLate = true)
        val old = app.copy(id = "old", label = "Bluefy", packageName = "demo.bluefy", versionCode = 2, updateRequired = true)
        show(listOf(crew.copy(apps = listOf(feedback, old))))
        compose.onNodeWithText("2 apps need attention").assertExists()
        frame.write("compactUpdatesMixed")
        compose.onNodeWithText("View").performClick()
        frame.settle(500)
        compose.onNodeWithText("Apps needing attention").assertExists()
        compose.onNode(hasText("Write") and hasClickAction()).performClick()
        frame.settle(500)
        compose.onNodeWithText("View").performClick()
        frame.settle(500)
        compose.onNode(hasText("Update") and hasClickAction()).performClick()
        frame.settle(500)
        assertEquals(listOf("feedback feedback", "update old"), did)
    }

    @Test fun largeTextAndOptionalProofRemainAccessible() {
        show(listOf(crew.copy(apps = emptyList(), optional = listOf(app.copy(pictureDue = true)))), fontScale = 2f)
        compose.onNodeWithText("Screenshot missing").assertExists()
        compose.onNodeWithText("Add").performScrollTo().assertIsDisplayed()
        frame.write("compactUpdatesLargeText")
    }
}
