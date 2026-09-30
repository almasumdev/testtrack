package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.HeartHandshake
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Megaphone
import com.composables.icons.lucide.Rocket
import com.composables.icons.lucide.UserPlus
import com.eazyverse.testtrack.net.ApiPost
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
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                accent ?: MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp),
            ).padding(padding),
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
private const val POST_LABEL = "Post an app"

/**
 * A dashed outline, in the vocabulary [RowDivider] already established.
 *
 * Dashed says "nothing here yet, put something in it" in a way no solid border does, which is
 * exactly what this control is: the empty slot at the top of your own posts. It is also the one
 * shape on the screen that is not a card, so it never reads as a post somebody wrote.
 *
 * Inset by half the stroke, because a stroke is drawn centred on the bounds and the outer half of
 * it would otherwise be clipped away, leaving a line that looks thinner on the corners.
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
 * The way in to posting, sitting where the first post would be.
 *
 * Sized like a slot rather than a button, because that is what it is: the gap at the top of the
 * list where your own post is going to go. Deep enough to read as a space waiting to be filled,
 * short enough that it does not hold the first real post below the fold. A control this size
 * reading as loud as it is big would fight every card under it, so the word in it is set large
 * and quiet instead. That pairing is what says placeholder rather than action.
 *
 * The label and nothing else. What it costs used to sit under it and now lives in the dialog the
 * tap opens, which is the last moment before anything is spent and so the only moment the number
 * has to be true by.
 */
@Composable
fun FeedAskButton(
    modifier: Modifier = Modifier,
    onPost: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clip(RoundedCornerShape(14.dp))
            .dashedBorder(MaterialTheme.colorScheme.outline, 14.dp)
            .clickable(onClick = onPost)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            POST_LABEL,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The same way in again, once [FeedAskButton] has scrolled off.
 *
 * The same bar, the same width, the same dashes and the same word, at a third of the height. Not
 * a floating circle with a plus in it, which would have arrived as a new action rather than as
 * the one already offered at the top of the page: this is that control following you down, and
 * the only thing it gives up on the way is the room to be tall.
 *
 * Filled in the page ground rather than the card surface, because it sits over a moving list of
 * cards and needs to read as a plate laid over them, not as one more of them drifting loose.
 */
@Composable
fun FeedAskBar(onPost: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background)
            .dashedBorder(MaterialTheme.colorScheme.outline, 14.dp)
            .clickable(onClick = onPost)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            POST_LABEL,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One developer's ask.
 *
 * The app is the headline and the ask is what it says. A list you scan by app, not by question:
 * you recognise the icon and the name first, and read what is being asked once one of them has
 * stopped you.
 *
 * The line above the button is the one thing that varies and so the one thing worth printing: how
 * many of the reviews this post bought are still going, and whether this reader is part way
 * through one of them already.
 */
@Composable
fun FeedRequestCard(
    post: ApiPost,
    onReview: () -> Unit,
) {
    FeedCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppTile(post.appName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    post.appName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (post.mine) "Your post" else "Posted by ${post.ownerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            // Live on Play means a public review, which is a different job from writing notes on
            // something still in closed testing, so the card says which before anybody starts.
            Pill(
                if (post.isLive) "On Play" else "Closed test",
                MaterialTheme.colorScheme.onSurfaceVariant,
                Status.neutralSoft,
            )
        }

        Spacer(Modifier.height(11.dp))
        Text(
            post.ask,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(13.dp))
        CardRule()
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    post.mine -> "${post.reviewsDone} of ${post.reviewsBought} done"
                    post.myState != null -> "You are part way through this one"
                    else -> "${post.reviewsBought - post.reviewsDone} still going"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            when {
                post.mine -> Unit
                post.myState == "approved" -> Pill("Done", Status.posted, Status.postedSoft)
                else ->
                    ActionChip(
                        if (post.myState == null) "Review · +${post.reward}" else "Carry on",
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.primaryContainer,
                        onReview,
                    )
            }
        }
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
            FeedEventKind.SHIPPED, FeedEventKind.MILESTONE -> Status.posted
            FeedEventKind.STARTED, FeedEventKind.JOINED, FeedEventKind.ANNOUNCEMENT ->
                MaterialTheme.colorScheme.primary
            FeedEventKind.STREAK, FeedEventKind.HELPED -> Status.short
            FeedEventKind.HALFWAY -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val icon =
        when (event.kind) {
            FeedEventKind.STARTED -> Lucide.Rocket
            FeedEventKind.HALFWAY -> Lucide.Gauge
            FeedEventKind.MILESTONE -> Lucide.Flag
            FeedEventKind.SHIPPED -> Lucide.CircleCheck
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
