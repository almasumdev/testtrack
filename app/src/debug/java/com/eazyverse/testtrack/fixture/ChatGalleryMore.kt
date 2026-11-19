package com.eazyverse.testtrack.fixture

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Crop
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Type
import com.composables.icons.lucide.Undo2
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.domain.ChatDraft
import com.eazyverse.testtrack.domain.SendState
import com.eazyverse.testtrack.fixture.GallerySamples.Companion.KENJI
import com.eazyverse.testtrack.fixture.GallerySamples.Companion.ME
import com.eazyverse.testtrack.ui.Ask
import com.eazyverse.testtrack.ui.CaptionBar
import com.eazyverse.testtrack.ui.ChatActions
import com.eazyverse.testtrack.ui.ChatImageViewer
import com.eazyverse.testtrack.ui.ChatJump
import com.eazyverse.testtrack.ui.ChatMentionSheet
import com.eazyverse.testtrack.ui.ChatMessageSheet
import com.eazyverse.testtrack.ui.ChatPictureEditor
import com.eazyverse.testtrack.ui.Crew
import com.eazyverse.testtrack.ui.CrewChatBody
import com.eazyverse.testtrack.ui.CrewChatEntry
import com.eazyverse.testtrack.ui.CrewChatTileButton
import com.eazyverse.testtrack.ui.CrewListRow
import com.eazyverse.testtrack.ui.CrewLobbyRow
import com.eazyverse.testtrack.ui.CrewMemberProfile
import com.eazyverse.testtrack.ui.CropBar
import com.eazyverse.testtrack.ui.CropShape
import com.eazyverse.testtrack.ui.EditHistory
import com.eazyverse.testtrack.ui.EditMode
import com.eazyverse.testtrack.ui.EditorButton
import com.eazyverse.testtrack.ui.Gutter
import com.eazyverse.testtrack.ui.PenBar
import com.eazyverse.testtrack.ui.PenStroke
import com.eazyverse.testtrack.ui.PictureCanvas
import com.eazyverse.testtrack.ui.PictureDoc
import com.eazyverse.testtrack.ui.PictureText
import com.eazyverse.testtrack.ui.SegmentedTabs
import com.eazyverse.testtrack.ui.WordsEntry
import com.eazyverse.testtrack.ui.chatProfileOf
import com.eazyverse.testtrack.ui.loadWordsFace
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.CrewChatViewModel
import kotlin.math.sin

/** A button in the gallery that opens a sheet, a dialog or a page over it. */
@Composable
internal fun OpenButton(
    label: String,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    Text(
        label,
        Modifier
            .padding(horizontal = Gutter)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(t.tile)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = t.tileInk,
    )
}

/** The lines the sheets are opened on. */
internal class SheetLines(
    s: GallerySamples,
) {
    val others = s.msg("so", KENJI, "${GallerySamples.seat(ME)} did yours open fine tonight?")
    val own = s.msg("sm", ME, "All good on mine, opened it a minute ago.")
    val failed = s.msg("sf", ME, "Opened all nine tonight.", send = SendState.FAILED, error = "No connection. Check your internet.")
    val picture = s.thread.first { it.id == "t8" }
    val bot = s.thread.first { it.id == "t7" }
}

internal fun sheetSection(
    s: GallerySamples,
    show: (Overlay) -> Unit,
    openPage: () -> Unit,
): Section {
    val a = galleryActions(show)
    val lines = SheetLines(s)
    return Section(
        "sheets",
        "Sheets and dialogs",
        listOf(
            Shot("The full chat page on its own, every tap works") { OpenButton("Open the chat page", openPage) },
            Shot("Long-press sheet, others' message") { OpenButton("Open") { show(Overlay.Message(lines.others)) } },
            Shot("Long-press sheet, your message") { OpenButton("Open") { show(Overlay.Message(lines.own)) } },
            Shot("Long-press sheet, the team's post (Reply and Copy)") { OpenButton("Open") { show(Overlay.Message(lines.bot)) } },
            Shot("Long-press sheet, your message that didn't send") { OpenButton("Open") { show(Overlay.Message(lines.failed)) } },
            Shot("Delete dialog") { OpenButton("Open") { show(Overlay.Delete(lines.own)) } },
            Shot("Mention picker") { OpenButton("Open") { show(Overlay.Mention) } },
            Shot("Profile (from a face)") { OpenButton("Open") { show(Overlay.Profile(KENJI)) } },
            Shot("Picture viewer") { OpenButton("Open") { show(Overlay.Viewer(lines.picture)) } },
            Shot("Menu: tap the three dots in the bar") { PageBox(s.view(s.threadRows()), a, 300.dp) },
            Shot("Menu while muted: tap the three dots") { PageBox(s.view(s.threadRows()).copy(muted = true), a, 300.dp) },
        ),
    )
}

/** What [Overlay] draws over the gallery: the real sheets, dialogs and the picture viewer. */
@Composable
internal fun OverlayHost(
    overlay: Overlay,
    s: GallerySamples,
    show: (Overlay) -> Unit,
    close: () -> Unit,
) {
    when (overlay) {
        is Overlay.Message -> {
            val line = overlay.line
            ChatMessageSheet(
                line = line,
                title = s.view().messageTitle(line),
                canReply = line.send == SendState.SENT,
                onReply = {},
                onCopy = {},
                onDelete = { if (line.send == SendState.SENT) show(Overlay.Delete(line)) },
                onDismiss = close,
            )
        }
        is Overlay.Delete ->
            Ask(
                title = "Delete this message?",
                body = "It goes for everyone in ${GallerySamples.CREW}. It still counts toward today's messages.",
                confirm = "Delete",
                dismiss = "Keep it",
                destructive = true,
                onConfirm = close,
                onDismiss = close,
            )
        Overlay.Mention ->
            ChatMentionSheet(
                people = s.people.values.filter { it.inCrew && !it.mine },
                crewName = GallerySamples.CREW,
                onPick = { _, _ -> },
                onDismiss = close,
            )
        is Overlay.Profile -> CrewMemberProfile(chatProfileOf(overlay.seat, s.people[overlay.seat], s.run(0)), close)
        is Overlay.Viewer -> {
            val line = overlay.line
            ChatImageViewer(line, s.name(line), s.view().plain(line.text), close)
        }
        Overlay.Editor -> {
            val vm =
                remember {
                    CrewChatViewModel().apply {
                        messagesLeft = 10
                        picturesLeft = 3
                    }
                }
            ChatPictureEditor(vm, Uri.parse(s.portrait), close)
        }
    }
}

/** The chat page full screen, as the app shows it, with the field, mute and the long-press all live. */
@Composable
internal fun GalleryPage(
    s: GallerySamples,
    show: (Overlay) -> Unit,
    onBack: () -> Unit,
) {
    var muted by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(ChatDraft()) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var shown by remember { mutableStateOf<String?>(null) }
    var jump by remember { mutableStateOf<ChatJump?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val view = s.view(s.threadRows()).copy(muted = muted, draft = draft, timeShown = shown, jump = jump, message = message)
    val base = galleryActions(show)
    val actions =
        ChatActions(
            onBack = onBack,
            onMute = { muted = !muted },
            onField = {
                field = it
                draft = draft.edit(it.text)
            },
            onPickPicture = { show(Overlay.Editor) },
            onLongPress = base.onLongPress,
            onFace = base.onFace,
            onPicture = base.onPicture,
            onTime = { shown = if (shown == it.id) null else it.id },
            // The whole thread is held here, so a quoted message is either in it or gone.
            onQuote = { quote ->
                val held = s.thread.firstOrNull { it.id == quote.id && !it.deleted }
                if (quote.deleted || held == null) message = "That message is gone." else jump = ChatJump(held.id, (jump?.n ?: 0) + 1)
            },
            onClearMessage = { message = null },
        )
    CrewChatBody(view, actions, field)
}

// The picture editor, from its own parts, held in each of its modes.

/** A drawn line and a few words on the picture, in its own pixels, so undo and the tools have something to show. */
@Composable
private fun editedDoc(bitmap: Bitmap): PictureDoc {
    val t = Tt.colors
    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val wave = (0..40).map { i -> Offset(w * (0.15f + i * 0.0175f), h * 0.62f + sin(i / 3f) * h * 0.04f) }
    return PictureDoc(
        bitmap.width,
        bitmap.height,
        strokes = listOf(PenStroke(wave, t.pens.getOrElse(2) { t.onPhoto }, w / 70f)),
        texts = listOf(PictureText(1, "Crashes here", t.pens.getOrElse(0) { t.onPhoto }, Offset(w * 0.5f, h * 0.45f), w / 9f, -6f)),
    )
}

@Composable
private fun EditorFrame(
    bitmap: Bitmap,
    mode: EditMode,
    edited: Boolean = true,
    shape: CropShape = CropShape.FREE,
    caption: String = "",
    problem: String? = null,
    typing: Boolean = false,
) {
    val t = Tt.colors
    val context = LocalContext.current
    remember { loadWordsFace(context) }
    val first = editedDoc(bitmap)
    val history = remember { EditHistory(PictureDoc(bitmap.width, bitmap.height)).apply { if (edited) commit(first) } }
    val start = history.doc
    remember { if (shape != CropShape.FREE) history.commit(start.copy(crop = start.cropFor(shape))) }
    val doc = history.doc
    val pen = t.pens.getOrElse(2) { t.onPhoto }
    val penWidth = with(LocalDensity.current) { 6.dp.toPx() }
    Box(Modifier.fillMaxWidth().height(640.dp).background(t.photo)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                EditorButton(Lucide.X, "Close") {}
                Spacer(Modifier.weight(1f))
                EditorButton(Lucide.Crop, "Crop and turn", active = mode == EditMode.CROP) {}
                Spacer(Modifier.width(8.dp))
                EditorButton(Lucide.Pencil, "Draw", active = mode == EditMode.DRAW) {}
                Spacer(Modifier.width(8.dp))
                EditorButton(Lucide.Type, "Add words") {}
                Spacer(Modifier.width(8.dp))
                EditorButton(Lucide.Undo2, "Undo", enabled = history.canUndo) { history.undo() }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PictureCanvas(
                    bitmap = bitmap,
                    doc = doc,
                    mode = mode,
                    pen = pen,
                    penWidth = penWidth,
                    shape = shape,
                    typing = null,
                    onStroke = { history.commit(doc.copy(strokes = doc.strokes + it)) },
                    onText = { moved -> history.commit(doc.copy(texts = doc.texts.map { if (it.id == moved.id) moved else it })) },
                    onTextTap = {},
                    onCrop = { history.commit(doc.copy(crop = it)) },
                )
            }
            when (mode) {
                EditMode.DRAW -> PenBar(pen, 6f, {}, {})
                EditMode.CROP -> CropBar(shape, {}, { history.commit(doc.turned()) }, {})
                EditMode.NONE -> {
                    var value by remember { mutableStateOf(TextFieldValue(caption, TextRange(caption.length))) }
                    CaptionBar(value, { value = it }, problem, canSend = problem == null, busy = false, onSend = {})
                }
            }
        }
        if (typing) WordsEntry("Night 6", t.pens.getOrElse(3) { t.onPhoto }) { _, _ -> }
    }
}

internal fun editorSection(
    s: GallerySamples,
    show: (Overlay) -> Unit,
): Section =
    Section(
        "editor",
        "Picture editor",
        listOf(
            Shot("The real editor, every tool works") { OpenButton("Open the picture editor") { show(Overlay.Editor) } },
            Shot("Main, just picked") { EditorFrame(s.editorBitmap, EditMode.NONE, edited = false) },
            Shot("Main, after a line and words") { EditorFrame(s.editorBitmap, EditMode.NONE) },
            Shot("Crop, 1:1") { EditorFrame(s.editorBitmap, EditMode.CROP, shape = CropShape.SQUARE) },
            Shot("Crop, free") { EditorFrame(s.editorBitmap, EditMode.CROP) },
            Shot("Pen") { EditorFrame(s.editorBitmap, EditMode.DRAW) },
            Shot("Caption written") { EditorFrame(s.editorBitmap, EditMode.NONE, caption = "The Start button does nothing here.") },
            Shot("Caption with a link") {
                EditorFrame(
                    s.editorBitmap,
                    EditMode.NONE,
                    caption = "See example.com/bug",
                    problem = "Links can't be sent here. Take it out to send.",
                )
            },
            Shot("Words being typed (opens the keyboard)") { EditorFrame(s.editorBitmap, EditMode.NONE, typing = true) },
        ),
    )

@Composable
private fun Tile(content: @Composable () -> Unit) = Box(Modifier.padding(horizontal = Gutter)) { content() }

/** The floating button over a scrolling list of tiles, as on a crew's page. */
@Composable
private fun Fab(
    s: GallerySamples,
    crew: Crew,
) = Box(Modifier.fillMaxWidth().height(300.dp)) {
    CrewChatEntry(crew, pruned = false, onChat = {}) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter)) {
            repeat(3) {
                CrewListRow(s.run(0)) {}
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

internal fun entrySection(s: GallerySamples): Section {
    val labels = listOf("Open", "Lobby", "Run", "Record")
    return Section(
        "entry",
        "Entry points",
        listOf(
            Shot("Run tile, chat 0") { Tile { CrewListRow(s.run(0)) {} } },
            Shot("Run tile, chat 3") { Tile { CrewListRow(s.run(3)) {} } },
            Shot("Run tile, chat 12 (9+)") { Tile { CrewListRow(s.run(12)) {} } },
            Shot("Run tile, chat 3 muted") { Tile { CrewListRow(s.run(3, muted = true)) {} } },
            Shot("Lobby tile, chat 0") { Tile { CrewLobbyRow(s.lobby(0)) {} } },
            Shot("Lobby tile, chat 3") { Tile { CrewLobbyRow(s.lobby(3)) {} } },
            Shot("Lobby tile, chat 12 (9+)") { Tile { CrewLobbyRow(s.lobby(12)) {} } },
            Shot("Lobby tile, chat 3 muted") { Tile { CrewLobbyRow(s.lobby(3, muted = true)) {} } },
            Shot("Tile chat buttons alone: 0, 3, 12, muted 3") {
                Row(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
                    listOf(s.run(0), s.run(3), s.run(12), s.run(3, muted = true)).forEach {
                        CrewChatTileButton(it) {}
                        Spacer(Modifier.width(28.dp))
                    }
                }
            },
            Shot("Floating Chat button, nothing new (scroll the list to fold it)") { Fab(s, s.run(0)) },
            Shot("Floating Chat button, 5 new (scroll the list to fold it)") { Fab(s, s.run(5)) },
            Shot("Floating Chat button, 120 new") { Fab(s, s.run(120)) },
            Shot("Floating Chat button, read only") { Fab(s, s.record()) },
            Shot("Tabs: new chat in the Lobby") {
                Tile { SegmentedTabs(labels, 2, counts = listOf(0, 0, 4, 0), chatDots = listOf(false, true, false, false)) {} }
            },
            Shot("Tabs: new chat in the Run (selected)") {
                Tile { SegmentedTabs(labels, 2, counts = listOf(0, 0, 4, 0), chatDots = listOf(false, false, true, false)) {} }
            },
            Shot("Tabs: no new chat") {
                Tile { SegmentedTabs(labels, 1, counts = listOf(0, 0, 4, 0)) {} }
            },
        ),
    )
}
