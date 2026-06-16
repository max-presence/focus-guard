package app.focusguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * 前台服务：常驻保持黑白 + 看门狗（被关自动拉回）+ 彩色缝隙（到点自动恢复黑白）。
 */
class GuardService : Service() {

    companion object {
        const val CHANNEL = "focusguard"
        const val NOTIF_ID = 1
        const val ACTION_START = "app.focusguard.START"
        const val ACTION_STOP = "app.focusguard.STOP"
        const val ACTION_BREAK = "app.focusguard.BREAK"
        const val EXTRA_BREAK_MIN = "break_min"

        @Volatile
        var running = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var inBreak = false

    private lateinit var observer: ContentObserver

    private val endBreak = Runnable {
        inBreak = false
        GrayscaleController.setGrayscale(this, true)
        updateNotification()
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                // 非缝隙期被关掉 → 拉回黑白
                if (!inBreak && !GrayscaleController.isOn(this@GuardService)) {
                    GrayscaleController.setGrayscale(this@GuardService, true)
                    updateNotification()
                }
            }
        }
        contentResolver.registerContentObserver(GrayscaleController.enabledUri, false, observer)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // 即便经 startForegroundService 拉起，也要先满足 startForeground 契约再停，避免崩溃
                ensureForeground()
                stopGuard()
                return START_NOT_STICKY
            }
            ACTION_BREAK -> {
                ensureForeground()
                startBreak(intent.getIntExtra(EXTRA_BREAK_MIN, 10))
            }
            else -> {
                running = true
                inBreak = false
                ensureForeground()
                GrayscaleController.setGrayscale(this, true)
                updateNotification()
            }
        }
        return START_STICKY
    }

    private fun ensureForeground() {
        startForeground(NOTIF_ID, buildNotification())
    }

    private fun startBreak(min: Int) {
        inBreak = true
        GrayscaleController.setGrayscale(this, false)
        handler.removeCallbacks(endBreak)
        handler.postDelayed(endBreak, min * 60_000L)
        updateNotification()
    }

    private fun stopGuard() {
        running = false
        handler.removeCallbacks(endBreak)
        GrayscaleController.setGrayscale(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (this::observer.isInitialized) {
            contentResolver.unregisterContentObserver(observer)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Focus Guard", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun servicePI(action: String, min: Int = 0): PendingIntent {
        val i = Intent(this, GuardService::class.java).setAction(action)
        if (min > 0) i.putExtra(EXTRA_BREAK_MIN, min)
        return PendingIntent.getService(
            this, action.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(): Notification {
        val text = if (inBreak) "彩色缝隙中，到点自动变回黑白"
        else "黑白守护中（被关会自动拉回）"
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Focus Guard")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .addAction(0, "彩色 10 分钟", servicePI(ACTION_BREAK, 10))
            .addAction(0, "停止", servicePI(ACTION_STOP))
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }
}
