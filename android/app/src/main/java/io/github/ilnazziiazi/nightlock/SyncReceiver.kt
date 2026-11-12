package io.github.ilnazziiazi.nightlock

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.provider.AlarmClock
import android.content.pm.PackageManager
import java.time.LocalDateTime
import java.time.ZoneId

/** Alarm / boot / clock-change entry point. Applies schedules and app access with one alarm. */
class SyncReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = sync(ctx)

    companion object {
        fun sync(ctx: Context) {
            val now = LocalDateTime.now()
            val schedule = Schedule.state(ctx, now)
            val start = schedule.start
            val end = schedule.end
            val nowMillis = System.currentTimeMillis()
            val apps = AllowedApps.selected(ctx)

            val am = ctx.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                ctx, 0, Intent(ctx, SyncReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val at = listOfNotNull(
                if (start == end) null else Schedule.nextBoundary(start, end, now)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                AllowedApps.nextChange(apps, nowMillis),
            ).minOrNull()
            if (at == null) am.cancel(pi) else {
                // setAlarmClock: the only alarm type vendor ROMs don't widen into an hour window.
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, pi), pi)
            }

            val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
            val owner = dpm.isDeviceOwnerApp(ctx.packageName)
            if (owner) arm(ctx, dpm, AllowedApps.active(apps, nowMillis))
            if (owner && Schedule.isActive(start, end, now.hour * 60 + now.minute)) {
                ctx.startActivity(Intent(ctx, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                LockActivity.current?.takeUnless { it.preview }?.release()
            }
        }

        /** Idempotent device-owner policy. Cheap, so re-applied on every sync. */
        private fun arm(ctx: Context, dpm: DevicePolicyManager, apps: Set<String>) {
            val admin = ComponentName(ctx, AdminReceiver::class.java)
            // Clock app: so a morning alarm's full-screen UI can show over the kiosk and be dismissed.
            val clock = ctx.packageManager.resolveActivity(
                Intent(AlarmClock.ACTION_SHOW_ALARMS), PackageManager.MATCH_DEFAULT_ONLY
            )?.activityInfo?.packageName
            dpm.setLockTaskPackages(admin, (listOfNotNull(ctx.packageName, clock) + apps).distinct().toTypedArray())
            dpm.setLockTaskFeatures(
                admin,
                DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD or DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
                    DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK
            )
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME)
        }
    }
}
