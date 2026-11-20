package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The (i) that opens the rest of a sentence, and the one-line status that may carry one.
 */

/**
 * A small (i) in [ink] that opens the app's callout with [content] in it, titled [title] for
 * TalkBack. Faint until it is open, then full ink on a round of its own colour.
 *
 * The round is 6dp wider than the [glyph]. It keeps its own open state unless [open] and [onOpen]
 * are given. [iconInset] is how far inside the anchor the glyph sits, so the callout's caret meets
 * it; the default is right for the dot on its own.
 */
@Composable
internal fun InfoDot(
    title: String,
    ink: Color,
    modifier: Modifier = Modifier,
    glyph: Dp = 14.dp,
    open: Boolean? = null,
    onOpen: ((Boolean) -> Unit)? = null,
    iconInset: Dp = 3.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    var own by remember { mutableStateOf(false) }
    val shown = open ?: own
    val set: (Boolean) -> Unit = { next -> if (onOpen != null) onOpen(next) else own = next }
    Box(modifier) {
        Box(
            Modifier
                .requiredSize(glyph + 6.dp)
                .clip(CircleShape)
                .then(if (shown) Modifier.background(ink.copy(alpha = 0.16f)) else Modifier)
                .clickable(role = Role.Button, onClickLabel = title) { set(!shown) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Info, title, Modifier.size(glyph).alpha(if (shown) 1f else 0.75f), tint = ink)
        }
        Callout(shown, { set(false) }, title, iconInset = iconInset, content = content)
    }
}

/** A status line's words: 13 on 18. */
private val StatusWords = tileStyle(13f, 18f, FontWeight.Medium)
private val StatusNote = tileStyle(12f, 17f, FontWeight.Normal)

/**
 * One status, said in a line: a 15dp [icon] and the [text] in the [tone], an optional (i) holding
 * [info], an optional [note] under it, and [actions] at the far end. At a font size over 1.3x the
 * actions drop under the words, at the right, so the words keep their room.
 *
 * A quiet tone keeps the words in the ordinary ink with a grey icon. [band] puts it on the soft
 * grey band a crew's own app sits on.
 */
@Composable
internal fun StatusLine(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.QUIET,
    info: String? = null,
    note: AnnotatedString? = null,
    band: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val t = Tt.colors
    val wide = LocalDensity.current.fontScale > 1.3f
    val quiet = tone == Tone.QUIET
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (band) {
                    Modifier
                        .background(lerp(t.bg, t.text, 0.05f), RoundedCornerShape(10.dp))
                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = if (band) 36.dp else 32.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(15.dp), tint = if (quiet) t.text3 else tone.ink())
                    Spacer(Modifier.width(6.dp))
                    Text(text, Modifier.weight(1f, fill = false), style = StatusWords, color = if (quiet) t.text else tone.ink())
                    if (info != null) {
                        Spacer(Modifier.width(4.dp))
                        InfoDot(text, t.text3) { CalloutLine(AnnotatedString(info)) }
                    }
                }
                if (note != null) Text(note, style = StatusNote, color = t.text3)
            }
            if (!wide && actions != null) Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        if (wide && actions != null) {
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}
