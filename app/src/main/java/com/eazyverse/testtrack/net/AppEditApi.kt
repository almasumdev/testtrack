package com.eazyverse.testtrack.net

/**
 * Change an app: PATCH /apps/{id}, with only the fields in [body] that are not null. The boxes
 * before a seat and the name and descriptions from the app's page both go through here. Answers
 * with the app as it is now.
 */
suspend fun Api.editApp(
    appId: String,
    body: AppEditIn,
): ApiApp = patch("/apps/$appId", body)
