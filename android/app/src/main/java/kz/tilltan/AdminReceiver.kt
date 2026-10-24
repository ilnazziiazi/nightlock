package kz.tilltan

import android.app.admin.DeviceAdminReceiver

/** Exists only so `dpm set-device-owner kz.tilltan/.AdminReceiver` has a target. */
class AdminReceiver : DeviceAdminReceiver()
