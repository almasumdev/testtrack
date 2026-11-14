package com.eazyverse.testtrack.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-port-mdpi", application = Application::class)
class MainBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun add_is_an_unlabelled_action_and_does_not_switch_tabs() {
        var adds = 0
        var selected: MainTab? = null
        compose.setContent {
            TestTrackTheme {
                MainBar(MainTab.APPS, { selected = it }, onAdd = { adds++ })
            }
        }
        listOf("Circle", "Crew", "My apps", "You").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Ranks").assertDoesNotExist()
        compose.onNodeWithText("Add").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add").performClick()
        assertEquals(1, adds)
        assertEquals(null, selected)
        compose.onNodeWithContentDescription("My apps").assertIsSelected()
        compose.onNodeWithText("Crew").performClick()
        assertEquals(MainTab.GROUPS, selected)
    }

    @Test
    fun ranks_marks_you_and_you_still_returns_to_the_profile() {
        var selected: MainTab? = null
        compose.setContent {
            TestTrackTheme { MainBar(MainTab.RANKS, { selected = it }) }
        }
        compose.onNodeWithContentDescription("You").assertIsSelected().performClick()
        assertEquals(MainTab.PROFILE, selected)
    }

    @Test
    fun narrow_bar_with_large_text_keeps_names_and_accessible_counts() {
        compose.setContent {
            TestTrackTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) {
                    Box(Modifier.width(320.dp)) {
                        MainBar(MainTab.CIRCLE, {}, counts = mapOf(MainTab.GROUPS to 123))
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Crew, 123 to do").assertIsDisplayed()
        compose.onNodeWithText("99+").assertIsDisplayed()
        listOf("Circle", "Crew", "My apps", "You").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        val add = compose.onNodeWithContentDescription("Add").fetchSemanticsNode().boundsInRoot
        assertTrue(add.width >= 48f)
        assertTrue(add.height >= 48f)
    }
}
