package io.github.ilnazziiazi.nightlock

import android.app.Activity
import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
    }

    /** sync() here is what makes settings unreachable at night: it immediately relaunches LockActivity. */
    override fun onResume() {
        super.onResume()
        render()
        SyncReceiver.sync(this)
    }

    private fun pick(cur: Int, onSet: (Int) -> Unit) =
        TimePickerDialog(this, { _, h, m -> onSet(h * 60 + m); render(); SyncReceiver.sync(this) }, cur / 60, cur % 60, true).show()

    private fun render() {
        val schedule = Schedule.state(this)
        findViewById<Button>(R.id.start).text = "${getString(R.string.from)} ${Schedule.hhmm(schedule.nextStart)}"
        findViewById<Button>(R.id.end).text = "${getString(R.string.until)} ${Schedule.hhmm(schedule.nextEnd)}"
        findViewById<TextView>(R.id.schedule).text =
            getString(R.string.schedule_current, Schedule.hhmm(schedule.start), Schedule.hhmm(schedule.end)) + "\n" +
                if (schedule.hasPending) {
                    getString(R.string.schedule_pending,
                        schedule.applyAt!!.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)))
                } else getString(R.string.schedule_rule)
        val owner = getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(packageName)
        findViewById<TextView>(R.id.status).setText(if (owner) R.string.owner_yes else R.string.owner_no)
    }
}
