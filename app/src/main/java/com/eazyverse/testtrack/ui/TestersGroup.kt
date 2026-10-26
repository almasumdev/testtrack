package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.Config
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.delay

/*
 * The one address a closed test has to let in, said wherever the app asks for it.
 *
 * "Add the testers group" used to be the whole instruction, in six places, and none of them said
 * which group: an owner had to know it from the group page they joined as a tester. Now each of
 * them shows the address with Copy, and the steps are the same three words everywhere.
 */

private const val PLAY_CONSOLE = "https://play.google.com/console"

private const val WHY = "Crew members and reviewers install your closed test through this Google Group."

/** The testers group's address, and Copy. Selectable too, for whoever long-presses it. */
@Composable
internal fun GroupAddress(modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500)
            copied = false
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tt.colors.field)
            .border(1.dp, Tt.colors.fieldBorder, shape)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                Config.GROUP_EMAIL,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                fontWeight = FontWeight.SemiBold,
                color = Tt.colors.accentInk,
            )
        }
        Row(
            Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(percent = 50))
                .background(Tt.colors.accent)
                .clickable(onClickLabel = "Copy the testers group address") {
                    clipboard.setText(AnnotatedString(Config.GROUP_EMAIL))
                    copied = true
                }.heightIn(min = 36.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (copied) Lucide.Check else Lucide.Copy, null, Modifier.size(15.dp), tint = Tt.colors.onAccent)
            Spacer(Modifier.width(6.dp))
            Text(
                if (copied) "Copied" else "Copy",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Tt.colors.onAccent,
            )
        }
    }
}

/** The three steps in Play Console; [last] is what to do once it is saved, which differs by screen. */
@Composable
internal fun GroupSteps(last: String) {
    val steps =
        listOf(
            bold("In Play Console, open ", "Test and release", ", then ", "Closed testing", ", then your track's ", "Testers", " tab."),
            bold("Under ", "Google Groups", ", paste the address and save."),
            AnnotatedString(last),
        )
    steps.forEachIndexed { index, step ->
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(Tt.colors.tile),
                contentAlignment = Alignment.Center,
            ) {
                Text("${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Tt.colors.tileInk)
            }
            Spacer(Modifier.width(10.dp))
            Text(step, Modifier.weight(1f).padding(top = 1.dp), style = MaterialTheme.typography.bodySmall, color = Tt.colors.text2)
        }
    }
}

/** Plain and bold in turn, starting plain: the names a person looks for on the Play Console page. */
private fun bold(vararg parts: String): AnnotatedString =
    buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
        }
    }

/** On Add an app, for a closed test: the group to add before looking the app up. */
@Composable
internal fun GroupSetupCard() {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tt.colors.glass)
            .border(1.dp, Tt.colors.glassBorder, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Tt.colors.tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.Users, null, Modifier.size(19.dp), tint = Tt.colors.tileInk)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "First, add our testers group",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Tt.colors.text,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(WHY, style = MaterialTheme.typography.bodySmall, color = Tt.colors.text2)
        Spacer(Modifier.height(10.dp))
        GroupAddress()
        Spacer(Modifier.height(2.dp))
        GroupSteps("Make sure a release is rolled out on that track.")
    }
}

/** The quiet way to the steps, where only the address is shown. */
@Composable
internal fun GroupHowLink(onClick: () -> Unit) {
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("How to add it", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Tt.colors.accentInk)
        Spacer(Modifier.width(4.dp))
        Icon(Lucide.ChevronRight, null, Modifier.size(15.dp), tint = Tt.colors.accentInk)
    }
}

/** The steps on their own, from a page that only had room for the address. */
@Composable
internal fun GroupHowSheet(onDismiss: () -> Unit) {
    val links = LocalUriHandler.current
    PickerSheet(title = "Add the testers group", onDismiss = onDismiss, skipHalf = true) { _ ->
        Text(WHY, style = MaterialTheme.typography.bodySmall, color = Tt.colors.text2)
        Spacer(Modifier.height(12.dp))
        GroupAddress()
        Spacer(Modifier.height(2.dp))
        GroupSteps("Make sure a release is rolled out on that track, then come back and tap Verify now.")
        Spacer(Modifier.height(16.dp))
        Secondary("Open Play Console", Modifier.fillMaxWidth()) { links.openUri(PLAY_CONSOLE) }
        Spacer(Modifier.height(16.dp))
    }
}
