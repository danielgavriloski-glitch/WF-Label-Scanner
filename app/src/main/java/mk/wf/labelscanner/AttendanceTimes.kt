package mk.wf.labelscanner

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Actual event timestamps, independent of duration/accounting calculations. */
object AttendanceTimes {
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun format(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        clock.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun lines(times: List<Long>, zone: ZoneId = ZoneId.systemDefault()): String =
        times.distinct().sorted().joinToString("\n") { format(it, zone) }.ifEmpty { "—" }

    fun description(arrivals: List<Long>, departures: List<Long>): String =
        "Дошол: ${lines(arrivals).replace("\n", ", ")}\nСи отишол: ${lines(departures).replace("\n", ", ")}"
}

data class AdminAttendanceInput(val employeeId: String, val type: String, val occurredAt: Long, val note: String = "") {
    fun error(now: Long): String? = when {
        employeeId.isBlank() -> "Избери вработен."
        type !in setOf("work_start", "work_end") -> "Избери пријава или одјава."
        occurredAt <= 0L -> "Избери валиден датум и време."
        occurredAt > now -> "Пријава/одјава не може да биде во иднина."
        note.length > 300 -> "Забелешката може да има најмногу 300 знаци."
        else -> null
    }

    /** Retrying the same manual event never appends a second attendance record. */
    fun documentId(): String {
        val value = "$employeeId|$type|${occurredAt / 1000L}"
        return "admin_" + MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
