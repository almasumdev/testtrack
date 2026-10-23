package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AddWay
import com.eazyverse.testtrack.viewmodel.PhoneApp

/*
 * The pieces of Add an app: the two ways in, the apps on this phone, and the app picked from it,
 * ready to add. Every way brings the app's real icon and name, so nothing goes onto My apps blank.
 */

/** The first page: which way in. The phone first, since it always has the icon and needs no Play. */
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
        ticks = listOf("Checked on Play right away", "For closed testing, add the testers group first"),
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
            ) { Icon(icon, null, Modifier.size(22.dp), tint = t.onAccent) }
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
                color = t.onAccent,
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

/** The apps on this phone, searchable, each with its own icon and its package under the name. */
@Composable
internal fun PhoneAppList(
    apps: List<PhoneApp>?,
    search: String,
    onSearch: (String) -> Unit,
    onPick: (PhoneApp) -> Unit,
) {
    DialogField(
        value = search,
        onValueChange = onSearch,
        label = "Search your apps",
        singleLine = true,
    )
    Spacer(Modifier.height(14.dp))
    when {
        apps == null ->
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        apps.isEmpty() ->
            Text(
                if (search.isBlank()) "No apps to pick on this phone." else "No app here matches that.",
                style = MaterialTheme.typography.bodyMedium,
                color = Tt.colors.text3,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        else ->
            TileGroup(count = apps.size) { index, place ->
                val app = apps[index]
                TileRow(
                    icon = Lucide.Smartphone,
                    title = app.label,
                    place = place,
                    subtitle = app.pkg,
                    leading = { AppIcon(app.pkg, app.label, size = 40.dp) },
                    trailing =
                        if (app.testBuild) {
                            { TestBuildTag() }
                        } else {
                            null
                        },
                    onClick = { onPick(app) },
                )
            }
    }
}

/** A debug build's package is not the one on Play, so picking it would add the wrong app. */
@Composable
private fun TestBuildTag() {
    Row(
        Modifier.clip(CircleShape).background(Status.shortSoft).padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.TriangleAlert, null, Modifier.size(12.dp), tint = Status.short)
        Spacer(Modifier.width(4.dp))
        Text("Test build", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Status.short)
    }
}

/** The app picked from this phone: its icon to change, its name to correct, and what happens next. */
@Composable
internal fun PickedApp(
    pkg: String,
    name: String,
    onName: (String) -> Unit,
    preview: ImageBitmap?,
    reading: Boolean,
    testBuild: Boolean,
    onChangeIcon: () -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.glass)
            .border(1.dp, t.glassBorder, shape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(92.dp).clickable(role = Role.Button, onClick = onChangeIcon)) {
            val icon = Modifier.size(84.dp).clip(RoundedCornerShape(25.dp))
            when {
                preview != null && !reading -> Image(preview, "App icon", icon)
                else ->
                    Box(icon.background(t.tile), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, t.glassBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.Pencil, "Change icon", Modifier.size(15.dp), tint = t.accentInk) }
        }
        Spacer(Modifier.height(14.dp))
        DialogField(
            value = name,
            onValueChange = onName,
            label = "Name, as Play shows it",
            maxLength = FeedRules.APP_NAME_MAX,
        )
        Spacer(Modifier.height(10.dp))
        Text(pkg, style = MaterialTheme.typography.bodySmall, color = t.text3)
        if (testBuild) {
            Spacer(Modifier.height(8.dp))
            Text(
                "This looks like a test build. Its package is not the one on Play, so testers " +
                    "would not find it. Pick the one installed from Play if you can.",
                style = MaterialTheme.typography.bodySmall,
                color = Status.short,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.clip(CircleShape).background(Status.shortSoft).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Lucide.Clock, null, Modifier.size(13.dp), tint = Status.short)
            Text("Not checked on Play yet", style = MaterialTheme.typography.labelMedium, color = Status.short)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "We check it on Play by ourselves once the testers group can see it. Until then you can " +
                "still put it in a crew.",
            style = MaterialTheme.typography.bodySmall,
            color = t.text2,
        )
    }
}
