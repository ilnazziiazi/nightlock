package io.github.ilnazziiazi.nightlock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

object AllowedApps {
    const val LIMIT = 6
    const val DELAY_MS = 24 * 60 * 60 * 1000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("allowed_apps", Context.MODE_PRIVATE)

    fun selected(ctx: Context): Map<String, Long> = prefs(ctx).all.mapValues { it.value as Long }

    fun active(selected: Map<String, Long>, now: Long): Set<String> =
        selected.filterValues { it <= now }.keys

    fun nextChange(selected: Map<String, Long>, now: Long): Long? =
        selected.values.filter { it > now }.minOrNull()

    fun edit(selected: Map<String, Long>, pkg: String, add: Boolean, now: Long): Map<String, Long> {
        require(pkg.isNotBlank())
        if (!add) return selected - pkg
        if (pkg in selected) return selected
        require(selected.size < LIMIT) { "At most $LIMIT apps" }
        return selected + (pkg to now + DELAY_MS)
    }

    fun save(ctx: Context, pkg: String, add: Boolean) {
        if (add) require(pkg in available(ctx))
        val updated = edit(selected(ctx), pkg, add, System.currentTimeMillis())
        prefs(ctx).edit().clear().apply {
            updated.forEach { (pkg, at) -> putLong(pkg, at) }
        }.apply()
    }

    fun available(ctx: Context): List<String> {
        val pm = ctx.packageManager
        val home = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
        val settings = pm.queryIntentActivities(Intent(Settings.ACTION_SETTINGS), 0)
            .map { it.activityInfo.packageName }
        // Launchers and device settings would let the allowlist undermine the kiosk.
        val excluded = (home + settings + ctx.packageName).toSet()
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }.distinct().filter { it !in excluded }
            .sortedBy { label(ctx, it) }
    }

    fun label(ctx: Context, pkg: String): String = try {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString().lowercase()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg.lowercase()
    }
}
