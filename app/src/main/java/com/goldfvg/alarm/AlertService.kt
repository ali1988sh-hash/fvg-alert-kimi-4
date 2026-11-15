package com.goldfvg.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread

class AlertService : Service() {

    companion object {
        const val CHANNEL_ID = "fvg_alerts"
        const val NOTIF_ID = 1
        const val ALARM_NOTIF_ID = 2
        const val ACTION_STOP = "com.goldfvg.alarm.STOP"
        const val ACTION_STOP_ALARM = "com.goldfvg.alarm.STOP_ALARM"
        private const val POLL_MS = 45_000L
        private const val CANDLE_MS = 900_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: FvgStore
    private val active = mutableListOf<Fvg>()
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        store = FvgStore(this)
        active.addAll(store.loadActive())
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { shutdown(); return START_NOT_STICKY }
            ACTION_STOP_ALARM -> { stopAlarm(); return START_STICKY }
        }
        startForeground(NOTIF_ID, buildBaseNotification("پایش FVG طلای ۱۵ دقیقه فعال است"))
        ensureWakeLock()
        AppState.running.value = true
        AppState.statusText.value = "پایش فعال — در انتظار دیتا…"
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        return START_STICKY
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            ensureWakeLock()
            pollOnce()
            handler.postDelayed(this, POLL_MS)
        }
    }

    private fun pollOnce() {
        thread {
            try {
                val candles = PriceFetcher.fetch15m(store.apiToken)
                val now = System.currentTimeMillis()
                val closed = candles.filter { it.time + CANDLE_MS + 15_000 < now }
                if (closed.isNotEmpty()) {
                    process(closed)
                    store.saveActive(active.filter { it.status == "active" })
                    AppState.candles.clear()
                    AppState.candles.addAll(candles)
                    AppState.fvgs.clear()
                    AppState.fvgs.addAll(active.filter { it.status == "active" })
                    AppState.lastPrice.value = candles.last().close
                    val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                    AppState.lastUpdate.value = fmt.format(java.util.Date(now))
                    AppState.statusText.value = "فعال — ${closed.size} کندل بسته پردازش شد"
                }
            } catch (e: Exception) {
                AppState.statusText.value = "خطا: ${e.message ?: "دریافت دیتا ناموفق"}"
            }
        }
    }

    private fun process(closed: List<Candle>) {
        val now = System.currentTimeMillis()
        val news = closed.filter { it.time > store.lastProcessedTime }.sortedBy { it.time }

        // rule 1: a candle closing outside the FVG cancels it
        // rule 2: price touching the FVG fires the alarm
        for (c in news) {
            for (f in active) {
                if (f.status != "active") continue
                when (f.type) {
                    "bullish" -> when {
                        c.close < f.bottom -> f.status = "cancelled"
                        c.low <= f.top -> {
                            f.status = "filled"
                            if (c.time > now - 30 * 60_000) triggerAlarm(f)
                        }
                    }
                    "bearish" -> when {
                        c.close > f.top -> f.status = "cancelled"
                        c.high >= f.bottom -> {
                            f.status = "filled"
                            if (c.time > now - 30 * 60_000) triggerAlarm(f)
                        }
                    }
                }
            }
        }

        // discover newly formed FVGs (3-candle pattern), keep last 48h
        val detected = FvgDetector.detect(closed)
            .filter { it.createdAt > now - 48 * 3600_000L }
        detected.forEach { d ->
            if (active.none { it.id == d.id }) active.add(d)
        }
        // drop old / dead FVGs
        active.removeAll { it.status != "active" && it.createdAt < now - 12 * 3600_000L }

        store.lastProcessedTime = closed.last().time
    }

    private fun triggerAlarm(f: Fvg) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(ALARM_NOTIF_ID, buildAlarmNotification(f))
        if (player == null) {
            try {
                val afd = resources.openRawResourceFd(com.goldfvg.alarm.R.raw.alarm)
                val mp = MediaPlayer()
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                mp.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                )
                mp.isLooping = true
                mp.prepare()
                mp.start()
                player = mp
            } catch (e: Exception) { /* player failed, notification still shown */ }
        }
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600), 0))
        }
        AppState.ringing.value = true
    }

    fun stopAlarm() {
        player?.let {
            try { it.stop() } catch (e: Exception) {}
            it.release()
        }
        player = null
        val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
        v.cancel()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ALARM_NOTIF_ID)
        AppState.ringing.value = false
    }

    private fun ensureWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "goldfvg:lock")
        }
        wakeLock?.let { if (!it.isHeld) it.acquire(30 * 60 * 1000L) }
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "هشدار FVG طلا", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "هشدار برخورد قیمت با FVG سه‌کندلی ۱۵ دقیقه"
            setSound(null, null)
            enableVibration(true)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun basePending(): PendingIntent {
        val i = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildBaseNotification(text: String): Notification {
        val stop = Intent(this, AlertService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(
            this, 1, stop,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("هشدار FVG طلا")
            .setContentText(text)
            .setContentIntent(basePending())
            .setOngoing(true)
            .addAction(0, "توقف کامل", stopPi)
            .build()
    }

    private fun buildAlarmNotification(f: Fvg): Notification {
        val typeFa = if (f.type == "bullish") "صعودی" else "نزولی"
        val stop = Intent(this, AlertService::class.java).setAction(ACTION_STOP_ALARM)
        val stopPi = PendingIntent.getService(
            this, 2, stop,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val full = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val fullPi = PendingIntent.getActivity(
            this, 3, full,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("قیمت به FVG رسید!")
            .setContentText("FVG $typeFa ۱۵ دقیقه — بازه ${"%,.2f".format(f.bottom)} تا ${"%,.2f".format(f.top)}")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(fullPi)
            .setFullScreenIntent(fullPi, true)
            .setVibrate(longArrayOf(0, 800, 400, 800))
            .setOngoing(true)
            .addAction(0, "قطع صدا", stopPi)
            .build()
    }

    private fun shutdown() {
        stopAlarm()
        handler.removeCallbacks(pollRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        AppState.running.value = false
        AppState.statusText.value = "غیرفعال"
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
