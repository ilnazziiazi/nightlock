package io.github.ilnazziiazi.nightlock

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        findViewById<Button>(R.id.start).setOnClickListener {
            pick(Schedule.state(this).nextStart) { Schedule.save(this, it, Schedule.state(this).nextEnd) }
        }
        findViewById<Button>(R.id.end).setOnClickListener {
            pick(Schedule.state(this).nextEnd) { Schedule.save(this, Schedule.state(this).nextStart, it) }
        }
        findViewById<Button>(R.id.apps).setOnClickListener { pickApps() }
        findViewById<Button>(R.id.preview).setOnClickListener {
            startActivity(Intent(this, LockActivity::class.java).putExtra("preview", true))
        }
    }

    /** sync() here is what makes settings unreachable at night: it immediately relaunches LockActivity. */
    override fun onResume() {
        super.onResume()
        render()
        SyncReceiver.sync(this)
    }

    private fun pick(cur: Int, onSet: (Int) -> Unit) =
        TimePickerDialog(this, { _, h, m -> onSet(h * 60 + m); render(); SyncReceiver.sync(this) }, cur / 60, cur % 60, true).show()

    private fun pickApps() {
        val selected = AllowedApps.selected(this)
        val packages = (selected.keys + AllowedApps.available(this)).distinct()
        val labels = packages.map { AllowedApps.label(this, it) }.toTypedArray()
        AlertDialog.Builder(this).setTitle(R.string.apps)
            .setMultiChoiceItems(labels, packages.map { it in selected }.toBooleanArray()) { dialog, index, checked ->
                val pkg = packages[index]
                if (checked && AllowedApps.selected(this).size >= AllowedApps.LIMIT) {
                    (dialog as AlertDialog).listView.setItemChecked(index, false)
                    Toast.makeText(this, R.string.apps_limit, Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        AllowedApps.save(this, pkg, checked)
                    } catch (_: IllegalArgumentException) {
                        (dialog as AlertDialog).listView.setItemChecked(index, false)
                        Toast.makeText(this, R.string.app_unavailable, Toast.LENGTH_SHORT).show()
                    }
                    render()
                    SyncReceiver.sync(this)
                }
            }.setPositiveButton(android.R.string.ok, null).show()
    }

    private fun render() {
        val schedule = Schedule.state(this)
        findViewById<Button>(R.id.start).text = "${getString(R.string.from)} ${Schedule.hhmm(schedule.nextStart)}"
        findViewById<Button>(R.id.end).text = "${getString(R.string.until)} ${Schedule.hhmm(schedule.nextEnd)}"
        findViewById<TextView>(R.id.schedule).apply {
            visibility = if (schedule.hasPending) View.VISIBLE else View.GONE
            text = getString(R.string.schedule_current, Schedule.hhmm(schedule.start), Schedule.hhmm(schedule.end))
        }
        val apps = AllowedApps.selected(this).keys.map { AllowedApps.label(this, it) }.sorted()
        findViewById<Button>(R.id.apps).text = getString(R.string.apps_count, apps.size, AllowedApps.LIMIT)
        findViewById<TextView>(R.id.apps_summary).text =
            (apps + getString(R.string.apps_rule)).joinToString("\n")
        val owner = getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(packageName)
        findViewById<TextView>(R.id.status).setText(if (owner) R.string.owner_yes else R.string.owner_no)
    }
}
