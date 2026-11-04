package com.salatitime.tv.ui

import android.content.Context
import com.salatitime.tv.alarm.AlertService
import com.salatitime.tv.alarm.PrayerScheduler
import com.salatitime.tv.data.AdhanRepository
import com.salatitime.tv.data.Prefs
import com.salatitime.tv.lock.LockManager
import com.salatitime.tv.prayer.AlertMode
import com.salatitime.tv.prayer.Prayer

object PrayerDialogs {

    fun summary(ctx: Context, prefs: Prefs, p: Prayer): String {
        val mode = prefs.alertMode(p)
        val parts = mutableListOf("${mode.icon} ${mode.label}")
        if (mode == AlertMode.ADHAN) parts += "♪ " + AdhanRepository.nameFor(ctx, prefs.soundIdFor(p))
        if (prefs.offset(p) != 0) parts += Format.offset(prefs.offset(p))
        if (p.isSalah && prefs.lockMinutes(p) > 0) parts += "🔒 " + LockManager.durationLabel(prefs.lockMinutes(p))
        return parts.joinToString("   ")
    }

    /** Réglages d'une prière : mode d'alerte, son, ajustement, test. */
    fun show(ctx: Context, prefs: Prefs, p: Prayer, onChanged: () -> Unit) {
        val mode = prefs.alertMode(p)
        val override = prefs.adhanOverride(p)
        val soundLabel = if (override.isEmpty()) "Par défaut (${AdhanRepository.nameFor(ctx, prefs.defaultAdhanId)})"
        else AdhanRepository.nameFor(ctx, override)
        val items = mutableListOf(
            "Alerte : ${mode.icon} ${mode.label}",
            "Son de l'adhan : $soundLabel",
            "Ajustement de l'horaire : ${Format.offset(prefs.offset(p))}",
            "▶  Tester cette alerte maintenant"
        )
        if (p.isSalah) items += "🔒  Bloquer la TV dès l'adhan : ${LockManager.durationLabel(prefs.lockMinutes(p))}"
        ctx.choose("${p.label}  ${p.arabic}", items) { which ->
            when (which) {
                0 -> ctx.choose("Alerte pour ${p.label}", AlertMode.entries.map { "${it.icon}  ${it.label}" }, mode.ordinal) {
                    prefs.setAlertMode(p, AlertMode.entries[it])
                    changed(ctx, onChanged)
                }
                1 -> {
                    val sounds = AdhanRepository.list(ctx)
                    val labels = listOf("Par défaut (${AdhanRepository.nameFor(ctx, prefs.defaultAdhanId)})") + sounds.map { it.name }
                    val checked = if (override.isEmpty()) 0 else sounds.indexOfFirst { it.id == override } + 1
                    ctx.choose("Son pour ${p.label}", labels, checked) {
                        prefs.setAdhanOverride(p, if (it == 0) "" else sounds[it - 1].id)
                        changed(ctx, onChanged)
                    }
                }
                2 -> {
                    val values = (-15..15).toList()
                    ctx.choose("Ajustement (minutes)", values.map { Format.offset(it) }, values.indexOf(prefs.offset(p))) {
                        prefs.setOffset(p, values[it])
                        changed(ctx, onChanged)
                    }
                }
                4 -> {
                    val values = LockManager.DURATIONS
                    ctx.choose("Bloquer la TV à partir de l'adhan de ${p.label}", values.map { LockManager.durationLabel(it) },
                        values.indexOf(prefs.lockMinutes(p))) {
                        prefs.setLockMinutes(p, values[it])
                        changed(ctx, onChanged)
                    }
                }
                3 -> AlertService.start(
                    ctx, p, false, System.currentTimeMillis(),
                    if (mode == AlertMode.NONE) AlertMode.ADHAN else mode
                )
            }
        }
    }

    private fun changed(ctx: Context, onChanged: () -> Unit) {
        PrayerScheduler.schedule(ctx)
        onChanged()
    }
}
