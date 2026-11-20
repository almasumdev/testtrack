package com.eazyverse.testtrack.ui

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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * What sits between a thread's groups: the day, a one-line note of what happened, and the (i) that
 * opens the rest of a note. [Thread] spaces them the way the design does, so a page only lists them.
 */

/** What a child of [Thread] is, for the space round it; anything else gets none of its own. */
internal enum class ThreadKind { GROUP, NOTE, DAY }

/**
 * A thread's groups, notes and day labels, top to bottom, spaced as the design's thread: 12dp over a
 * group, 12dp above and below a note, 6dp between stacked notes, 14dp over a day. Anything else is
 * laid out with no space of its own, and a child that draws nothing takes no space at all.
 */
@Composable
internal fun Thread(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier.fillMaxWidth()) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val placed = measurables.map { it.measure(loose) to (it.layoutId as? ThreadKind) }
        var y = 0
        var previous: ThreadKind? = null
        var started = false
        val tops = IntArray(placed.size) { -1 }
        placed.forEachIndexed { i, (placeable, kind) ->
            if (placeable.height == 0) return@forEachIndexed
            val gap = (if (started) after(previous) else 0) + before(kind, if (started) previous else null)
            y += gap.dp.roundToPx()
            tops[i] = y
            y += placeable.height
            previous = kind
            started = true
        }
        if (started) y += after(previous).dp.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placed.maxOfOrNull { it.first.width } ?: 0
        layout(width, y.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            placed.forEachIndexed { i, (placeable, _) -> if (tops[i] >= 0) placeable.place(0, tops[i]) }
        }
    }
}

/** The space a child of [kind] asks for over itself, given what came before it. */
private fun before(
    kind: ThreadKind?,
    previous: ThreadKind?,
): Int =
    when (kind) {
        ThreadKind.NOTE -> if (previous == ThreadKind.NOTE) -6 else 12
        ThreadKind.GROUP -> if (previous == ThreadKind.NOTE) 0 else 12
        ThreadKind.DAY -> 14
        null -> 0
    }

/** The space a child of [kind] keeps under itself. */
private fun after(kind: ThreadKind?): Int = if (kind == ThreadKind.NOTE) 12 else 0

/** "Today", "Yesterday" or a date, small and centred over the groups of that day. */
@Composable
internal fun ThreadDay(text: String) {
    Box(Modifier.fillMaxWidth().layoutId(ThreadKind.DAY), contentAlignment = Alignment.Center) {
        Text(text, style = chatStyle(12f, 16f), fontWeight = FontWeight.SemiBold, color = Tt.colors.text3, maxLines = 1)
    }
}

/** How a note speaks: a plain fact, information, settled, needs a look, or gone wrong. */
enum class NoteTone { QUIET, INFO, OK, WARN, BAD }

/** [text] with each of [phrases] in bold, in the note's own colour, the way a note sets a figure. */
internal fun noteText(
    text: String,
    vararg phrases: String,
): AnnotatedString =
    buildAnnotatedString {
        append(text)
        phrases.filter { it.isNotBlank() }.forEach { phrase ->
            val at = text.indexOf(phrase)
            if (at >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"), at, at + phrase.length)
        }
    }

/**
 * What happened, as one line across the middle of the thread: hugging its words, 26dp tall, a soft
 * tint of its [tone] and a small [icon] first. With [more] it ends on an (i) that opens the rest in
 * the app's callout, titled [moreTitle].
 */
@Composable
internal fun ThreadNote(
    text: AnnotatedString,
    tone: NoteTone = NoteTone.QUIET,
    icon: ImageVector = Lucide.Info,
    more: AnnotatedString? = null,
    moreTitle: String = "More about this",
) {
    val t = Tt.colors
    val (ink, ground) =
        when (tone) {
            NoteTone.QUIET -> t.text2 to t.glass2
            NoteTone.INFO -> t.accentInk to t.accentInk.copy(alpha = 0.12f)
            NoteTone.OK -> t.ok to t.okBg
            NoteTone.WARN -> t.warn to t.warnBg
            NoteTone.BAD -> t.bad to t.badBg
        }
    Box(Modifier.fillMaxWidth().layoutId(ThreadKind.NOTE), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(ground)
                .padding(start = 10.dp, end = if (more != null) 6.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(13.dp).alpha(0.9f), tint = ink)
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                Modifier.weight(1f, fill = false),
                style = tileStyle(12.5f, 16f, FontWeight.SemiBold),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (more != null) {
                Spacer(Modifier.width(6.dp))
                InfoDot(moreTitle, ink) { CalloutLine(more) }
            }
        }
    }
}

@Composable
internal fun ThreadNote(
    text: String,
    tone: NoteTone = NoteTone.QUIET,
    icon: ImageVector = Lucide.Info,
    more: String? = null,
    moreTitle: String = "More about this",
) = ThreadNote(AnnotatedString(text), tone, icon, more?.let(::AnnotatedString), moreTitle)

/**
 * A small (i) in [ink] that opens the app's callout with [content] in it, titled [title] for
 * TalkBack. Faint until it is open, then full ink on a round of its own colour.
 */
@Composable
internal fun InfoDot(
    title: String,
    ink: Color,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .requiredSize(20.dp)
                .clip(CircleShape)
                .then(if (open) Modifier.background(ink.copy(alpha = 0.16f)) else Modifier)
                .clickable(role = Role.Button, onClickLabel = title) { open = !open },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Info, title, Modifier.size(14.dp).alpha(if (open) 1f else 0.75f), tint = ink)
        }
        Callout(open, { open = false }, title, iconInset = 3.dp, content = content)
    }
}

/** A heading over a part of the page, with an optional quiet tag at its far end such as "Optional". */
@Composable
internal fun ThreadHeading(
    title: String,
    modifier: Modifier = Modifier,
    tag: String = "",
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 19.sp),
            fontWeight = FontWeight.Bold,
            color = Tt.colors.text,
        )
        if (tag.isNotBlank()) HeadingTag(tag)
    }
}

/** A screenshot from a thread, whole, over everything; it scrolls when it is taller than the screen. */
@Composable
internal fun ProofViewer(
    ref: String,
    onDismiss: () -> Unit,
    picture: @Composable (String) -> Unit = { ProofImage(it, whole = true) },
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Tt.colors.inverse)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                IconButton(onClick = onDismiss) { Icon(Lucide.X, "Close", tint = Tt.colors.onInverse) }
            }
            Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) { picture(ref) }
        }
    }
}

/**
 * The last 18dp of a page that scrolls above a pinned foot: the page's own colour fading in, so
 * what runs under the foot goes soft rather than being cut. Laid over the bottom of the scroll.
 */
@Composable
internal fun FootFade(modifier: Modifier = Modifier) {
    val bg = Tt.colors.bg
    Box(
        modifier
            .fillMaxWidth()
            .height(18.dp)
            .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg))),
    )
}
