package com.eazyverse.testtrack.net

/** The picture of an app, onto the visit it was taken on: what makes that visit count. */
suspend fun Api.visitPicture(
    seatId: String,
    picture: PictureIn,
): ApiSeat = post("/crews/seats/$seatId/visit/picture", picture)

/** Every member's pictures of your own app in this crew, night by night. */
suspend fun Api.crewPictures(crewId: String): ApiCrewPictures = get("/crews/$crewId/pictures")
