package com.eazyverse.testtrack

/**
 * Deployment-specific configuration.
 *
 * Every value comes from `local.properties` (or an environment variable) via BuildConfig, so
 * nothing here is committed. A missing key arrives as an empty string rather than a wrong value
 * See [isConfigured].
 */
object Config {
    /** OAuth *Web* client ID. The membership service checks the ID token's `aud` against it. */
    val WEB_CLIENT_ID: String = BuildConfig.WEB_CLIENT_ID

    /** Apps Script web app that answers `{ email, isMember }` for a Google ID token. */
    val GATE_URL: String = BuildConfig.GATE_URL

    /** Public group page, opened when a tester still needs to join. */
    val GROUP_URL: String = BuildConfig.GROUP_URL

    /**
     * The group's address, which an owner adds to their closed track's testers: the name in
     * [GROUP_URL] at googlegroups.com, so the two can never name different groups.
     */
    val GROUP_EMAIL: String =
        GROUP_URL
            .substringAfter("/g/", "")
            .substringBefore('/')
            .substringBefore('?')
            .let { if (it.isBlank()) "" else "$it@googlegroups.com" }

    /**
     * Only files this app creates. Non-sensitive, so no Google verification is required.
     * Do not widen to `drive` or `drive.readonly`: those are restricted scopes.
     */
    const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    const val DRIVE_FOLDER = "TestTrack Proofs"

    /** False when local.properties was not filled in, so the app can say so plainly. */
    val isConfigured: Boolean
        get() = WEB_CLIENT_ID.isNotBlank() && GATE_URL.isNotBlank()
}
