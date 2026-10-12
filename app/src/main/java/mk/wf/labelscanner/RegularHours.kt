package mk.wf.labelscanner

object RegularHours {
    fun allocateWeek(days: Map<String, Long>): Map<String, Long> {
        var remaining = 40L * 3600000L
        return days.toSortedMap().mapValues { (_, worked) ->
            minOf(worked.coerceAtLeast(0), 8L * 3600000L, remaining).also { remaining -= it }
        }
    }
}
