package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide

/**
 * Asking the circle to look at your app.
 *
 * A page rather than a dialog, because it is two steps and the first one can fail in ways worth
 * explaining. A dialog that has to say "Play will not show us that app, check the testers group
 * is on the track" is a dialog that has outgrown being one.
 *
 * The owner pastes one thing: the link Play Console gave them. Everything else about the app,
 * its name, its icon, who publishes it, is read off Play by the server and shown back for
 * confirmation. They used to type the name and the package by hand, which is two chances to make
 * a typo that nobody would catch until fifteen people failed to install the wrong thing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    vm: AskViewModel = viewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask for feedback") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Lucide.ArrowLeft, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            FoundApp(vm)
            if (vm.found != null) {
                Spacer(Modifier.height(20.dp))
                TheAsk(vm)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { vm.post(onDone) },
                    enabled = vm.canPost,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (vm.posting) "Posting" else "Post")
                }
            }
            vm.message?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The first step, and the one the whole page exists for.
 *
 * Once an app is found the field stays on screen but goes quiet, so somebody who pasted the
 * wrong link can see what they pasted and change it without starting again.
 */
@Composable
private fun FoundApp(vm: AskViewModel) {
    val found = vm.found
    if (found == null) {
        DialogField(
            value = vm.query,
            onValueChange = vm::type,
            label = "Play link or package name",
            placeholder = "https://play.google.com/store/apps/details?id=...",
            supporting =
                "Paste the link from Play Console. We will read the app's name and icon from " +
                    "Play, including on a closed track.",
            singleLine = false,
            minHeight = 72.dp,
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = vm::look,
            enabled = vm.canLook,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (vm.looking) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Find my app")
            }
        }
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SubcomposeAsyncImage(
            model = found.icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
            loading = {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
            },
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(found.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                found.developer.ifBlank { found.`package` },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = vm::startOver) { Text("Change") }
    }
}

/** What you want looked at, and what it is worth. */
@Composable
private fun TheAsk(vm: AskViewModel) {
    DialogField(
        value = vm.ask,
        onValueChange = { vm.ask = it.take(FeedRules.ASK_MAX) },
        label = "What should they look at",
        placeholder = "Is the monthly total readable at a glance?",
        supporting = "One thing, asked plainly. Broad asks come back broad.",
        singleLine = false,
        minHeight = 84.dp,
        maxLength = FeedRules.ASK_MAX,
    )
    Spacer(Modifier.height(14.dp))
    DialogField(
        value = vm.offer,
        onValueChange = { vm.offer = it.filter(Char::isDigit).take(3) },
        label = "What a review earns",
        keyboardType = KeyboardType.Number,
        error = vm.rewardError,
        supporting =
            "Costs ${vm.cost} tix, which is ${FeedRules.REVIEWS_BOUGHT} reviews' worth. " +
                "You have ${vm.tix}.",
    )
}
