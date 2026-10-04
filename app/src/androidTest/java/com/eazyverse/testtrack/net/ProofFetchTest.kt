package com.eazyverse.testtrack.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A proof screenshot reaches somebody entitled to it.
 *
 * Shipped broken: the image loader was given the plain client, which carries no token, so the
 * server refused every proof with a 401 and no screenshot ever showed, to owners or jurors. Needs
 * a seeded session that may see one proof (`access`, `refresh`, `proof` arguments).
 */
@RunWith(AndroidJUnit4::class)
class ProofFetchTest {
    private val args = InstrumentationRegistry.getArguments()
    private val access = args.getString("access").orEmpty()
    private val refresh = args.getString("refresh").orEmpty()
    private val proof = args.getString("proof").orEmpty()

    @Before
    fun requireASeededSession() {
        assumeTrue("No seeded session; pass access, refresh and proof.", access.isNotEmpty() && proof.isNotEmpty())
        ApiSession.load(InstrumentationRegistry.getInstrumentation().targetContext)
        ApiSession.remember(Tokens(access, refresh, 900))
    }

    private fun status(client: okhttp3.OkHttpClient): Pair<Int, String?> =
        client.newCall(Request.Builder().url(Api.proofUrl(proof)).build()).execute().use { it.code to it.header("Content-Type") }

    @Test
    fun the_image_client_carries_the_session_and_gets_the_picture() {
        assertEquals(200 to "image/jpeg", status(Api.images))
    }

    @Test
    fun the_plain_client_does_not_which_is_why_images_have_their_own() {
        assertEquals(401, status(Api.http).first)
    }
}
