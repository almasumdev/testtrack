package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Text
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * What a request asks of each reviewer, on the Ask page: three tiles, Stars, Written and Detailed,
 * each with the least it pays (FeedRules.Feedback), and with Detailed the ready reviews an owner may
 * give. The chosen tile is washed and checked, the way a picker marks its chosen row.
 */

private val PageLineBold = tileStyle(13f, 18f, FontWeight.SemiBold)
private val CountLine = tileStyle(12.5f, 15f, FontWeight.Normal)

private fun FeedRules.Feedback.icon(): ImageVector =
    when (this) {
        FeedRules.Feedback.STARS -> Lucide.Star
        FeedRules.Feedback.WRITTEN -> Lucide.Text
        FeedRules.Feedback.DETAILED -> Lucide.FileText
    }

private fun FeedRules.Feedback.tileName(): String =
    when (this) {
        FeedRules.Feedback.STARS -> "Stars"
        FeedRules.Feedback.WRITTEN -> "Written"
        FeedRules.Feedback.DETAILED -> "Detailed"
    }

/** The line under the tiles: what a review at this level has. */
private fun FeedRules.Feedback.line(): String = if (chars == 0) "A star rating only." else "A star rating and at least $chars characters."

/** The three levels side by side, then one line saying what the chosen one has. */
@Composable
internal fun AskLevelTiles(
    chosen: FeedRules.Feedback,
    enabled: Boolean,
    onChoose: (FeedRules.Feedback) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeedRules.Feedback.entries.forEach { level ->
            ChoiceTile(
                level.icon(),
                level.tileName(),
                "From ${level.least} tix",
                selected = level == chosen,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) { onChoose(level) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(chosen.line(), style = TextStyles.PageLine, color = Tt.colors.text2)
}

/**
 * The ready reviews of a detailed request: optional, one per review bought at most. Each goes to one
 * reviewer only, who may use it, change it or write their own, and the (i) says so. [errors] is each
 * field's problem in place; [spotAsk] goes up each time the page's hint asks for them, which scrolls
 * to the first one with a problem, or to the line asking for fewer.
 */
@Composable
internal fun AskReadyTexts(
    texts: List<String>,
    errors: List<String?>,
    max: Int,
    enabled: Boolean,
    spotAsk: Int,
    onAdd: () -> Unit,
    onEdit: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val t = Tt.colors
    val least = FeedRules.Feedback.DETAILED.chars
    val spots = remember(texts.size) { List(texts.size) { BringIntoViewRequester() } }
    val overSpot = remember { BringIntoViewRequester() }
    LaunchedEffect(spotAsk) {
        if (spotAsk == 0) return@LaunchedEffect
        val bad = errors.indexOfFirst { it != null }
        when {
            bad in spots.indices -> spots[bad].bringIntoView()
            texts.size > max -> overSpot.bringIntoView()
        }
    }
    Spacer(Modifier.height(18.dp))
    InfoLine(
        AnnotatedString("Ready reviews give a reviewer a text to start from."),
        icon = Lucide.Pencil,
        info =
            buildAnnotatedString {
                append("Reviewers don't have to use these. Each one goes to ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = t.text)) { append("one reviewer only") }
                append(", who can use it, change it or write their own.")
            },
        infoTitle = "About ready reviews",
        infoLook = InfoDotLook.ACCENT,
    )
    texts.forEachIndexed { i, text ->
        Spacer(Modifier.height(if (i == 0) 18.dp else 16.dp))
        OutlinedField(
            value = text,
            onValueChange = { onEdit(i, it.take(FeedRules.WRITTEN_MAX)) },
            label = "Ready review ${i + 1}",
            modifier = Modifier.bringIntoViewRequester(spots.getOrElse(i) { overSpot }),
            placeholder = "At least $least characters",
            error = errors.getOrNull(i),
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            maxLength = FeedRules.WRITTEN_MAX,
            enabled = enabled,
            trailing = {
                IconButton(onClick = { onRemove(i) }, enabled = enabled, modifier = Modifier.size(36.dp)) {
                    Icon(Lucide.X, "Remove ready review ${i + 1}", Modifier.size(18.dp), tint = t.text4)
                }
            },
        )
    }
    when {
        texts.size > max ->
            IconLine(
                Lucide.CircleAlert,
                t.bad,
                AnnotatedString("Remove ${texts.size - max}. There can be one for each review you ask for."),
                PageLineBold,
                t.bad,
                Modifier.padding(top = 14.dp).bringIntoViewRequester(overSpot),
                iconSize = 13.dp,
            )
        texts.size < max ->
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                RowPill(
                    "Add a ready review",
                    PillKind.SOFT,
                    modifier = if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA),
                    leading = Lucide.Plus,
                    size = PillSize.SMALL,
                    touchTarget = false,
                    onClick = { if (enabled) onAdd() },
                )
                Spacer(Modifier.width(10.dp))
                Text("${texts.size} of $max", style = CountLine, color = t.text3)
            }
        else ->
            Text(
                "One for each review you asked for.",
                Modifier.padding(top = 12.dp),
                style = TextStyles.PageLine,
                color = t.text3,
            )
    }
}

/** Characters as the server counts them: spaces between words count once, ends not at all. */
internal fun plainLength(text: String): Int =
    text
        .trim()
        .split(Regex("\\s+"))
        .joinToString(" ")
        .length
