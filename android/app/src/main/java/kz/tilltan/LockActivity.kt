package kz.tilltan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.Chronometer
import android.window.OnBackInvokedDispatcher
import java.time.LocalDateTime
import java.time.ZoneId

class LockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        setContentView(R.layout.lock)
        findViewById<Button>(R.id.call).setOnClickListener { startActivity(Intent(Intent.ACTION_DIAL)) }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {}

        val endAt = Schedule.nextBoundary(Schedule.start(this), Schedule.end(this), LocalDateTime.now())
            .atZone(ZoneId.systemDefault()).toEpochSecond() * 1000
        findViewById<Chronometer>(R.id.left).apply {
            base = SystemClock.elapsedRealtime() + (endAt - System.currentTimeMillis())
            start()
        }
    }

    override fun onResume() {
        super.onResume()
        startLockTask()
    }

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
