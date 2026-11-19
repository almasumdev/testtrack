package com.eazyverse.testtrack.ui

import androidx.compose.ui.graphics.Color

/*
 * The picture editor's state as plain values: which bars show, what Back does, and what finishing
 * a piece of typed words does to the picture. Kept out of the composable so it can be tested.
 */

/** The bar along the top: the tools, or the colours and the tick while words are typed. */
internal enum class TopBar { TOOLS, WORDS }

/** The bar along the bottom: the pen, the crop, the caption with send, or nothing while words are typed. */
internal enum class BottomBar { NONE, PEN, CROP, CAPTION }

internal data class EditorBars(
    val top: TopBar,
    val bottom: BottomBar,
)

/** Exactly one set of controls: typing words replaces the tools and the send button, never joins them. */
internal fun barsFor(
    typingWords: Boolean,
    mode: EditMode,
): EditorBars =
    when {
        typingWords -> EditorBars(TopBar.WORDS, BottomBar.NONE)
        mode == EditMode.DRAW -> EditorBars(TopBar.TOOLS, BottomBar.PEN)
        mode == EditMode.CROP -> EditorBars(TopBar.TOOLS, BottomBar.CROP)
        else -> EditorBars(TopBar.TOOLS, BottomBar.CAPTION)
    }

/** What Back does, innermost first: words in hand, then a tool, then the editor itself. */
internal enum class BackStep { FINISH_WORDS, LEAVE_TOOL, CLOSE }

internal fun backStep(
    typingWords: Boolean,
    mode: EditMode,
): BackStep =
    when {
        typingWords -> BackStep.FINISH_WORDS
        mode != EditMode.NONE -> BackStep.LEAVE_TOOL
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
