package com.eazyverse.testtrack.net

import com.google.firebase.auth.FirebaseAuth
import kotlinx.serialization.encodeToString

/**
 * The You page's last known profile, kept on the phone and shown at once.
 *
 * The other kept answers in [ApiCache] are an offline backup only. This one is shown online too,
 * because a name, a face and an address do not go stale the way a list does: the page draws what
 * it had and the next answer replaces it quietly, figures included. Only with nothing kept, which
 * is the first sign-in on this phone, does the page shimmer.
 *
 * It is the same file [Api.profile] writes, so every fresh answer keeps it current, and it goes
 * with everything else in [ApiCache] when the session ends. Belt and braces on top of that: it is
 * only handed back while signed in, and never for an address other than the signed-in one.
 */
object ProfileCache {
    /** The kept profile for whoever is signed in now, or null. A small disk read. */
    fun read(): ApiProfile? = read(runCatching { FirebaseAuth.getInstance().currentUser?.email }.getOrNull())

    /** The kept profile if it belongs to [email]; any kept one when the address is not known. */
    fun read(email: String?): ApiProfile? {
        if (!ApiSession.signedIn) return null
        val kept = Api.last<ApiProfile>(Api.PROFILE, afterFailure = true) ?: return null
        return kept.takeIf { email.isNullOrBlank() || it.email.equals(email, ignoreCase = true) }
    }

    /**
     * Keep [profile] after a change made on this phone, such as a new name or face, so the next
     * visit opens on it even if the refresh after the change does not get through. Refused if
     * the session changed since [generation] was read, like every other write to the cache.
     */
    fun keep(
        profile: ApiProfile,
        generation: Long,
    ) = ApiCache.write(Api.PROFILE, Api.json.encodeToString(profile), generation)
}
