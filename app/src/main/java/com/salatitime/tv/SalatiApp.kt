package com.salatitime.tv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.salatitime.tv.alarm.PrayerScheduler

class SalatiApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Heures de prière", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rappel à l'heure de chaque prière"
                setSound(null, null) // le son est géré par l'application
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Lecture de l'adhan", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Affichée pendant la lecture de l'appel à la prière"
            }
        )
        PrayerScheduler.schedule(this)
    }

    companion object {
        const val CHANNEL_ALERTS = "prayer_alerts"
        const val CHANNEL_SERVICE = "adhan_playback"
    }
}
