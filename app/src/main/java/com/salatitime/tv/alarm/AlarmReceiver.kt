package com.salatitime.tv.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.salatitime.tv.prayer.Prayer
import java.time.Instant
import java.time.ZoneId

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(AlertService.EXTRA_PRAYER)
        val time = intent.getLongExtra(AlertService.EXTRA_TIME, System.currentTimeMillis())
        val pre = intent.getBooleanExtra(AlertService.EXTRA_PRE, false)
        val prayer = Prayer.entries.firstOrNull { it.name == prayerName }

        // Ignorer une alerte arrivée très en retard (TV éteinte, etc.)
        val lateMs = System.currentTimeMillis() - time
        if (prayer != null && lateMs < MAX_LATE_MS) {
            try {
                AlertService.start(context, prayer, pre, time)
            } catch (e: Exception) {
                Log.e("Salati", "Impossible de démarrer l'alerte", e)
            }
        }
        PrayerScheduler.schedule(context, Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))
    }

    companion object {
        const val ACTION_PRAYER = "com.salatitime.tv.PRAYER_ALARM"
        private const val MAX_LATE_MS = 5 * 60 * 1000L
    }
}
