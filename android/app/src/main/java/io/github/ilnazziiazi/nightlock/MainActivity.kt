package io.github.ilnazziiazi.nightlock

import android.app.Activity
import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        findViewById<Button>(R.id.start).setOnClickListener {
            pick(Schedule.start(this)) { Schedule.save(this, it, Schedule.end(this)) }
        }
        findViewById<Button>(R.id.end).setOnClickListener {
            pick(Schedule.end(this)) { Schedule.save(this, Schedule.start(this), it) }
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
        findViewById<Button>(R.id.start).text = "${getString(R.string.from)} ${Schedule.hhmm(Schedule.start(this))}"
        findViewById<Button>(R.id.end).text = "${getString(R.string.until)} ${Schedule.hhmm(Schedule.end(this))}"
        val owner = getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(packageName)
        findViewById<TextView>(R.id.status).setText(if (owner) R.string.owner_yes else R.string.owner_no)
    }
}
