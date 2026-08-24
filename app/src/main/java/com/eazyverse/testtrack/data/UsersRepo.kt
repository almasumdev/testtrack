package com.eazyverse.testtrack.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions

// ---- users ---------------------------------------------------------------------------

/**
 * Records the signed-in tester so other members' grids can show a name, not a raw uid.
 *
 * The name is written on the way in only while nobody has chosen one. This runs on every
 * sign-in, so writing it unconditionally would undo the profile screen: a tester sets their
 * name, signs out and back in, and Google's version lands on top of it.
 *
 * The address with its domain cut off counts as nobody having chosen. That is what this used
 * to store for everybody, so treating it as unset is what repairs the accounts that already
 * carry it, on their next sign-in, without touching a name anybody typed.
 */
suspend fun Repo.upsertUser(
    uid: String,
    email: String,
    displayName: String,
) {
    val doc = db.collection("users").document(uid)
    val existing =
        runCatching { await(doc.get()).getString("displayName").orEmpty() }
            .getOrDefault("")
    val chosen =
        existing.isNotBlank() &&
            !existing.equals(email.substringBefore('@'), ignoreCase = true)

    val fields =
        mutableMapOf<String, Any>(
            "uid" to uid,
            "email" to email,
            "updatedAt" to System.currentTimeMillis(),
        )
    if (!chosen) fields["displayName"] = displayName

    await(doc.set(fields, SetOptions.merge()))
}

/**
 * Records which handset this account signs in from.
 *
 * Its own collection rather than a field on the user document, and that is the whole of the
 * privacy design. `users/{uid}` is readable by every signed-in account, because a grid has to
 * turn a uid into a name; a device fingerprint sitting there would let any tester work out
 * which of their cohort share a phone. Here it is readable by its owner and by an admin, and
 * by nobody else.
 *
 * Keyed by uid, so each account claims one row and two accounts on one handset are two rows
 * carrying the same value. That is the whole detection: a duplicate is a repeated string.
 *
 * Failure is swallowed by the caller. A tester whose phone will not report an id still has a
 * working app, and this is a signal for an admin rather than a step in anybody's setup.
 */
suspend fun Repo.claimDevice(
    uid: String,
    fingerprint: String,
) {
    await(
        db.collection("devices").document(uid).set(
            mapOf(
                "uid" to uid,
                "deviceId" to fingerprint,
                "updatedAt" to System.currentTimeMillis(),
            ),
            SetOptions.merge(),
        ),
    )
}

/**
 * Just the members of one group, which is all any grid or dashboard ever needs.
 *
 * There is deliberately no "every tester" variant. One existed, went unused, and was a whole
 * user table one careless call away — the rules allow it, because a grid has to turn a uid
 * into a name, and that permission is only defensible while the queries stay this narrow.
 */
suspend fun Repo.testers(uids: Collection<String>): List<Tester> {
    if (uids.isEmpty()) return emptyList()
    return uids
        .distinct()
        .chunked(30)
        .flatMap { batch ->
            await(db.collection("users").whereIn("uid", batch).get())
                .documents
                .mapNotNull(::parseTester)
        }.sortedBy { it.shortName }
}

/**
 * The reason an admin gave for pausing this account, or null if it is in good standing.
 *
 * An empty string is a block with no reason recorded, which is still a block — hence a nullable
 * return rather than a blank one, so the two cannot be confused at the call site.
 */
suspend fun Repo.blockedReason(uid: String): String? {
    val doc = await(db.collection("users").document(uid).get())
    if (doc.getBoolean("blocked") != true) return null
    return doc.getString("blockedReason").orEmpty()
}

private fun parseTester(doc: DocumentSnapshot) =
    Tester(
        uid = doc.getString("uid") ?: doc.id,
        email = doc.getString("email").orEmpty(),
        displayName = doc.getString("displayName").orEmpty(),
        photo = doc.getString("photo").orEmpty(),
    ).takeIf { it.email.isNotBlank() }

/** This account's own row, for the profile screen. */
suspend fun Repo.me(uid: String): Tester? = parseTester(await(db.collection("users").document(uid).get()))

/**
 * The name and face this account shows the rest of its cohort.
 *
 * Written as one call because they are edited on one screen and a half-saved profile is worse
 * than an unsaved one. `photo` is a blank string rather than a deletion when it is cleared,
 * which keeps the shape of the document fixed and the rules' key list honest.
 */
suspend fun Repo.updateProfile(
    uid: String,
    displayName: String,
    photo: String,
) {
    await(
        db.collection("users").document(uid).set(
            mapOf(
                "displayName" to displayName.trim(),
                "photo" to photo,
                "updatedAt" to System.currentTimeMillis(),
            ),
            SetOptions.merge(),
        ),
    )
}

/**
 * Everything this account has ever submitted, whatever became of it.
 *
 * Constrained on `ownerUid` because rules are not filters: the read rule admits an app to its
 * owner, so the query has to say who is asking or the whole set is refused before a single
 * document is looked at. Unsorted here and ordered by the screen, which wants live ones first
 * rather than oldest first.
 */
suspend fun Repo.myApps(uid: String): List<TestApp> =
    await(
        db
            .collection("apps")
            .whereEqualTo("ownerUid", uid)
            .get(),
    ).documents.map(::parseApp)
