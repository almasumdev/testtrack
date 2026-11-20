package com.eazyverse.testtrack.ui

import android.app.Application
import androidx.compose.ui.geometry.Rect
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
    fun eachModeShowsExactlyOneSetOfControls() {
        assertEquals(EditorBars(TopBar.TOOLS, BottomBar.FOOTER), barsFor(false, EditMode.NONE))
        assertEquals(EditorBars(TopBar.DRAW, BottomBar.PEN), barsFor(false, EditMode.DRAW))
        assertEquals(EditorBars(TopBar.CROP, BottomBar.CROP), barsFor(false, EditMode.CROP))
        assertEquals(EditorBars(TopBar.HIDE, BottomBar.HIDE), barsFor(false, EditMode.HIDE))
    }

    @Test
    fun backFinishesWordsFirstThenLeavesTheToolThenClosesTheEditor() {
        assertEquals(BackStep.FINISH_WORDS, backStep(true, EditMode.NONE))
        assertEquals(BackStep.FINISH_WORDS, backStep(true, EditMode.DRAW))
        assertEquals(BackStep.FINISH_WORDS, backStep(true, EditMode.CROP))
        assertEquals(BackStep.LEAVE_TOOL, backStep(false, EditMode.DRAW))
        assertEquals(BackStep.LEAVE_TOOL, backStep(false, EditMode.HIDE))
        assertEquals(BackStep.CANCEL_CROP, backStep(false, EditMode.CROP))
        assertEquals(BackStep.CLOSE, backStep(false, EditMode.NONE))
    }

    @Test
    fun rollbackTakesBackEverythingSinceTheMarkAndLeavesNoUndoStep() {
        val history = EditHistory(blank)
        val before = history.doc.withWords(null, "One", red, 1L)
        history.commit(before)
        val mark = history.steps
        history.commit(history.doc.turned())
        history.commit(history.doc.copy(crop = Rect(0f, 0f, 0.5f, 0.5f)))
        history.rollback(mark)
        assertEquals(before, history.doc)
        assertEquals(mark, history.steps)
    }

    @Test
    fun undoSinceOnlyReachesEditsMadeAfterTheMark() {
        val history = EditHistory(blank)
        val first = blank.withWords(null, "One", red, 1L)
        history.commit(first)
        val mark = history.steps
        history.undoSince(mark)
        assertEquals(first, history.doc)
        history.commit(first.turned())
        history.undoSince(mark)
        assertEquals(first, history.doc)
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
