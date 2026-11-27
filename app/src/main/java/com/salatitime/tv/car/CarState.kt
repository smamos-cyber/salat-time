package com.salatitime.tv.car

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.net.Uri

/**
 * Le téléphone est-il branché à Android Auto ?
 * En voiture : pas de réveil d'écran ni de blocage, et l'adhan passe par le canal « guidage »
 * que la voiture fait toujours entendre (même quand elle est sur la radio).
 */
object CarState {
    /** Moment où Android Auto s'est connecté au service audio de l'appli (0 = non connecté). */
    @Volatile var connectedAt = 0L

    /** Fournisseur publié par Android Auto (même mécanisme que androidx.car.app.connection.CarConnection). */
    private val CONNECTION_URI = Uri.parse("content://androidx.car.app.connection")
    private const val COLUMN_STATE = "CarConnectionState"

    fun isInCar(ctx: Context): Boolean {
        if (connectedAt > 0L) return true
        val ui = ctx.getSystemService(UiModeManager::class.java)
        if (ui?.currentModeType == Configuration.UI_MODE_TYPE_CAR) return true
        return isProjecting(ctx)
    }

    /** Interroge directement Android Auto : 1 = voiture native, 2 = Android Auto (projection). */
    private fun isProjecting(ctx: Context): Boolean = try {
        ctx.contentResolver.query(CONNECTION_URI, arrayOf(COLUMN_STATE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(COLUMN_STATE)
                idx >= 0 && c.getInt(idx) > 0
            } else false
        } ?: false
    } catch (_: Exception) {
        false
    }
}
