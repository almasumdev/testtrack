package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiMyPrivateTests
import com.eazyverse.testtrack.net.ApiPrivateResults
import com.eazyverse.testtrack.net.ApiPrivateTester
import com.eazyverse.testtrack.net.PrivateInviteIn
import com.eazyverse.testtrack.net.PrivateVisitIn
import com.eazyverse.testtrack.viewmodel.InviteRefusal
import com.eazyverse.testtrack.viewmodel.PrivateState
import com.eazyverse.testtrack.viewmodel.PrivateTest
import com.eazyverse.testtrack.viewmodel.looksLikeEmail
import com.eazyverse.testtrack.viewmodel.mergeMine
import com.eazyverse.testtrack.viewmodel.privateCountsLine
import com.eazyverse.testtrack.viewmodel.toResults
import com.eazyverse.testtrack.viewmodel.toTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Private testers, read from the server's own shapes and drawn as the screens draw them: the
 * owner's grid and lines, the tester's band, and an ask that stops while the reader is away.
 */
class PrivateTestersWireTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val mine =
        """
        {"tests":[
          {"id":"a1","seat_id":"s1","crew_id":"c1","state":"invited","app_name":"Budget Buddy",
           "package_name":"","icon":"https://x/i.png","notes":"","owner_name":"Hasan Rahman",
           "crew_name":"Crew 58","day":0,"first_night":0,"run_days":14,"opened_today":false,"opened_days":0},
          {"id":"a2","seat_id":"s2","crew_id":"c2","state":"accepted","app_name":"Tide Times",
           "package_name":"com.example.tide","icon":"","notes":"Sign in with demo","owner_name":"Mina Das",
           "crew_name":"Crew 61","day":9,"first_night":5,"run_days":14,"opened_today":true,"opened_days":4}
        ]}
        """.trimIndent()

    private fun tester(
        id: String,
        state: String,
        nights: String = "[]",
        first: Int = 0,
        day: Int = 0,
    ) = "{\"id\":\"$id\",\"seat_id\":\"s1\",\"tester_id\":\"u$id\",\"name\":\"Tester $id\",\"photo\":\"\"," +
        "\"email\":\"$id@example.com\",\"state\":\"$state\",\"end_reason\":\"\",\"created_at\":\"2026-11-20T10:00:00Z\"," +
        "\"answered_at\":null,\"ended_at\":null,\"first_night\":$first,\"day\":$day,\"nights\":$nights,\"opened_days\":0}"

    private val results =
        "{\"seat_id\":\"s1\",\"app_name\":\"Budget Buddy\",\"run_days\":14,\"testers\":[" +
            tester(
                "lina",
                "accepted",
                "[{\"day\":3,\"opened\":true},{\"day\":4,\"opened\":false},{\"day\":5,\"opened\":true}]",
                3,
                5,
            ) + "," +
            tester("omar", "accepted", "[{\"day\":4,\"opened\":true},{\"day\":5,\"opened\":false}]", 4, 5) + "," +
            tester("sam", "invited") + "," +
            tester("rafi", "declined") + "," +
            tester("tania", "removed") + "]}"

    @Test
    fun `the reader's asks read, with the package only once accepted`() {
        val tests = json.decodeFromString<ApiMyPrivateTests>(mine).tests.map { it.toTest() }
        val (invite, testing) = tests
        assertEquals(PrivateState.INVITED, invite.state)
        assertEquals("", invite.packageName)
        assertEquals("Hasan Rahman", invite.ownerName)
        assertEquals(PrivateState.ACCEPTED, testing.state)
        assertEquals("com.example.tide", testing.packageName)
        assertEquals("Sign in with demo", testing.notes)
        assertEquals(5, testing.daysLeft)
        assertEquals(5, testing.nightsIn)
        assertTrue(testing.openedToday)
    }

    @Test
    fun `an ask with only its id still reads`() {
        val got =
            json
                .decodeFromString<ApiMyPrivateTests>("{\"tests\":[{\"id\":\"x\"}]}")
                .tests
                .single()
                .toTest()
        assertEquals(PrivateState.INVITED, got.state)
        assertEquals(14, got.runDays)
        val bare = json.decodeFromString<ApiPrivateTester>("{\"id\":\"y\",\"email\":\"y@example.com\"}")
        assertTrue(bare.nights.isEmpty())
        assertEquals(0, bare.firstNight)
        assertTrue(json.decodeFromString<ApiMyPrivateTests>("{}").tests.isEmpty())
    }

    @Test
    fun `the owner's list drops those taken off and counts who tests`() {
        val read = json.decodeFromString<ApiPrivateResults>(results).toResults()
        assertEquals(listOf("lina", "omar", "sam", "rafi"), read.shown.map { it.id })
        assertEquals(2, read.testing)
        assertEquals(1, read.invited)
        assertEquals("2 testing, 1 invited. Only you see them.", privateCountsLine(read))
        assertEquals("Add people by email. Only you see them.", privateCountsLine(null))
        assertTrue(read.shown.first().openedTonight)
        assertFalse(read.shown[1].openedTonight)
    }

    @Test
    fun `nights are squares with nothing owed`() {
        val read = json.decodeFromString<ApiPrivateResults>(results).toResults()
        val lina = privateSquares(read.testers[0], 14)
        assertEquals(14, lina.size)
        assertEquals(listOf(Square.BEFORE, Square.BEFORE, Square.PICTURE, Square.NOT_OWED, Square.PICTURE), lina.take(5))
        assertEquals(Square.TO_COME, lina[5])
        val omar = privateSquares(read.testers[1], 14)
        assertEquals(Square.TONIGHT, omar[4])
        assertFalse(omar.contains(Square.MISSED))
        assertTrue(privateSquares(read.testers[2], 14).isEmpty())
    }

    @Test
    fun `the owner's lines, labels, order and chip`() {
        val read = json.decodeFromString<ApiPrivateResults>(results).toResults()
        val byId = read.testers.associateBy { it.id }
        assertEquals("Opened tonight" to Tone.OK, privateLineOf(byId.getValue("lina")))
        assertEquals("Not opened tonight" to Tone.QUIET, privateLineOf(byId.getValue("omar")))
        assertEquals("Invited, no answer yet" to Tone.QUIET, privateLineOf(byId.getValue("sam")))
        assertEquals("Declined your invite" to Tone.BAD, privateLineOf(byId.getValue("rafi")))
        assertEquals("3 nights", privateNightsLabel(byId.getValue("lina")))
        assertEquals("", privateNightsLabel(byId.getValue("sam")))
        assertEquals(listOf("lina", "omar", "sam", "rafi"), privateOrder(read.shown).map { it.id })
        assertEquals("1 of 2 opened tonight" to Tone.ACCENT, privateChipOf(read.shown))
        assertTrue(privateTesterOf(byId.getValue("sam"), 14).squares.isEmpty())
        assertEquals("Testing ends with Crew 58 in 6 days.", privateEndsLine("Crew 58", 6))
    }

    @Test
    fun `an accepted ask gone from the list stays as stopped, an unanswered one goes`() {
        val before =
            listOf(
                PrivateTest("a1", "s1", "c1", PrivateState.INVITED, "Budget Buddy"),
                PrivateTest("a2", "s2", "c2", PrivateState.ACCEPTED, "Tide Times", crewName = "Crew 61", day = 14, runDays = 14),
            )
        val after = mergeMine(before, emptyList())
        assertEquals(listOf("a2"), after.map { it.id })
        assertEquals(PrivateState.ENDED, after.single().state)
        assertEquals("Crew 61 ended, so testing stopped", after.single().stoppedLine())
        val midRun = before[1].copy(day = 6, ownerName = "Mina Das")
        assertEquals("Testing for Mina Das stopped", midRun.stoppedLine())
    }

    @Test
    fun `the tester's band says what to do tonight`() {
        val test = PrivateTest("a2", "s2", "c2", PrivateState.ACCEPTED, "Tide Times", "com.example.tide", day = 9, firstNight = 5)
        assertEquals("Install", privateBandOf(test, installed = false).action)
        assertEquals("Open", privateBandOf(test, installed = true).action)
        assertEquals(null, privateBandOf(test.copy(openedToday = true), installed = true).action)
        assertEquals("Opened tonight", privateBandOf(test.copy(openedToday = true), installed = true).text)
        assertEquals(null, privateBandOf(test.copy(state = PrivateState.ENDED), installed = true).action)
        assertTrue(test.asCrewApp().privateTestId == "a2")
    }

    @Test
    fun `what goes out names the server's fields`() {
        val visit = json.encodeToJsonElement(PrivateVisitIn.serializer(), PrivateVisitIn(12_000, "com.android.vending")).jsonObject
        assertEquals(setOf("used_ms", "installer"), visit.keys)
        val invite = json.encodeToJsonElement(PrivateInviteIn.serializer(), PrivateInviteIn("s1", "sam@example.com")).jsonObject
        assertEquals(setOf("seat_id", "email"), invite.keys)
    }

    @Test
    fun `an address is checked for its shape only`() {
        assertTrue(looksLikeEmail(" sam.rahman@gmail.com "))
        assertFalse(looksLikeEmail("sam.rahman"))
        assertFalse(looksLikeEmail("sam@gmail"))
        assertFalse(looksLikeEmail("sam rahman@gmail.com"))
        assertTrue(InviteRefusal("a@b.co", InviteRefusal.NO_ACCOUNT, "No TestTrack account uses that email.").noAccount)
        assertTrue(InviteRefusal.DECLINED_BEFORE in InviteRefusal.INLINE)
    }
}
