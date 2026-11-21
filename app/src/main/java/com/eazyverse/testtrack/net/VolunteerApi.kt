package com.eazyverse.testtrack.net

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/*
 * Volunteering in a running crew: the crews with spots, taking one, and leaving it.
 */

/** Running crews with volunteer spots, and where the reader stands with each. */
suspend fun Api.volunteerSpots(): ApiVolunteerSpots = get("/volunteers/spots")

/** Take a volunteer spot. Answers with the crew as the new volunteer now sees it. */
suspend fun Api.volunteer(body: VolunteerIn): ApiCrew = post("/volunteers", body)

/** Leave a volunteer spot for good: that crew never takes this account back. */
suspend fun Api.leaveVolunteer(volunteerId: String) {
    sendText(Request.Builder().url(url("/volunteers/$volunteerId/leave")).post("{}".toRequestBody(JSON)))
}
