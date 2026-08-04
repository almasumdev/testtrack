package com.eazyverse.testtrack

/**
 * Deployment-specific configuration.
 *
 * Every value comes from `local.properties` (or an environment variable) via BuildConfig, so
 * nothing here is committed. A missing key arrives as an empty string rather than a wrong value
 * — see [isConfigured].
 */
object Config {

    /** OAuth *Web* client ID. The membership service checks the ID token's `aud` against it. */
    val WEB_CLIENT_ID: String = BuildConfig.WEB_CLIENT_ID

    /** Apps Script web app that answers `{ email, isMember }` for a Google ID token. */
    val GATE_URL: String = BuildConfig.GATE_URL

    /** Public group page, opened when a tester still needs to join. */
    val GROUP_URL: String = BuildConfig.GROUP_URL

    /**
     * Only files this app creates. Non-sensitive, so no Google verification is required.
     * Do not widen to `drive` or `drive.readonly` — those are restricted scopes.
     */
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    const val DRIVE_FOLDER = "TestTrack Proofs"

    /** Sign-in is Gmail-only; Play Console closed testing is keyed to Google accounts. */
    val ALLOWED_DOMAINS = setOf("gmail.com", "googlemail.com")

    /** False when local.properties was not filled in, so the app can say so plainly. */
    val isConfigured: Boolean
        get() = WEB_CLIENT_ID.isNotBlank() && GATE_URL.isNotBlank()
}
