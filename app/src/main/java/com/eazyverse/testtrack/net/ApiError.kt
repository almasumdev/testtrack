package com.eazyverse.testtrack.net

/**
 * Something the API refused, with the reason it gave.
 *
 * Carries the stable code as well as the sentence, because a screen that wants to react to one
 * particular refusal should match on the code. Matching on the message means a reworded string
 * silently changes behaviour.
 */
class ApiError(
    val status: Int,
    val code: String,
    override val message: String,
) : Exception(message) {
    /** The session is gone or was never valid. The one case worth handling everywhere. */
    val isAuth: Boolean get() = status == 401 || code == "unauthenticated"
}
