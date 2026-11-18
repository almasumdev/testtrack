package com.eazyverse.testtrack.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The link check against the cases the server is tested with. chat_link_cases.json is a copy of
 * the backend's tests/fixtures/chat_link_cases.json: change both together.
 */
class ChatLinksTest {
    private val cases =
        Json
            .parseToJsonElement(
                requireNotNull(javaClass.classLoader?.getResource("chat_link_cases.json")).readText(),
            ).jsonObject

    private fun list(key: String) = cases[key]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }

    private val allow = list("allow_packages").toSet()

    @Test
    fun `every blocked case is refused`() {
        val missed = list("blocked").filterNot { ChatLinks.blocked(it, allow) }
        assertTrue("let through: $missed", missed.isEmpty())
    }

    @Test
    fun `every allowed case goes`() {
        val refused = list("allowed").filter { ChatLinks.blocked(it, allow) }
        assertTrue("refused: $refused", refused.isEmpty())
    }

    @Test
    fun `a crew package is only allowed for that crew`() {
        assertFalse(ChatLinks.blocked("Try com.crew.app tonight", setOf("com.crew.app")))
        assertTrue(ChatLinks.blocked("Try com.crew.app tonight"))
    }

    @Test
    fun `each fold on its own`() {
        assertEquals("example.com", ChatLinks.normalise("\uFF25\uFF38\uFF21\uFF2D\uFF30\uFF2C\uFF25\uFF0E\uFF23\uFF2F\uFF2D"))
        assertEquals("zerowidth.com", ChatLinks.normalise("zero\u200Bwidth.com"))
        assertEquals("example.com", ChatLinks.normalise("example\u3002com"))
        assertEquals("example.com", ChatLinks.normalise("example[.]com"))
        assertEquals("example.com", ChatLinks.normalise("example (dot) com"))
        assertEquals("example.com", ChatLinks.normalise("example dot com"))
        assertEquals("example.com", ChatLinks.normalise("e x a m p l e . c o m"))
        assertEquals("example.com", ChatLinks.normalise("example .com"))
        assertEquals("thanks. me too", ChatLinks.normalise("Thanks. Me too"))
        assertEquals("  tonight", ChatLinks.normalise("com.crew.app tonight", setOf("com.crew.app")))
    }

    @Test
    fun `long hostile text is checked quickly`() {
        val inputs =
            listOf(
                "a.".repeat(1500),
                "a-".repeat(1500) + "a.com",
                ("x" + "-".repeat(60)).repeat(48),
                "e ".repeat(1500),
                "1.".repeat(1500),
                "@".repeat(3000),
                "a.b".repeat(1000),
                "a" + " ".repeat(2998) + ".",
                "a".repeat(3000),
            )
        // Once to load the classes and the top-level domains, then timed.
        inputs.forEach { ChatLinks.blocked(it) }
        inputs.forEach { input ->
            val start = System.nanoTime()
            ChatLinks.blocked(input)
            val ms = (System.nanoTime() - start) / 1_000_000
            assertTrue("took $ms ms on ${input.take(20)}", ms < 50)
        }
    }
}
