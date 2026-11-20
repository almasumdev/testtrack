package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.FlaskConical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Scale
import com.eazyverse.testtrack.domain.textUseLabel
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The pieces a review to check and a case to decide share: the kind of review, chips, plain lines with a word or two
 * in bold, the frame with its pinned answers, and the page while it loads.
 */

/** What kind of review a page or a row is about, as its label says it. */
enum class CheckKind(
    val label: String,
    val icon: ImageVector,
) {
    PLAY("Live app", Lucide.Play),
    CLOSED("Closed test", Lucide.FlaskConical),
    DISPUTED("Disputed", Lucide.Scale),
}

/** [text] with each of [phrases] in it drawn in the strongest ink, the way the design bolds a figure. */
@Composable
internal fun strong(
    text: String,
    vararg phrases: String,
): AnnotatedString {
    val bold = SpanStyle(fontWeight = FontWeight.SemiBold, color = Tt.colors.text)
    return buildAnnotatedString {
        append(text)
        phrases.filter { it.isNotBlank() }.forEach { phrase ->
            val at = text.indexOf(phrase)
            if (at >= 0) addStyle(bold, at, at + phrase.length)
        }
    }
}

/** An (i)'s callout: one plain sentence. */
@Composable
internal fun CalloutLine(text: AnnotatedString) {
    Text(text, style = TextStyles.PageLine, color = Tt.colors.text2)
}

/** A row of chips that wraps onto a second line rather than running off the page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipRow(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** "100 characters, changed your ready review": how long a text is and whose words it is in. */
internal fun textMeta(
    written: String,
    needs: Int,
    use: String?,
): String {
    val label = textUseLabel(use)
    if (needs <= 0) return label.orEmpty()
    val length = "${plainLength(written)} characters"
    return if (label == null) length else "$length, ${label.replaceFirstChar { it.lowercase() }}"
}

/** What the request asked each reviewer to write, as one line and its figure; null for stars alone. */
internal fun askedLine(
    feedback: String,
    needs: Int,
    owner: Boolean,
): Pair<String, String>? {
    if (needs <= 0) return null
    val kind = FeedRules.Feedback.of(feedback)
    val figure = "$needs+ characters"
    val line =
        when {
            kind == FeedRules.Feedback.STARS -> "It needed $figure of text."
            owner -> "You asked for ${kind.label.lowercase()}, $figure."
            else -> "The request asked for ${kind.label.lowercase()}, $figure."
        }
    return line to figure
}

/**
 * The Check review page while it is on its way, in the shapes it will take: the bar's title and
 * subtitle, the app, the day, the reviewer's picture and words with the face beside them, and the
 * answers pinned under it.
 */
@Composable
internal fun CheckSkeleton(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().semantics { contentDescription = "Loading" }) {
        PageBar("", onBack, loading = true)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState(), enabled = false)
                .padding(start = Gutter, end = Gutter, top = 4.dp),
        ) {
            AppHeadSkeleton(icon = AppTileIcon, corner = 16.dp)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Skeleton(width = 44.dp, height = 12.dp) }
            Spacer(Modifier.height(16.dp))
            Skeleton(width = 70.dp, height = 11.dp, modifier = Modifier.padding(start = 36.dp))
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Spacer(Modifier.width(36.dp))
                Skeleton(width = 158.dp, height = 208.dp, corner = 18.dp)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Skeleton(width = 28.dp, height = 28.dp, corner = 14.dp)
                Spacer(Modifier.width(8.dp))
                Skeleton(width = 230.dp, height = 96.dp, corner = 18.dp)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 6.dp + dockRoom())) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Skeleton(width = 190.dp, height = 12.dp) }
            Spacer(Modifier.height(10.dp))
            Skeleton(height = ButtonHeight, corner = 26.dp)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Skeleton(width = 120.dp, height = 14.dp)
                Skeleton(width = 60.dp, height = 14.dp)
            }
        }
    }
}

/**
 * A page of this kind: the bar with an optional subtitle and figure at its end, the page scrolling
 * under it, and the answers pinned above the dock.
 */
@Composable
internal fun CheckFrame(
    title: String,
    onBack: () -> Unit,
    barEnd: String? = null,
    subtitle: String? = null,
    subtitleTone: BarTone = BarTone.NORMAL,
    foot: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PageBar(title, onBack, subtitle = subtitle, subtitleTone = subtitleTone, count = barEnd)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Gutter, end = Gutter, top = 4.dp, bottom = 20.dp),
                content = content,
            )
            if (foot != null) FootFade(Modifier.align(Alignment.BottomCenter))
        }
        if (foot != null) {
            Column(
                Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 6.dp + dockRoom()),
                content = foot,
            )
        }
    }
}
