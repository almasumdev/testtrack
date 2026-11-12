package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.HeartHandshake
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Megaphone
import com.composables.icons.lucide.MessageSquareText
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Rocket
import com.composables.icons.lucide.UserPlus
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
    /** A post needs room to breathe; a one line event does not. Same edge either way. */
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    /** A card that does one thing does it from anywhere on it, not only from its chip. */
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(Tt.colors.glass)
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
 * A dashed outline, in the vocabulary [RowDivider] already established.
 *
 * Dashed says "nothing here yet, put something in it", which is what this control is. Inset by
 * half the stroke, because a stroke is drawn centred on the bounds and the outer half of it would
 * otherwise be clipped away.
 */
private fun Modifier.dashedBorder(
    color: Color,
    corner: Dp,
    width: Dp = 1.5.dp,
) = drawBehind {
    val inset = width.toPx() / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2f, size.height - inset * 2f),
        cornerRadius = CornerRadius(corner.toPx()),
        style =
            Stroke(
                width = width.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
            ),
    )
}

/**
 * The same way in again, once [FeedAskRow] has scrolled off.
 *
 * The same dashed bar and the same word, full width. Not a floating circle with a plus in it, which
 * would have arrived as a new action rather than as the one already offered at the top of the page:
 * this is that control following you down.
 *
 * Filled in the soft accent tile, the ground of the cards' own Review chips, so it reads as a
 * plate laid over the cards and as something to tap, not one more card drifting loose.
 */
@Composable
fun FeedAskBar(onPost: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Tt.colors.tile)
            .dashedBorder(MaterialTheme.colorScheme.outline, 14.dp)
            .clickable(onClick = onPost)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            POST_LABEL,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Tt.colors.tileInk,
        )
    }
}

private val RequestTitle = TextStyle(fontFamily = Barlow, fontSize = 17.sp, lineHeight = 22.sp)
private val RequestNote = TextStyle(fontFamily = Barlow, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)

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
    val t = Tt.colors
    val shape = remember { TicketShape() }
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
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(t.card)
            .border(1.dp, t.cardLine, shape)
            .then(if (post.mine) Modifier else Modifier.clickable(onClick = onReview)),
    ) {
        Row(
            Modifier.weight(1f).padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppMark(post.packageName, post.appName, post.icon, size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    post.appName,
                    style = RequestTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = t.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(note, style = RequestNote, color = t.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TicketStub(post.reward, post.mine)
    }
}

/** The uppercase word at the top of a card saying which kind of post this is. */
@Composable
private fun KindLabel(
    text: String,
    icon: ImageVector,
    tone: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(13.dp), tint = tone)
        Spacer(Modifier.width(5.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = tone,
        )
    }
}

/** Who was in it, without listing fourteen names nobody reads. */
@Composable
private fun FaceStack(
    faces: List<String>,
    more: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        faces.forEach {
            Initial(it, size = 26.dp)
            Spacer(Modifier.width(5.dp))
        }
        if (more > 0) {
            Text(
                "and $more more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Something that happened, written by the app rather than by a person.
 *
 * The kind in small caps, the event, what it leaves out, and who it was. Four things, in that
 * order, because that is the order they are wanted in: the label says whether this is worth
 * reading, the headline is the thing itself, the detail is there if it was, and the faces are who
 * to recognise.
 *
 * The one change from how this started is the edge. Two of the eight kinds used to have the card
 * outlined in their own tone, and eight cards down a screen with two of them ringed in colour made
 * the rectangle the loudest thing on the page. The border is [ColorScheme.outlineVariant] on every
 * card here and on every card in Feedback, so both tabs are plainly one screen; the tone still
 * lands, on the glyph and the word at the top where it belongs.
 */
@Composable
fun FeedEventCard(event: FeedEvent) {
    // Four tones for seven kinds, grouped by what sort of thing happened rather than one each:
    // green for a fortnight ending well, amber for one person's own record, the muted tone for a
    // cohort still on its way, and the accent for anything addressed to the reader now, which is
    // a group opening, somebody arriving, and the one message a person actually wrote.
    val tone =
        when (event.kind) {
            FeedEventKind.MILESTONE -> Status.posted
            FeedEventKind.STARTED, FeedEventKind.JOINED, FeedEventKind.FIRST_REVIEW, FeedEventKind.ANNOUNCEMENT ->
                MaterialTheme.colorScheme.primary
            FeedEventKind.STREAK, FeedEventKind.HELPED -> Status.short
            FeedEventKind.HALFWAY -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val icon =
        when (event.kind) {
            FeedEventKind.STARTED -> Lucide.Rocket
            FeedEventKind.HALFWAY -> Lucide.Gauge
            FeedEventKind.MILESTONE -> Lucide.Flag
            FeedEventKind.FIRST_REVIEW -> Lucide.MessageSquareText
            FeedEventKind.STREAK -> Lucide.Flame
            FeedEventKind.JOINED -> Lucide.UserPlus
            FeedEventKind.HELPED -> Lucide.HeartHandshake
            FeedEventKind.ANNOUNCEMENT -> Lucide.Megaphone
        }

    FeedCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KindLabel(event.kind.label, icon, tone)
            Spacer(Modifier.weight(1f))
            Text(
                feedAge(event.happenedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            event.headline,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            event.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (event.faces.isNotEmpty() || event.tix > 0) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FaceStack(event.faces, event.moreFaces)
                if (event.tix > 0) {
                    Pill("+${event.tix} tix", Status.short, Status.shortSoft)
                }
            }
        }
    }
}
