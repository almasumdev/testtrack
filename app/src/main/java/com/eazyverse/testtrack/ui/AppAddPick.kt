package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Image
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
 * The app picked from this phone, as settings rows: the app itself (tap to pick another), its icon
 * and its name, each changeable. The icon shown is the one it will go in with.
 */
@Composable
internal fun PickedApp(
    pkg: String,
    name: String,
    preview: ImageBitmap?,
    fromPicture: Boolean,
    onPickAnother: () -> Unit,
    onChangeIcon: () -> Unit,
    onRename: (String) -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    TileGroup(count = 3) { index, place ->
        when (index) {
            0 ->
                TileRow(
                    icon = Lucide.Image,
                    title = name,
                    place = place,
                    subtitle = pkg,
                    leading = { PreviewIcon(preview) },
                    trailing = {
                        Text(
                            "Change",
                            style = MaterialTheme.typography.bodySmall,
                            color = Tt.colors.accentInk,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    onClick = onPickAnother,
                )
            1 ->
                TileRow(
                    icon = Lucide.Image,
                    title = "App icon",
                    place = place,
                    subtitle = if (fromPicture) "Your picture" else "The icon on this phone",
                    onClick = onChangeIcon,
                )
            else ->
                TileRow(
                    icon = Lucide.Pencil,
                    title = "Name",
                    place = place,
                    subtitle = "As Play shows it",
                    onClick = { renaming = true },
                )
        }
    }
    if (renaming) RenameApp(name, onDone = { renaming = false }, onRename = onRename)
}

/** The icon it will go in with, or its shape while that is being read. */
@Composable
private fun PreviewIcon(preview: ImageBitmap?) {
    if (preview != null) {
        Image(preview, null, Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)))
    } else {
        Skeleton(width = 36.dp, height = 36.dp, corner = 11.dp)
    }
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
 * What happens to an app picked from the phone, in the same callout the app's page uses for an
 * app not verified yet. A test build says so first, in its stead: its package is not Play's.
 */
@Composable
internal fun PickedNotice(testBuild: Boolean) {
    val shape = RoundedCornerShape(18.dp)
    val tone = if (testBuild) Status.missed else Status.short
    val wash = if (testBuild) Status.missedSoft else Status.shortSoft
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(wash)
            .border(1.dp, tone.copy(alpha = 0.28f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (testBuild) Lucide.TriangleAlert else Lucide.Clock, null, Modifier.size(18.dp), tint = tone)
            Spacer(Modifier.width(8.dp))
            Text(
                if (testBuild) "This is a test build" else "Not checked on Play yet",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tone,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (testBuild) {
                "Its package is not the one on Play, so testers would not find it. Pick the copy installed from Play."
            } else {
                "We check it on Play by ourselves once the testers group can see it. You can put it in a crew before then."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.text2,
        )
    }
}
