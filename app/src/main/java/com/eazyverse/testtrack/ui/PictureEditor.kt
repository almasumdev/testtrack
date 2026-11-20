package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.ProofEditing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The tools a picture editor can offer. Its top bar shows them in this order. */
internal enum class PictureTool { CROP, DRAW, WORDS, HIDE }

/**
 * The picture as edited, for whoever opened the editor to write out. Both ways out burn the boxes
 * over details into the pixels before anything else, so they can never be lifted off.
 */
internal class EditedPicture(
    private val bitmap: Bitmap,
    val doc: PictureDoc,
    private val shadow: Int,
    private val hideInk: Int,
) {
    /** The whole edit at the picture's full size. The caller recycles it. Slow, so off the main thread. */
    fun flatten(): Bitmap = flattenPicture(bitmap, doc, shadow, hideInk)

    /** As a chat picture goes: at most 1600 on its long side, a JPEG under 1 MB. Slow, so off the main thread. */
    fun chatJpeg(): ByteArray = exportPicture(bitmap, doc, shadow, hideInk)
}

/** What the bar under the picture gets while no tool is open. */
internal class PictureFooter(
    val busy: Boolean,
    /** Why the last try to finish failed, or null. */
    val failed: String?,
    /** How many boxes hide details. */
    val hidden: Int,
    /**
     * Finishes once: [work] writes the picture out and says whether the editor closes. A throw
     * shows [failText] and the editor stays open. Taps while it runs do nothing.
     */
    val finish: (failText: String, work: suspend (EditedPicture) -> Boolean) -> Unit,
)

/**
 * A picture full screen before it is used, as in any messenger: the [tools] asked for (crop and
 * turn, draw, words, hide details), undo, and [footer] under it with no tool open, which finishes.
 * Back steps out of a tool first, then closes. Closing without finishing changes nothing.
 * [onUnreadable] hears when the picture can't be opened, and the editor closes; without it the
 * editor says [unreadable] itself.
 */
@Composable
internal fun PictureEditor(
    source: Uri,
    tools: Set<PictureTool>,
    onClose: () -> Unit,
    onUnreadable: (() -> Unit)? = null,
    unreadable: String = "That picture couldn't be opened. Try another one.",
    footer: @Composable (PictureFooter) -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(source) { mutableStateOf(false) }
    val busy = remember { mutableStateOf(false) }
    LaunchedEffect(source) {
        try {
            bitmap = ProofEditing.load(context, source)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (onUnreadable != null) {
                onUnreadable()
                onClose()
            } else {
                failed = true
            }
        }
    }
    Dialog(
        onDismissRequest = { if (!busy.value) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogSystemBars(dark = true)
        Box(Modifier.fillMaxSize().background(Tt.colors.photo)) {
            bitmap?.let { Editor(it, tools, busy, onClose, footer) }
            if (failed) Unreadable(unreadable, onClose)
        }
    }
}

/** Words being typed onto the picture: new ones have no id yet. */
private data class Typing(
    val id: Long?,
    val field: TextFieldValue,
    val colour: Color,
) {
    constructor(id: Long?, text: String, colour: Color) : this(id, TextFieldValue(text, TextRange(text.length)), colour)
}

@Composable
private fun Editor(
    bitmap: Bitmap,
    tools: Set<PictureTool>,
    busy: MutableState<Boolean>,
    onClose: () -> Unit,
    footer: @Composable (PictureFooter) -> Unit,
) {
    val t = Tt.colors
    val context = LocalContext.current
    remember { loadWordsFace(context) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val history = remember(bitmap) { EditHistory(PictureDoc(bitmap.width, bitmap.height)) }
    val doc = history.doc
    var mode by remember { mutableStateOf(EditMode.NONE) }
    var shape by remember { mutableStateOf(CropShape.FREE) }
    var pen by remember { mutableStateOf(t.pens.getOrElse(2) { t.onPhoto }) }
    var penWidth by remember { mutableFloatStateOf(6f) }
    var typing by remember { mutableStateOf<Typing?>(null) }
    // The undo steps when the open tool began, so Back can take a crop back and undo reaches only the tool's own edits.
    var toolMark by remember { mutableIntStateOf(0) }
    var nextId by remember { mutableLongStateOf(1L) }
    var failed by remember { mutableStateOf<String?>(null) }
    val shadow = t.photoScrim.toArgb()
    val hideInk = t.photo.toArgb()

    val bars = barsFor(typing != null, mode)
    val back = backStep(typing != null, mode)
    var bottomHeight by remember { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    /**
     * Puts the words on the picture (or takes them off when emptied) and goes back to the tools.
     * A second call with nothing open does nothing, so the words can never be added twice.
     */
    fun finishWords() {
        val was = typing ?: return
        typing = null
        history.commit(history.doc.withWords(was.id, was.field.text, was.colour, nextId))
        if (was.id == null) nextId++
        focus.clearFocus(force = true)
        keyboard?.hide()
    }

    /** Puts the crop and turns back as they were when the crop opened, and leaves it. */
    fun cancelCrop() {
        history.rollback(toolMark)
        shape = CropShape.FREE
        mode = EditMode.NONE
    }

    // The tick and Back do the same, as a messenger does: words, strokes and boxes are kept. Only a
    // crop's Back and close take its changes back.
    BackHandler(enabled = back != BackStep.CLOSE) {
        when (back) {
            BackStep.FINISH_WORDS -> finishWords()
            BackStep.LEAVE_TOOL -> mode = EditMode.NONE
            BackStep.CANCEL_CROP -> cancelCrop()
            BackStep.CLOSE -> Unit
        }
    }

    fun finish(
        failText: String,
        work: suspend (EditedPicture) -> Boolean,
    ) {
        // Read busy afresh: a second tap can land before the button redraws as busy.
        if (busy.value) return
        busy.value = true
        failed = null
        scope.launch {
            try {
                if (work(EditedPicture(bitmap, history.doc, shadow, hideInk))) onClose() else busy.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = failText
                busy.value = false
            }
        }
    }

    fun open(tool: PictureTool) {
        toolMark = history.steps
        when (tool) {
            PictureTool.CROP -> mode = EditMode.CROP
            PictureTool.DRAW -> mode = EditMode.DRAW
            PictureTool.HIDE -> mode = EditMode.HIDE
            PictureTool.WORDS -> typing = Typing(null, "", t.onPhoto)
        }
    }

    // The bars and the cutout only. The keyboard is left out on purpose: the picture must not move
    // or shrink when it opens, so only the pieces that need it take its inset, each on its own.
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
        Column(Modifier.fillMaxSize()) {
            when (bars.top) {
                TopBar.TOOLS ->
                    ToolsTopBar(
                        tools = tools,
                        canUndo = history.canUndo,
                        onClose = { if (!busy.value) onClose() },
                        onTool = ::open,
                        onUndo = history::undo,
                    )
                TopBar.DRAW, TopBar.HIDE ->
                    DrawTopBar(
                        canUndo = history.steps > toolMark,
                        onUndo = { history.undoSince(toolMark) },
                        onDone = { mode = EditMode.NONE },
                        doneLabel = if (bars.top == TopBar.HIDE) "Done hiding" else "Done drawing",
                    )
                TopBar.CROP -> CropTopBar(onCancel = ::cancelCrop, onDone = { mode = EditMode.NONE })
                // The words' own top bar covers this; the room stays so the picture holds its place.
                TopBar.WORDS -> Spacer(Modifier.height(EditorTopBar))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PictureCanvas(
                    bitmap = bitmap,
                    doc = doc,
                    mode = mode,
                    pen = pen,
                    penWidth = with(density) { penWidth.dp.toPx() },
                    shape = shape,
                    typing = typing?.id,
                    onStroke = { history.commit(doc.copy(strokes = doc.strokes + it)) },
                    onText = { moved -> history.commit(doc.copy(texts = doc.texts.map { if (it.id == moved.id) moved else it })) },
                    onTextTap = { if (PictureTool.WORDS in tools) typing = Typing(it.id, it.text, it.color) },
                    onCrop = { history.commit(doc.copy(crop = it)) },
                    onHide = { history.commit(doc.copy(hides = doc.hides + it)) },
                )
            }
            // The room the bottom bar takes, kept while the words hide it. The bar itself floats
            // over this in the Box below, so the keyboard lifts the bar without touching the picture.
            Spacer(Modifier.height(with(density) { bottomHeight.toDp() }))
        }
        if (bars.bottom != BottomBar.NONE) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
                Box(Modifier.fillMaxWidth().onSizeChanged { bottomHeight = it.height }) {
                    when (bars.bottom) {
                        BottomBar.PEN -> PenBar(pen, penWidth, { pen = it }, { penWidth = it })
                        BottomBar.HIDE -> HideBar(doc.hides.size)
                        BottomBar.CROP ->
                            CropBar(
                                shape = shape,
                                onShape = {
                                    shape = it
                                    history.commit(doc.copy(crop = doc.cropFor(it)))
                                },
                                onTurn = {
                                    // A shape turns with the picture: 4:3 becomes 3:4, and one not in the list is free.
                                    shape =
                                        when (shape) {
                                            CropShape.FOUR_THREE -> CropShape.THREE_FOUR
                                            CropShape.THREE_FOUR -> CropShape.FOUR_THREE
                                            CropShape.WIDE -> CropShape.FREE
                                            else -> shape
                                        }
                                    history.commit(doc.turned())
                                },
                            )
                        else -> footer(PictureFooter(busy.value, failed, doc.hides.size, ::finish))
                    }
                }
            }
        }
        typing?.let { words ->
            WordsEntry(
                value = words.field,
                // Read the state afresh: the keyboard sends a last edit as the field leaves, and that
                // must not bring a finished entry back from the copy this lambda was made with.
                onValue = { value -> typing = typing?.copy(field = value) },
                ink = words.colour,
                onInk = { colour -> typing = typing?.copy(colour = colour) },
                onDone = ::finishWords,
            )
        }
    }
}
