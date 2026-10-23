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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.FlaskConical
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.PhoneApp

/*
 * Adding an app from this phone: the picker sheet of the apps installed here, and the app picked
 * from it, ready to add. The rows are the app's settings rows, so this reads like the rest of it.
 */

/**
 * The apps on this phone as a picker: search first, then one row each with its own icon and its
 * package under the name. Tapping a row picks it and closes the sheet; the one already picked
 * carries the check.
 */
@Composable
internal fun PhoneAppSheet(
    apps: List<PhoneApp>?,
    search: String,
    onSearch: (String) -> Unit,
    picked: String?,
    onPick: (PhoneApp) -> Unit,
    onDismiss: () -> Unit,
) {
    PickerSheet(
        title = "Pick your app",
        subtitle = "The apps installed on this phone.",
        tall = true,
        onDismiss = onDismiss,
    ) { close ->
        SearchField(search, onSearch, placeholder = "Search by name or package")
        Spacer(Modifier.height(12.dp))
        when {
            apps == null -> PhoneAppsLoading()
            apps.isEmpty() ->
                Text(
                    if (search.isBlank()) {
                        "There are no apps to pick on this phone."
                    } else {
                        "No app on this phone matches \"${search.trim()}\"."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tt.colors.text3,
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp),
                )
            else ->
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    itemsIndexed(apps, key = { _, app -> app.pkg }) { index, app ->
                        PhoneAppRow(app, placeOf(index, apps.size), chosen = app.pkg == picked) {
                            close { onPick(app) }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
        }
    }
}

@Composable
private fun PhoneAppRow(
    app: PhoneApp,
    place: TilePlace,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    TileRow(
        icon = Lucide.Image,
        title = app.label,
        place = place,
        subtitle = if (app.testBuild) null else app.pkg,
        subtitleRich =
            if (app.testBuild) {
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Status.short, fontWeight = FontWeight.SemiBold)) { append("Test build  ") }
                    append(app.pkg)
                }
            } else {
                null
            },
        leading = { AppIcon(app.pkg, app.label, size = 36.dp) },
        // The check's room is kept on every row, so a title never shifts when it is chosen.
        trailing = {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (chosen) Icon(Lucide.Check, "Picked", Modifier.size(18.dp), tint = Tt.colors.accentInk)
            }
        },
        onClick = onClick,
    )
}

/** Rows in the shape the apps will take, while this phone is asked for them. */
@Composable
private fun PhoneAppsLoading() {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(LOADING_ROWS) { i ->
            val shape = placeOf(i, LOADING_ROWS).shape()
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(shape)
                    .background(Tt.colors.glass)
                    .border(1.dp, Tt.colors.glassBorder, shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Skeleton(width = 36.dp, height = 36.dp, corner = 11.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Skeleton(width = NAMES[i % NAMES.size].dp, height = 13.dp)
                    Spacer(Modifier.height(6.dp))
                    Skeleton(width = PACKAGES[i % PACKAGES.size].dp, height = 10.dp)
                }
            }
        }
    }
}

private const val LOADING_ROWS = 7
private val NAMES = listOf(96, 128, 84, 112, 140, 90, 118)
private val PACKAGES = listOf(170, 150, 186, 140, 160, 176, 132)

/**
 * The app picked from this phone, drawn the way its own page will draw it once added: the icon large
 * in the middle, its name and package under it, and chips for where it is and whether it is checked.
 * Tap the icon to change it and the name to rename it; nothing else needs saying.
 */
@Composable
internal fun PickedApp(
    pkg: String,
    name: String,
    preview: ImageBitmap?,
    live: Boolean,
    testBuild: Boolean,
    onPickAnother: () -> Unit,
    onChangeIcon: () -> Unit,
    onRename: (String) -> Unit,
) {
    val t = Tt.colors
    var renaming by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clickable(role = Role.Button, onClickLabel = "Change icon", onClick = onChangeIcon)) {
            val icon = Modifier.size(88.dp).clip(RoundedCornerShape(26.dp))
            if (preview != null) Image(preview, "App icon", icon) else Skeleton(width = 88.dp, height = 88.dp, corner = 26.dp)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, t.glassBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.Pencil, null, Modifier.size(15.dp), tint = t.accentInk) }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClickLabel = "Rename") { renaming = true }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 26.sp),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Icon(Lucide.Pencil, null, Modifier.size(16.dp), tint = t.accentInk)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            pkg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppChip(
                if (live) Lucide.Globe else Lucide.FlaskConical,
                if (live) "Live" else "Closed test",
                MaterialTheme.colorScheme.onSurfaceVariant,
                Status.neutralSoft,
            )
            if (testBuild) {
                AppChip(Lucide.TriangleAlert, "Test build", Status.missed, Status.missedSoft)
            } else {
                AppChip(Lucide.Clock, "Not checked yet", Status.short, Status.shortSoft)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Pick a different app",
            style = MaterialTheme.typography.bodyMedium,
            color = t.accentInk,
            fontWeight = FontWeight.Medium,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onPickAnother)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
    if (renaming) RenameApp(name, onDone = { renaming = false }, onRename = onRename)
}

@Composable
private fun RenameApp(
    current: String,
    onDone: () -> Unit,
    onRename: (String) -> Unit,
) {
    var typed by remember { mutableStateOf(current) }
    Ask(
        title = "The app's name",
        body = "As Play shows it, so testers know it is the same app.",
        confirm = "Save",
        confirmEnabled = typed.isNotBlank(),
        onConfirm = {
            onRename(typed.trim())
            onDone()
        },
        onDismiss = onDone,
    ) {
        DialogField(
            value = typed,
            onValueChange = { typed = it.take(FeedRules.APP_NAME_MAX) },
            label = "Name",
            maxLength = FeedRules.APP_NAME_MAX,
        )
    }
}

/**
 * What happens next to an app picked from the phone. One quiet line, as the check needs nothing
 * from them; a test build gets the red callout instead, since it would add the wrong app.
 */
@Composable
internal fun PickedNotice(testBuild: Boolean) {
    if (!testBuild) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
            Icon(Lucide.Info, null, Modifier.padding(top = 2.dp).size(15.dp), tint = Status.short)
            Spacer(Modifier.width(8.dp))
            Text(
                "We check it on Play by ourselves once the testers group can see it. You can put it in a crew before then.",
                style = MaterialTheme.typography.bodySmall,
                color = Tt.colors.text3,
            )
        }
        return
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Status.missedSoft)
            .border(1.dp, Status.missed.copy(alpha = 0.28f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.TriangleAlert, null, Modifier.size(18.dp), tint = Status.missed)
            Spacer(Modifier.width(8.dp))
            Text("This is a test build", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Status.missed)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Its package is not the one on Play, so testers would not find it. Pick the copy installed from Play.",
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.text2,
        )
    }
}
