package mk.wf.labelscanner

enum class WorkPresence { UNKNOWN, OFF, WORKING, BREAK }

data class PresenceEvent(val id: String, val employeeId: String, val type: String, val at: Long?,
    val source: String = "", val enteredBy: String = "")

data class WorkPresenceSnapshot(val state: WorkPresence = WorkPresence.UNKNOWN, val event: PresenceEvent? = null,
    val confirmed: Boolean = false, val pending: Boolean = false, val error: String? = null, val eventCount: Int = 0) {
    fun allows(action: String): Boolean = confirmed && !pending && error == null && when (state) {
        WorkPresence.OFF -> action == "work_start"
        WorkPresence.WORKING -> action in setOf("break_start", "work_end")
        WorkPresence.BREAK -> action in setOf("break_end", "work_end")
        WorkPresence.UNKNOWN -> false
    }

    fun matchesIntent(eventId: String?): Boolean = event?.id == eventId

    val title: String get() = when (state) {
        WorkPresence.OFF -> "ОДЈАВЕН ОД РАБОТА"
        WorkPresence.WORKING -> "НА РАБОТА"
        WorkPresence.BREAK -> "НА ПАУЗА"
        WorkPresence.UNKNOWN -> "СЕ ПРОВЕРУВА СТАТУСОТ"
    }
}

object AttendancePresence {
    val types = setOf("work_start", "work_end", "break_start", "break_end")
    private fun priority(type: String) = when (type) {
        "work_start" -> 0
        "break_start" -> 1
        "break_end" -> 2
        else -> 3 // A departure wins an equal-timestamp tie.
    }

    /** Replay effective attendance times, not the time an administrator entered a correction. */
    fun reduce(employeeId: String, events: List<PresenceEvent>): WorkPresenceSnapshot {
        var state = WorkPresence.OFF
        var last: PresenceEvent? = null
        // Server-confirmed timestamps remain authoritative when a phone clock is behind.
        val relevant = events.filter { it.employeeId == employeeId && it.type in types && it.at != null }
            .sortedWith(compareBy({ it.at }, { priority(it.type) }, { it.id }))
        for (event in relevant) {
            when (event.type) {
                "work_start" -> if (state == WorkPresence.OFF) { state = WorkPresence.WORKING; last = event }
                "work_end" -> { state = WorkPresence.OFF; last = event }
                "break_start" -> if (state == WorkPresence.WORKING) { state = WorkPresence.BREAK; last = event }
                "break_end" -> if (state == WorkPresence.BREAK) { state = WorkPresence.WORKING; last = event }
            }
        }
        return WorkPresenceSnapshot(state, last, eventCount = relevant.size)
    }
}
