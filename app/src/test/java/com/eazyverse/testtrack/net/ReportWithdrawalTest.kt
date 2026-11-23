package com.eazyverse.testtrack.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportWithdrawalTest {
    private fun refusal(
        status: Int,
        code: String,
    ) = withdrawRefusal(ApiError(status, code, "server words"))

    @Test fun an_old_server_without_the_route_reads_as_not_available() {
        assertEquals(ReportWithdrawal.Unavailable, refusal(404, "unknown"))
        assertEquals(ReportWithdrawal.Unavailable, refusal(405, "unknown"))
    }

    @Test fun each_refusal_code_has_its_own_short_line() {
        val lines = listOf("no_report", "report_night_over", "report_whole_run", "report_app_out").map { refusal(409, it) }
        assertTrue(lines.all { it is ReportWithdrawal.Refused })
        assertEquals(4, lines.toSet().size)
    }

    @Test fun a_seat_that_is_gone_or_a_session_problem_is_left_to_the_caller() {
        assertNull(refusal(404, "not_found"))
        assertNull(refusal(401, "unauthenticated"))
        assertNull(refusal(500, "unknown"))
    }
}
