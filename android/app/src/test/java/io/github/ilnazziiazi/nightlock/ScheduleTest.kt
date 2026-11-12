package io.github.ilnazziiazi.nightlock

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

    private val night = Schedule.State(22 * 60, 7 * 60)
    private val noon = LocalDateTime.of(2026, 1, 1, 12, 0)
    private val morning = LocalDateTime.of(2026, 1, 2, 7, 0)

    @Test
    fun shorteningWaitsUntilTheNearestNightEnds() {
        val changed = night.edit(23 * 60, 6 * 60, noon)
        assertEquals(night.start, changed.start)
        assertEquals(night.end, changed.end)
        assertEquals(morning, changed.applyAt)
        assertTrue(changed.hasPending)
        assertEquals(changed, changed.resolve(morning.minusNanos(1)))
        assertEquals(Schedule.State(23 * 60, 6 * 60), changed.resolve(morning))
        assertEquals(Schedule.State(23 * 60, 6 * 60), changed.resolve(morning.plusDays(3)))
    }

    @Test
    fun extendingAppliesImmediately() {
        val changed = night.edit(21 * 60, 8 * 60, noon)
        assertEquals(21 * 60, changed.start)
        assertEquals(8 * 60, changed.end)
        assertFalse(changed.hasPending)
    }

    @Test
    fun mixedEditsExtendNowAndShortenLater() {
        val later = night.edit(23 * 60, 8 * 60, noon)
        assertEquals(22 * 60, later.start)
        assertEquals(8 * 60, later.end)
        assertEquals(morning.plusHours(1), later.applyAt)
        assertEquals(Schedule.State(23 * 60, 8 * 60), later.resolve(morning.plusHours(1)))

        val earlier = night.edit(21 * 60, 6 * 60, noon)
        assertEquals(21 * 60, earlier.start)
        assertEquals(7 * 60, earlier.end)
        assertEquals(morning, earlier.applyAt)
    }

    @Test
    fun separatePickerEditsPreserveBothPendingTimes() {
        val first = night.edit(23 * 60, night.nextEnd, noon)
        val second = first.edit(first.nextStart, 6 * 60, noon.plusHours(1))
        assertEquals(night.edit(23 * 60, 6 * 60, noon), second)
    }

    @Test
    fun revertingAnExtensionCannotShortenTheProtectedNight() {
        val extended = night.edit(21 * 60, 8 * 60, noon)
        val reverted = extended.edit(22 * 60, 7 * 60, noon.plusHours(1))
        assertEquals(21 * 60, reverted.start)
        assertEquals(8 * 60, reverted.end)
        assertEquals(morning.plusHours(1), reverted.applyAt)
        assertEquals(night, reverted.resolve(morning.plusHours(1)))
    }

    @Test
    fun pendingChangesCanBeReplacedOrCancelled() {
        val first = night.edit(23 * 60, 6 * 60, noon)
        val replacement = first.edit(23 * 60 + 30, 5 * 60, noon.plusHours(1))
        assertEquals(morning, replacement.applyAt)
        assertEquals(Schedule.State(23 * 60 + 30, 5 * 60), replacement.resolve(morning))
        val cancelled = first.edit(night.start, night.end, noon.plusHours(1))
        assertFalse(cancelled.hasPending)
        assertEquals(night, cancelled.resolve(morning))
    }

    @Test
    fun editingAtUnlockProtectsTheFollowingNight() {
        val pending = night.edit(23 * 60, 6 * 60, noon)
        val edited = pending.edit(23 * 60 + 30, 5 * 60, morning)
        assertEquals(23 * 60, edited.start)
        assertEquals(6 * 60, edited.end)
        assertEquals(morning.plusDays(1).minusHours(1), edited.applyAt)
        assertEquals(morning, night.edit(23 * 60, 6 * 60, morning.minusDays(1)).applyAt)
    }

    @Test
    fun editingDuringTheLockCannotReleaseItEarly() {
        for (now in listOf(noon.withHour(23), morning.minusMinutes(1))) {
            val edited = night.edit(23 * 60, 6 * 60, now)
            assertEquals(morning, edited.applyAt)
            assertTrue(Schedule.isActive(edited.start, edited.end, now.hour * 60 + now.minute))
        }
    }

    @Test
    fun disablingIsDeferredButEnablingIsImmediate() {
        val disabled = night.edit(7 * 60, 7 * 60, noon)
        assertEquals(night.start, disabled.start)
        assertEquals(night.end, disabled.end)
        assertEquals(Schedule.State(7 * 60, 7 * 60), disabled.resolve(morning))
        assertEquals(night, Schedule.State(0, 0).edit(night.start, night.end, noon))
    }

    @Test
    fun daytimeWindowsAndMidnightEditsUseCircularTime() {
        val daytime = Schedule.State(9 * 60, 17 * 60).edit(10 * 60, 16 * 60, noon.withHour(8))
        assertEquals(noon.withHour(17), daytime.applyAt)
        assertEquals(9 * 60, daytime.start)
        assertEquals(17 * 60, daytime.end)

        val afterMidnight = Schedule.State(60, 7 * 60).edit(23 * 60, 8 * 60, noon)
        assertEquals(23 * 60, afterMidnight.start)
        assertEquals(8 * 60, afterMidnight.end)
        assertFalse(afterMidnight.hasPending)
        assertEquals(morning.plusHours(1), afterMidnight.applyAt)
    }

    @Test
    fun largeExtensionsCannotMoveTheProtectedEndBackToToday() {
        val pending = night.edit(23 * 60, 7 * 60, noon)
        val extended = pending.edit(23 * 60, 21 * 60, noon)
        assertEquals(morning.withHour(21), extended.applyAt)
        assertEquals(22 * 60, extended.resolve(noon.withHour(21)).start)

        val immediate = night.edit(22 * 60, 21 * 60, noon)
        val thenShortened = immediate.edit(23 * 60, 21 * 60, noon.plusMinutes(1))
        assertEquals(extended, thenShortened)
    }

    @Test
    fun everyEditPreservesAllCurrentlyLockedMinutes() {
        for (original in listOf(night, Schedule.State(9 * 60, 17 * 60), Schedule.State(60, 7 * 60))) {
            for (start in 0 until 1440 step 180) {
                for (end in 0 until 1440 step 180) {
                    val edited = original.edit(start, end, noon)
                    for (minute in 0 until 1440) {
                        if (Schedule.isActive(original.start, original.end, minute)) {
                            assertTrue("$original -> $edited at $minute",
                                Schedule.isActive(edited.start, edited.end, minute))
                        }
                    }
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidTimesAreRejected() {
        night.edit(1440, 7 * 60, noon)
    }
}
