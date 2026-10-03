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
 * The last step: the evidence that a review was left on Play.
 *
 * Every review is left on Play, a public review for a live app and private tester feedback for one
 * in closed testing, and nobody outside Google can read either. So the one thing asked for here is
 * the screenshot of it.
 */
@Composable
fun ReviewProofDialog(
    busy: Boolean = false,
    onSend: (proof: Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf<Uri?>(null) }

    // The system picker, which is permissionless: it hands back one item the person chose and
    // never gives the app the library. Asking for READ_MEDIA_IMAGES to read one file somebody is
    // about to hand over anyway is a permission prompt bought for nothing.
    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) picked = uri
        }

    Ask(
        title = "Prove you left it",
        body =
            "Take a screenshot of what you left on Play, then pick it here. The developer sees it " +
                "and has two days to accept it.",
        confirm = if (busy) "Sending" else "Send",
        confirmEnabled = picked != null && !busy,
        onConfirm = { picked?.let(onSend) },
        onDismiss = onDismiss,
        content = {
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
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (picked == null) {
                    "Your phone's own screenshot, taken after the review is posted."
                } else {
                    "Only the developer and, if they refuse it, the members deciding, can see it."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
