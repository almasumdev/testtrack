package com.eazyverse.testtrack.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.eazyverse.testtrack.domain.ChatAllowance
import com.eazyverse.testtrack.domain.ChatLimits
import com.eazyverse.testtrack.domain.DraftProblem
import com.eazyverse.testtrack.viewmodel.CrewChatViewModel
import com.eazyverse.testtrack.viewmodel.sendPicture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The picture button's work: the system's photo picker, then the picked picture in the editor,
 * which sends it. Answers what a tap on the button does.
 */
@Composable
internal fun rememberChatPicturePicker(vm: CrewChatViewModel): () -> Unit {
    var picked by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberImagePicker { uri -> if (uri != null) picked = uri }
    picked?.let { uri -> ChatPictureEditor(vm, uri) { picked = null } }
    return pick
}

/** The chat's tools: crop and turn, draw, and words. */
private val ChatTools = setOf(PictureTool.CROP, PictureTool.DRAW, PictureTool.WORDS)

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
    // Held here, not in the caption bar, so the caption outlives a tool being opened over it.
    var caption by remember { mutableStateOf(vm.draft) }
    var field by remember { mutableStateOf(TextFieldValue(vm.draft.text, TextRange(vm.draft.text.length))) }
    PictureEditor(
        source = uri,
        tools = ChatTools,
        onClose = onClose,
        onUnreadable = { Snacks.error("Couldn't open that picture. Try another.") },
    ) { footer ->
        val problem = ChatAllowance.problem(caption, vm.limits, vm.packages)
        val canSend = !footer.busy && problem == DraftProblem.NONE && ChatAllowance.canAttach(vm.messagesLeft, vm.picturesLeft, vm.canPost)
        CaptionBar(
            value = field,
            onValue = { next ->
                caption = caption.edit(next.text)
                field = next
            },
            problem = footer.failed ?: captionLine(problem, vm.limits),
            canSend = canSend,
            busy = footer.busy,
            onSend = {
                if (canSend) {
                    footer.finish("That picture couldn't be made ready. Try again.") { picture ->
                        val jpeg = withContext(Dispatchers.Default) { picture.chatJpeg() }
                        vm.sendPicture(context, jpeg, caption)
                    }
                }
            },
        )
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
