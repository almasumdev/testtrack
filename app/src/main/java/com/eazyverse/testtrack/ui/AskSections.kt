package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.Config
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AskFailure

private val PageLine = tileStyle(13f, 18f, FontWeight.Normal)
private val DeveloperStyle = tileStyle(12.5f, 14f, FontWeight.Medium)
private val PriceLine = tileStyle(12.5f, 14f, FontWeight.Medium)
private val PriceLineBold = tileStyle(12.5f, 14f, FontWeight.SemiBold)
private val HintStyle = tileStyle(12.5f, 16f, FontWeight.SemiBold)

@Composable
internal fun AskAvailability(
    isLive: Boolean,
    onChange: (Boolean) -> Unit,
) = TwoChoice("Where can people try your app?", "Live on Play", "Closed testing", isLive, onChange)

/** A question with two answers side by side, the one chosen filled. True is the first answer. */
@Composable
internal fun TwoChoice(
    question: String,
    first: String,
    second: String,
    firstChosen: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Text(
        question,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = Tt.colors.text,
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(true to first, false to second).forEach { (isFirst, label) ->
            val selected = firstChosen == isFirst
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (selected) Tt.colors.tile else androidx.compose.ui.graphics.Color.Transparent)
                    .border(1.dp, if (selected) Tt.colors.tileInk else Tt.colors.glassBorderStrong, shape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange(isFirst) })
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Tt.colors.tileInk else Tt.colors.text2)
            }
        }
    }
}

@Composable
internal fun AskMessage(failure: AskFailure) {
    Row(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Amber when the fault is ours and there is a way on; red when something needs fixing.
        val tone = if (failure.ours) Tt.colors.warn else Tt.colors.bad
        Icon(Lucide.CircleAlert, "Error", Modifier.size(20.dp), tint = tone)
        Column {
            Text(failure.title, color = tone, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(failure.detail, color = tone, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun AskAppPlaceholder() {
    Box(Modifier.fillMaxSize().background(Tt.colors.tile), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.testtrack_robot), null, Modifier.size(36.dp))
    }
}

/**
 * The app a request is for, as the seat page's head without the rename: its icon with where it is
 * found on the corner, its name on up to two lines and its developer.
 */
@Composable
internal fun AskAppHead(app: AppEntry) {
    val t = Tt.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(AppTileIcon)) {
            AppMark(app.pkg, app.name, app.icon, size = AppTileIcon, ownIcon = true)
            SourceMark(live = app.live, Modifier.align(Alignment.BottomEnd).offset(5.dp, 5.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, style = AppTileName, color = t.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(app.developer.ifBlank { app.pkg }, style = DeveloperStyle, color = t.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * What testers do with the app, said before anything is posted. A live app adds the part an owner
 * should decide knowingly: Google does not allow rewarding reviews. An app nothing has verified
 * says so in amber, with what to do behind the (i).
 */
@Composable
internal fun AskTrackLines(
    live: Boolean,
    verified: Boolean,
) {
    val t = Tt.colors
    val bold = SpanStyle(fontWeight = FontWeight.SemiBold, color = t.text)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (live) {
            InfoLine(Lucide.Star, AnnotatedString("Testers use it on two days, then rate and review it on Google Play."))
            InfoLine(
                Lucide.TriangleAlert,
                AnnotatedString("Google doesn't allow rewarding reviews and may remove ones it doubts."),
                warn = true,
                title = "Why reviews come in slowly",
            ) {
                Text(
                    buildAnnotatedString {
                        append("So the reviews come in slowly, the way real users leave them. Whether to ask is ")
                        withStyle(bold) { append("your decision") }
                        append(".")
                    },
                    style = PageLine,
                    color = t.text2,
                )
            }
        } else {
            InfoLine(
                Lucide.MessageSquare,
                AnnotatedString(
                    "Testers use it for ${FeedRules.CLOSED_USE_SECONDS / 60} minutes, then send private feedback on " +
                        "Google Play. You get a picture of it here.",
                ),
            )
        }
        if (!verified && live) {
            InfoLine(Lucide.CircleAlert, AnnotatedString("Waiting for Play."), warn = true, title = "Waiting for Play") {
                Text("Google Play didn't answer when you added it. We keep asking.", style = PageLine, color = t.text2)
            }
        } else if (!verified) {
            InfoLine(Lucide.CircleAlert, AnnotatedString("Not checked on Play yet."), warn = true, title = "Add our testers group") {
                Text(
                    buildAnnotatedString {
                        append("Add ")
                        withStyle(bold) { append(Config.GROUP_EMAIL) }
                        append(" to your closed track's testers, or reviewers can't install it.")
                    },
                    style = PageLine,
                    color = t.text2,
                )
            }
        }
    }
}

/**
 * A page line with its icon as the first glyph. With [more] it ends on an (i) that opens a callout
 * holding the rest; [warn] puts the icon in amber and the words in the strongest ink.
 */
@Composable
internal fun InfoLine(
    icon: ImageVector,
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    warn: Boolean = false,
    title: String = "",
    more: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val t = Tt.colors
    var open by remember { mutableStateOf(false) }
    IconLine(
        icon,
        if (warn) t.warn else t.text3,
        text,
        PageLine,
        if (warn) t.text else t.text2,
        modifier,
        trailing =
            more?.let { body ->
                {
                    Box {
                        Box(
                            Modifier
                                .requiredSize(22.dp)
                                .clip(CircleShape)
                                .then(if (open) Modifier.background(t.accentInk.copy(alpha = 0.1f)) else Modifier)
                                .clickable(role = Role.Button, onClickLabel = title) { open = !open },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Lucide.Info, title, Modifier.size(14.dp), tint = if (open) t.accentInk else t.text3)
                        }
                        Callout(open, { open = false }, title, iconInset = 4.dp, content = body)
                    }
                }
            },
        trailingWidth = 26.dp,
    )
}

/**
 * What the request costs and what there is to pay it from, laid out as the seat page's price: the
 * coins tile, the total, and the balance under it, amber with Circle one tap away when it is short.
 * The balance is a skeleton until it is read. Then the rule about paying now.
 */
@Composable
internal fun AskPrice(
    reviews: Int,
    cost: Int,
    tix: Int?,
    onOpenCircle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    val short = tix != null && cost > tix
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(t.tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Coins, null, Modifier.size(20.dp), tint = t.tileInk)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "$reviews ${if (reviews == 1) "review" else "reviews"}, $cost tix",
                style = AppTileName,
                color = t.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (tix == null) 3.dp else 2.dp))
            when {
                tix == null -> Skeleton(width = 110.dp, height = 12.dp)
                short ->
                    Text("You have $tix tix, ${cost - tix} short", style = PriceLineBold, color = t.warn, maxLines = 1)
                else -> Text("You have $tix tix", style = PriceLine, color = t.text3, maxLines = 1)
            }
        }
        if (short) {
            Spacer(Modifier.width(8.dp))
            SoftPill("Open Circle", onClick = onOpenCircle)
        }
    }
    Spacer(Modifier.height(14.dp))
    // The page's rule as drawn: a 4dp dot in text3, 10dp before the words.
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(t.text3),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Paid now. Close the request early and the tix for open reviews come back.",
            Modifier.weight(1f),
            style = PageLine,
            color = t.text2,
        )
    }
}

/** The line over the button while something stops the request, centred; a tap goes to it. */
@Composable
internal fun AskHint(
    block: AskBlock,
    shortBy: Int,
    onSpot: () -> Unit,
) {
    val t = Tt.colors
    val (icon, tone, text) =
        when (block) {
            AskBlock.SHORT -> Triple(Lucide.TriangleAlert, t.warn, "$shortBy tix short")
            AskBlock.READY -> Triple(Lucide.CircleAlert, t.accentInk, "Fix the ready reviews")
        }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 20.dp)
            .clickable(role = Role.Button, onClick = onSpot),
        contentAlignment = Alignment.Center,
    ) {
        IconLine(icon, tone, AnnotatedString(text), HintStyle, tone, textAlign = TextAlign.Center, maxLines = 1)
    }
}
