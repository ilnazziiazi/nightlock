package kz.tilltan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.widget.Button
import android.widget.TextView
import java.time.LocalDateTime
import java.time.ZoneId

class LockActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var endAt = 0L

    /** Ticks on the wall-clock second boundary, so the display never skips or stalls. */
    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val left = ((endAt - now + 500) / 1000).coerceAtLeast(0)
            findViewById<TextView>(R.id.left).text = DateUtils.formatElapsedTime(left)
            handler.postDelayed(this, 1000 - now % 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        setContentView(R.layout.lock)
        findViewById<Button>(R.id.call).setOnClickListener { startActivity(Intent(Intent.ACTION_DIAL)) }
        endAt = Schedule.nextBoundary(Schedule.start(this), Schedule.end(this), LocalDateTime.now())
            .atZone(ZoneId.systemDefault()).toEpochSecond() * 1000
    }

    override fun onResume() {
        super.onResume()
        startLockTask()
        tick.run()
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
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
