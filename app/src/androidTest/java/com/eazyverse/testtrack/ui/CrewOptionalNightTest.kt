package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The night an app comes back to its run, on a real screen. Nobody owes it and anybody may open it,
 * so it sits in its own list, stays out of what is left tonight and out of Open all, and its own row
 * still opens it. What an open then counts for is the server's to judge and is tested there.
 */
@RunWith(AndroidJUnit4::class)
class CrewOptionalNightTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private var openedAll = 0

    private val actions =
        CrewActions(
            onOpen = { opened += it.label },
            onOpenAll = { openedAll++ },
            onInstall = {},
            onReport = {},
            onLeave = {},
            onTakeSeat = {},
        )

    private fun app(
        id: String,
        label: String,
        done: Boolean = false,
    ) = CrewApp(id = id, label = label, packageName = "com.example.$id", owner = "Kenji", crew = "Crew 05", done = done)

    private fun seat(
        id: String,
        label: String,
    ) = CrewSeat(id = id, appName = label, owner = "Kenji", packageName = "com.example.$id", state = SeatState.CONFIRMED)

    private fun crew(
        apps: List<CrewApp>,
        optional: List<CrewApp>,
    ) = Crew(
        id = "c1",
        name = "Crew 05",
        phase = CrewPhase.RUN,
        seats = (apps + optional).map { seat(it.id, it.label) },
        apps = apps,
        optional = optional,
        day = 6,
    )

    private fun show(crew: Crew) =
        compose.setContent {
            TestTrackTheme { Box(Modifier.fillMaxSize()) { CrewRunPhase(crew, actions) } }
        }

    private fun reach(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val picture = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir, "$name.png").outputStream().use {
            picture.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun theMemberPutBackOwesNothingAndCanStillOpenAnything() {
        show(crew(emptyList(), listOf(app("a", "Steps Daily"), app("b", "Lift Log", done = true))))

        compose.onNodeWithText("Tonight is optional").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasText("Open all", substring = true)).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasText("left tonight", substring = true)).fetchSemanticsNodes().isEmpty())
        save("optional-owner-top")

        reach("Optional tonight")
        compose.onNodeWithText("You don't have to open these tonight. If you do, it counts and shows.").assertIsDisplayed()
        reach("Steps Daily")
        compose.onNode(hasText("Open") and hasClickAction()).performClick()
        assertEquals(listOf("Steps Daily"), opened)
        reach("Lift Log")
        compose.onNodeWithText("Opened").assertIsDisplayed()
        save("optional-owner-list")
    }

    @Test
    fun aTesterStillOwesTheRestAndOpenAllLeavesTheOptionalAppOut() {
        show(crew(listOf(app("a", "Steps Daily")), listOf(app("back", "Came Back"))))

        compose.onNodeWithText("1 left tonight").assertIsDisplayed()
        compose.onNode(hasText("Open Steps Daily") and hasClickAction()).performClick()
        assertEquals(1, openedAll)
        save("optional-tester-top")

        reach("Optional tonight")
        reach("Came Back")
        compose.onNodeWithText("Came Back").assertIsDisplayed()
        save("optional-tester-list")
    }

    @Test
    fun theRulesSayWhatAMissIsAndWhatTheRefundAsks() {
        compose.setContent {
            TestTrackTheme {
                androidx.compose.foundation.lazy
                    .LazyColumn { item { CrewRulesCard() } }
            }
        }

        reach("If any app stays unopened on any night")
        compose.onNodeWithText("If any app stays unopened on any night").assertIsDisplayed()
        save("rules-refund")
        compose.onNode(hasText("Or if you open every app on all ${CrewRules.RUN_DAYS} days")).assertExists()
        val miss = "A day is missed when ${CrewRules.MISS_UNOPENED} or more apps stay unopened, or you open none."
        reach(miss)
        compose.onNodeWithText(miss).assertIsDisplayed()
        save("rules-miss")
    }
}
