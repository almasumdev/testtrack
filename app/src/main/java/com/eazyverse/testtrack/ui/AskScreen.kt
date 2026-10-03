package com.eazyverse.testtrack.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * Asking the circle to look at your app.
 *
 * A page rather than a dialog, because finding the app is its own step and it can fail in ways
 * worth explaining. A dialog that has to say "Play will not show us that app, check the testers
 * group is on the track" has outgrown being one.
 *
 * Two questions before anything else: where the app is, and its link. The name, icon and
 * publisher are read off Play and shown back to confirm. That used to be typed by hand, which is
 * two chances to make a typo nobody would catch until fifteen people failed to install the wrong
 * thing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    vm: AskViewModel = viewModel(),
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Ask for feedback", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, "Back") }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Tell us where your app is and paste its link. We read the name and icon from " +
                    "Play, so there is nothing else to type about the app itself.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.5f,
            )

            Spacer(Modifier.height(28.dp))
            LiveChoice(vm.isLive, vm::choose)

            Spacer(Modifier.height(24.dp))
            if (vm.found == null) Finding(vm) else Found(vm)

            if (vm.found != null) {
                Spacer(Modifier.height(32.dp))
                Text(
                    "What you want looked at",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(14.dp))
                DialogField(
                    value = vm.ask,
                    onValueChange = { vm.ask = it.take(FeedRules.ASK_MAX) },
                    label = "One thing to look at",
                    placeholder = "Is the monthly total readable at a glance?",
                    supporting = "Asked plainly. Broad asks come back broad.",
                    singleLine = false,
                    minHeight = 96.dp,
                    maxLength = FeedRules.ASK_MAX,
                )
                Spacer(Modifier.height(22.dp))
                DialogField(
                    value = vm.offer,
                    onValueChange = { vm.offer = it.filter(Char::isDigit).take(3) },
                    label = "What a review earns",
                    placeholder = FeedRules.MIN_REWARD.toString(),
                    error = vm.rewardError,
                    supporting =
                        "Costs ${vm.cost} tix, which is ${FeedRules.REVIEWS_BOUGHT} reviews' " +
                            "worth. You have ${vm.tix}.",
                    keyboardType = KeyboardType.Number,
                )
                Spacer(Modifier.height(28.dp))
                Primary(
                    "Post",
                    busy = vm.posting,
                    enabled = vm.canPost,
                    tall = true,
                    onClick = { vm.post(onDone) },
                )
            }

            vm.message?.let {
                Spacer(Modifier.height(20.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.45f,
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Before an app is found: one field and one button, and nothing else competing with them. */
@Composable
private fun Finding(vm: AskViewModel) {
    DialogField(
        value = vm.query,
        onValueChange = vm::type,
        label = "Play link or package name",
        placeholder = "play.google.com/store/apps/details?id=...",
        supporting = "Or just the package name, like com.example.myapp.",
        singleLine = false,
        minHeight = 76.dp,
        imeAction = ImeAction.Search,
    )
    Spacer(Modifier.height(22.dp))
    Primary(
        "Find my app",
        busy = vm.looking,
        enabled = vm.canLook,
        tall = true,
        onClick = vm::look,
    )
}

/**
 * The app we found, bordered rather than filled.
 *
 * Every card in this app is a hairline rather than a raised or tinted block, so this one is too.
 * The whole row is the way back, so somebody who pasted the wrong link taps the thing that is
 * wrong rather than hunting for an undo.
 */
@Composable
private fun Found(vm: AskViewModel) {
    val app = vm.found ?: return
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = vm::startOver)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SubcomposeAsyncImage(
            model = app.icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)),
            loading = {},
            error = {},
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                app.developer.ifBlank { app.`package` },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "Change",
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.accentInk,
            fontWeight = FontWeight.Medium,
        )
    }
}
