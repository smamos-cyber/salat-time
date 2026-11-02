package com.salatitime.tv.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.salatitime.tv.R
import com.salatitime.tv.SalatiApp
import com.salatitime.tv.data.AdhanRepository
import com.salatitime.tv.data.Prefs
import com.salatitime.tv.prayer.AlertMode
import com.salatitime.tv.prayer.Prayer
import com.salatitime.tv.ui.MainActivity
import com.salatitime.tv.ui.Format

/**
 * Service déclenché à l'heure de la prière :
 *  - mode Notification : bannière par-dessus l'écran en cours + bip + notification
 *  - mode Adhan : panneau « Arrêter » par-dessus l'écran + lecture de l'appel à la prière
 *    (les autres applis sont mises en pause grâce au focus audio).
 */
class AlertService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var focusRequest: AudioFocusRequest? = null
    private lateinit var overlay: OverlayManager
    private val stopRunnable = Runnable { stopAll() }

    override fun onCreate() {
        super.onCreate()
        overlay = OverlayManager(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prayer = intent?.getStringExtra(EXTRA_PRAYER)?.let { n -> Prayer.entries.firstOrNull { it.name == n } }
        if (intent == null || intent.action == ACTION_STOP || prayer == null) {
            // startForeground doit tout de même être appelé après startForegroundService
            startInForeground("Salati Time", "")
            stopAll()
            return START_NOT_STICKY
        }
        val prefs = Prefs(this)
        val pre = intent.getBooleanExtra(EXTRA_PRE, false)
        val time = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
        val forced = intent.getStringExtra(EXTRA_FORCE_MODE)?.let { n -> AlertMode.entries.firstOrNull { it.name == n } }
        val mode = if (pre) AlertMode.NOTIFICATION else forced ?: prefs.alertMode(prayer)

        val title = when {
            pre -> "${prayer.label} dans ${prefs.preAlertMinutes} min"
            prayer == Prayer.SUNRISE -> "Lever du soleil (Chourouk)"
            else -> "C'est l'heure de la prière : ${prayer.label}"
        }
        val subtitle = "${prayer.arabic}  •  ${Format.time(time)}"

        startInForeground(title, subtitle)
        handler.removeCallbacks(stopRunnable)
        releaseAudio()
        overlay.hide()

        when (mode) {
            AlertMode.NONE -> stopAll()
            AlertMode.NOTIFICATION -> {
                postInfoNotification(prayer, title, subtitle)
                if (!overlay.showBanner(title, subtitle)) toast(title, subtitle)
                playBeep()
                handler.postDelayed(stopRunnable, BANNER_DURATION_MS)
            }
            AlertMode.ADHAN -> {
                postInfoNotification(prayer, title, subtitle)
                val shown = overlay.showAdhan(title, subtitle, onStop = { stopAll() })
                if (!shown) toast(title, subtitle)
                playAdhan(prefs.soundIdFor(prayer), prefs.volume)
                handler.postDelayed(stopRunnable, MAX_ADHAN_MS)
            }
        }
        return START_NOT_STICKY
    }

    // ---------------- Audio ----------------

    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA) // suit le volume de la TV
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun requestFocus() {
        val am = getSystemService(AudioManager::class.java) ?: return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = req
        am.requestAudioFocus(req)
    }

    private fun abandonFocus() {
        val am = getSystemService(AudioManager::class.java) ?: return
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun playAdhan(soundId: String, volume: Int) {
        requestFocus()
        val uri: Uri? = AdhanRepository.fileFor(this, soundId)?.let { Uri.fromFile(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (uri == null) {
            playFallbackTone(); return
        }
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(this@AlertService, uri)
                val v = (volume.coerceIn(0, 100)) / 100f
                setVolume(v, v)
                setOnCompletionListener { stopAll() }
                setOnErrorListener { _, _, _ -> playFallbackTone(); true }
                prepare()
                start()
            }
        } catch (e: Exception) {
            playFallbackTone()
        }
    }

    private fun playBeep() {
        try {
            tone = ToneGenerator(AudioManager.STREAM_MUSIC, Prefs(this).volume.coerceIn(10, 100)).also {
                it.startTone(ToneGenerator.TONE_PROP_BEEP2, 600)
            }
        } catch (_: Exception) { }
    }

    private fun playFallbackTone() {
        releasePlayer()
        try {
            tone?.release()
            tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90).also {
                it.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 4000)
            }
        } catch (_: Exception) { }
        handler.postDelayed(stopRunnable, 5000)
    }

    private fun releasePlayer() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    private fun releaseAudio() {
        releasePlayer()
        tone?.release()
        tone = null
        abandonFocus()
    }

    private fun stopAll() {
        handler.removeCallbacks(stopRunnable)
        releaseAudio()
        overlay.hide()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopRunnable)
        releaseAudio()
        overlay.hide()
        super.onDestroy()
    }

    // ---------------- Notifications ----------------

    private fun startInForeground(title: String, text: String) {
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, AlertService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, SalatiApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .addAction(0, "Arrêter", stopPi)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, FG_ID, n, type)
    }

    private fun postInfoNotification(prayer: Prayer, title: String, text: String) {
        val nm = NotificationManagerCompat.from(this)
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(this, SalatiApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(30 * 60 * 1000L)
            .setContentIntent(openAppIntent())
            .build()
        try {
            nm.notify(1000 + prayer.ordinal, n)
        } catch (_: SecurityException) { }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Repli si l'autorisation « par-dessus les autres applis » n'est pas accordée. */
    private fun toast(title: String, subtitle: String) {
        val text = "🕌 $title\n$subtitle"
        Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
        handler.postDelayed({ Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show() }, 3500)
    }

    companion object {
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_PRE = "pre"
        const val EXTRA_TIME = "time"
        const val EXTRA_FORCE_MODE = "forceMode"
        const val ACTION_STOP = "com.salatitime.tv.STOP_ALERT"
        private const val FG_ID = 42
        private const val BANNER_DURATION_MS = 20_000L
        private const val MAX_ADHAN_MS = 10 * 60_000L

        fun start(ctx: Context, prayer: Prayer, pre: Boolean, time: Long, forceMode: AlertMode? = null) {
            val i = Intent(ctx, AlertService::class.java)
                .putExtra(EXTRA_PRAYER, prayer.name)
                .putExtra(EXTRA_PRE, pre)
                .putExtra(EXTRA_TIME, time)
            forceMode?.let { i.putExtra(EXTRA_FORCE_MODE, it.name) }
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, AlertService::class.java).setAction(ACTION_STOP))
        }
    }
}
