package com.salatitime.tv.car

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/**
 * Le téléphone est-il branché à Android Auto ?
 * En voiture : pas de réveil d'écran ni de blocage, et surtout pas de son coupé.
 */
object CarState {
    /** Moment où Android Auto s'est connecté au service audio de l'appli (0 = non connecté). */
    @Volatile var connectedAt = 0L

    fun isInCar(ctx: Context): Boolean {
        val ui = ctx.getSystemService(UiModeManager::class.java)
        return connectedAt > 0L || ui?.currentModeType == Configuration.UI_MODE_TYPE_CAR
    }
}
