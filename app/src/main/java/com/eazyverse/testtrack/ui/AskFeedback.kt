package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
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

private val LevelName = tileStyle(15f, 18f, FontWeight.SemiBold)
private val LevelNameChosen = tileStyle(15f, 18f, FontWeight.Bold)
private val LevelFrom = tileStyle(12f, 15f, FontWeight.Normal)
private val PageLine = tileStyle(13f, 18f, FontWeight.Normal)
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
            LevelTile(level, level == chosen, enabled, { onChoose(level) }, Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(chosen.line(), style = PageLine, color = Tt.colors.text2)
}

@Composable
private fun LevelTile(
    level: FeedRules.Feedback,
    chosen: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(TileRadius)
    Box(
        modifier
            .clip(shape)
            .background(if (chosen) t.leafAccentSoft else t.bg)
            .border(1.dp, if (chosen) Color.Transparent else t.cardLine, shape)
            .selectable(selected = chosen, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .then(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA)),
    ) {
        // The design's 11/12/10 inside its 1dp line, which this border draws inside the tile.
        Column(Modifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, top = 12.dp, bottom = 11.dp)) {
            Icon(level.icon(), null, Modifier.size(18.dp), tint = t.tileInk)
            Spacer(Modifier.height(6.dp))
            Text(
                level.tileName(),
                style = if (chosen) LevelNameChosen else LevelName,
                color = if (chosen) t.tabSelected else t.text,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text("From ${level.least} tix", style = LevelFrom, color = t.text3, maxLines = 1)
        }
        if (chosen) {
            Icon(
                Lucide.Check,
                null,
                Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 11.dp).size(15.dp),
                tint = t.tabSelected,
            )
        }
    }
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
        Lucide.Pencil,
        AnnotatedString("Ready reviews give a reviewer a text to start from."),
        title = "About ready reviews",
    ) {
        Text(
            buildAnnotatedString {
                append("Reviewers don't have to use these. Each one goes to ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = t.text)) { append("one reviewer only") }
                append(", who can use it, change it or write their own.")
            },
            style = PageLine,
            color = t.text2,
        )
    }
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
                SoftPill(
                    "Add a ready review",
                    icon = Lucide.Plus,
                    onClick = { if (enabled) onAdd() },
                    modifier = if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA),
                )
                Spacer(Modifier.width(10.dp))
                Text("${texts.size} of $max", style = CountLine, color = t.text3)
            }
        else ->
            Text(
                "One for each review you asked for.",
                Modifier.padding(top = 12.dp),
                style = PageLine,
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
