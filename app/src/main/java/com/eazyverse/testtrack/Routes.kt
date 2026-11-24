package com.eazyverse.testtrack

import android.net.Uri

object Routes {
    const val ONBOARDING = "onboarding"

    // There is no sign-in route. It is a sheet over whatever asked for it; see ui/SignInSheet.

    /** Not one screen: the five tabbed ones, held by [com.eazyverse.testtrack.ui.MainShell]. */
    const val HOME = "home"

    /** Asking the circle to look at your app. A page, because finding the app is its own step. */
    const val ABOUT = "about"

    const val REVIEW = "review?post={post}"

    fun review(post: String) = "review?post=${Uri.encode(post)}"

    /** A review of your app, or a case to decide, on its own page. */
    const val CHECK = "check/{id}"

    fun check(id: String) = "check/${Uri.encode(id)}"

    /** One crew on its own page. */
    const val CREW = "crew/{id}"
    const val CREW_MEMBERS = "crew/{id}/members"

    fun crew(id: String) = "crew/${Uri.encode(id)}"

    fun crewMembers(id: String) = "crew/${Uri.encode(id)}/members"

    /** A crew's chat. See ChatRoutes.kt. */
    const val CREW_CHAT = "crew/{id}/chat"

    fun crewChat(id: String) = "crew/${Uri.encode(id)}/chat"

    /** The members you blocked, from Settings. */
    const val BLOCKED = "blocked-members"

    /** Asking for feedback on an app from My apps. */
    const val ASK_APP = "ask/{app}"

    fun askFor(appId: String) = "ask/${Uri.encode(appId)}"

    /** An app on My apps, on its own page. */
    const val APP = "app/{id}"

    fun app(id: String) = "app/${Uri.encode(id)}"

    /** Taking a seat with one app of My apps, on its own screen. */
    const val CREW_SEAT = "seat/{app}"

    fun crewSeat(appId: String) = "seat/${Uri.encode(appId)}"

    /** Adding an app to My apps. */
    const val APP_ADD = "app-add"
}
