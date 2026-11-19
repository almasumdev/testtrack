package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.eazyverse.testtrack.domain.ChatAllowance
import com.eazyverse.testtrack.domain.ChatLimits
import com.eazyverse.testtrack.domain.DraftProblem
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.CrewChatViewModel
import com.eazyverse.testtrack.viewmodel.loadChatPicture
import com.eazyverse.testtrack.viewmodel.sendPicture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The picture button's work: the system's photo picker, then the picked picture in the editor,
 * which sends it. Answers what a tap on the button does.
 */
@Composable
internal fun rememberChatPicturePicker(vm: CrewChatViewModel): () -> Unit {
    var picked by remember { mutableStateOf<Uri?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) picked = uri
        }
    picked?.let { uri -> ChatPictureEditor(vm, uri) { picked = null } }
    return remember(launcher) { { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
}

/**
 * A picked picture full screen before it goes, as in any messenger: crop and turn it, draw on it,
 * put words on it, undo, and add a caption. Send flattens it into one picture and sends it with
 * the caption as one message.
 */
@Composable
internal fun ChatPictureEditor(
    vm: CrewChatViewModel,
    uri: Uri,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        try {
            bitmap = loadChatPicture(context, uri)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            vm.message = "That picture couldn't be opened. Try another one."
            onClose()
        }
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogSystemBars(dark = true)
        Box(Modifier.fillMaxSize().background(Tt.colors.photo)) {
            bitmap?.let { Editor(vm, it, onClose) }
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
    vm: CrewChatViewModel,
    bitmap: Bitmap,
    onClose: () -> Unit,
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
    // The undo steps when the open tool began, so Back can take a crop back and undo reaches only the pen's strokes.
    var toolMark by remember { mutableIntStateOf(0) }
    var nextId by remember { mutableLongStateOf(1L) }
    var caption by remember { mutableStateOf(vm.draft) }
    var field by remember { mutableStateOf(TextFieldValue(vm.draft.text, TextRange(vm.draft.text.length))) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    val shadow = t.photoScrim.toArgb()

    val problem = ChatAllowance.problem(caption, vm.limits, vm.packages)
    val canSend = !busy && problem == DraftProblem.NONE && ChatAllowance.canAttach(vm.messagesLeft, vm.picturesLeft, vm.canPost)

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

    // The tick and Back do the same, as a messenger does: words and strokes are kept. Only a crop's
    // Back and close take its changes back.
    BackHandler(enabled = back != BackStep.CLOSE) {
        when (back) {
            BackStep.FINISH_WORDS -> finishWords()
            BackStep.LEAVE_TOOL -> mode = EditMode.NONE
            BackStep.CANCEL_CROP -> cancelCrop()
            BackStep.CLOSE -> Unit
        }
    }

    fun send() {
        // Read busy afresh: a second tap can land before the button redraws as busy.
        if (busy || !canSend) return
        busy = true
        failed = null
        scope.launch {
            try {
                val jpeg = withContext(Dispatchers.Default) { exportPicture(bitmap, history.doc, shadow) }
                if (vm.sendPicture(context, jpeg, caption)) onClose() else busy = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = "That picture couldn't be made ready. Try again."
                busy = false
            }
        }
    }

    // The bars and the cutout only. The keyboard is left out on purpose: the picture must not move
    // or shrink when it opens, so only the pieces that need it take its inset, each on its own.
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
        Column(Modifier.fillMaxSize()) {
            when (bars.top) {
                TopBar.TOOLS ->
                    ToolsTopBar(
                        canUndo = history.canUndo,
                        onClose = onClose,
                        onCrop = {
                            toolMark = history.steps
                            mode = EditMode.CROP
                        },
                        onDraw = {
                            toolMark = history.steps
                            mode = EditMode.DRAW
                        },
                        onWords = { typing = Typing(null, "", t.onPhoto) },
                        onUndo = history::undo,
                    )
                TopBar.DRAW ->
                    DrawTopBar(
                        canUndo = history.steps > toolMark,
                        onUndo = { history.undoSince(toolMark) },
                        onDone = { mode = EditMode.NONE },
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
                    onTextTap = { typing = Typing(it.id, it.text, it.color) },
                    onCrop = { history.commit(doc.copy(crop = it)) },
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
                        else ->
                            CaptionBar(
                                value = field,
                                onValue = { next ->
                                    caption = caption.edit(next.text)
                                    field = next
                                },
                                problem = failed ?: captionLine(problem, vm.limits),
                                canSend = canSend,
                                busy = busy,
                                onSend = ::send,
                            )
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

/** Why the caption cannot go, or null when it can. */
private fun captionLine(
    problem: DraftProblem,
    limits: ChatLimits,
): String? =
    when (problem) {
        DraftProblem.LINK -> "Links can't be sent here. Take it out to send."
        DraftProblem.TOO_LONG -> "That's over ${limits.maxLength} characters. Make it shorter to send."
        DraftProblem.TOO_MANY_MENTIONS -> "You can mention up to ${limits.maxMentions} people or apps in one message."
        DraftProblem.NONE -> null
    }
