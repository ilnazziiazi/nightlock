package io.github.ilnazziiazi.nightlock

import android.content.Context
import java.time.LocalDateTime
import java.time.LocalTime

/** Daily lock window [start, end) in minutes of day. start == end means disabled. */
object Schedule {
    data class State(
        val start: Int,
        val end: Int,
        val nextStart: Int = start,
        val nextEnd: Int = end,
        val applyAt: LocalDateTime? = null,
    ) {
        val hasPending get() = start != nextStart || end != nextEnd

        fun resolve(now: LocalDateTime): State =
            if (applyAt != null && !now.isBefore(applyAt)) State(nextStart, nextEnd) else this

        fun edit(newStart: Int, newEnd: Int, now: LocalDateTime): State {
            require(newStart in 0 until 1440 && newEnd in 0 until 1440)
            val current = resolve(now)
            if (current.start == current.end) return State(newStart, newEnd)

            var start = current.start
            var end = current.end
            if (newStart != newEnd) {
                if (covers(newStart, end, start, end)) start = newStart
                if (covers(start, newEnd, start, end)) end = newEnd
            }
            val boundary = nextBoundary(current.start, current.end, now)
            val oldEnd = current.applyAt ?: if (isActive(current.start, current.end, now.hour * 60 + now.minute)) {
                boundary
            } else {
                nextBoundary(current.start, current.end, boundary)
            }
            // Keep the protected night even when an extension also makes the previous night active.
            val applyAt = oldEnd.plusMinutes(((end - current.end + 1440) % 1440).toLong())
            return State(start, end, newStart, newEnd, applyAt)
        }
    }

    private fun covers(start: Int, end: Int, oldStart: Int, oldEnd: Int): Boolean {
        val length = (end - start + 1440) % 1440
        val offset = (oldStart - start + 1440) % 1440
        return length > 0 && offset + (oldEnd - oldStart + 1440) % 1440 <= length
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nightlock", Context.MODE_PRIVATE)

    fun state(ctx: Context, now: LocalDateTime = LocalDateTime.now()): State {
        val prefs = prefs(ctx)
        val start = prefs.getInt("start", 22 * 60)
        val end = prefs.getInt("end", 6 * 60)
        val saved = State(start, end, prefs.getInt("next_start", start), prefs.getInt("next_end", end),
            prefs.getString("apply_at", null)?.let { LocalDateTime.parse(it) })
        return saved.resolve(now).also { if (it != saved) write(ctx, it) }
    }

    fun save(ctx: Context, start: Int, end: Int) {
        val now = LocalDateTime.now()
        write(ctx, state(ctx, now).edit(start, end, now))
    }

    private fun write(ctx: Context, state: State) {
        prefs(ctx).edit().putInt("start", state.start).putInt("end", state.end)
            .putInt("next_start", state.nextStart).putInt("next_end", state.nextEnd)
            .putString("apply_at", state.applyAt?.toString()).apply()
    }

    fun isActiveNow(ctx: Context): Boolean {
        val now = LocalDateTime.now()
        val state = state(ctx, now)
        return isActive(state.start, state.end, now.hour * 60 + now.minute)
    }

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
