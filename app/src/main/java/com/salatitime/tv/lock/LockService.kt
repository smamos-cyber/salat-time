package com.salatitime.tv.lock

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.salatitime.tv.R
import com.salatitime.tv.SalatiApp
import com.salatitime.tv.alarm.AlertService
import com.salatitime.tv.data.Prefs
import com.salatitime.tv.prayer.Prayer
import com.salatitime.tv.ui.Format

/**
 * Garde l'écran de blocage affiché par-dessus tout jusqu'à l'heure de fin,
 * coupe le son et met en pause les autres applications.
 */
class LockService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: LockOverlay? = null
    private var focusRequest: AudioFocusRequest? = null
    private var tickCount = 0

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (!LockManager.isActive(this)) {
            LockManager.end(this)
            stopSelf()
            return START_NOT_STICKY
        }
        if (overlay == null) {
            overlay = LockOverlay(this) { code -> tryUnlock(code) }
        }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_STICKY // relancé par le système s'il est tué
    }

    private fun tick() {
        val remaining = LockManager.remainingMs(this)
        if (remaining <= 0) {
            LockManager.end(this)
            return
        }
        val prefs = Prefs(this)
        val ov = overlay ?: return
        // Pendant un adhan, laisser le son et le panneau de l'adhan passer
        val alertPlaying = AlertService.isPlaying
        if (alertPlaying) {
            LockManager.unmute(this)
        } else {
            LockManager.mute(this)
            if (tickCount % 10 == 0) requestFocus()
        }
        if (!ov.isShown() && !alertPlaying) ov.show(prefs.unlockCode.isNotEmpty())
        val prayer = Prayer.entries.firstOrNull { it.name == prefs.lockPrayer }
        ov.update(
            title = prayer?.let { "Temps de la prière : ${it.label}  ${it.arabic}" } ?: "Temps de la prière",
            countdown = formatRemaining(remaining),
            until = "La TV sera de nouveau disponible à ${Format.time(prefs.lockUntil)}"
        )
        tickCount++
    }

    private fun tryUnlock(code: String): Boolean {
        val saved = Prefs(this).unlockCode
        if (saved.isNotEmpty() && code == saved) {
            LockManager.end(this)
            return true
        }
        return false
    }

    private fun formatRemaining(ms: Long): String {
        val total = (ms + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    // ---------- Pause des autres applis ----------

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private fun requestFocus() {
        val am = getSystemService(AudioManager::class.java) ?: return
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener { }
            .build().also { focusRequest = it }
        runCatching { am.requestAudioFocus(req) }
    }

    private fun abandonFocus() {
        val am = getSystemService(AudioManager::class.java) ?: return
        focusRequest?.let { runCatching { am.abandonAudioFocusRequest(it) } }
        focusRequest = null
    }

    // ---------- Service ----------

    private fun startInForeground() {
        val n = NotificationCompat.Builder(this, SalatiApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("TV bloquée pendant la prière")
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, FG_ID, n, type)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        overlay?.hide()
        overlay = null
        abandonFocus()
        if (!LockManager.isActive(this)) LockManager.unmute(this)
        super.onDestroy()
    }

    private companion object {
        const val FG_ID = 43
    }
}
