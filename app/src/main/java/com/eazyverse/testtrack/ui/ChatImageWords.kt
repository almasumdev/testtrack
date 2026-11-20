package com.eazyverse.testtrack.ui

import androidx.compose.ui.graphics.Color

/*
 * The picture editor's state as plain values: which bars show, what Back does, and what finishing
 * a piece of typed words does to the picture. Kept out of the composable so it can be tested.
 */

/**
 * The bar along the top, one set at a time: the tools, or while a tool is in use only its own
 * controls with the tick (the colours for words, undo for the pen or the boxes, close and the tick
 * for a crop).
 */
internal enum class TopBar { TOOLS, WORDS, DRAW, CROP, HIDE }

/**
 * The bar along the bottom: the pen, the crop, how to hide details, the footer that finishes (the
 * chat's caption with send), or nothing while words are typed.
 */
internal enum class BottomBar { NONE, PEN, CROP, HIDE, FOOTER }

internal data class EditorBars(
    val top: TopBar,
    val bottom: BottomBar,
)

/** Exactly one set of controls: a tool in use replaces the tools and the send button, never joins them. */
internal fun barsFor(
    typingWords: Boolean,
    mode: EditMode,
): EditorBars =
    when {
        typingWords -> EditorBars(TopBar.WORDS, BottomBar.NONE)
        mode == EditMode.DRAW -> EditorBars(TopBar.DRAW, BottomBar.PEN)
        mode == EditMode.CROP -> EditorBars(TopBar.CROP, BottomBar.CROP)
        mode == EditMode.HIDE -> EditorBars(TopBar.HIDE, BottomBar.HIDE)
        else -> EditorBars(TopBar.TOOLS, BottomBar.FOOTER)
    }

/**
 * What Back does, innermost first: words in hand, then a tool, then the editor itself. Leaving the
 * pen or the boxes keeps them; leaving the crop puts it back as it was when it was opened.
 */
internal enum class BackStep { FINISH_WORDS, LEAVE_TOOL, CANCEL_CROP, CLOSE }

internal fun backStep(
    typingWords: Boolean,
    mode: EditMode,
): BackStep =
    when {
        typingWords -> BackStep.FINISH_WORDS
        mode == EditMode.CROP -> BackStep.CANCEL_CROP
        mode == EditMode.DRAW || mode == EditMode.HIDE -> BackStep.LEAVE_TOOL
        else -> BackStep.CLOSE
    }

/**
 * The picture after typing is done. New words ([editing] null) are added unless blank; words being
 * edited take the new text and colour, or go away when emptied.
 */
internal fun PictureDoc.withWords(
    editing: Long?,
    text: String,
    colour: Color,
    newId: Long,
): PictureDoc =
    when {
        editing == null && text.isNotBlank() -> copy(texts = texts + newWords(newId, text.trim(), colour))
        editing != null && text.isBlank() -> copy(texts = texts.filterNot { it.id == editing })
        editing != null -> copy(texts = texts.map { if (it.id == editing) it.copy(text = text.trim(), color = colour) else it })
        else -> this
    }
