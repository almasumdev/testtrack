package com.eazyverse.testtrack.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * The app against the real API, from a real device.
 *
 * Two things are being proved here and neither can be proved without a network.
 *
 * First, that the phone can reach the API at all: the right host, a certificate Android trusts,
 * and a route through. A unit test with a fake server proves none of that, and every one of them
 * has been the thing actually broken at some point.
 *
 * Second, that the two sides agree about the shape of what they send. The agreed plan is to
 * generate this client from the API's own OpenAPI document so drift is impossible; until that is
 * built, this reads the same document and checks that every field the Kotlin models expect is
 * really in it. A renamed field fails here instead of deserialising to null on somebody's screen.
 */
@RunWith(AndroidJUnit4::class)
class ApiContractTest {
    private val http =
        OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    private val json = Json { ignoreUnknownKeys = true }

    private fun fetch(path: String): String {
        val request = Request.Builder().url(com.eazyverse.testtrack.BuildConfig.API_BASE + path).build()
        http.newCall(request).execute().use { response ->
            assertTrue("$path answered ${response.code}", response.isSuccessful)
            return response.body?.string().orEmpty()
        }
    }

    @Test
    fun the_api_is_reachable_from_this_device() {
        assertTrue(fetch("/health").contains("\"status\":\"ok\""))
    }

    @Test
    fun an_unauthenticated_call_comes_back_in_the_agreed_error_shape() {
        runBlocking {
            ApiSession.clear()
            try {
                Api.me()
                fail("A call with no token should not have succeeded.")
            } catch (e: ApiError) {
                // The code is what the app branches on. The message is for a person and may be
                // reworded at any time, so nothing asserts on it.
                assertEquals(401, e.status)
                assertEquals("unauthenticated", e.code)
                assertTrue(e.isAuth)
            }
        }
    }

    @Test
    fun every_field_the_models_expect_is_in_the_schema() {
        val schemas =
            json
                .parseToJsonElement(fetchOpenApi())
                .jsonObject["components"]!!
                .jsonObject["schemas"]!!
                .jsonObject

        expect(schemas, "PostOut", POST_FIELDS)
        expect(schemas, "ReviewOut", REVIEW_FIELDS)
        expect(schemas, "DisputeOut", DISPUTE_FIELDS)
        expect(schemas, "TokensOut", "access_token refresh_token expires_in".split(" "))
        expect(schemas, "MeOut", "id email display_name tix is_admin".split(" "))
    }

    private fun fetchOpenApi(): String {
        val base = com.eazyverse.testtrack.BuildConfig.API_BASE
        val request = Request.Builder().url("$base/openapi.json").build()
        http.newCall(request).execute().use { response ->
            assertTrue("openapi.json answered ${response.code}", response.isSuccessful)
            return response.body?.string().orEmpty()
        }
    }

    private fun expect(
        schemas: kotlinx.serialization.json.JsonObject,
        name: String,
        fields: List<String>,
    ) {
        val properties =
            schemas[name]?.jsonObject?.get("properties")?.jsonObject
                ?: fail("The API no longer publishes a $name").let { return }
        for (field in fields) {
            assertTrue("$name lost the field $field", properties.containsKey(field))
        }
    }

    private companion object {
        val POST_FIELDS =
            listOf(
                "id",
                "app_name",
                "package_name",
                "ask",
                "is_live",
                "reward",
                "reviews_bought",
                "reviews_done",
                "owner_name",
                "mine",
                "my_review_id",
                "my_state",
                "created_at",
            )
        val REVIEW_FIELDS =
            listOf(
                "id",
                "post_id",
                "state",
                "used_ms",
                "needed_ms",
                "proof_ref",
                "written",
                "reason",
                "created_at",
                "submitted_at",
                "decided_at",
            )
        val DISPUTE_FIELDS =
            listOf(
                "id",
                "review_id",
                "app_name",
                "ask",
                "is_live",
                "proof_ref",
                "written",
                "reason",
                "stake",
                "votes_cast",
                "votes_needed",
                "outcome",
            )
    }
}
