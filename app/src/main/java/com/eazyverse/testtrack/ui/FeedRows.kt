package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.ui.theme.Barlow
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * The feed's own container.
 *
 * A bordered card, where every list elsewhere in the app is rows on the page ground separated by a
 * dashed rule. That is a deliberate split rather than an inconsistency: a group screen is one list
 * of fourteen like things, and a feed is a stack of unlike posts by different people, where the
 * boundary between two of them is the thing a reader needs most. Bordered rather than raised, so
 * it stays flat like the rest of the app instead of arriving as a floating slab.
 */
@Composable
fun FeedCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    /** A post needs room to breathe. */
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    /** A card that does one thing does it from anywhere on it, not only from its chip. */
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(Tt.colors.bg)
            .border(
                1.dp,
                accent ?: Tt.colors.glassBorder,
                RoundedCornerShape(CardRadius),
            ).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** The rule inside a card, between what a post is and what you can do about it. */
@Composable
fun CardRule() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * An app's face on a card, before there is a real icon to draw.
 *
 * [AppIcon] cannot help here. It reads the icon out of PackageManager, which works everywhere else
 * in this app because everywhere else the app is one you are testing and therefore installed. In
 * Circle it never is: not having it yet is the reason the post exists. So this stands in until a
 * post carries an icon URL and Coil draws it, which is a field on the model and a swap of this one
 * composable.
 *
 * Tinted from the name rather than left grey, which is the whole difference between a mark and a
 * missing image. Three grey letter tiles down a list read as three things that failed to load;
 * three coloured ones read as three different apps, which is the job the icon does in a list
 * anyway. Hue comes off the name so it is stable across sessions and devices with nothing stored,
 * and saturation and lightness are fixed, so every tile has the same weight and they look like a
 * set rather than a paint box.
 *
 * Deliberately not built from the status tones. Green, amber and rose all mean something specific
 * elsewhere in this app, and one of them means it on this very card, where a reviewed post carries
 * a green Done pill.
 */
@Composable
fun AppTile(
    name: String,
    size: Dp = 44.dp,
) {
    val (fill, ink) = Tt.colors.nameTile((name.hashCode().rem(360) + 360).rem(360).toFloat())
    Box(
        Modifier
            .size(size)
            // The same corner ratio AppIcon rounds a real icon by, so nothing shifts when one
            // takes this one's place.
            .clip(RoundedCornerShape(size * 0.3f))
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ink,
        )
    }
}

/** What both ways in to posting are labelled. One word for one action, in one place. */
private const val POST_LABEL = "Ask for feedback"

/** The name line and the grey line under it, on a request and on the row that asks for one. */
private val RowTitle = TextStyle(fontFamily = Barlow, fontSize = 16.sp, lineHeight = 21.sp)
private val RowNote = TextStyle(fontFamily = Barlow, fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal)

/**
 * The way in to posting, a row at the top of the list.
 *
 * Laid out like a request under it, an icon tile, two lines and a chevron, so it reads as the first
 * thing on the list rather than as a button. What sets it apart is the missing fill: an outline
 * with nothing in it is the slot your own request goes into.
 *
 * The label and nothing else about cost. What it costs lives in the dialog the tap opens, which is
 * the last moment before anything is spent and so the only moment the number has to be true by.
 */
@Composable
fun FeedAskRow(
    modifier: Modifier = Modifier,
    onPost: () -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, t.cardLine, shape)
            .clickable(onClick = onPost)
            .padding(start = 10.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(t.tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Plus, null, Modifier.size(20.dp), tint = t.tileInk)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                POST_LABEL,
                style = RowTitle,
                fontWeight = FontWeight.SemiBold,
                color = t.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Get reviews of your own app",
                style = RowNote,
                color = t.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = t.text4)
    }
}

/**
 * The same way in again, once [FeedAskRow] has scrolled off.
 *
 * The same word, full width. Not a floating circle with a plus in it, which would have arrived as a
 * new action rather than as the one already offered at the top of the page: this is that control
 * following you down.
 *
 * Filled with the page colour, because it floats over the list and cannot be see-through, so it
 * reads as the same unfilled box as [FeedAskRow] at the top and the dock under it, with the same
 * hairline. The label is in the accent ink the stubs use.
 */
@Composable
fun FeedAskBar(onPost: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Tt.colors.bg)
            .border(1.dp, Tt.colors.cardLine, RoundedCornerShape(14.dp))
            .clickable(onClick = onPost)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            POST_LABEL,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Tt.colors.tabSelected,
        )
    }
}

/**
 * One request, anonymous: the app's face, its name, which kind it is, and what it pays, drawn as a
 * ticket. The pay is the stub on the right, torn off along a dashed line between two bites out of
 * the card's edge.
 *
 * Decided October 2026: no owner's name (it invites reviewing friends), no slot count (the feed
 * only lists requests the reader can take), and no question (the review screen shows it). The
 * whole card is the way in.
 */
@Composable
fun FeedRequestCard(
    post: ApiPost,
    onReview: () -> Unit,
) {
    // Which kind decides how it is reviewed. Never whose it is: requests are anonymous, and what
    // the developer asks is on the review itself.
    val kind = if (post.isLive) CheckKind.PLAY.label else CheckKind.CLOSED.label
    val note =
        when (FeedRules.Feedback.of(post.feedback)) {
            FeedRules.Feedback.STARS -> kind
            FeedRules.Feedback.WRITTEN -> "$kind, written review"
            FeedRules.Feedback.DETAILED -> "$kind, detailed review"
        }
    // The list holds only requests the reader can still start (FeedViewModel.openPosts), so a
    // tile is the same from the moment it appears to the moment it is started and leaves.
    TicketCard(
        leading = { AppMark(post.packageName, post.appName, post.icon, size = 52.dp) },
        title = post.appName,
        note = note,
        onClick = if (post.mine) null else onReview,
        stub = { TicketStub(post.reward, post.mine) },
    )
}
