package com.eazyverse.testtrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import com.eazyverse.testtrack.domain.ChatState
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular

/*
 * The ways into a crew's chat: a floating Chat button on the crew's page, and its unread count on
 * the crew tiles, where a tap opens it too, and the Crew tab's segments.
 */

/** Room under a list for the floating button, so it never sits over the last row. */
private val FabRoom = 52.dp + 22.dp + 8.dp

/** How a crew's page offers its chat, or doesn't. */
internal enum class ChatEntry { NONE, OPEN, READ_ONLY }

internal fun chatEntryOf(
    crew: Crew?,
    pruned: Boolean = false,
): ChatEntry =
    when {
        crew == null || crew.chatState == ChatState.OFF || crew.chatState == ChatState.GONE -> ChatEntry.NONE
        crew.phase == CrewPhase.RECORD -> if (pruned || crew.pruned) ChatEntry.NONE else ChatEntry.READ_ONLY
        crew.phase == CrewPhase.LOBBY || crew.phase == CrewPhase.RUN -> ChatEntry.OPEN
        else -> ChatEntry.NONE
    }

/**
 * [content], with the chat's button over its bottom end and room left under its list for it. The
 * button folds to a round one while the list scrolls down and opens out again on the way back up.
 */
@Composable
internal fun CrewChatEntry(
    crew: Crew?,
    pruned: Boolean,
    onChat: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val entry = chatEntryOf(crew, pruned)
    var wide by remember { mutableStateOf(true) }
    val watch =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    when {
                        available.y < -2f -> wide = false
                        available.y > 2f -> wide = true
                    }
                    return Offset.Zero
                }
            }
        }
    Box(modifier.fillMaxSize().nestedScroll(watch)) {
        if (entry == ChatEntry.NONE) {
            content()
            return@Box
        }
        CompositionLocalProvider(LocalDockRoom provides dockRoom() + FabRoom) { content() }
        ChatFab(entry, crew?.chatUnread ?: 0, wide, onChat)
    }
}

/** The button itself: filled with the unread count on a crew still going, glass with a lock once it is over. */
@Composable
private fun BoxScope.ChatFab(
    entry: ChatEntry,
    unread: Int,
    wide: Boolean,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    val readOnly = entry == ChatEntry.READ_ONLY
    val shape = RoundedCornerShape(26.dp)
    val count = if (readOnly) 0 else unread
    Row(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = Gutter, bottom = 22.dp + dockRoom())
            .height(52.dp)
            .defaultMinSize(minWidth = 52.dp)
            .shadow(if (readOnly) 0.dp else 8.dp, shape)
            .clip(shape)
            .background(if (readOnly) t.glass2 else t.primary)
            .then(if (readOnly) Modifier.border(1.dp, t.glassBorder, shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription =
                    when {
                        readOnly -> "Chat, read only"
                        count > 0 -> "Chat, $count unread"
                        else -> "Chat"
                    }
            }.padding(start = if (wide) 18.dp else 16.dp, end = if (wide && count > 0) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (readOnly) t.text else t.onPrimary
        Box {
            Icon(Lucide.MessageCircle, null, Modifier.size(if (readOnly) 19.dp else 20.dp), tint = ink)
            // Folded, the count is a dot on the icon.
            if (!wide && count > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-3).dp)
                        .size(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(t.onPrimary),
                )
            }
        }
        AnimatedVisibility(wide, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Text("Chat", style = chatStyle(15.5f, 20f), fontWeight = FontWeight.Bold, color = ink)
                if (readOnly) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Lucide.Lock, null, Modifier.size(14.dp), tint = t.text3)
                } else if (count > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .defaultMinSize(minWidth = 24.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(t.onPrimary)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (count > 99) "99+" else "$count",
                            style = chatStyle(12.5f, 24f).tabular(),
                            fontWeight = FontWeight.Bold,
                            color = t.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Unread chat as a small pill: the chat mark and the count, in the badge fill, or quiet with a
 * crossed-out bell when the chat is muted. [small] is the size for a tab segment; the crew tiles
 * use [CrewChatTileButton].
 */
@Composable
internal fun ChatBadge(
    count: Int,
    muted: Boolean = false,
    small: Boolean = false,
) {
    val t = Tt.colors
    val height: Dp = if (small) 17.dp else 24.dp
    Row(
        Modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(if (muted) t.tile else t.badgeFill)
            .padding(start = if (small) 3.dp else 6.dp, end = if (small) 4.dp else 8.dp)
            .semantics { contentDescription = if (muted) "$count unread, muted" else "$count unread in chat" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (muted) t.text3 else t.onAccent
        Icon(if (muted) Lucide.BellOff else Lucide.MessageCircle, null, Modifier.size(if (small) 10.dp else 16.dp), tint = ink)
        Spacer(Modifier.width(if (small) 1.dp else 4.dp))
        Text(
            if (count > 99) "99+" else "$count",
            style = chatStyle(if (small) 10.5f else 14f, if (small) 17f else 24f).tabular().copy(letterSpacing = 0.sp),
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1,
        )
    }
}

/** The chat mark's slot on a crew tile: the same 28dp as the tile's app icon, so both sit on one centre line. */
private val TileMark = 28.dp

/** The tap area around it, larger than the mark without making the row any taller. */
private val TileTouch = 40.dp

/**
 * A crew tile's way into its chat: the chat mark with the unread count on its corner, "9+" past
 * nine and no count at all when nothing is new. Quiet grey when the chat is muted.
 */
@Composable
internal fun CrewChatTileButton(
    crew: Crew,
    onClick: () -> Unit,
) {
    if (chatEntryOf(crew) == ChatEntry.NONE) return
    val t = Tt.colors
    val count = crew.chatUnread.coerceAtLeast(0)
    val muted = crew.chatMuted
    val said =
        when {
            count == 0 -> "Open chat"
            muted -> "Open chat, muted, $count unread"
            else -> "Open chat, $count unread"
        }
    Box(
        Modifier
            .size(TileMark)
            // Laid out at the mark's size, touched at the larger one around it.
            .wrapContentSize(unbounded = true)
            .size(TileTouch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = said },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(TileMark), contentAlignment = Alignment.Center) {
            Icon(Lucide.MessageCircle, null, Modifier.size(24.dp), tint = t.text2)
            if (count > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .defaultMinSize(minWidth = 18.dp)
                        .height(18.dp)
                        .clip(CircleShape)
                        // A ring in the tile's colour, so the count reads apart from the mark under it.
                        .background(t.bg)
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(if (muted) t.text3 else t.badgeFill)
                        .padding(horizontal = 3.5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (count > 9) "9+" else "$count",
                        style = chatStyle(10f, 15f).tabular().copy(letterSpacing = 0.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (muted) t.bg else t.onAccent,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Unread chat over the Lobby's or the Run's crews, leaving muted chats out, for the segment's badge. */
internal fun chatUnreadOf(
    crews: List<Crew>,
    phase: CrewPhase,
): Int =
    if (phase != CrewPhase.LOBBY && phase != CrewPhase.RUN) {
        0
    } else {
        crews.filter { it.phase == phase && !it.chatMuted && it.chatState != ChatState.OFF }.sumOf { it.chatUnread }
    }
