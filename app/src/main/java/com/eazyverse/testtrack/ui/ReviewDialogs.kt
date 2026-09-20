package com.eazyverse.testtrack.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The last step: what was left, and the evidence for it.
 *
 * Two shapes behind one dialog. A live app needs a screenshot, because a Play review is the one
 * piece of this nobody outside Google can read. An app still in closed testing has no Play review
 * to point at, so what it needs is the words.
 */
@Composable
fun ReviewProofDialog(
    live: Boolean,
    busy: Boolean = false,
    onSend: (proof: Uri?, written: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var written by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Uri?>(null) }

    // The system picker, which is permissionless: it hands back one item the person chose and
    // never gives the app the library. Asking for READ_MEDIA_IMAGES to read one file somebody is
    // about to hand over anyway is a permission prompt bought for nothing.
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) picked = uri
        }

    val ready = if (live) picked != null else written.trim().length >= 40

    Ask(
        title = if (live) "Prove you left it" else "What did you find?",
        body =
            if (live) {
                "Take a screenshot of your review on Play, then pick it here. The developer " +
                    "sees it and has two days to accept it."
            } else {
                "This app is not on Play yet, so your words are the review."
            },
        confirm = if (busy) "Sending" else "Send",
        confirmEnabled = ready && !busy,
        onConfirm = { onSend(picked, written.trim()) },
        onDismiss = onDismiss,
        content = {
            if (live) {
                Text(
                    "Screenshot",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Secondary(
                    if (picked == null) "Choose the screenshot" else "Chosen. Pick another",
                    Modifier.fillMaxWidth(),
                ) {
                    pick.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (picked == null) {
                        "Your phone's own screenshot, taken after the review is posted."
                    } else {
                        "Only the developer and, if they refuse it, the members deciding, " +
                            "can see it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DialogField(
                    value = written,
                    onValueChange = { written = it.take(600) },
                    label = "What you found",
                    placeholder = "The sign-up screen took three tries before it took my number.",
                    supporting =
                        if (written.trim().length < 40) {
                            "A few more words. Say what you did and what happened."
                        } else {
                            "Good. Send it when you are ready."
                        },
                    singleLine = false,
                    minHeight = 120.dp,
                    maxLength = 600,
                )
            }
            Spacer(Modifier.height(4.dp))
        },
    )
}

/**
 * Open an app's Play listing.
 *
 * The market scheme first, which lands inside the Play app where the review sheet actually is.
 * The web address is the fallback for a device with no Play, where at least the page loads.
 */
fun openPlay(
    context: Context,
    packageName: String,
) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
    val web =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        )
    runCatching { context.startActivity(market) }.onFailure {
        runCatching { context.startActivity(web) }
    }
}

/** Open the app being reviewed, if it is there. */
fun openApp(
    context: Context,
    packageName: String,
) {
    val launch = context.packageManager.getLaunchIntentForPackage(packageName)
    if (launch != null) {
        runCatching { context.startActivity(launch) }
    } else {
        openPlay(context, packageName)
    }
}
