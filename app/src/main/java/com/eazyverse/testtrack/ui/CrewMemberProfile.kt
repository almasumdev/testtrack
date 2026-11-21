package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.HandHeart
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular

/**
 * Who a tester is, from a tap on their face in the grid or on their tile: their face and name,
 * their email when the server shares it (selectable, to copy), the app they run in this crew, and
 * where their run is. The app's one dialog, with no title and only Close.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CrewMemberProfile(
    member: CrewTester,
    onDismiss: () -> Unit,
) {
    val t = Tt.colors
    Ask(title = null, confirm = null, onConfirm = {}, onDismiss = onDismiss, dismiss = "Close") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(if (member.out) Modifier.alpha(0.5f) else Modifier) {
                Avatar(
                    member.photo,
                    initialOf(member.name),
                    size = 64.dp,
                    letterStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        member.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 21.sp),
                        fontWeight = FontWeight.Bold,
                        color = t.text,
                    )
                    if (member.mine) StatusChip("You", Tone.QUIET)
                }
                // A volunteer has no app line below, so the tag and their first night say who they are.
                if (member.volunteer) {
                    Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip("Volunteer", Tone.TILE, Lucide.HandHeart, size = ChipSize.SMALL)
                        if (member.firstNight > 1) StatusChip("From night ${member.firstNight}", Tone.QUIET, size = ChipSize.SMALL)
                    }
                }
                if (member.email.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            member.email,
                            Modifier.padding(top = 3.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 18.sp),
                            color = t.text3,
                        )
                    }
                }
            }
        }
        if (member.appName.isNotBlank() || member.packageName.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(t.bg, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppMark(member.packageName, member.appName, member.icon, size = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        member.appName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = t.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (member.packageName.isNotBlank()) {
                        Text(
                            member.packageName,
                            Modifier.padding(top = 2.dp),
                            style =
                                MaterialTheme.typography.labelSmall
                                    .tabular()
                                    .copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                            color = t.text4,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ProfileStatus(member)
    }
}

/** Where their run is: their day and tonight's line, the run's line once over, or the night they left. */
@Composable
private fun ProfileStatus(member: CrewTester) {
    val t = Tt.colors
    val ink = if (member.tone == Tone.QUIET) t.text3 else member.tone.ink()
    val text =
        buildAnnotatedString {
            when {
                member.mine -> append("Your day ${member.day}")
                member.out ->
                    withStyle(SpanStyle(color = t.text3)) {
                        append(member.outDay?.let { "Left on day $it" } ?: "Left the run")
                    }
                member.over -> withStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold)) { append(member.line) }
                else -> {
                    append("Their day ${member.day}, ")
                    withStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold)) {
                        append(member.line.replaceFirstChar { it.lowercase() })
                    }
                }
            }
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (member.out) Lucide.LogOut else Lucide.Calendar, null, Modifier.size(15.dp), tint = t.text3)
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 18.sp),
            color = t.text2,
        )
    }
}
