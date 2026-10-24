package kz.tilltan

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.telecom.TelecomManager
import java.time.LocalDateTime
import java.time.ZoneId

/** Alarm / boot / clock-change entry point. All state lives in Schedule; this just applies it. */
class SyncReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = sync(ctx)

    companion object {
        fun sync(ctx: Context) {
            val start = Schedule.start(ctx)
            val end = Schedule.end(ctx)
            val now = LocalDateTime.now()

            val am = ctx.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                ctx, 0, Intent(ctx, SyncReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            if (start == end) am.cancel(pi) else {
                val at = Schedule.nextBoundary(start, end, now).atZone(ZoneId.systemDefault())
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochSecond() * 1000, pi)
            }

            val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
            val owner = dpm.isDeviceOwnerApp(ctx.packageName)
            if (owner) arm(ctx, dpm)
            if (owner && Schedule.isActive(start, end, now.hour * 60 + now.minute)) {
                ctx.startActivity(Intent(ctx, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                LockActivity.current?.release()
            }
        }

        /** Idempotent device-owner policy. Cheap, so re-applied on every sync. */
        private fun arm(ctx: Context, dpm: DevicePolicyManager) {
            val admin = ComponentName(ctx, AdminReceiver::class.java)
            val dialer = ctx.getSystemService(TelecomManager::class.java).defaultDialerPackage
            dpm.setLockTaskPackages(admin, listOfNotNull(ctx.packageName, dialer).toTypedArray())
            dpm.setLockTaskFeatures(
                admin,
                DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD or DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
            )
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME)
        }
    }
}
