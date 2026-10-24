package kz.tilltan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScheduleTest {
    private val s = 22 * 60
    private val e = 6 * 60

    @Test
    fun windowCrossingMidnight() {
        assertTrue(Schedule.isActive(s, e, 22 * 60))
        assertTrue(Schedule.isActive(s, e, 23 * 60 + 59))
        assertTrue(Schedule.isActive(s, e, 0))
        assertTrue(Schedule.isActive(s, e, 5 * 60 + 59))
        assertFalse(Schedule.isActive(s, e, 6 * 60))
        assertFalse(Schedule.isActive(s, e, 12 * 60))
        assertFalse(Schedule.isActive(s, e, 21 * 60 + 59))
    }

    @Test
    fun windowWithinDayAndDisabled() {
        assertTrue(Schedule.isActive(9 * 60, 17 * 60, 12 * 60))
        assertFalse(Schedule.isActive(9 * 60, 17 * 60, 20 * 60))
        assertFalse(Schedule.isActive(9 * 60, 9 * 60, 9 * 60))
    }

    @Test
    fun nextBoundary() {
        assertEquals(LocalDateTime.of(2026, 1, 1, 22, 0), Schedule.nextBoundary(s, e, LocalDateTime.of(2026, 1, 1, 12, 0)))
        assertEquals(LocalDateTime.of(2026, 1, 2, 6, 0), Schedule.nextBoundary(s, e, LocalDateTime.of(2026, 1, 1, 23, 30)))
        assertEquals(LocalDateTime.of(2026, 1, 2, 6, 0), Schedule.nextBoundary(s, e, LocalDateTime.of(2026, 1, 2, 5, 59, 30)))
        assertEquals(LocalDateTime.of(2026, 1, 2, 6, 0), Schedule.nextBoundary(s, e, LocalDateTime.of(2026, 1, 1, 22, 0)))
        assertEquals(LocalDateTime.of(2026, 1, 2, 22, 0), Schedule.nextBoundary(s, e, LocalDateTime.of(2026, 1, 2, 6, 0)))
    }
}
