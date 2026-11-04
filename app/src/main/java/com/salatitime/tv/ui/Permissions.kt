package com.salatitime.tv.ui

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.annotation.SuppressLint

object Permissions {

    fun adbHelp(ctx: Context) = """
        Certains téléviseurs Android TV / Google TV n'affichent pas l'écran d'autorisation.
        Vous pouvez l'accorder depuis un ordinateur (débogage ADB activé sur la TV) :

        adb connect <IP_DE_LA_TV>
        adb shell appops set ${ctx.packageName} SYSTEM_ALERT_WINDOW allow

        Puis relancez l'application.
    """.trimIndent()

    fun canOverlay(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun canExactAlarm(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: true
    }

    fun requestOverlay(a: Activity) {
        val specific = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${a.packageName}"))
        val generic = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        if (!tryStart(a, specific) && !tryStart(a, generic)) {
            a.info("Autorisation à activer manuellement", adbHelp(a))
        }
    }

    /** Téléphone : sans cette exemption, certains fabricants retardent ou suppriment les alarmes. */
    fun ignoresBatteryOptimizations(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) ?: true

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(a: Activity) {
        val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${a.packageName}"))
        if (!tryStart(a, i) && !tryStart(a, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            a.info("Batterie", "Ouvrez Paramètres > Applications > Salati Time > Batterie et choisissez « Non restreinte ».")
        }
    }

    fun requestExactAlarm(a: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${a.packageName}"))
        if (!tryStart(a, i)) {
            a.info(
                "Alarmes exactes",
                "Ouvrez Paramètres > Applications > ${a.applicationInfo.loadLabel(a.packageManager)} et autorisez « Alarmes et rappels »."
            )
        }
    }

    private fun tryStart(a: Activity, i: Intent): Boolean = try {
        a.startActivity(i); true
    } catch (e: Exception) {
        false
    }
}
