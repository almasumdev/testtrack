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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Smartphone
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AddWay

/*
 * The first page of Add an app: the two ways in. Both bring the app's real icon and name, so
 * nothing goes onto My apps blank. The phone's way is in AppAddPick.kt.
 */

/** Which way in. The phone first, since it always has the icon and needs nothing from Play. */
@Composable
internal fun AddWayChoice(
    selected: AddWay,
    onSelect: (AddWay) -> Unit,
) {
    Text(
        "How do you want to add it?",
        style = MaterialTheme.typography.bodyMedium,
        color = Tt.colors.text2,
    )
    Spacer(Modifier.height(12.dp))
    WayCard(
        icon = Lucide.Smartphone,
        tint = Status.posted,
        title = "Pick from this phone",
        body = "Choose your app from the apps on this phone. Its icon and name come with it.",
        ticks = listOf("Your app needs to be installed here", "Checked on Play later, by itself"),
        tag = "Works before Play is ready",
        selected = selected == AddWay.PHONE,
        onClick = { onSelect(AddWay.PHONE) },
    )
    Spacer(Modifier.height(14.dp))
    WayCard(
        icon = Lucide.Link,
        tint = Tt.colors.accent,
        title = "Play link or package name",
        body = "Paste your Google Play link or type the package name. Play gives us the icon and name.",
        ticks = listOf("Checked on Play right away", "For closed testing, we show you which group to add"),
        tag = null,
        selected = selected == AddWay.PLAY,
        onClick = { onSelect(AddWay.PLAY) },
    )
}

@Composable
private fun WayCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    body: String,
    ticks: List<String>,
    tag: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(18.dp)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(t.glass)
                .border(if (selected) 2.dp else 1.dp, if (selected) t.accent else t.glassBorder, shape)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(14.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(22.dp), tint = t.inkOn(tint)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = t.text2)
                Spacer(Modifier.height(6.dp))
                ticks.forEach { line ->
                    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.Check, null, Modifier.size(14.dp), tint = Status.posted)
                        Spacer(Modifier.width(6.dp))
                        Text(line, style = MaterialTheme.typography.bodySmall, color = t.text2)
                    }
                }
            }
        }
        if (tag != null) {
            Text(
                tag,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = t.inkOn(Status.posted),
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-12).dp, y = (-9).dp)
                        .clip(CircleShape)
                        .background(Status.posted)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
