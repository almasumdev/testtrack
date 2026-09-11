package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.data.*

@Composable
fun GroupRow(
    progress: GroupProgress,
    onClick: () -> Unit,
) {
    val group = progress.group
    val day = group.dayIndex()
    val left = progress.toTest - progress.done

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Gutter, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row's own leading mark, same idea as the hero ring above: a day still owed reads
        // amber and fills green once nothing is left, a group still filling its roster reads
        // neutral against the same threshold its own text already states.
        when {
            day != null && progress.toTest > 0 -> {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    SegmentedRing(
                        done = progress.done,
                        total = progress.toTest,
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 5.dp,
                        fillColor = if (left <= 0) Status.posted else Status.short,
                    )
                    if (left <= 0) {
                        Icon(
                            Lucide.Check,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = Status.posted,
                        )
                    } else {
                        // Same convention as the hero ring above it: the number in the middle is
                        // how many are done, not how many are left — one rule for every ring on
                        // the screen rather than a different one per size.
                        Text(
                            "${progress.done}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
            }
            day == null && !group.running -> {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    SegmentedRing(
                        done = group.size,
                        total = TestGroup.THRESHOLD,
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 5.dp,
                        fillColor = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        "${group.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(14.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                group.name.ifBlank { "Group" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    day != null ->
                        "Day ${day + 1} of ${group.runDays} · " +
                            if (left <= 0) "all done" else "$left left today"
                    group.running -> "Run finished"
                    else -> "Forming · ${group.size} of ${TestGroup.THRESHOLD} members"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            Lucide.ChevronRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun PendingRow(
    app: TestApp,
    onEditNotes: () -> Unit,
    onWithdraw: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, app.label)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Shown back to the owner, clipped the same way the group's rows clip it, so what they
            // are reading here is what the twelve people will be reading there.
            if (app.notes.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    app.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Two labels, because an owner who has never written notes has no reason to expect the row
        // hides a field, and "Notes" on its own reads as a heading rather than something to press.
        TextButton(onClick = onEditNotes) {
            Text(if (app.notes.isBlank()) "Add notes" else "Edit notes")
        }
        // Named rather than hidden behind three dots. A menu holding one item is a tap spent
        // discovering that there was only ever one thing to do.
        TextButton(onClick = onWithdraw) {
            Text("Withdraw", color = MaterialTheme.colorScheme.error)
        }
    }
}
