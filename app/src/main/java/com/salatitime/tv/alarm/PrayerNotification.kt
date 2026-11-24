package com.salatitime.tv.alarm

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.salatitime.tv.R
import com.salatitime.tv.SalatiApp
import com.salatitime.tv.prayer.Prayer

/**
 * Notification de prière, courte et vibrante, pensée pour être relayée telle quelle
 * sur une montre connectée (Huawei Santé, Wear OS, Galaxy Wearable…).
 */
object PrayerNotification {

    fun post(ctx: Context, prayer: Prayer, title: String, text: String, open: PendingIntent?) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(ctx, SalatiApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setVibrate(longArrayOf(0, 400, 250, 400, 250, 700))
            .setOnlyAlertOnce(false)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setLocalOnly(false) // autorise le relais vers la montre
            .setTimeoutAfter(30 * 60 * 1000L)
            .apply { if (open != null) setContentIntent(open) }
            .build()
        try {
            nm.notify(1000 + prayer.ordinal, n)
        } catch (_: SecurityException) { }
    }
}
