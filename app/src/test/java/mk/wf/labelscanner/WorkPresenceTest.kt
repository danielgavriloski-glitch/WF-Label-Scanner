package mk.wf.labelscanner

import org.junit.Assert.*
import org.junit.Test

class WorkPresenceTest {
    private fun event(id: String, type: String, at: Long, employee: String = "a") = PresenceEvent(id, employee, type, at)
    private fun state(vararg events: PresenceEvent) = AttendancePresence.reduce("a", events.toList(), 1000)

    @Test fun administratorDepartureClosesWorkingAndBreakStates() {
        val start = event("start", "work_start", 100)
        val pause = event("pause", "break_start", 200)
        val end = event("admin-end", "work_end", 300).copy(source = "admin_manual", enteredBy = "Админ")
        assertEquals(WorkPresence.WORKING, state(start).state)
        assertEquals(WorkPresence.BREAK, state(start, pause).state)
        for (events in listOf(listOf(start, end), listOf(start, pause, end))) {
            val result = AttendancePresence.reduce("a", events, 1000).copy(confirmed = true)
            assertEquals(WorkPresence.OFF, result.state)
            assertEquals("Админ", result.event?.enteredBy)
            assertFalse(result.allows("break_start")); assertFalse(result.allows("break_end")); assertFalse(result.allows("work_end"))
            assertTrue(result.allows("work_start"))
        }
    }

    @Test fun latePauseEventsCannotReopenAnAdministrativelyClosedShift() {
        val result = state(event("start", "work_start", 100), event("admin-end", "work_end", 200),
            event("late-resume", "break_end", 300), event("late-pause", "break_start", 400))
        assertEquals(WorkPresence.OFF, result.state); assertEquals("admin-end", result.event?.id)
    }

    @Test fun backdatedCorrectionsDoNotOverrideANewerRealArrival() {
        val result = state(event("new-arrival", "work_start", 300), event("historical-end", "work_end", 200))
        assertEquals(WorkPresence.WORKING, result.state); assertEquals("new-arrival", result.event?.id)
    }

    @Test fun effectiveTimestampAndDepartureTieTakePriorityOverArrivalOrder() {
        val events = listOf(event("end", "work_end", 200), event("start", "work_start", 100), event("restart", "work_start", 200))
        assertEquals(WorkPresence.OFF, AttendancePresence.reduce("a", events.reversed(), 1000).state)
        assertEquals(WorkPresence.OFF, AttendancePresence.reduce("a", events, 1000).state)
    }

    @Test fun unrelatedHistoryFutureAndUntimedEventsCannotChangeThisEmployee() {
        val history = (1..2500).map { event("other$it", "work_start", it.toLong(), "another") }
        val events = history + listOf(event("start", "work_start", 100), event("end", "work_end", 200),
            event("future", "work_start", 2000), PresenceEvent("pending", "a", "work_start", null))
        assertEquals(WorkPresence.OFF, AttendancePresence.reduce("a", events, 1000).state)
        assertEquals(2, AttendancePresence.reduce("a", events, 1000).eventCount)
    }

    @Test fun cachePendingAndFailedStatusNeverEnableActions() {
        val active = state(event("start", "work_start", 100))
        assertFalse(active.allows("work_end"))
        assertFalse(active.copy(confirmed = true, pending = true).allows("work_end"))
        assertFalse(active.copy(confirmed = true, error = "offline").allows("work_end"))
        assertTrue(active.copy(confirmed = true).allows("work_end"))
    }

    @Test fun anInFlightPinIntentMustMatchTheCurrentAttendanceEvent() {
        val before = state(event("start", "work_start", 100)).copy(confirmed = true)
        val after = state(event("start", "work_start", 100), event("admin-end", "work_end", 200)).copy(confirmed = true)
        assertTrue(before.matchesIntent("start")); assertFalse(after.matchesIntent("start"))
        assertTrue(after.matchesIntent("admin-end"))
    }
}
