package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserX
import com.eazyverse.testtrack.domain.instantOf
import com.eazyverse.testtrack.domain.shortDate
import com.eazyverse.testtrack.viewmodel.BlockedMember
import com.eazyverse.testtrack.viewmodel.BlockedMembersViewModel

/**
 * The members you blocked, from Settings: who, since when, and Unblock on each row. Blocking is
 * done from a crew chat; this is where it is undone. An unblocked row simply goes.
 */
@Composable
fun BlockedMembersScreen(
    onBack: () -> Unit,
    vm: BlockedMembersViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { vm.load() }
    BlockedMembersBody(
        members = vm.members,
        failed = vm.failed,
        busy = vm.busy,
        onUnblock = vm::unblock,
        onRetry = vm::load,
        onBack = onBack,
    )
}

/** The page drawn from plain values, in every state it has. */
@Composable
fun BlockedMembersBody(
    members: List<BlockedMember>?,
    failed: String?,
    busy: String?,
    onUnblock: (BlockedMember) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { PageBar("Blocked members", onBack) },
    ) { padding ->
        val page = Modifier.fillMaxSize().padding(padding)
        when {
            members == null && failed != null -> Box(page) { RobotOffline("Blocked members didn't load", onRetry = onRetry) }
            members == null -> Box(page) { BlockedLoading() }
            members.isEmpty() ->
                Box(page) {
                    RobotState(
                        Prop.Lock,
                        "You haven't blocked anyone.",
                        "A member you block in a crew chat shows up here, so you can unblock them.",
                    )
                }
            else ->
                LazyColumn(
                    page,
                    contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = Gutter + dockRoom()),
                ) {
                    items(members, key = { it.userId }) { member ->
                        Box(
                            Modifier.padding(bottom = 8.dp),
                        ) { BlockedRow(member, busy = busy == member.userId, locked = busy != null, onUnblock) }
                    }
                }
        }
    }
}

@Composable
private fun BlockedRow(
    member: BlockedMember,
    busy: Boolean,
    locked: Boolean,
    onUnblock: (BlockedMember) -> Unit,
) {
    val since = instantOf(member.since)?.let { "Blocked ${shortDate(it)}" }
    TileRow(
        Lucide.UserX,
        member.name.ifBlank { "A member" },
        subtitle = since,
        leading = { Avatar(member.photo, initialOf(member.name), size = 36.dp) },
        trailing = {
            Box(Modifier.offset(x = 8.dp)) {
                InlineAction("Unblock", enabled = !locked, busy = busy) { onUnblock(member) }
            }
        },
    )
}

/** Four rows on their way: a face, a name and the line under it, and the button's place. */
@Composable
private fun BlockedLoading() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = Gutter, end = Gutter, top = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Loading blocked members" },
    ) {
        repeat(4) { i ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            CrewCard(padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Skeleton(width = 36.dp, height = 36.dp, corner = 18.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Skeleton(height = 15.dp, modifier = Modifier.fillMaxWidth(0.5f))
                        Spacer(Modifier.height(6.dp))
                        Skeleton(height = 12.dp, modifier = Modifier.fillMaxWidth(0.3f))
                    }
                    Spacer(Modifier.width(12.dp))
                    Skeleton(width = 64.dp, height = 18.dp)
                }
            }
        }
    }
}
