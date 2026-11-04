package com.salatitime.tv.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.salatitime.tv.lock.LockManager

/** Reprogramme l'alarme après redémarrage, mise à jour, changement d'heure ou de fuseau. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PrayerScheduler.schedule(context)
        // Reprendre le blocage s'il n'est pas terminé (TV redémarrée, débranchée…)
        LockManager.ensureRunning(context)
    }
}
