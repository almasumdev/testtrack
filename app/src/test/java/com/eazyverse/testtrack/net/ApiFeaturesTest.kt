package com.eazyverse.testtrack.net

import com.eazyverse.testtrack.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiFeaturesTest {
    /** Runs [url] through [ApiFeatures.header] and answers locally, returning the request as it left. */
    private fun sent(url: String): Request {
        var seen: Request? = null
        val answer =
            Interceptor { chain ->
                seen = chain.request()
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(ApiFeatures.header)
                .addInterceptor(answer)
                .build()
        client.newCall(Request.Builder().url(url).build()).execute().close()
        return seen!!
    }

    @Test fun valueIsTheCommaSeparatedList() {
        assertEquals("multi-crew", ApiFeatures.VALUE)
        assertEquals(ApiFeatures.LIST.joinToString(","), ApiFeatures.VALUE)
    }

    @Test fun requestToOurApiCarriesTheHeader() {
        assertEquals("multi-crew", sent(BuildConfig.API_BASE + "/volunteers/spots").header("X-TT-Features"))
    }

    @Test fun otherHostsAreNotTold() {
        assertNull(sent("https://lh3.googleusercontent.com/a/picture").header(ApiFeatures.HEADER))
    }

    @Test fun sharedClientUsesIt() {
        assertTrue(ApiFeatures.header in Api.http.interceptors)
    }
}
