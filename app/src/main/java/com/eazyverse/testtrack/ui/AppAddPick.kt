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
import androidx.compose.foundation.lazy.items
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
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.Config
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
            apps == null -> PhoneAppsLoading(Modifier.weight(1f))
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
        chosen = chosen,
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
private fun PhoneAppsLoading(modifier: Modifier = Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(3.dp), userScrollEnabled = false) {
        items(LOADING_ROWS) { i ->
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

private const val LOADING_ROWS = SKELETON_ROWS
private val NAMES = listOf(96, 128, 84, 112, 140, 90, 118)
private val PACKAGES = listOf(170, 150, 186, 140, 160, 176, 132)

/**
 * The app picked from this phone, drawn the way its own page will draw it once added: [AppHero]
 * with chips for where it is and whether it is checked. Tap the icon to change it and the name to
 * rename it; nothing else needs saying.
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
    var renaming by remember { mutableStateOf(false) }
    AppHero(
        name = name,
        pkg = pkg,
        live = live,
        onChangeIcon = onChangeIcon,
        onRename = { renaming = true },
        icon = {
            val icon = Modifier.size(AppHeroIcon).clip(RoundedCornerShape(AppHeroIcon * 0.3f))
            if (preview != null) {
                Image(preview, "App icon", icon)
            } else {
                Skeleton(width = AppHeroIcon, height = AppHeroIcon, corner = AppHeroIcon * 0.3f)
            }
        },
        chips = {
            if (testBuild) {
                StatusChip("Test build", Tone.BAD, Lucide.TriangleAlert, size = ChipSize.FACT)
            } else {
                StatusChip("Not checked yet", Tone.WARN, Lucide.Clock, size = ChipSize.FACT)
            }
        },
        below = {
            Spacer(Modifier.height(16.dp))
            // A button, not a line of coloured text: as text it read as a label, not as a way back
            // to the list of apps.
            Secondary("Choose another app", onClick = onPickAnother)
        },
    )
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
        OutlinedField(
            value = typed,
            onValueChange = { typed = it.take(FeedRules.APP_NAME_MAX) },
            label = "Name",
            maxLength = FeedRules.APP_NAME_MAX,
        )
    }
}

/**
 * What happens next to an app picked from the phone. One quiet line, as the check needs nothing
 * from them; a test build gets the red line instead, since it would add the wrong app.
 */
@Composable
internal fun PickedNotice(
    testBuild: Boolean,
    /** Live on Play: looked up on its public page as it is added, and never for a crew. */
    live: Boolean,
) {
    if (testBuild) {
        InfoLine(
            "This is a test build. Its package is not the one on Play, so testers would not find it. " +
                "Pick the copy installed from Play.",
            tone = Tone.BAD,
            icon = Lucide.TriangleAlert,
        )
    } else {
        InfoLine(
            if (live) {
                "We look it up on Google Play's public page as you add it."
            } else {
                "We check it on Play by ourselves once ${Config.GROUP_EMAIL} is on your closed track's testers. " +
                    "You can put it in a crew before then."
            },
        )
    }
}
