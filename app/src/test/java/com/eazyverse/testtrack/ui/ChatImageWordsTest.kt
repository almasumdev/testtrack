package com.eazyverse.testtrack.ui

import android.app.Application
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The doc maths uses android.graphics.Matrix, so this runs under Robolectric.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChatImageWordsTest {
    private val red = Color(0xFFFF0000)
    private val blue = Color(0xFF0000FF)
    private val blank = PictureDoc(400, 300)

    @Test
    fun typingShowsOnlyTheWordsBarsAndNeverTheToolsOrSend() {
        EditMode.entries.forEach { mode ->
            assertEquals(EditorBars(TopBar.WORDS, BottomBar.NONE), barsFor(true, mode))
        }
    }

    @Test
    fun notTypingShowsTheToolsAndTheBarOfTheModeInUse() {
        assertEquals(EditorBars(TopBar.TOOLS, BottomBar.CAPTION), barsFor(false, EditMode.NONE))
        assertEquals(EditorBars(TopBar.TOOLS, BottomBar.PEN), barsFor(false, EditMode.DRAW))
        assertEquals(EditorBars(TopBar.TOOLS, BottomBar.CROP), barsFor(false, EditMode.CROP))
    }

    @Test
    fun backFinishesWordsFirstThenLeavesTheToolThenClosesTheEditor() {
        assertEquals(BackStep.FINISH_WORDS, backStep(true, EditMode.NONE))
        assertEquals(BackStep.FINISH_WORDS, backStep(true, EditMode.DRAW))
        assertEquals(BackStep.LEAVE_TOOL, backStep(false, EditMode.CROP))
        assertEquals(BackStep.LEAVE_TOOL, backStep(false, EditMode.DRAW))
        assertEquals(BackStep.CLOSE, backStep(false, EditMode.NONE))
    }

    @Test
    fun newWordsAreAddedTrimmedWithTheNewId() {
        val doc = blank.withWords(null, "  Night 6 ", red, 7L)
        assertEquals(1, doc.texts.size)
        assertEquals(7L, doc.texts[0].id)
        assertEquals("Night 6", doc.texts[0].text)
        assertEquals(red, doc.texts[0].color)
    }

    @Test
    fun newBlankWordsChangeNothing() {
        assertEquals(blank, blank.withWords(null, "   ", red, 1L))
    }

    @Test
    fun editedWordsTakeTheNewTextAndColourInPlace() {
        val one = blank.withWords(null, "One", red, 1L)
        val two = one.withWords(null, "Two", red, 2L)
        val edited = two.withWords(1L, "Uno", blue, 3L)
        assertEquals(listOf("Uno", "Two"), edited.texts.map { it.text })
        assertEquals(blue, edited.texts[0].color)
        assertEquals(red, edited.texts[1].color)
    }

    @Test
    fun editedWordsEmptiedAreTakenOff() {
        val one = blank.withWords(null, "One", red, 1L)
        val two = one.withWords(null, "Two", red, 2L)
        assertEquals(listOf("Two"), two.withWords(1L, "  ", red, 3L).texts.map { it.text })
    }
}
