package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.domain.textUseLabel
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The pieces a review to check and a case to decide share: the app at the top, the reviewer with
 * their chips, plain lines with a word or two in bold, and the page while it loads.
 */

/** What an (i) opens to: one plain sentence, in the callout's own ink. */
internal val CheckLineStyle = tileStyle(13f, 18f, FontWeight.Normal)

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
    Text(text, style = CheckLineStyle, color = Tt.colors.text2)
}

/**
 * The app a page is about: its icon with where it is found on the corner, the name on up to two
 * lines, and one quiet line under it. A tap opens its Play page, as the old header's did.
 */
@Composable
internal fun CheckAppHead(
    name: String,
    packageName: String,
    icon: String,
    live: Boolean,
    sub: String = if (live) "Live app" else "Closed test",
) {
    val t = Tt.colors
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = packageName.isNotBlank(), onClickLabel = "Open $name on Google Play") {
                openPlay(context, packageName)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(AppTileIcon)) {
            AppMark(packageName, name, icon, size = AppTileIcon)
            SourceMark(live = live, Modifier.align(Alignment.BottomEnd).offset(5.dp, 5.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = AppTileName, color = t.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                sub,
                style = tileStyle(12.5f, 15f, FontWeight.Medium),
                color = t.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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

/** Who sent a review: their face, their name, and under it the chips that matter, such as time left. */
@Composable
internal fun ReviewerRow(
    name: String,
    face: ReviewFace = ReviewFace.TO_CHECK,
    chips: @Composable FlowRowScope.() -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ReviewerFace(face, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = tileStyle(16f, 20f, FontWeight.SemiBold),
                color = Tt.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            ChipRow(content = chips)
        }
    }
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

/** A page of these while it is on its way: the app, the reviewer and a message, in their own shapes. */
@Composable
internal fun CheckSkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), enabled = false)
            .padding(horizontal = Gutter, vertical = 8.dp)
            .semantics { contentDescription = "Loading" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(width = AppTileIcon, height = AppTileIcon, corner = 16.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Skeleton(width = 140.dp, height = 15.dp)
                Spacer(Modifier.height(7.dp))
                Skeleton(width = 80.dp, height = 11.dp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(width = 40.dp, height = 40.dp, corner = 20.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Skeleton(width = 120.dp, height = 15.dp)
                Spacer(Modifier.height(8.dp))
                Skeleton(width = 150.dp, height = 22.dp, corner = 11.dp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Skeleton(modifier = Modifier.fillMaxWidth(0.8f), height = 12.dp)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Skeleton(width = 28.dp, height = 28.dp, corner = 14.dp)
            Spacer(Modifier.width(8.dp))
            Skeleton(width = 186.dp, height = 300.dp, corner = 18.dp)
        }
    }
}

/**
 * A page of this kind: the bar with an optional figure at its end, the page scrolling under it, and
 * the answers pinned above the dock.
 */
@Composable
internal fun CheckFrame(
    title: String,
    onBack: () -> Unit,
    barEnd: String? = null,
    foot: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PageBar(title, onBack) {
            if (barEnd != null) {
                Text(
                    barEnd,
                    style = tileStyle(14f, 18f, FontWeight.Bold).copy(fontFeatureSettings = "tnum"),
                    color = Tt.colors.text2,
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 20.dp),
            content = content,
        )
        if (foot != null) {
            Column(
                Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 6.dp + dockRoom()),
                content = foot,
            )
        }
    }
}
