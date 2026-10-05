package mk.wf.labelscanner

import org.junit.Test
import org.junit.Assert.assertEquals

class RegularHoursTest {
    private val h=3600000L
    @Test fun dailyCapRemovesOvertime() {
        assertEquals(mapOf("20261005" to 8*h),RegularHours.allocateWeek(mapOf("20261005" to 12*h)))
    }
    @Test fun weekendDoesNotExceedWeeklyCap() {
        val days=(5..11).associate { "202610%02d".format(it) to 10*h }
        val result=RegularHours.allocateWeek(days)
        assertEquals(40*h,result.values.sum())
        assertEquals(0L,result["20261010"])
        assertEquals(0L,result["20261011"])
    }
    @Test fun missingWeekdayHoursCanBeFilledOnWeekend() {
        val result=RegularHours.allocateWeek(mapOf("20261005" to 4*h,"20261006" to 8*h,"20261007" to 8*h,"20261008" to 8*h,"20261009" to 8*h,"20261010" to 8*h))
        assertEquals(4*h,result["20261010"])
        assertEquals(40*h,result.values.sum())
    }
    @Test fun filteredWeekendStillUsesCompleteWeekAllocation() {
        val result=RegularHours.allocateWeek((5..10).associate { "202610%02d".format(it) to 8*h })
        assertEquals(0L,result.filterKeys { it=="20261010" }.values.sum())
    }
}
