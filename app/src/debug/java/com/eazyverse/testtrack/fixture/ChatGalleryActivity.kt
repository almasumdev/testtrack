package com.eazyverse.testtrack.fixture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Sun
import com.eazyverse.testtrack.ui.Gutter
import com.eazyverse.testtrack.ui.PageBar
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.exchangeBackground

/**
 * Every crew chat component and state, drawn by the app's own composables from invented samples.
 * Debug builds only (this source set), for screenshots on a real phone. No network, no account.
 *
 *     adb shell am start -n com.eazyverse.testtrack.debug/com.eazyverse.testtrack.fixture.ChatGalleryActivity
 *
 * Extras: `--es section bubbles|pictures|page|composer|sheets|editor|entry` shows one section,
 * `--ez dark true|false` forces the theme, and `--es open` opens one thing straight away:
 * page, message_others, message_own, message_bot, message_failed, delete, mention, profile, viewer, editor.
 */
class ChatGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val samples = GallerySamples(this)
        val section = intent.getStringExtra(EXTRA_SECTION)
        val open = intent.getStringExtra(EXTRA_OPEN)
        val dark = if (intent.hasExtra(EXTRA_DARK)) intent.getBooleanExtra(EXTRA_DARK, false) else null
        setContent {
            val system = isSystemInDarkTheme()
            var isDark by rememberSaveable { mutableStateOf(dark ?: system) }
            TestTrackTheme(darkTheme = isDark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize().exchangeBackground()) {
                        ChatGallery(samples, section, open, isDark, { isDark = !isDark }, ::finish)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_SECTION = "section"
        const val EXTRA_OPEN = "open"
        const val EXTRA_DARK = "dark"
    }
}

private fun overlayFor(
    name: String?,
    s: GallerySamples,
): Overlay? {
    val lines = SheetLines(s)
    return when (name) {
        "message_others" -> Overlay.Message(lines.others)
        "message_own" -> Overlay.Message(lines.own)
        "message_bot" -> Overlay.Message(lines.bot)
        "message_failed" -> Overlay.Message(lines.failed)
        "delete" -> Overlay.Delete(lines.own)
        "profile" -> Overlay.Profile(GallerySamples.KENJI)
        "viewer" -> Overlay.Viewer(lines.picture)
        "editor" -> Overlay.Editor
        else -> null
    }
}

@Composable
private fun ChatGallery(
    s: GallerySamples,
    section: String?,
    open: String?,
    dark: Boolean,
    onTheme: () -> Unit,
    onBack: () -> Unit,
) {
    var overlay by remember { mutableStateOf(overlayFor(open, s)) }
    var page by rememberSaveable { mutableStateOf(open == "page") }
    var picked by rememberSaveable { mutableStateOf(section) }
    val show: (Overlay) -> Unit = { overlay = it }
    val sections =
        remember(s) {
            listOf(
                bubbleSection(s, show),
                pictureSection(s, show),
                pageSection(s, show),
                composerSection(s, show),
                sheetSection(s, show) { page = true },
                editorSection(s, show),
                entrySection(s),
            )
        }
    if (page) {
        BackHandler { page = false }
        Box(Modifier.fillMaxSize().safeDrawingPadding()) { GalleryPage(s, show) { page = false } }
    } else {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            PageBar("Chat gallery", onBack) {
                IconButton(onClick = onTheme) {
                    Icon(
                        if (dark) Lucide.Sun else Lucide.Moon,
                        if (dark) "Light theme" else "Dark theme",
                        Modifier.size(20.dp),
                        tint = Tt.colors.text2,
                    )
                }
            }
            SectionChips(sections, picked) { picked = it }
            val shown = sections.filter { picked == null || it.id == picked }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
                shown.forEach { sec ->
                    item(key = "title-${sec.id}") { SectionTitle(sec.title) }
                    sec.shots.forEachIndexed { i, shot ->
                        item(key = "${sec.id}-$i") { ShotView(shot) }
                    }
                }
            }
        }
    }
    overlay?.let { OverlayHost(it, s, show) { overlay = null } }
}

@Composable
private fun SectionChips(
    sections: List<Section>,
    picked: String?,
    onPick: (String?) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Gutter, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip("All", picked == null) { onPick(null) }
        sections.forEach { Chip(it.title, picked == it.id) { onPick(it.id) } }
    }
}

@Composable
private fun Chip(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    Text(
        label,
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (on) t.accent else t.glass2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (on) t.onAccent else t.text,
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        Modifier.padding(start = Gutter, end = Gutter, top = 28.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Tt.colors.text,
    )
}

@Composable
private fun ShotView(shot: Shot) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            shot.label,
            Modifier.padding(start = Gutter, end = Gutter, top = 18.dp, bottom = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Tt.colors.text3,
        )
        shot.content()
    }
}
