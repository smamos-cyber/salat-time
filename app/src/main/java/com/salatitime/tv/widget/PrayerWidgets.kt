package com.salatitime.tv.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.salatitime.tv.R
import com.salatitime.tv.data.Prefs
import com.salatitime.tv.prayer.Prayer
import com.salatitime.tv.ui.Format
import com.salatitime.tv.ui.MainActivity
import com.salatitime.tv.ui.theme.ThemeEngine
import java.time.LocalDate
import java.time.ZonedDateTime

/** Widget « Horaires de prière » : prochaine prière, compte à rebours, six horaires du jour. */
class TimesWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = PrayerWidgets.updateAll(ctx)
    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action in PrayerWidgets.REFRESH_ACTIONS) PrayerWidgets.updateAll(ctx)
    }
}

/** Petit widget « Prochaine prière ». */
class NextPrayerWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = PrayerWidgets.updateAll(ctx)
    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action in PrayerWidgets.REFRESH_ACTIONS) PrayerWidgets.updateAll(ctx)
    }
}

object PrayerWidgets {

    const val ACTION_REFRESH = "com.salatitime.tv.WIDGET_REFRESH"
    val REFRESH_ACTIONS = setOf(
        ACTION_REFRESH,
        Intent.ACTION_DATE_CHANGED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
    )

    private data class Next(val prayer: Prayer, val time: ZonedDateTime)

    /** Met à jour tous les widgets posés sur l'écran d'accueil, puis programme la prochaine mise à jour. */
    /** Vues des deux widgets, sans les poser (aperçu de la version de test). */
    fun previewViews(ctx: Context): Pair<RemoteViews, RemoteViews> {
        val prefs = Prefs(ctx)
        val now = ZonedDateTime.now()
        val today = prefs.timesFor(LocalDate.now())
        val next = nextPrayer(prefs, now, today)
        return timesViews(ctx, prefs, today, next, now) to nextViews(ctx, next, now)
    }

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx) ?: return
        val timesIds = mgr.getAppWidgetIds(ComponentName(ctx, TimesWidget::class.java))
        val nextIds = mgr.getAppWidgetIds(ComponentName(ctx, NextPrayerWidget::class.java))
        if (timesIds.isEmpty() && nextIds.isEmpty()) return

        val prefs = Prefs(ctx)
        val now = ZonedDateTime.now()
        val today = prefs.timesFor(LocalDate.now())
        val next = nextPrayer(prefs, now, today)

        if (timesIds.isNotEmpty()) mgr.updateAppWidget(timesIds, timesViews(ctx, prefs, today, next, now))
        if (nextIds.isNotEmpty()) mgr.updateAppWidget(nextIds, nextViews(ctx, next, now))

        scheduleRefresh(ctx, next?.time, now)
    }

    private fun nextPrayer(prefs: Prefs, now: ZonedDateTime, today: Map<Prayer, ZonedDateTime>?): Next? {
        today ?: return null
        Prayer.entries.filter { it.isSalah }.firstOrNull { today[it]?.isAfter(now) == true }
            ?.let { return Next(it, today.getValue(it)) }
        val fajr = prefs.timesFor(LocalDate.now().plusDays(1))?.get(Prayer.FAJR) ?: return null
        return Next(Prayer.FAJR, fajr)
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 300, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Compte à rebours qui avance tout seul, sans réveiller le téléphone chaque seconde. */
    private fun RemoteViews.countdown(id: Int, target: ZonedDateTime, now: ZonedDateTime, format: String) {
        val remaining = java.time.Duration.between(now, target).toMillis().coerceAtLeast(0)
        setChronometer(id, SystemClock.elapsedRealtime() + remaining, format, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) setChronometerCountDown(id, true)
    }

    private fun RemoteViews.background(ctx: Context) {
        val th = ThemeEngine.theme(ctx)
        setInt(R.id.widgetBg, "setColorFilter", th.bgBottom)
        setInt(R.id.widgetBg, "setImageAlpha", 0xE6)
        setOnClickPendingIntent(R.id.widgetRoot, openApp(ctx))
    }

    private fun timesViews(
        ctx: Context, prefs: Prefs, today: Map<Prayer, ZonedDateTime>?, next: Next?, now: ZonedDateTime
    ): RemoteViews {
        val th = ThemeEngine.theme(ctx)
        val v = RemoteViews(ctx.packageName, R.layout.widget_times)
        v.background(ctx)
        v.setTextColor(R.id.nextLabel, th.text)
        v.setTextColor(R.id.countdown, th.accent)
        v.setTextColor(R.id.subtitle, th.textDim)

        if (today == null || next == null) {
            v.setTextViewText(R.id.nextLabel, "Salati Time")
            v.setTextViewText(R.id.subtitle, "Ouvrez l'appli pour choisir votre ville")
            v.setChronometer(R.id.countdown, SystemClock.elapsedRealtime(), "", false)
            for (i in 0 until 6) {
                v.setTextViewText(ids(i).second, Prayer.entries[i].label)
                v.setTextViewText(ids(i).third, "--:--")
            }
            return v
        }

        v.setTextViewText(R.id.nextLabel, "${next.prayer.label}  ${next.prayer.arabic}  à ${Format.hm(next.time)}")
        v.countdown(R.id.countdown, next.time, now, "dans %s")
        v.setTextViewText(R.id.subtitle, prefs.placeName.ifBlank { "Ma position" } + "  ·  " + Format.hijri(LocalDate.now()))

        val highlight = if (next.time.toLocalDate() == LocalDate.now()) next.prayer else null
        for ((i, p) in Prayer.entries.withIndex()) {
            val (cell, name, time) = ids(i)
            val isNext = p == highlight
            v.setTextViewText(name, p.label)
            v.setTextViewText(time, today[p]?.let { Format.hm(it) } ?: "--:--")
            v.setTextColor(name, if (isNext) th.accent else th.textDim)
            v.setTextColor(time, if (isNext) th.accent else if (p.isSalah) th.text else th.textDim)
            v.setInt(cell, "setBackgroundResource", if (isNext) R.drawable.widget_cell_next else R.drawable.widget_cell)
        }
        return v
    }

    private fun nextViews(ctx: Context, next: Next?, now: ZonedDateTime): RemoteViews {
        val th = ThemeEngine.theme(ctx)
        val v = RemoteViews(ctx.packageName, R.layout.widget_next)
        v.background(ctx)
        v.setTextColor(R.id.nextName, th.accentSoft)
        v.setTextColor(R.id.nextTime, th.text)
        v.setTextColor(R.id.countdown, ColorUtils.setAlphaComponent(th.accent, 0xFF))
        if (next == null) {
            v.setTextViewText(R.id.nextName, "Salati Time")
            v.setTextViewText(R.id.nextTime, "--:--")
            v.setChronometer(R.id.countdown, SystemClock.elapsedRealtime(), "Choisir la ville", false)
            return v
        }
        v.setTextViewText(R.id.nextName, "${next.prayer.label}  ${next.prayer.arabic}")
        v.setTextViewText(R.id.nextTime, Format.hm(next.time))
        v.countdown(R.id.countdown, next.time, now, "dans %s")
        return v
    }

    private fun ids(i: Int): Triple<Int, Int, Int> = when (i) {
        0 -> Triple(R.id.cell0, R.id.name0, R.id.time0)
        1 -> Triple(R.id.cell1, R.id.name1, R.id.time1)
        2 -> Triple(R.id.cell2, R.id.name2, R.id.time2)
        3 -> Triple(R.id.cell3, R.id.name3, R.id.time3)
        4 -> Triple(R.id.cell4, R.id.name4, R.id.time4)
        else -> Triple(R.id.cell5, R.id.name5, R.id.time5)
    }

    /** Prochaine mise à jour : à l'heure de la prochaine prière (changement de surlignage), ou à minuit. */
    private fun scheduleRefresh(ctx: Context, nextTime: ZonedDateTime?, now: ZonedDateTime) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        val at = listOfNotNull(nextTime, midnight).filter { it.isAfter(now) }.minOrNull() ?: midnight
        val pi = PendingIntent.getBroadcast(
            ctx, 301,
            Intent(ctx, TimesWidget::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val trigger = at.toInstant().toEpochMilli() + 2_000L
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                am.setExact(AlarmManager.RTC, trigger, pi)
            } else {
                am.set(AlarmManager.RTC, trigger, pi)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC, trigger, pi)
        }
    }
}
