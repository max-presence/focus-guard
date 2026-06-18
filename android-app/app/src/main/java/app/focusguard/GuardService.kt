package app.focusguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import java.util.Calendar

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
        const val ACTION_END_BREAK = "app.focusguard.END_BREAK"
        const val ACTION_TICK = "app.focusguard.TICK"
        const val EXTRA_BREAK_MIN = "break_min"
        const val EXTRA_BREAK_MS = "break_ms"
        private const val PREFS = "guard"
        private const val KEY_GUARD_ENABLED = "guard_enabled"
        private const val KEY_BREAK_UNTIL = "break_until_elapsed"
        private const val TEN_MINUTES_MS = 10 * 60_000L
        private const val WATCHDOG_MS = 2_000L
        private const val WATCHDOG_ALARM_MS = 60_000L

        @Volatile
        var running = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var inBreak = false

    private lateinit var observer: ContentObserver
    private var wakeLock: PowerManager.WakeLock? = null

    private val endBreak = Runnable {
        finishBreak()
    }

    private val watchdog = object : Runnable {
        override fun run() {
            enforceState()
            if (prefs().getBoolean(KEY_GUARD_ENABLED, false)) {
                handler.postDelayed(this, WATCHDOG_MS)
            }
        }
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
                running = true
                ensureForeground()
                startBreak(intent.breakDurationMs())
            }
            ACTION_END_BREAK -> {
                running = true
                ensureForeground()
                finishBreak()
            }
            ACTION_TICK -> {
                running = true
                ensureForeground()
                acquireWakeLock()
                startWatchdog()
                enforceState()
            }
            ACTION_START -> {
                running = true
                ensureForeground()
                startGuard()
            }
            else -> resumeGuard()
        }
        return START_STICKY
    }

    private fun ensureForeground() {
        startForeground(NOTIF_ID, buildNotification())
    }

    private fun startGuard() {
        prefs().edit()
            .putBoolean(KEY_GUARD_ENABLED, true)
            .remove(KEY_BREAK_UNTIL)
            .apply()
        inBreak = false
        handler.removeCallbacks(endBreak)
        cancelBreakAlarm()
        acquireWakeLock()
        GrayscaleController.setGrayscale(this, true)
        startWatchdog()
        updateNotification()
    }

    private fun startBreak(durationMs: Long) {
        prefs().edit().putBoolean(KEY_GUARD_ENABLED, true).apply()
        acquireWakeLock()
        val breakUntil = minOf(
            SystemClock.elapsedRealtime() + durationMs,
            todayElevenPmElapsed()
        )
        if (breakUntil <= SystemClock.elapsedRealtime()) {
            finishBreak()
            return
        }
        inBreak = true
        GrayscaleController.setGrayscale(this, false)
        prefs().edit().putLong(KEY_BREAK_UNTIL, breakUntil).apply()
        handler.removeCallbacks(endBreak)
        handler.postDelayed(endBreak, breakUntil - SystemClock.elapsedRealtime())
        scheduleBreakAlarm(breakUntil)
        startWatchdog()
        updateNotification()
    }

    private fun stopGuard() {
        running = false
        inBreak = false
        handler.removeCallbacks(endBreak)
        handler.removeCallbacks(watchdog)
        cancelBreakAlarm()
        cancelWatchdogAlarm()
        prefs().edit()
            .putBoolean(KEY_GUARD_ENABLED, false)
            .remove(KEY_BREAK_UNTIL)
            .apply()
        GrayscaleController.setGrayscale(this, false)
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (this::observer.isInitialized) {
            contentResolver.unregisterContentObserver(observer)
        }
        handler.removeCallbacks(endBreak)
        handler.removeCallbacks(watchdog)
        if (prefs().getBoolean(KEY_GUARD_ENABLED, false)) {
            scheduleWatchdogAlarm(SystemClock.elapsedRealtime() + 5_000L)
        }
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Focus Guard", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun servicePI(action: String, breakMs: Long = 0L): PendingIntent {
        val i = Intent(this, GuardService::class.java).setAction(action)
        if (breakMs > 0L) i.putExtra(EXTRA_BREAK_MS, breakMs)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val requestCode = 31 * action.hashCode() + breakMs.hashCode()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, requestCode, i, flags)
        } else {
            PendingIntent.getService(this, requestCode, i, flags)
        }
    }

    private fun receiverPI(action: String): PendingIntent {
        val i = Intent(this, GuardReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun alarmPI(): PendingIntent = receiverPI(ACTION_END_BREAK)

    private fun watchdogPI(): PendingIntent = receiverPI(ACTION_TICK)

    private fun prefs() = getSharedPreferences(PREFS, MODE_PRIVATE)

    private fun resumeGuard() {
        if (!prefs().getBoolean(KEY_GUARD_ENABLED, false)) {
            stopSelf()
            return
        }
        running = true
        ensureForeground()
        acquireWakeLock()
        restoreState()
        startWatchdog()
    }

    private fun restoreState() {
        val breakUntil = prefs().getLong(KEY_BREAK_UNTIL, 0L)
        val remaining = breakUntil - SystemClock.elapsedRealtime()
        if (remaining > 0L) {
            inBreak = true
            GrayscaleController.setGrayscale(this, false)
            handler.removeCallbacks(endBreak)
            handler.postDelayed(endBreak, remaining)
            scheduleBreakAlarm(breakUntil)
        } else {
            finishBreak()
        }
        updateNotification()
    }

    private fun finishBreak() {
        inBreak = false
        handler.removeCallbacks(endBreak)
        cancelBreakAlarm()
        prefs().edit().remove(KEY_BREAK_UNTIL).apply()
        GrayscaleController.setGrayscale(this, true)
        startWatchdog()
        updateNotification()
    }

    private fun enforceState() {
        inBreak = GuardState.enforce(this)
        scheduleWatchdogAlarm(SystemClock.elapsedRealtime() + WATCHDOG_ALARM_MS)
        updateNotification()
    }

    private fun scheduleBreakAlarm(breakUntil: Long) {
        val alarm = getSystemService(AlarmManager::class.java)
        alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, breakUntil, alarmPI())
    }

    private fun cancelBreakAlarm() {
        getSystemService(AlarmManager::class.java).cancel(alarmPI())
    }

    private fun startWatchdog() {
        handler.removeCallbacks(watchdog)
        handler.post(watchdog)
        scheduleWatchdogAlarm(SystemClock.elapsedRealtime() + WATCHDOG_ALARM_MS)
    }

    private fun scheduleWatchdogAlarm(whenElapsed: Long) {
        val alarm = getSystemService(AlarmManager::class.java)
        alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, whenElapsed, watchdogPI())
    }

    private fun cancelWatchdogAlarm() {
        getSystemService(AlarmManager::class.java).cancel(watchdogPI())
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FocusGuard:watchdog")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun Intent?.breakDurationMs(): Long {
        if (this == null) return TEN_MINUTES_MS
        val explicitMs = getLongExtra(EXTRA_BREAK_MS, 0L)
        if (explicitMs > 0L) return explicitMs
        return getIntExtra(EXTRA_BREAK_MIN, 10) * 60_000L
    }

    private fun todayElevenPmElapsed(): Long {
        val nowWall = System.currentTimeMillis()
        val eleven = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val delay = (eleven - nowWall).coerceAtLeast(0L)
        return SystemClock.elapsedRealtime() + delay
    }

    private fun buildNotification(): Notification {
        val text = if (inBreak) "彩色缝隙中，到点自动变回黑白"
        else "黑白守护中（被关会自动拉回）"
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Focus Guard")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .addAction(0, "彩色 10 分钟", servicePI(ACTION_BREAK, TEN_MINUTES_MS))
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
