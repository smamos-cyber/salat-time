package com.salatitime.tv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.salatitime.tv.alarm.PrayerScheduler
import com.salatitime.tv.data.AdhanRepository
import com.salatitime.tv.lock.LockManager

class SalatiApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        // Ancien canal sans vibration : remplacé (les réglages d'un canal ne peuvent pas être modifiés)
        nm.deleteNotificationChannel("prayer_alerts")
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Heures de prière", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rappel à l'heure de chaque prière (transmis aux montres connectées)"
                setSound(null, null) // le son est géré par l'application
                enableVibration(true) // la montre vibre au poignet
                vibrationPattern = longArrayOf(0, 400, 250, 400, 250, 700)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Lecture de l'adhan", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Affichée pendant la lecture de l'appel à la prière"
            }
        )
        PrayerScheduler.schedule(this)
        LockManager.ensureRunning(this)
        Thread { AdhanRepository.ensureBundledDefault(this) }.start()
    }

    companion object {
        const val CHANNEL_ALERTS = "prayer_alerts_v2"
        const val CHANNEL_SERVICE = "adhan_playback"
    }
}
