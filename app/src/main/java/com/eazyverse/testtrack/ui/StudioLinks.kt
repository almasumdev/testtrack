package com.eazyverse.testtrack.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.eazyverse.testtrack.BuildConfig

/*
 * Where the About page and the Support rows send people: Play, the studio, and a mail to it.
 * Every one hands the reader to an app this one does not own, so every one fails in silence.
 */

private const val PACKAGE = "com.eazyverse.testtrack"
private const val DEVELOPER_ID = "5795337481821127926"
private const val STUDIO_MAIL = "hello@eazyverse.com"
internal const val STUDIO_SITE = "https://eazyverse.com"
internal const val STUDIO_TERMS = "https://eazyverse.com/terms"
internal const val APP_PRIVACY = "https://eazyverse.com/apps/testtrack/privacy"
internal const val PORTFOLIO = "https://almasum.dev"

/** The app's own listing: Play first, the web second, nothing if the phone has neither. */
internal fun openPlayListing(context: Context) {
    if (!openUrl(context, "market://details?id=$PACKAGE")) {
        openUrl(context, "https://play.google.com/store/apps/details?id=$PACKAGE")
    }
}

internal fun openDeveloperPage(context: Context) {
    if (!openUrl(context, "market://dev?id=$DEVELOPER_ID")) {
        openUrl(context, "https://play.google.com/store/apps/dev?id=$DEVELOPER_ID")
    }
}

/**
 * The feedback mail, the same from every door (mkit docs/in-app-review.md): to the studio, the app
 * named, a blank line to write in, then the three facts we would otherwise have to ask for, and the
 * star that was tapped when it came from a low one. Nothing else: no logs, no account details.
 */
internal fun sendFeedback(
    context: Context,
    stars: Int? = null,
) {
    val body =
        buildString {
            append("\n\n---\n")
            append("TestTrack ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
            append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("${Build.MANUFACTURER} ${Build.MODEL}")
            stars?.let { append("\nRating tapped: $it/5") }
        }
    mail(context, "TestTrack feedback", body)
}

/** Whether anything on the phone can write an email; needs the manifest's mailto query. */
internal fun canMail(context: Context): Boolean =
    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).resolveActivity(context.packageManager) != null

/**
 * A mail to the studio, through the chooser every time. Handed straight to the system it goes to
 * whatever default was saved, usually Gmail and usually not the account somebody meant to write
 * from; the mailto filter keeps the sheet to mail apps only, and the extras survive it.
 */
internal fun mail(
    context: Context,
    subject: String,
    body: String? = null,
) {
    val write =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$STUDIO_MAIL")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            body?.let { putExtra(Intent.EXTRA_TEXT, it) }
        }
    runCatching {
        context.startActivity(Intent.createChooser(write, "Send with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** False when nothing on the phone can open it; the caller decides whether there is a fallback. */
internal fun openUrl(
    context: Context,
    url: String,
): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
