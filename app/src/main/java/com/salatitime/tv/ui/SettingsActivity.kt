package com.salatitime.tv.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.salatitime.tv.R
import com.salatitime.tv.alarm.PrayerScheduler
import com.salatitime.tv.data.Prefs
import com.salatitime.tv.prayer.CalcMethod
import com.salatitime.tv.prayer.HighLatRule
import com.salatitime.tv.prayer.Prayer

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var rows: RowList

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        findViewById<TextView>(R.id.screenTitle).text = "Paramètres"
        prefs = Prefs(this)
        rows = RowList(findViewById<LinearLayout>(R.id.container))
        build()
    }

    override fun onResume() {
        super.onResume()
        rows.refresh()
    }

    private fun changed() {
        PrayerScheduler.schedule(this)
        rows.refresh()
    }

    private fun build() {
        rows.header("Localisation")
        rows.row("📍  Lieu", { prefs.placeLabel() }) {
            LocationDialogs.show(this, prefs) { rows.refresh() }
        }.requestFocus()

        rows.header("Calcul des horaires")
        rows.row("Méthode de calcul", { prefs.method.label }) {
            choose("Méthode de calcul", CalcMethod.entries.map { it.label }, prefs.method.ordinal) {
                prefs.method = CalcMethod.entries[it]; changed()
            }
        }
        val asrLabels = listOf("Standard (chafiite, malikite, hanbalite)", "Hanafite")
        rows.row("Calcul de Asr", { asrLabels[if (prefs.asrHanafi) 1 else 0] }) {
            choose("Calcul de Asr", asrLabels, if (prefs.asrHanafi) 1 else 0) {
                prefs.asrHanafi = it == 1; changed()
            }
        }
        rows.row("Latitudes élevées", { prefs.highLat.label }) {
            choose("Latitudes élevées", HighLatRule.entries.map { it.label }, prefs.highLat.ordinal) {
                prefs.highLat = HighLatRule.entries[it]; changed()
            }
        }

        rows.header("Alertes")
        val preValues = listOf(0, 5, 10, 15, 20, 30)
        rows.row("Rappel avant la prière", {
            if (prefs.preAlertMinutes == 0) "Désactivé" else "${prefs.preAlertMinutes} min avant"
        }) {
            choose("Rappel avant la prière", preValues.map { if (it == 0) "Désactivé" else "$it min avant" },
                preValues.indexOf(prefs.preAlertMinutes)) {
                prefs.preAlertMinutes = preValues[it]; changed()
            }
        }
        val volumes = (10..100 step 10).toList()
        rows.row("Volume de l'adhan", { "${prefs.volume} %" }) {
            choose("Volume de l'adhan", volumes.map { "$it %" }, volumes.indexOf(prefs.volume)) {
                prefs.volume = volumes[it]; changed()
            }
        }
        for (p in Prayer.entries) {
            rows.row("${p.label}   ${p.arabic}", { PrayerDialogs.summary(this, prefs, p) }) {
                PrayerDialogs.show(this, prefs, p) { rows.refresh() }
            }
        }

        rows.header("Autorisations")
        rows.row("Afficher par-dessus les autres applis", {
            if (Permissions.canOverlay(this)) "✅ Accordée" else "❌ À activer"
        }) { Permissions.requestOverlay(this) }
        rows.row("Alarmes exactes", {
            if (Permissions.canExactAlarm(this)) "✅ Accordée" else "❌ À activer"
        }) { Permissions.requestExactAlarm(this) }
        rows.row("Aide : l'écran d'autorisation n'existe pas sur ma TV") {
            info("Activer via ADB", Permissions.adbHelp(this))
        }
    }
}
