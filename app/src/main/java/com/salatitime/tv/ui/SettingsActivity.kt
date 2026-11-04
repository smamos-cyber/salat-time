package com.salatitime.tv.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.salatitime.tv.R
import com.salatitime.tv.alarm.AlarmReceiver
import com.salatitime.tv.alarm.AlertService
import com.salatitime.tv.alarm.PrayerScheduler
import com.salatitime.tv.data.Prefs
import com.salatitime.tv.data.WakeMode
import com.salatitime.tv.lock.LockManager
import com.salatitime.tv.prayer.AlertMode
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
        rows.row("📺  Allumer la TV pour la prière", { prefs.wakeMode.label }) {
            choose("Allumer la TV si elle est en veille", WakeMode.entries.map { it.label }, prefs.wakeMode.ordinal) {
                prefs.wakeMode = WakeMode.entries[it]; changed()
            }
        }
        rows.row("🧪  Tester le réveil de la TV", { "adhan dans 1 minute" }) { scheduleWakeTest() }
        rows.header("Blocage de la TV pendant la prière")
        rows.row("🔒  Durée du blocage", { lockSummary() }) {
            info(
                "Durée du blocage",
                "Choisissez la durée pour chaque prière dans la liste des prières ci-dessous " +
                    "(ligne « Bloquer la TV dès l'adhan »), de 10 min à 2 h.\n\n" +
                    "Le blocage commence dès le début de l'adhan et reprend même si la TV est éteinte, " +
                    "redémarrée ou débranchée."
            )
        }
        rows.row("🔑  Code de déblocage d'urgence", {
            if (prefs.unlockCode.isEmpty()) "Aucun (déblocage impossible avant la fin)" else "Défini (${prefs.unlockCode.length} chiffres)"
        }) {
            askText("Code de déblocage (4 à 8 chiffres, vide = aucun)", "1234", inputType = PIN_INPUT) { code ->
                val c = code.trim()
                if (c.isEmpty() || c.matches(Regex("\\d{4,8}"))) {
                    prefs.unlockCode = c; rows.refresh()
                } else info("Code invalide", "Le code doit contenir de 4 à 8 chiffres.")
            }
        }
        rows.row("🧪  Tester le blocage", { "1 minute" }) {
            LockManager.start(this, Prayer.DHUHR, 1)
        }
        rows.header("Prières")
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
        rows.row("Aide : la TV ne s'allume pas pour l'adhan") {
            info("Allumer la TV pour l'adhan", WAKE_HELP)
        }
        rows.row("Aide : l'écran d'autorisation n'existe pas sur ma TV") {
            info("Activer via ADB", Permissions.adbHelp(this))
        }
    }

    /** Programme un adhan d'essai dans 1 minute, avec réveil forcé de la TV. */
    private fun scheduleWakeTest() {
        val am = getSystemService(AlarmManager::class.java) ?: return
        val time = System.currentTimeMillis() + 60_000L
        val intent = Intent(this, AlarmReceiver::class.java)
            .setAction(ACTION_WAKE_TEST)
            .putExtra(AlertService.EXTRA_PRAYER, Prayer.DHUHR.name)
            .putExtra(AlertService.EXTRA_PRE, false)
            .putExtra(AlertService.EXTRA_TIME, time)
            .putExtra(AlertService.EXTRA_FORCE_MODE, AlertMode.ADHAN.name)
            .putExtra(AlertService.EXTRA_FORCE_WAKE, true)
        val pi = PendingIntent.getBroadcast(
            this, 200, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            val show = PendingIntent.getActivity(
                this, 201, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(time, show), pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
        }
        info(
            "Test programmé",
            "Éteignez maintenant la TV avec la télécommande (mise en veille, sans la débrancher).\n\n" +
                "Dans 1 minute, elle devrait se rallumer et jouer l'adhan."
        )
    }

    private fun lockSummary(): String {
        val set = Prayer.entries.filter { it.isSalah && prefs.lockMinutes(it) > 0 }
        if (set.isEmpty()) return "Désactivé"
        return set.joinToString(" · ") { "${it.label} ${LockManager.durationLabel(prefs.lockMinutes(it))}" }
    }

    private companion object {
        val PIN_INPUT = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        const val ACTION_WAKE_TEST = "com.salatitime.tv.WAKE_TEST"
        const val WAKE_HELP =
            "Une application ne peut rallumer la TV que si elle est en veille, pas si elle est débranchée.\n\n" +
                "1. Activez le « Démarrage rapide » (Sony, Philips : Paramètres > Système > Alimentation ; " +
                "TCL, Hisense : « Démarrage instantané » ou « Veille rapide »).\n" +
                "2. Désactivez le mode « Éco » ou « Veille à faible consommation » s'il existe.\n" +
                "3. Vérifiez que l'autorisation « Afficher par-dessus les autres applis » est accordée.\n" +
                "4. Utilisez « Tester le réveil de la TV » pour vérifier.\n\n" +
                "Certains modèles bloquent malgré tout le réveil par une application."
    }
}
