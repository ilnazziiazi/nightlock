package io.github.ilnazziiazi.nightlock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.view.inputmethod.InputMethodManager

object SuspendedApps {
    fun sync(ctx: Context, dpm: DevicePolicyManager, admin: ComponentName, allowed: Set<String>, active: Boolean) {
        val prefs = ctx.getSharedPreferences("suspended_apps", Context.MODE_PRIVATE)
        val previous = prefs.getStringSet("packages", emptySet())!!.toSet()
        try {
            val candidates = if (active) AllowedApps.available(ctx).toSet() else emptySet()
            // Some keyboards have launcher icons; suspending them would break typing in allowed apps.
            val keyboards = ctx.getSystemService(InputMethodManager::class.java).enabledInputMethodList
                .map { it.packageName }
            reconcile(previous, candidates, allowed + keyboards, active,
                save = { packages ->
                    // Persist before changing Android policy: a killed process must not lose the undo list.
                    check(prefs.edit().putStringSet("packages", packages).commit())
                },
                setSuspended = { packages, suspended ->
                    dpm.setPackagesSuspended(admin, packages.toTypedArray(), suspended).toSet().also {
                        if (it.isNotEmpty()) Log.w("night lock", "Could not set suspended=$suspended for $it")
                    }
                })
        } catch (e: RuntimeException) {
            // Keep the undo list and let the kiosk/alarm continue even if suspension fails.
            Log.e("night lock", "Could not update suspended apps", e)
        }
    }

    internal fun reconcile(
        previous: Set<String>, candidates: Set<String>, allowed: Set<String>, active: Boolean,
        save: (Set<String>) -> Unit,
        setSuspended: (Set<String>, Boolean) -> Set<String>,
    ) {
        val target = if (active) (previous + candidates) - allowed else emptySet()
        val restore = previous - target
        if (previous.isEmpty() && target.isEmpty()) return
        save(previous + target)
        val failedRestore = if (restore.isEmpty()) emptySet() else setSuspended(restore, false)
        if (target.isNotEmpty()) setSuspended(target, true)
        // Keep failed restorations for the next sync; restore only packages we tracked.
        save(target + failedRestore)
    }
}
