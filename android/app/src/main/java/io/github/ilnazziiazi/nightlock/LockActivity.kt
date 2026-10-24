package io.github.ilnazziiazi.nightlock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class LockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        setContentView(R.layout.lock)
        findViewById<Button>(R.id.call).setOnClickListener { startActivity(Intent(Intent.ACTION_DIAL)) }
        findViewById<TextView>(R.id.until).text =
            "${getString(R.string.until).lowercase()} ${Schedule.hhmm(Schedule.end(this))}"
    }

    /** Never pin outside the window: guards against the system restoring this screen in the morning. */
    override fun onResume() {
        super.onResume()
        if (Schedule.isActiveNow(this)) startLockTask() else release()
    }

    /** Legacy back path (predictive back is disabled in the manifest): swallow it. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}

    fun release() {
        stopLockTask()
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
