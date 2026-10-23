package com.eazyverse.testtrack.viewmodel

import com.eazyverse.testtrack.net.ApiError
import java.net.URI
import java.net.URLDecoder

/*
 * Finding an app on Play by what somebody pasted: the package in a link, and what to say when Play
 * would not show it. Used by Add an app.
 */

/** Display information only; colours and actions stay in the screen. */
data class AskFailure(
    val title: String,
    val detail: String,
    val invalidLink: Boolean = false,
    /** Picking the app from this phone is offered instead: our lookup is down, or Play is slow to show it. */
    val pickInstead: Boolean = false,
    /** The fault is ours, not their app's, so it is said in amber rather than red. */
    val ours: Boolean = pickInstead,
)

fun lookupFailure(
    error: Throwable,
    closed: Boolean = false,
): AskFailure =
    when {
        error !is ApiError -> AskFailure("Couldn't connect", "Check your internet connection, then try again. Your app link is still here.")
        error.code == "package_unreadable" ->
            AskFailure(
                "Check the app link",
                "Enter a full Play link or package name, such as com.eazyverse.timelapse.",
                invalidLink = true,
            )
        error.code == "listing_not_found" ->
            AskFailure(
                "We couldn't find this app",
                "Check the package name. If your app isn't public yet, choose Closed testing.",
                invalidLink = true,
            )
        // Usually the testers group missing from the track, but Play can take hours to show a
        // closed track once the group is added, so the owner can still go on, warned.
        error.code == "listing_not_visible" ->
            AskFailure(
                "We can't access this listing",
                "Check that the testers group is added to your closed track and the release is available. " +
                    "Just added it? Play can take a few hours. You can pick it from this phone instead.",
                pickInstead = true,
                ours = false,
            )
        error.code == "listing_unavailable" && closed ->
            AskFailure(
                "Play check is down on our side",
                "Your app is fine. Try again, or pick it from this phone instead.",
                pickInstead = true,
            )
        error.code == "listing_unavailable" -> AskFailure("App lookup is unavailable", error.message)
        else -> AskFailure("Couldn't find your app", error.message)
    }

private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

/**
 * The package name in what was pasted, accepted exactly as the server's app/services/packages.py
 * accepts it: a bare package name, or a play.google.com or market link carrying `?id=`.
 */
fun packageFrom(query: String): String? {
    val text = query.trim()
    if (PACKAGE_NAME.matches(text) && text.length <= 255) return text
    val uri = runCatching { URI(if ("//" in text) text else "https://$text") }.getOrNull() ?: return null
    if (uri.host !in setOf("play.google.com", "market.android.com") && uri.scheme != "market") return null
    val id =
        uri.rawQuery
            .orEmpty()
            .split('&')
            .firstOrNull { it.startsWith("id=") }
            ?.removePrefix("id=")
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?.trim()
            .orEmpty()
    return id.takeIf { PACKAGE_NAME.matches(it) && it.length <= 255 }
}
