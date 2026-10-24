package io.github.ilnazziiazi.nightlock

import android.content.Context
import java.time.LocalDateTime
import java.time.LocalTime

/** Daily lock window [start, end) in minutes of day. start == end means disabled. */
object Schedule {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nightlock", Context.MODE_PRIVATE)
    fun start(ctx: Context) = prefs(ctx).getInt("start", 22 * 60)
    fun end(ctx: Context) = prefs(ctx).getInt("end", 6 * 60)
    fun save(ctx: Context, start: Int, end: Int) =
        prefs(ctx).edit().putInt("start", start).putInt("end", end).apply()

    fun isActiveNow(ctx: Context): Boolean =
        LocalTime.now().let { isActive(start(ctx), end(ctx), it.hour * 60 + it.minute) }

    fun isActive(start: Int, end: Int, nowMin: Int): Boolean = when {
        start == end -> false
        start < end -> nowMin in start until end
        else -> nowMin >= start || nowMin < end
    }

    /** Next moment the active state flips (end of window if active, else next start). */
    fun nextBoundary(start: Int, end: Int, now: LocalDateTime): LocalDateTime {
        val target = if (isActive(start, end, now.hour * 60 + now.minute)) end else start
        val t = now.toLocalDate().atTime(LocalTime.of(target / 60, target % 60))
        return if (t.isAfter(now)) t else t.plusDays(1)
    }

    fun hhmm(min: Int) = "%02d:%02d".format(min / 60, min % 60)
}
