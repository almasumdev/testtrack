package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.domain.RoundRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Open all opens only what will count, and a round skips what is refused anyway.
 *
 * An older copy past its 24 hours, or a copy Play did not install, used to be in Open all: the
 * server refused that open, the whole round stopped there, and it stopped there again every time.
 */
class OpenAllTest {
    private fun app(
        id: String,
        versionCode: Long? = null,
        updateRequired: Boolean = false,
        done: Boolean = false,
        reported: Boolean = false,
        feedbackLate: Boolean = false,
        day: Int = 3,
    ) = CrewApp(
        id = id,
        label = "App $id",
        packageName = "com.example.$id",
        owner = "Kenji",
        done = done,
        reported = reported,
        versionCode = versionCode,
        updateRequired = updateRequired,
        feedbackLate = feedbackLate,
        day = day,
    )

    private val copies =
        mapOf(
            "com.example.ok" to HereCopy(true, fromPlay = true, versionCode = 5),
            "com.example.old" to HereCopy(true, fromPlay = true, versionCode = 4),
            "com.example.side" to HereCopy(true, fromPlay = false, versionCode = 5),
        )

    private fun copy(pkg: String) = copies[pkg] ?: HereCopy(false)

    @Test
    fun `an older copy past its 24 hours is left out, inside them it still opens`() {
        assertTrue(refusedHere(app("old", versionCode = 5, updateRequired = true), copy("com.example.old")))
        assertFalse(refusedHere(app("old", versionCode = 5, updateRequired = false), copy("com.example.old")))
        assertFalse(refusedHere(app("ok", versionCode = 5, updateRequired = true), copy("com.example.ok")))
    }

    @Test
    fun `a copy not from Play is left out`() {
        assertTrue(refusedHere(app("side"), copy("com.example.side")))
    }

    @Test
    fun `open all takes what will count and leaves the rest on their rows`() {
        val apps =
            listOf(
                app("ok"),
                app("old", versionCode = 5, updateRequired = true),
                app("side"),
                app("missing"),
                app("ok", done = true),
                app("ok", reported = true),
                app("ok", feedbackLate = true),
            )
        assertEquals(listOf("ok"), openAllOf(apps, ::copy).map { it.id })
    }

    @Test
    fun `once updated, open all opens just that one`() {
        val updated = mapOf("com.example.old" to HereCopy(true, fromPlay = true, versionCode = 5))
        val left = listOf(app("old", versionCode = 5, updateRequired = true), app("ok", done = true))
        assertEquals(listOf("old"), openAllOf(left, { updated[it] ?: HereCopy(false) }).map { it.id })
    }

    @Test
    fun `an app skipped only for tonight's picture waiting`() {
        val apps = listOf(app("ok", day = 6))
        assertTrue(openAllOf(apps, ::copy, sendingTonight = { it.day == 6 }).isEmpty())
        assertEquals(1, openAllOf(apps, ::copy, sendingTonight = { it.day == 5 }).size)
    }

    @Test
    fun `a round skips an open refused on purpose and stops for anything else`() {
        listOf(400, 403, 404, 409, 422).forEach { assertTrue("$it", RoundRules.refusedOpen(it)) }
        assertFalse(RoundRules.refusedOpen(401))
        assertFalse(RoundRules.refusedOpen(403, auth = true))
        assertFalse(RoundRules.refusedOpen(429))
        assertFalse(RoundRules.refusedOpen(500))
        assertFalse(RoundRules.refusedOpen(0))
    }

    @Test
    fun `the round's last line says how many were left`() {
        assertEquals("That is tonight done.", RoundRules.lastWord(emptyList()))
        assertEquals("1 left. Update Steps first.", RoundRules.lastWord(listOf("Update Steps first.")))
    }
}
