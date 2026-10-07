package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ScanSearch
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.PlayCheckViewModel

/**
 * The team's tools on the Me page. Nothing is drawn for anybody who is not an admin.
 *
 * One row today: whether the server's Play check is signed in to Google, and the way to sign it
 * in when Google has stopped it with a question only a person can answer.
 */
@Composable
fun AdminTools(vm: PlayCheckViewModel = viewModel()) {
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.load() }
    if (!vm.admin) return

    Column {
        SectionTitle("Team")
        TileGroup(count = 1) { _, place ->
            TileRow(
                Lucide.ScanSearch,
                "Play check",
                place,
                subtitle = "How crew apps are seen on Play",
                value =
                    when (vm.signedIn) {
                        true -> "Signed in"
                        false -> "Sign in"
                        null -> "Checking"
                    },
                onClick = { showing = true },
            )
        }
    }

    if (showing) {
        PlayCheckSheet(vm, onClose = {
            if (vm.open) vm.cancel()
            showing = false
        })
    }
}

/**
 * The server's browser, at arm's length: its page as a picture, taps on the picture sent as
 * clicks, typing and a few keys sent as keystrokes. Sign in to the Play-check account and answer
 * Google's question, then Done.
 */
@Composable
private fun PlayCheckSheet(
    vm: PlayCheckViewModel,
    onClose: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(Gutter),
        ) {
            Text("Play check", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    vm.open -> "This is the server's browser. Tap the picture to click, type below, then Done."
                    vm.signedIn == true -> "Signed in. Crew apps are checked on Play as the testers group sees them."
                    else ->
                        "Signed out (${vm.reason.ifBlank { "unknown" }}). Until it is signed in, new crew " +
                            "apps wait, and go in unchecked after 12 hours."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            vm.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Tt.colors.accentInk)
            }
            Spacer(Modifier.height(14.dp))

            if (!vm.open) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Primary(if (vm.busy) "Opening…" else "Open sign-in", enabled = !vm.busy) { vm.start() }
                    Secondary("Close", onClick = onClose)
                }
                return@Column
            }

            val shot = vm.screen
            if (shot == null) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    RobotLoader(label = "Opening the browser")
                }
            } else {
                Image(
                    shot,
                    contentDescription = "The server's browser",
                    contentScale = ContentScale.FillWidth,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(shot) {
                                detectTapGestures { at ->
                                    val scale = shot.width.toFloat() / size.width
                                    vm.tap((at.x * scale).toInt(), (at.y * scale).toInt())
                                }
                            },
                )
            }
            Spacer(Modifier.height(12.dp))
            DialogField(value = text, onValueChange = { text = it }, label = "Type into the page")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Secondary("Send text", enabled = text.isNotEmpty()) {
                    vm.type(text)
                    text = ""
                }
                Secondary("Enter") { vm.key("Enter") }
                Secondary("Tab") { vm.key("Tab") }
                Secondary("⌫") { vm.key("Backspace") }
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Primary(if (vm.busy) "Checking…" else "Done", enabled = !vm.busy) { vm.finish() }
                Spacer(Modifier.width(8.dp))
                Secondary("Cancel", onClick = { vm.cancel() })
            }
        }
    }
}
