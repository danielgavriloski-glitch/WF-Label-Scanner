package com.mbidesign.terminal

import java.util.Calendar
import java.util.TimeZone

data class Worker(val id: String, val uid: String, val name: String, val active: Boolean = true)
data class AttendanceEvent(
    val id: String, val employeeId: String, val uid: String, val name: String,
    val type: String, val time: Long, val method: String = "face", val status: String = "pending",
    val deviceUid: String = "", val message: String = "", val clockTrusted: Boolean = false
)
enum class ShiftState { OUT, WORKING, PAUSED }

object Attendance {
    val zone: TimeZone = TimeZone.getTimeZone("Europe/Skopje")
    val types = setOf("work_start", "work_end", "break_start", "break_end")
    fun dayStart(time: Long): Long = Calendar.getInstance(zone).apply {
        timeInMillis = time; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    fun next(state: ShiftState, type: String): ShiftState? = when {
        state == ShiftState.OUT && type == "work_start" -> ShiftState.WORKING
        state == ShiftState.WORKING && type == "break_start" -> ShiftState.PAUSED
        state == ShiftState.PAUSED && type == "break_end" -> ShiftState.WORKING
        state != ShiftState.OUT && type == "work_end" -> ShiftState.OUT
        else -> null
    }
    fun state(events: List<AttendanceEvent>): ShiftState {
        var result = ShiftState.OUT
        for (e in events.sortedWith(compareBy({ it.time }, { it.id }))) {
            result = next(result, e.type) ?: result
        }
        return result
    }
    // MBI counts breaks inside attendance hours and reports pause separately.
    fun durations(events: List<AttendanceEvent>, now: Long): Pair<Long, Long> {
        var start: Long? = null; var pause: Long? = null
        var worked = 0L; var breaks = 0L
        for (e in events.sortedBy { it.time }) when (e.type) {
            "work_start" -> if (start == null) start = e.time
            "break_start" -> if (start != null && pause == null) pause = e.time
            "break_end" -> { pause?.let { breaks += (e.time - it).coerceAtLeast(0) }; pause = null }
            "work_end" -> {
                start?.let { worked += (e.time - it).coerceAtLeast(0) }; start = null
                pause?.let { breaks += (e.time - it).coerceAtLeast(0) }; pause = null
            }
        }
        start?.let { worked += (now - it).coerceAtLeast(0) }
        pause?.let { breaks += (now - it).coerceAtLeast(0) }
        return worked to breaks
    }
    fun label(type: String): String = when (type) {
        "work_start" -> "Дојдов на работа"; "work_end" -> "Заминувам"
        "break_start" -> "Почнувам пауза"; "break_end" -> "Завршив пауза"; else -> type
    }
    fun duration(ms: Long): String { val min = ms / 60000; return "%02d:%02d".format(min / 60, min % 60) }
    fun conflict(existing: List<AttendanceEvent>, candidate: AttendanceEvent): String? {
        val all = (existing.filter { it.id != candidate.id } + candidate).sortedWith(compareBy({ it.time }, { it.id }))
        var state = ShiftState.OUT
        for (e in all) {
            val updated = next(state, e.type)
            if (updated == null) return "Несогласување во редоследот на пријавите. Провери таблет и телефон."
            state = updated
        }
        return null
    }
}
