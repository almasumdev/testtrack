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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.HeartHandshake
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Megaphone
import com.composables.icons.lucide.MessageSquareText
import com.composables.icons.lucide.Rocket
import com.composables.icons.lucide.UserPlus
import com.eazyverse.testtrack.R
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
 * Filled in the soft accent tile, the ground of the cards' own Review chips, rather than the card
 * surface or the page ground. Over a moving list it has to read as a plate laid over the cards and
 * as something to tap: on the card surface it was one more card drifting loose, and on the page
 * ground it read grey and switched off.
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

/**
 * One request, anonymous: the app's face, its name, which kind it is, and what it pays.
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
    // In progress and waiting count as taken: the server says what is left.
    val open = post.slotsOpen ?: (post.reviewsBought - post.reviewsDone)
    // The same cases that show something to do, so the card and its corner always agree.
    val actionable = !post.mine && post.myState != "approved" && !(post.myState == null && open <= 0)
    FeedCard(onClick = if (actionable) onReview else null, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // As tall as the name and the pill together, as the app's header on its review is.
            AppMark(post.packageName, post.appName, post.icon, size = HEADER_ICON)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    post.appName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Which kind decides how it is reviewed. Never whose it is: requests are anonymous,
                // and what the developer asks is on the review itself.
                KindPill(if (post.isLive) CheckKind.PLAY else CheckKind.CLOSED)
            }
            Spacer(Modifier.width(10.dp))
            when {
                post.mine -> Unit
                post.myState == "approved" -> Pill("Done", Status.posted, Status.postedSoft)
                post.myState == null && open <= 0 -> Pill("Full", Tt.colors.text3, Status.neutralSoft)
                post.myState in Underway ->
                    Pill(
                        "Carry on",
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.primaryContainer,
                    )
                post.myState != null ->
                    Pill(
                        "See it",
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.primaryContainer,
                    )
                else -> TixReward(post.reward)
            }
        }
    }
}

/**
 * What a review pays: the tix ticket itself, as the top bar draws it, with the number in it, and a
 * streak of light across it, as if it caught the light.
 */
@Composable
private fun TixReward(tix: Int) {
    val shine = Tt.colors.ticketShine
    Box(
        Modifier
            .clearAndSetSemantics { contentDescription = "$tix tix" }
            .size(width = 52.dp, height = 36.dp)
            .paint(painterResource(R.drawable.tix_ticket_blank))
            .drawWithContent {
                drawContent()
                // Inside the navy edge only, leaning like light through a window.
                val edge = 3.dp.toPx()
                val inside =
                    Path().apply {
                        addRoundRect(
                            RoundRect(edge, edge, size.width - edge, size.height - edge, CornerRadius(4.dp.toPx())),
                        )
                    }
                clipPath(inside) {
                    rotate(22f) {
                        val x = size.width * 0.14f
                        val w = 8.dp.toPx()
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(shine.copy(alpha = 0f), shine.copy(alpha = 0.7f), shine.copy(alpha = 0f)),
                                startX = x,
                                endX = x + w,
                            ),
                            topLeft = Offset(x, -size.height),
                            size =
                                androidx.compose.ui.geometry
                                    .Size(w, size.height * 3f),
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$tix",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = if (tix >= 1000) 13.sp else 17.sp, fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.ExtraBold,
            color = Tt.colors.ticketInk,
            maxLines = 1,
        )
    }
}

/** A review of the reader's own still being worked on, and one sent and not yet settled. */
private val Underway = setOf("claimed", "installed", "used")
private val Sent = setOf("submitted", "disputed")

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
