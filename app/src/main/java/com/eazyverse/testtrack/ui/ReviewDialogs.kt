package com.eazyverse.testtrack.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    onSend: (proof: String, written: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var written by remember { mutableStateOf("") }
    var proof by remember { mutableStateOf("") }

    val ready = if (live) proof.isNotBlank() else written.trim().length >= 40

    Ask(
        title = if (live) "Prove you left it" else "What did you find?",
        body =
            if (live) {
                "Paste or pick the screenshot of your review. The developer sees this and has " +
                    "two days to accept it."
            } else {
                "This app is not on Play yet, so your words are the review."
            },
        confirm = "Send",
        confirmEnabled = ready,
        onConfirm = { onSend(proof, written.trim()) },
        onDismiss = onDismiss,
        content = {
            if (live) {
                DialogField(
                    value = proof,
                    onValueChange = { proof = it.take(200) },
                    label = "Screenshot",
                    placeholder = "Tap the camera button while you are on the review page",
                    supporting =
                        "The floating button captures it for you without leaving Play. " +
                            "Take it after the review is posted.",
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
