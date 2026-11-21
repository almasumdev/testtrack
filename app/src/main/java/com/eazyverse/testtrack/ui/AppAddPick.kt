package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.PhoneApp

/*
 * Adding an app from this phone: the picker sheet of the apps installed here, and the app picked
 * from it, ready to add.
 */

/**
 * The apps on this phone as a picker, open at full height: search first, then one tile each with
 * its own icon and its package under the name. Tapping a tile picks it and closes the sheet; the
 * one already picked has the soft wash and the check. A test build carries the red mark instead.
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
    PickerSheet(title = "Pick your app", full = true, onDismiss = onDismiss) { close ->
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
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(apps, key = { it.pkg }) { app ->
                        PhoneAppTile(app, chosen = app.pkg == picked) { close { onPick(app) } }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
        }
    }
}

private val PhoneAppIcon = 44.dp
private val PhoneAppName = tileStyle(16f, 19f, FontWeight.SemiBold)
private val PhoneAppNameChosen = tileStyle(16f, 19f, FontWeight.Bold)
private val PhoneAppPackage = tileStyle(12.5f, 15f, FontWeight.Medium)

/** One app on this phone: its own 14dp tile, the chosen one washed, with the check at the end. */
@Composable
private fun PhoneAppTile(
    app: PhoneApp,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .choiceTile(chosen, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.pkg, app.label, size = PhoneAppIcon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = if (chosen) PhoneAppNameChosen else PhoneAppName,
                color = if (chosen) t.tabSelected else t.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(app.pkg, style = PhoneAppPackage, color = t.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        if (app.testBuild) {
            // The mark's 40dp target reaches past the row's padding rather than making the tile taller.
            TestBuildMark("2. Pick it here.", Modifier.offset(x = 3.dp).size(width = 34.dp, height = 20.dp), inset = 2.dp)
        } else {
            // The check's room is kept on every tile, so a name never shifts when it is chosen.
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (chosen) Icon(Lucide.Check, "Picked", Modifier.size(18.dp), tint = t.tabSelected)
            }
        }
    }
}

/**
 * A test build's mark: the red alert, 16dp with no round, in a 40dp target, opening the app's
 * callout with why it can't be added and the steps; [lastStep] is how to pick the right one from
 * where the mark is. [inset] is how far inside [modifier]'s box the glyph sits, for the caret.
 */
@Composable
private fun TestBuildMark(
    lastStep: String,
    modifier: Modifier,
    inset: Dp,
) {
    val t = Tt.colors
    InfoDot(
        "This is a test build",
        t.bad,
        modifier,
        glyph = 16.dp,
        iconInset = inset,
        look = InfoDotLook.STILL,
        slot = 40.dp,
        icon = Lucide.CircleAlert,
    ) {
        CalloutLine(
            buildAnnotatedString {
                withStyle(SpanStyle(color = t.text, fontWeight = FontWeight.SemiBold)) { append("This is a test build") }
                append("\nIt isn't the copy installed from Play, so testers couldn't find it.")
                append("\n1. Install your app from Play.\n")
                append(lastStep)
            },
        )
    }
}

/** Tiles in the shape the apps will take, while this phone is asked for them. */
@Composable
private fun PhoneAppsLoading(modifier: Modifier = Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
        items(LOADING_ROWS) { i ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .tileFrame()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Skeleton(width = PhoneAppIcon, height = PhoneAppIcon, corner = 13.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Skeleton(width = NAMES[i % NAMES.size].dp, height = 14.dp)
                    Spacer(Modifier.height(6.dp))
                    Skeleton(width = PACKAGES[i % PACKAGES.size].dp, height = 11.dp)
                }
            }
        }
    }
}

private const val LOADING_ROWS = 9
private val NAMES = listOf(96, 128, 84, 112, 140, 90, 118, 100, 120)
private val PACKAGES = listOf(170, 150, 186, 140, 160, 176, 132, 160, 150)

/**
 * The app picked from this phone, drawn the way its own page will draw it once added: [AppHero]
 * with chips for where it is and whether it is checked. Tap the icon to change it and the name to
 * rename it. A test build has the red mark in place of the second chip.
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
                // 28dp tall so the chips keep their height; the 40dp target reaches past it.
                TestBuildMark(
                    "2. Tap Choose another app and pick it.",
                    Modifier.align(Alignment.CenterVertically).size(width = 32.dp, height = 28.dp),
                    inset = 6.dp,
                )
            } else {
                StatusChip("Not checked yet", Tone.WARN, Lucide.Clock, size = ChipSize.FACT)
            }
        },
        below = { ChooseAnother(onPickAnother) },
    )
    if (renaming) RenameApp(name, onDone = { renaming = false }, onRename = onRename)
}

/** Under the hero: back to the apps, or to the start page, for a different one. */
@Composable
internal fun ChooseAnother(onClick: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    RowPill("Choose another app", PillKind.SOFT, size = PillSize.SMALL, touchTarget = false, onClick = onClick)
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
            hint = "As Play shows it",
            maxLength = FeedRules.APP_NAME_MAX,
        )
    }
}

/**
 * What happens next to an app picked from the phone, in one quiet line. A test build has none:
 * its red mark says it.
 */
@Composable
internal fun PickedNotice(
    testBuild: Boolean,
    /** Live on Play: looked up on its public page as it is added, and never for a crew. */
    live: Boolean,
) {
    if (testBuild) return
    InfoLine(
        if (live) {
            "We look it up on Google Play's public page as you add it."
        } else {
            "We check it on Play once our testers group is on your closed track. You can put it in a crew before then."
        },
        Modifier.padding(top = 18.dp),
    )
}
