package com.mbidesign.terminal

import org.junit.Assert.*
import org.junit.Test

class AttendanceTest {
    private fun event(type: String, time: Long) = AttendanceEvent("$type-$time", "e", "u", "Employee", type, time)
    @Test fun rejectsDuplicateArrivalAndInvalidPause() {
        assertNull(Attendance.next(ShiftState.WORKING,"work_start"))
        assertNull(Attendance.next(ShiftState.OUT,"break_start"))
        assertNull(Attendance.next(ShiftState.OUT,"work_end"))
        assertNull(Attendance.next(ShiftState.WORKING,"break_end"))
    }
    @Test fun pauseIsIncludedAndClosedByDeparture() {
        val start = 100000L
        val events = listOf(event("work_start",start),event("break_start",start+7*3600000),event("work_end",start+8*3600000))
        val duration = Attendance.durations(events,start+9*3600000)
        assertEquals(8*3600000L,duration.first)
        assertEquals(3600000L,duration.second)
        assertEquals(ShiftState.OUT,Attendance.state(events))
    }
    @Test fun rejectsConflictBetweenPhoneAndTerminal() {
        assertNotNull(Attendance.conflict(listOf(event("work_start",1000)),event("work_start",5000)))
        assertNull(Attendance.conflict(listOf(event("work_start",1000)),event("work_end",5000)))
    }
    @Test fun completeOfflineShiftCanBeReplayedInOrder() {
        val events = listOf(event("work_start",1),event("break_start",2),event("break_end",3),event("work_end",4))
        var cloud = emptyList<AttendanceEvent>()
        events.forEach { e -> assertNull(Attendance.conflict(cloud + events.filter { it.time > e.time },e)); cloud = cloud + e }
        assertEquals(ShiftState.OUT,Attendance.state(cloud))
    }
}
