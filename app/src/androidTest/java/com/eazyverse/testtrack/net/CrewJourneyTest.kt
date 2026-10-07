package com.eazyverse.testtrack.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eazyverse.testtrack.data.Device
import com.eazyverse.testtrack.ui.CrewPhase
import com.eazyverse.testtrack.ui.CrewRules
import com.eazyverse.testtrack.ui.crewPhase
import com.eazyverse.testtrack.ui.toCrew
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Taking a seat, and everything that refuses one, from this phone against the real server.
 *
 * [com.eazyverse.testtrack.ui.CrewWireTest] covers what the app works out from an answer this
 * repository wrote by hand. This runs the real Api object over the network and maps what actually
 * comes back, so a renamed field or a changed refusal shows up here rather than on somebody's
 * screen.
 *
 * Signing in works the way ReviewJourneyTest's does: the session is minted on the server by
 * scripts/seed_e2e.py and arrives as instrumentation arguments. Without them every test skips.
 * Run it through scripts/e2e.sh.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CrewJourneyTest {
    private val args = InstrumentationRegistry.getArguments()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val reviewerAccess = args.getString("reviewerAccess").orEmpty()
    private val reviewerRefresh = args.getString("reviewerRefresh").orEmpty()
    private val ownerAccess = args.getString("ownerAccess").orEmpty()
    private val ownerRefresh = args.getString("ownerRefresh").orEmpty()

    @Before
    fun requireASeededWorld() {
        assumeTrue(
            "No seeded session. Run scripts/e2e.sh rather than this class on its own.",
            reviewerAccess.isNotEmpty(),
        )
        ApiSession.load(context)
        ApiSession.remember(Tokens(reviewerAccess, reviewerRefresh, 900))
    }

    private fun fingerprint() = requireNotNull(Device.fingerprint(context)) { "no ANDROID_ID" }

    private fun uniquePackage() = "com.eazyverse.e2e.crew${System.currentTimeMillis()}"

    /*
     * Finding this account's own rows, rather than assuming they are the only ones there.
     *
     * The seeded world shares a server with whatever else is on it, including crews seeded by
     * hand for somebody to look at. A journey that says "the crew" is a journey that starts
     * failing the first time the thing it tests is used by anybody else.
     */

    private suspend fun myCrew(): ApiCrew =
        Api.crews().mine.firstOrNull { it.mySeatId != null }
            ?: error("This account holds no seat, so there is nothing to look at.")

    private suspend fun mineNow(seatId: String): ApiCrew =
        Api.crews().mine.firstOrNull { crew -> crew.members.any { it.id == seatId } }
            ?: error("The seat just taken is not in any crew this account is in.")

    private suspend fun myOwnSeat(): ApiSeat = myCrew().members.first { it.mine }

    // ------------------------------------------------------------------ the journey

    @Test
    fun t1_a_seat_goes_into_a_crew_once_play_shows_it() =
        runBlocking {
            val before = Api.crews()
            assertNull("A seeded account should start free to take a seat", before.blocked)

            // The server looks the app up on Play as the testers group sees it. A package nobody
            // published is refused with how to fix it; the rest of this journey needs a real
            // closed test, named by the e2e script, to go any further.
            val pkg = args.getString("crewPackage").orEmpty()
            if (pkg.isEmpty()) {
                try {
                    Api.takeSeat(SeatIn(packageName = uniquePackage(), deviceFp = fingerprint(), timezone = "Asia/Dhaka"))
                    fail("A package Play does not show should be refused.")
                } catch (e: ApiError) {
                    assertEquals("listing_not_visible", e.code)
                }
                assumeTrue("No crewPackage argument: nothing on Play to take a seat with.", false)
            }

            val seat =
                Api.takeSeat(
                    SeatIn(
                        packageName = pkg,
                        notes = "Test account: demo@example.com / demo",
                        deviceFp = fingerprint(),
                        timezone = "Asia/Dhaka",
                    ),
                )

            assertTrue(seat.state == "confirmed" || seat.state == "checking")
            assertTrue(seat.mine)

            // And the crew it landed in now says so.
            //
            // Nothing here counts absolutely. This runs against the live server, which has other
            // people's crews on it, and a test that assumes it is alone in the world passes only
            // until somebody else uses the thing it is testing.
            val crew = mineNow(seat.id)
            assertEquals(CrewRules.SEATS, crew.seats)
            assertEquals(CrewRules.RUN_DAYS, crew.runDays)
            assertEquals("filling", crew.state)
            // Taken but not counted: a seat nobody has installed is somebody's intention.
            assertTrue("The new seat is in the taken count", crew.taken >= 1)
            assertTrue("A crew cannot hold more than its seats", crew.taken <= crew.seats)
            assertTrue(crew.members.any { it.id == seat.id && it.mine })

            // The screen's own reading of it.
            val drawn = crew.toCrew()
            assertEquals(CrewPhase.LOBBY, drawn.phase)
            assertEquals(0, drawn.day)
            assertTrue("A crew that has not started owes nothing tonight", drawn.apps.isEmpty())

            // Holding one now, so the server says why another cannot be taken.
            assertNotNull(
                "The screen has to be told why it cannot offer a seat",
                Api.crews().blocked,
            )
        }

    @Test
    fun t2_a_second_seat_before_the_first_starts_is_refused() =
        runBlocking {
            try {
                Api.takeSeat(
                    SeatIn(
                        appName = "Another",
                        packageName = uniquePackage(),
                        deviceFp = fingerprint(),
                        timezone = "Asia/Dhaka",
                    ),
                )
                fail("Holding an unstarted seat should stop a second one.")
            } catch (e: ApiError) {
                assertEquals(409, e.status)
                assertEquals("conflict", e.code)
            }
        }

    @Test
    fun t3_nobody_reports_their_own_app() =
        runBlocking {
            val mine = myOwnSeat()
            try {
                Api.report(mine.id, "It does not install for me.")
                fail("Reporting your own app should be refused.")
            } catch (e: ApiError) {
                assertEquals(403, e.status)
            }
        }

    @Test
    fun t4_the_crew_page_is_readable_from_inside_and_not_from_outside() =
        runBlocking {
            val crew = myCrew()

            // A member sees the list, which carries package names and the notes that get a tester
            // past a front door.
            val inside = Api.crew(crew.id)
            assertTrue(inside.members.isNotEmpty())
            assertTrue(inside.members.any { it.mine })

            // The seeded owner is not in this crew, so the same id is refused.
            ApiSession.remember(Tokens(ownerAccess, ownerRefresh, 900))
            try {
                Api.crew(crew.id)
                fail("A crew should not be readable by somebody outside it.")
            } catch (e: ApiError) {
                assertEquals(403, e.status)
            } finally {
                ApiSession.remember(Tokens(reviewerAccess, reviewerRefresh, 900))
            }
        }

    @Test
    fun t5_a_seat_can_be_given_up_and_the_place_reused() =
        runBlocking {
            val mine = myOwnSeat()

            val gone = Api.withdraw(mine.id)
            assertEquals("withdrawn", gone.state)

            val after = Api.crews()
            assertNull("Giving the seat up frees the account", after.blocked)

            // And the same account may take another, which is exactly what somebody told to fix
            // their listing is asked to do.
            val again =
                Api.takeSeat(
                    SeatIn(
                        packageName = mine.packageName,
                        deviceFp = fingerprint(),
                        timezone = "Asia/Dhaka",
                    ),
                )
            assertTrue(again.state == "confirmed" || again.state == "checking")
            assertFalse(again.id == mine.id)
        }

    @Test
    fun t6_the_open_crew_the_server_offers_is_one_that_can_still_be_joined() =
        runBlocking {
            val answer = Api.crews()
            val open = answer.open
            if (open == null) {
                // Legitimate: this account is already holding the only seat it may hold.
                assertNotNull(answer.blocked)
                return@runBlocking
            }
            assertEquals("filling", open.state)
            assertTrue("An open crew has room in it", open.taken < open.seats)
            assertEquals(CrewPhase.OPEN, crewPhase(open, member = false))
        }
}
