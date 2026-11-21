package com.eazyverse.testtrack.net

/*
 * Private testers: the owner asks by email and sees the nights; the person asked answers, opens
 * the app, or stops. Under /private-tests. See PrivateWire.kt for the shapes.
 */

/** Ask somebody to test the app on [seatId]. An ask already open comes back unchanged. */
suspend fun Api.privateInvite(body: PrivateInviteIn): ApiPrivateTester = post("/private-tests", body)

/** The asks made of the reader that are still open. */
suspend fun Api.myPrivateTests(): ApiMyPrivateTests = get("/private-tests/mine")

suspend fun Api.acceptPrivate(
    id: String,
    body: PrivateAcceptIn,
): ApiMyPrivateTest = post("/private-tests/$id/accept", body)

/** Final for that app in that run: its owner can't ask again. */
suspend fun Api.declinePrivate(id: String): ApiMyPrivateTest = post("/private-tests/$id/decline", Empty())

/** Stop testing an app said yes to. The nights opened stay in the owner's list. */
suspend fun Api.leavePrivate(id: String): ApiMyPrivateTest = post("/private-tests/$id/leave", Empty())

/** The owner takes somebody off, or takes back an ask not answered yet. */
suspend fun Api.removePrivate(id: String): ApiPrivateTester = post("/private-tests/$id/remove", Empty())

/** Tonight's open of a private test, counted after 10 seconds in front on a copy from Play. */
suspend fun Api.privateVisit(
    id: String,
    body: PrivateVisitIn,
): ApiMyPrivateTest = post("/private-tests/$id/visit", body)

/** Everyone the owner asked about the app on [seatId], with each night. Owner only. */
suspend fun Api.privateResults(seatId: String): ApiPrivateResults = get("/private-tests/seat/$seatId")
