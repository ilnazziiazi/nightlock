package io.github.ilnazziiazi.nightlock

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast

class LockActivity : Activity() {
    var preview = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        setContentView(R.layout.lock)
        findViewById<Button>(R.id.close_preview).setOnClickListener { if (preview) finish() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        refresh()
    }

    /** Never pin outside the window: guards against the system restoring this screen in the morning. */
    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val owner = getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(packageName)
        val active = Schedule.isActiveNow(this)
        preview = intent.getBooleanExtra("preview", false) && !(owner && active)
        findViewById<TextView>(R.id.until).text =
            "${getString(R.string.until).lowercase()} ${Schedule.hhmm(Schedule.state(this).end)}"
        findViewById<Button>(R.id.close_preview).visibility = if (preview) View.VISIBLE else View.GONE
        renderApps()
        if (!preview) {
            if (owner && active) startLockTask() else release()
        }
    }

    private fun renderApps() {
        val selected = AllowedApps.selected(this)
        val packages = (if (preview) selected.keys else AllowedApps.active(selected, System.currentTimeMillis()))
            .sortedBy { AllowedApps.label(this, it) }
        val grid = findViewById<GridLayout>(R.id.app_grid)
        grid.removeAllViews()
        packages.forEachIndexed { index, pkg ->
            val button = Button(this, null, 0, R.style.AppButton).apply {
                text = AllowedApps.label(this@LockActivity, pkg)
                setOnClickListener { launch(pkg) }
                setOnLongClickListener {
                    AlertDialog.Builder(this@LockActivity)
                        .setMessage(getString(R.string.remove_app, text))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.remove) { _, _ ->
                            AllowedApps.save(this@LockActivity, pkg, false)
                            SyncReceiver.sync(this@LockActivity)
                            renderApps()
                        }.show()
                    true
                }
            }
            grid.addView(button, GridLayout.LayoutParams().apply {
                rowSpec = GridLayout.spec(index / 2)
                columnSpec = GridLayout.spec(index % 2, 1f)
                width = 0
                height = (80 * resources.displayMetrics.density).toInt()
                val margin = (4 * resources.displayMetrics.density).toInt()
                setMargins(margin, margin, margin, margin)
            })
        }
        findViewById<TextView>(R.id.apps_hint).setText(when {
            packages.isEmpty() -> R.string.apps_empty
            preview -> R.string.apps_preview_hint
            else -> R.string.apps_remove_hint
        })
    }

    private fun launch(pkg: String) {
        if (preview) {
            Toast.makeText(this, R.string.preview_only, Toast.LENGTH_SHORT).show()
            return
        }
        val dpm = getSystemService(DevicePolicyManager::class.java)
        if (pkg !in AllowedApps.active(AllowedApps.selected(this), System.currentTimeMillis()) || !dpm.isLockTaskPermitted(pkg)) {
            renderApps()
            return
        }
        try {
            val launch = packageManager.getLaunchIntentForPackage(pkg) ?: throw ActivityNotFoundException()
            startActivity(launch, ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle())
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    /** Legacy back path (predictive back is disabled in the manifest): swallow it. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (preview) finish() }

    fun release() {
        if (!preview) stopLockTask()
        finish()
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        var current: LockActivity? = null
    }
}
