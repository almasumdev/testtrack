package com.eazyverse.testtrack.data

/**
 * Last-known values, held for the life of the process.
 *
 * Every screen here is a read of data that barely changes minute to minute, and a ViewModel is
 * rebuilt on every navigation — so without this, walking into a group and straight back out spins
 * a loading indicator over content the app already had. Screens read the cache synchronously,
 * render it, and refresh underneath: the spinner is only ever seen on genuinely first sight.
 *
 * Deliberately not persisted. It is a nicety for a session, not a source of truth, and stale
 * proof counts surviving a cold start would be worse than a brief skeleton.
 */
object Cache {

    private val entries = mutableMapOf<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: String): T? = entries[key] as? T

    fun put(key: String, value: Any) {
        entries[key] = value
    }

    /**
     * Signing out must not leave the next account looking at the last one's groups.
     *
     * The decoded faces go too. They are keyed by their own bytes rather than by account, so they
     * could not be served to the wrong person, but holding a stranger's photograph in memory after
     * they have signed out is not a thing to leave to that argument.
     */
    fun clear() {
        entries.clear()
        Photo.clear()
    }

    fun groups(uid: String) = "groups:$uid"
    fun pending(uid: String) = "pending:$uid"
    fun group(id: String) = "group:$id"
    fun appsIn(groupId: String) = "appsIn:$groupId"
    fun app(id: String) = "app:$id"
    fun proofs(appId: String) = "proofs:$appId"
    fun testers(groupId: String) = "testers:$groupId"
    fun doneToday(uid: String, groupId: String, day: Int) = "done:$uid:$groupId:$day"
    fun reporters(appId: String, day: Int) = "reporters:$appId:$day"
}
