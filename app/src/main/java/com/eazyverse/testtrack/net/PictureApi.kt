package com.eazyverse.testtrack.net

/** The picture of an app, onto the visit it was taken on: what makes that visit count. */
suspend fun Api.visitPicture(
    seatId: String,
    picture: PictureIn,
): ApiSeat = post("/crews/seats/$seatId/visit/picture", picture)

/** Every member's pictures of your own app in this crew, night by night. */
suspend fun Api.crewPictures(crewId: String): ApiCrewPictures = get("/crews/$crewId/pictures")

/** The owner tells the crew a newer version is out, read off the copy Play put on their phone. */
suspend fun Api.tellUpdate(
    seatId: String,
    version: VersionIn,
): ApiSeat = post("/crews/seats/$seatId/version", version)

/** A member's private feedback on Play for one app, with its picture: once a run. */
suspend fun Api.sendFeedback(
    seatId: String,
    feedback: FeedbackIn,
): ApiSeat = post("/crews/seats/$seatId/feedback", feedback)

/** The owner marks their app down tonight, so nobody owes it. */
suspend fun Api.markDown(seatId: String): ApiSeat = post("/crews/seats/$seatId/down", emptyMap<String, String>())
