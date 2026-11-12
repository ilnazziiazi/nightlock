package io.github.ilnazziiazi.nightlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowedAppsTest {
    private val now = 1_800_000_000_000L

    @Test
    fun addingWaitsExactly24Hours() {
        val selected = AllowedApps.edit(emptyMap(), "example.app", true, now)
        assertEquals(now + AllowedApps.DELAY_MS, selected["example.app"])
        assertTrue(AllowedApps.active(selected, now + AllowedApps.DELAY_MS - 1).isEmpty())
        assertEquals(setOf("example.app"), AllowedApps.active(selected, now + AllowedApps.DELAY_MS))
    }

    @Test
    fun removalIsImmediateForActiveAndPendingApps() {
        val selected = mapOf("active" to now - 1, "pending" to now + 100)
        assertFalse("active" in AllowedApps.edit(selected, "active", false, now))
        assertFalse("pending" in AllowedApps.edit(selected, "pending", false, now))
        assertEquals(selected, AllowedApps.edit(selected, "absent", false, now))
    }

    @Test
    fun readdingStartsANewWaitWithoutChangingOtherApps() {
        val selected = mapOf("active" to now - 1, "other" to now + 100)
        val removed = AllowedApps.edit(selected, "active", false, now)
        val readded = AllowedApps.edit(removed, "active", true, now + 1000)
        assertEquals(now + 1000 + AllowedApps.DELAY_MS, readded["active"])
        assertEquals(selected["other"], readded["other"])
    }

    @Test
    fun selectingAnExistingAppDoesNotResetItsWait() {
        val selected = mapOf("active" to now - 1, "pending" to now + 100)
        assertEquals(selected, AllowedApps.edit(selected, "active", true, now))
        assertEquals(selected, AllowedApps.edit(selected, "pending", true, now))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sixPlacesIncludePendingApps() {
        val selected = (1..6).associate { "app$it" to now + it }
        AllowedApps.edit(selected, "seventh", true, now)
    }

    @Test
    fun removingFreesAPlaceButReplacementStillWaits() {
        val selected = (1..6).associate { "app$it" to now - 1 }
        val removed = AllowedApps.edit(selected, "app1", false, now)
        val replaced = AllowedApps.edit(removed, "replacement", true, now)
        assertEquals(6, replaced.size)
        assertEquals(5, AllowedApps.active(replaced, now).size)
        assertEquals(now + AllowedApps.DELAY_MS, replaced["replacement"])
    }

    @Test
    fun alarmTargetsTheNextPendingAdditionOnly() {
        val selected = mapOf("active" to now - 100, "first" to now + 100, "second" to now + 200)
        assertEquals(now + 100, AllowedApps.nextChange(selected, now))
        assertEquals(now + 200, AllowedApps.nextChange(selected, now + 100))
        assertNull(AllowedApps.nextChange(selected, now + 200))
        assertNull(AllowedApps.nextChange(emptyMap(), now))
    }
}
