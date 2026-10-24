package io.github.ilnazziiazi.nightlock

import android.app.admin.DeviceAdminReceiver

/** Exists only so `dpm set-device-owner io.github.ilnazziiazi.nightlock/.AdminReceiver` has a target. */
class AdminReceiver : DeviceAdminReceiver()
