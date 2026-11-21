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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleX
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.HandHeart
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular

/**
 * Who a tester is, from a tap on their face in the grid or on their tile: their face and name,
 * their email when the server shares it (selectable, to copy), the app they run in this crew, and
 * where their run is, as a few fact chips. The dialog's lead is the person, so it has no title,
 * and only Close. A tester whose app was taken out is drawn faded, face and app.
 */
@Composable
internal fun CrewMemberProfile(
    member: CrewTester,
    onDismiss: () -> Unit,
) {
    val faded = if (member.out) Modifier.alpha(OUT_ALPHA) else Modifier
    Ask(
        title = null,
        lead = { ProfileLead(member, faded) },
        dismiss = "Close",
        onDismiss = onDismiss,
    ) {
        if (!member.volunteer && (member.appName.isNotBlank() || member.packageName.isNotBlank())) {
            ProfileApp(member, faded)
            Spacer(Modifier.height(12.dp))
        }
        ProfileFacts(member)
    }
}

/** The face, the name with what they are here, and the email under it. */
@Composable
private fun ProfileLead(
    member: CrewTester,
    faded: Modifier,
) {
    val t = Tt.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(faded) {
            Avatar(
                member.photo,
                initialOf(member.name),
                size = 48.dp,
                letterStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    member.name,
                    Modifier.weight(1f, fill = false),
                    style = TextStyles.DialogTitle,
                    color = t.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    member.mine -> {
                        Spacer(Modifier.width(8.dp))
                        StatusChip("You", Tone.QUIET, size = ChipSize.SMALL)
                    }
                    member.volunteer -> {
                        Spacer(Modifier.width(8.dp))
                        StatusChip("Volunteer", Tone.TILE, Lucide.HandHeart, size = ChipSize.SMALL)
                    }
                }
            }
            if (member.email.isNotBlank()) {
                SelectionContainer {
                    Text(member.email, Modifier.padding(top = 2.dp), style = EmailStyle, color = t.text3)
                }
            }
        }
    }
}

/** The app they run in this crew, on its own small panel. */
@Composable
private fun ProfileApp(
    member: CrewTester,
    faded: Modifier,
) {
    val t = Tt.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(faded)
            .background(if (t.isDark) t.glass2 else t.bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppMark(member.packageName, member.appName, member.icon, size = 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                member.appName,
                style = AppNameStyle,
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

/** Where their run is, as chips, two to a row at most. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileFacts(member: CrewTester) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = 2,
    ) {
        profileFacts(member).forEach { StatusChip(it.text, it.tone, it.icon, size = ChipSize.FACT) }
    }
}

/** One fact chip's words, tone and mark. */
private class Fact(
    val text: String,
    val tone: Tone,
    val icon: ImageVector?,
)

/**
 * The facts: the night they left, for one taken out, with the nights they missed; otherwise their
 * night (or a volunteer's first night) and tonight's line in its own tone.
 */
private fun profileFacts(member: CrewTester): List<Fact> {
    if (member.out) {
        val missed = member.squares.count { it == Square.MISSED }
        return listOfNotNull(
            Fact(member.outDay?.let { "Out on night $it" } ?: "Out of the run", Tone.BAD, Lucide.LogOut),
            if (missed > 0) Fact("Missed $missed", Tone.QUIET, Lucide.Moon) else null,
        )
    }
    val night =
        when {
            member.over -> null
            member.volunteer && member.firstNight > 1 -> Fact("Since night ${member.firstNight}", Tone.QUIET, Lucide.Calendar)
            member.day > 0 -> Fact("Night ${member.day}", Tone.QUIET, Lucide.Calendar)
            else -> null
        }
    val line = member.line.takeIf { it.isNotBlank() }?.let { Fact(it, member.tone, toneIcon(member.tone)) }
    return listOfNotNull(night, line)
}

private fun toneIcon(tone: Tone): ImageVector? =
    when (tone) {
        Tone.OK -> Lucide.CircleCheck
        Tone.WARN -> Lucide.Clock
        Tone.BAD -> Lucide.CircleX
        else -> null
    }

private const val OUT_ALPHA = 0.5f
private val EmailStyle = tileStyle(13f, 17f, FontWeight.Normal)
private val AppNameStyle = tileStyle(14f, 18f, FontWeight.SemiBold)
