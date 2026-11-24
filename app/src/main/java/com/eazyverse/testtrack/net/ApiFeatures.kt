package com.eazyverse.testtrack.net

import com.eazyverse.testtrack.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor

/**
 * What this build understands that an older one does not, told to our server on every request.
 *
 * The server keeps new behaviour behind this header, so an app that never sends it goes on being
 * answered the old way. Add a name to [LIST] when the app learns a new one; never take one out
 * while builds that rely on it are still about.
 */
object ApiFeatures {
    const val HEADER = "X-TT-Features"

    /**
     * `multi-crew`: volunteering in several crews at once, and spots that are over left off the list.
     * `chat`: the crew chat with its reports, blocks and rules; the server shows no chat without it.
     */
    val LIST = listOf("multi-crew", "chat")

    val VALUE: String = LIST.joinToString(",")

    private val apiHost: String by lazy { BuildConfig.API_BASE.toHttpUrl().host }

    /** Puts [HEADER] on requests to our API only; another host has no use for it. */
    val header =
        Interceptor { chain ->
            val request = chain.request()
            if (request.url.host != apiHost) return@Interceptor chain.proceed(request)
            chain.proceed(request.newBuilder().header(HEADER, VALUE).build())
        }
}
