package com.salatitime.tv.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.salatitime.tv.data.Prefs
import com.salatitime.tv.prayer.AlertMode
import com.salatitime.tv.prayer.Prayer
import com.salatitime.tv.ui.MainActivity
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Programme UNE alarme : le prochain événement (prière ou rappel avant la prière).
 * À chaque déclenchement, l'alarme suivante est reprogrammée.
 */
object PrayerScheduler {

    data class Event(val prayer: Prayer, val pre: Boolean, val time: ZonedDateTime)

    fun nextEvent(ctx: Context, after: ZonedDateTime = ZonedDateTime.now()): Event? {
        val prefs = Prefs(ctx)
        if (!prefs.hasLocation()) return null
        val pre = prefs.preAlertMinutes
        val events = mutableListOf<Event>()
        val startDate = after.withZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        for (i in 0L..2L) {
            val times = prefs.timesFor(startDate.plusDays(i)) ?: continue
            for ((p, t) in times) {
                if (prefs.alertMode(p) == AlertMode.NONE) continue
                events += Event(p, false, t)
                if (pre > 0 && p.isSalah) events += Event(p, true, t.minusMinutes(pre.toLong()))
            }
        }
        return events.filter { it.time.isAfter(after) }.minByOrNull { it.time }
    }

    fun schedule(ctx: Context, after: ZonedDateTime = ZonedDateTime.now()) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(ctx, null)
        val event = nextEvent(ctx, after)
        if (event == null) {
            am.cancel(pi)
            return
        }
        val operation = pendingIntent(ctx, event)
        val millis = event.time.toInstant().toEpochMilli()
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                val show = PendingIntent.getActivity(
                    ctx, 101, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                // setAlarmClock = le plus fiable, même quand la TV est en veille (Doze)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), operation)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
        }
    }

    private fun pendingIntent(ctx: Context, event: Event?): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_PRAYER
            if (event != null) {
                putExtra(AlertService.EXTRA_PRAYER, event.prayer.name)
                putExtra(AlertService.EXTRA_PRE, event.pre)
                putExtra(AlertService.EXTRA_TIME, event.time.toInstant().toEpochMilli())
            }
        }
        return PendingIntent.getBroadcast(
            ctx, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
