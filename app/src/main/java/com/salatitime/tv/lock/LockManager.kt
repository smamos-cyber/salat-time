package com.salatitime.tv.lock

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import android.content.Intent
import com.salatitime.tv.data.HadithRepository
import com.salatitime.tv.data.Prefs
import com.salatitime.tv.prayer.Prayer

/**
 * Blocage de la TV pendant la prière.
 *
 * L'heure de fin est enregistrée : le blocage reprend tout seul après extinction,
 * redémarrage ou débranchement de la TV, tant que le temps n'est pas écoulé.
 */
object LockManager {

    /** Durées proposées (minutes), 0 = pas de blocage. */
    val DURATIONS = listOf(0, 10, 15, 20, 30, 45, 60, 75, 90, 105, 120)

    fun durationLabel(min: Int): String = when {
        min <= 0 -> "Désactivé"
        min < 60 -> "$min min"
        min % 60 == 0 -> "${min / 60} h"
        else -> "${min / 60} h ${"%02d".format(min % 60)}"
    }

    fun isActive(ctx: Context): Boolean = remainingMs(ctx) > 0

    fun remainingMs(ctx: Context): Long {
        val prefs = Prefs(ctx)
        val until = prefs.lockUntil
        if (until <= 0) return 0
        val now = System.currentTimeMillis()
        // Si l'heure de la TV a été reculée, ne jamais bloquer plus longtemps que la durée choisie
        val maxMs = prefs.lockDurationMin * 60_000L
        if (until - now > maxMs) {
            prefs.lockUntil = now + maxMs
            return maxMs
        }
        return (until - now).coerceAtLeast(0)
    }

    /** Démarre (ou prolonge) le blocage pour la prière donnée. */
    fun start(ctx: Context, prayer: Prayer, minutes: Int) {
        if (minutes <= 0) return
        val prefs = Prefs(ctx)
        val until = System.currentTimeMillis() + minutes * 60_000L
        if (until > prefs.lockUntil) {
            // Nouveau blocage : nouveau hadith (conservé en cas de redémarrage de la TV)
            if (!isActive(ctx)) prefs.lockHadith = HadithRepository.randomIndex(ctx, prefs.lockHadith)
            prefs.lockUntil = until
            prefs.lockDurationMin = minutes
            prefs.lockPrayer = prayer.name
        }
        ensureRunning(ctx)
    }

    /** Relance l'écran de blocage s'il doit encore être affiché (démarrage, ouverture de l'appli…). */
    fun ensureRunning(ctx: Context) {
        if (!isActive(ctx)) {
            cleanup(ctx)
            return
        }
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, LockService::class.java))
        } catch (e: Exception) {
            Log.e("Salati", "Impossible de démarrer le blocage", e)
        }
    }

    /** Fin du blocage (temps écoulé ou code de déblocage). */
    fun end(ctx: Context) {
        Prefs(ctx).lockUntil = 0
        cleanup(ctx)
        ctx.stopService(Intent(ctx, LockService::class.java))
    }

    private fun cleanup(ctx: Context) {
        unmute(ctx)
    }

    // ---------- Son coupé pendant le blocage ----------

    fun mute(ctx: Context) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        try {
            if (!am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                Prefs(ctx).lockMuted = true
            }
        } catch (_: Exception) { }
    }

    /** Rétablit le son seulement si c'est le blocage qui l'avait coupé. */
    fun unmute(ctx: Context) {
        val prefs = Prefs(ctx)
        if (!prefs.lockMuted) return
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        } catch (_: Exception) { }
        prefs.lockMuted = false
    }
}
