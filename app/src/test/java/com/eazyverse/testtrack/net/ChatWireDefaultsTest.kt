package com.eazyverse.testtrack.net

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The safety fields read from a server that does not send them yet, and from one that does. */
class ChatWireDefaultsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `an older page has no rules to ask and no stubs`() {
        val page = json.decodeFromString<ApiChatPage>("""{"state":"open","can_post":true,"messages":[{"id":"m1","text":"hi"}]}""")
        assertFalse(page.rulesNeeded)
        assertEquals(0, page.rulesVersion)
        assertFalse(page.messages[0].blocked)
        assertFalse(page.messages[0].held)
    }

    @Test
    fun `a page asks for the rules and marks stubs`() {
        val page =
            json.decodeFromString<ApiChatPage>(
                """{"rules_needed":true,"rules_version":2,"messages":[{"id":"a","blocked":true},{"id":"b","held":true}]}""",
            )
        assertTrue(page.rulesNeeded)
        assertEquals(2, page.rulesVersion)
        assertTrue(page.messages[0].blocked)
        assertTrue(page.messages[1].held)
    }

    @Test
    fun `a report answer, the rules and a blocked member decode`() {
        val report = json.decodeFromString<ApiChatReport>("""{"id":"r1","target":"message","reason":"spam","held":true}""")
        assertTrue(report.held)
        assertEquals("spam", report.reason)
        val rules = json.decodeFromString<ApiChatRules>("""{"rules_version":1,"accepted_version":1,"rules_needed":false}""")
        assertFalse(rules.rulesNeeded)
        assertEquals(1, rules.acceptedVersion)
        val who = json.decodeFromString<ApiBlocked>("""{"user_id":"u1","name":"Kenji","blocked_at":"2026-11-24T00:00:00+00:00"}""")
        assertEquals("u1", who.userId)
        assertEquals("", who.photo)
        assertEquals("", json.decodeFromString<ApiBlocked>("{}").userId)
    }

    @Test
    fun `a report goes out with its reason code and note`() {
        assertEquals("""{"reason":"other","note":"why"}""", json.encodeToString(ChatReportIn("other", "why")))
        assertEquals("""{"version":1}""", json.encodeToString(ChatRulesIn(1)))
    }
}
