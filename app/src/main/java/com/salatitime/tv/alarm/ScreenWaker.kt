package com.salatitime.tv.alarm

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.salatitime.tv.ui.WakeActivity

/**
 * Rallume l'écran de la TV (sortie de veille) à l'heure de la prière.
 *
 * Deux mécanismes combinés, car le comportement varie selon les fabricants :
 *  - un wake lock avec ACQUIRE_CAUSES_WAKEUP (allume l'écran sur la plupart des TV) ;
 *  - une activité transparente avec setTurnScreenOn(true) (méthode officielle depuis Android 8.1).
 *
 * Ne fonctionne que si la TV est en veille « rapide » (démarrage rapide activé) :
 * une TV complètement éteinte ou débranchée ne peut pas être rallumée par une application.
 */
object ScreenWaker {

    private const val WAKE_LOCK_MS = 60_000L

    fun isScreenOn(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    /** Retourne true si l'écran était éteint et qu'un réveil a été demandé. */
    fun wake(ctx: Context): Boolean {
        if (isScreenOn(ctx)) return false
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return false
        try {
            @Suppress("DEPRECATION")
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "SalatiTime:wakeScreen"
            )
            wl.acquire(WAKE_LOCK_MS)
        } catch (e: Exception) {
            Log.w("Salati", "Wake lock refusé", e)
        }
        try {
            ctx.startActivity(
                Intent(ctx, WakeActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_HISTORY
                )
            )
        } catch (e: Exception) {
            Log.w("Salati", "Impossible de lancer l'écran de réveil", e)
        }
        return true
    }
}
