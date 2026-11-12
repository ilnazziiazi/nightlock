package io.github.ilnazziiazi.nightlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuspendedAppsTest {
    private var saved = emptySet<String>()
    private val suspended = mutableSetOf<String>()

    private fun sync(candidates: Set<String>, allowed: Set<String> = emptySet(), active: Boolean = true) {
        SuspendedApps.reconcile(saved, candidates, allowed, active,
            save = { saved = it },
            setSuspended = { packages, suspend ->
                assertTrue(saved.containsAll(packages))
                if (suspend) suspended.addAll(packages) else suspended.removeAll(packages)
                emptySet()
            })
    }

    @Test
    fun nightProtectsAllowedAppsAndClockAndMorningRestoresOnlyOurApps() {
        suspended.add("managed.elsewhere")
        sync(setOf("music", "calculator", "clock", "keyboard"), setOf("calculator", "clock", "keyboard"))
        assertEquals(setOf("music", "managed.elsewhere"), suspended)
        sync(emptySet(), active = false)
        assertEquals(setOf("managed.elsewhere"), suspended)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun activationRestoresAppAndRemovalSuspendsItAgain() {
        sync(setOf("music", "calculator"), setOf("calculator"))
        sync(setOf("calculator"), setOf("music"))
        assertEquals(setOf("calculator"), suspended)
        sync(setOf("music", "calculator"), setOf("music"))
        assertEquals(setOf("calculator"), suspended)
    }

    @Test
    fun processDeathAfterSystemChangeKeepsDurableUndoList() {
        try {
            SuspendedApps.reconcile(emptySet(), setOf("music"), emptySet(), true,
                save = { saved = it },
                setSuspended = { packages, _ ->
                    suspended.addAll(packages)
                    throw IllegalStateException("process died before completion")
                })
        } catch (_: IllegalStateException) { }
        assertEquals(setOf("music"), saved)
        sync(emptySet(), active = false)
        assertTrue(suspended.isEmpty())
        assertTrue(saved.isEmpty())
    }

    @Test
    fun failedRestoreIsRetriedAfterRestart() {
        sync(setOf("music", "video"))
        SuspendedApps.reconcile(saved, emptySet(), emptySet(), false,
            save = { saved = it },
            setSuspended = { _, suspend ->
                assertTrue(!suspend)
                suspended.remove("video")
                setOf("music")
            })
        assertEquals(setOf("music"), saved)
        sync(emptySet(), active = false)
        assertTrue(suspended.isEmpty())
    }

    @Test
    fun protectedPackageRejectionDoesNotLoseUndoList() {
        SuspendedApps.reconcile(emptySet(), setOf("music", "dialer"), emptySet(), true,
            save = { saved = it },
            setSuspended = { packages, _ ->
                suspended.addAll(packages - "dialer")
                setOf("dialer")
            })
        assertEquals(setOf("music"), suspended)
        sync(emptySet(), active = false)
        assertTrue(suspended.isEmpty())
        assertTrue(saved.isEmpty())
    }

    @Test
    fun daytimeWithoutTrackedAppsDoesNotChangePolicyOrWritePrefs() {
        SuspendedApps.reconcile(emptySet(), setOf("music"), emptySet(), false,
            save = { throw AssertionError("Unexpected write") },
            setSuspended = { _, _ -> throw AssertionError("Unexpected policy change") })
    }

    @Test
    fun failedJournalWriteNeverChangesSystemPolicy() {
        var called = false
        try {
            SuspendedApps.reconcile(emptySet(), setOf("music"), emptySet(), true,
                save = { throw IllegalStateException("disk full") },
                setSuspended = { _, _ -> called = true; emptySet() })
        } catch (_: IllegalStateException) { }
        assertTrue(!called)
    }
}
