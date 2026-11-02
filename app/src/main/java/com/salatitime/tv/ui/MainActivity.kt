package com.salatitime.tv.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.salatitime.tv.R
import com.salatitime.tv.alarm.AlertService
import com.salatitime.tv.alarm.PrayerScheduler
import com.salatitime.tv.data.Prefs
import com.salatitime.tv.prayer.AlertMode
import com.salatitime.tv.prayer.Prayer
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private val cards = LinkedHashMap<Prayer, View>()
    private var times: Map<Prayer, ZonedDateTime> = emptyMap()
    private var timesDate: LocalDate? = null
    private var highlighted: Prayer? = null

    private lateinit var clock: TextView
    private lateinit var dateTv: TextView
    private lateinit var hijriTv: TextView
    private lateinit var cityTv: TextView
    private lateinit var nextTv: TextView
    private lateinit var countdownTv: TextView

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        clock = findViewById(R.id.clock)
        dateTv = findViewById(R.id.date)
        hijriTv = findViewById(R.id.hijri)
        cityTv = findViewById(R.id.city)
        nextTv = findViewById(R.id.nextPrayer)
        countdownTv = findViewById(R.id.countdown)

        val row = findViewById<LinearLayout>(R.id.prayerRow)
        for (p in Prayer.entries) {
            val card = LayoutInflater.from(this).inflate(R.layout.item_prayer, row, false)
            card.findViewById<TextView>(R.id.prayerArabic).text = p.arabic
            card.findViewById<TextView>(R.id.prayerName).text = p.label
            card.setOnClickListener { PrayerDialogs.show(this, prefs, p) { refreshTimes() } }
            row.addView(card)
            cards[p] = card
        }

        findViewById<View>(R.id.btnLocation).setOnClickListener { LocationDialogs.show(this, prefs) { refreshTimes() } }
        findViewById<View>(R.id.btnSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.btnAdhans).setOnClickListener { startActivity(Intent(this, AdhanActivity::class.java)) }
        findViewById<View>(R.id.btnTest).setOnClickListener { showTestMenu() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        cards[Prayer.FAJR]?.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        refreshTimes()
        handler.post(ticker)
        maybeAutoLocate()
        maybeShowOverlayHint()
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun maybeAutoLocate() {
        val today = LocalDate.now().toEpochDay()
        if (!prefs.hasLocation() || (prefs.autoLocation && prefs.lastAutoLocationDay != today)) {
            if (!prefs.hasLocation()) {
                LocationDialogs.autoDetect(this, prefs) { refreshTimes() }
            } else {
                // Mise à jour silencieuse une fois par jour
                prefs.lastAutoLocationDay = today
                LocationDialogs.autoDetectSilently(this, prefs) { refreshTimes() }
            }
        }
    }

    private fun maybeShowOverlayHint() {
        if (Permissions.canOverlay(this) || prefs.overlayHintShown) return
        prefs.overlayHintShown = true
        AlertDialog.Builder(this)
            .setTitle("Afficher les alertes sur tous les écrans")
            .setMessage(
                "Pour que l'heure de la prière s'affiche même quand vous regardez une chaîne, " +
                    "Netflix ou YouTube, autorisez Salati Time à « s'afficher par-dessus les autres applications »."
            )
            .setPositiveButton("Autoriser") { _, _ -> Permissions.requestOverlay(this) }
            .setNegativeButton("Plus tard", null)
            .show()
    }

    private fun refreshTimes() {
        val today = LocalDate.now()
        times = prefs.timesFor(today) ?: emptyMap()
        timesDate = today
        cityTv.text = "📍 " + prefs.placeLabel()
        dateTv.text = Format.date(today)
        hijriTv.text = Format.hijri(today)
        for ((p, card) in cards) {
            card.findViewById<TextView>(R.id.prayerTime).text = times[p]?.let { Format.hm(it) } ?: "--:--"
            val mode = prefs.alertMode(p)
            card.findViewById<TextView>(R.id.prayerMode).text =
                mode.icon + if (prefs.offset(p) != 0) "  " + Format.offset(prefs.offset(p)) else ""
        }
        highlighted = null
        PrayerScheduler.schedule(this)
        tick()
    }

    private fun tick() {
        val now = ZonedDateTime.now()
        clock.text = Format.hms(now)
        if (timesDate != now.toLocalDate()) {
            refreshTimes(); return
        }
        if (times.isEmpty()) {
            nextTv.text = "Choisissez votre localisation pour afficher les horaires"
            countdownTv.text = ""
            return
        }
        var next = times.entries.firstOrNull { it.value.isAfter(now) }?.toPair()
        if (next == null) {
            // Après Icha : prochaine prière = Fajr de demain
            val fajr = prefs.timesFor(now.toLocalDate().plusDays(1))?.get(Prayer.FAJR)
            if (fajr != null) next = Prayer.FAJR to fajr
        }
        if (next == null) return
        val (p, t) = next
        nextTv.text = "Prochaine : ${p.label}  ${p.arabic}  à ${Format.hm(t)}"
        countdownTv.text = "dans " + Format.countdown(Duration.between(now, t).seconds + 1)

        val highlight = if (t.toLocalDate() == now.toLocalDate()) p else null
        if (highlight != highlighted) {
            highlighted = highlight
            for ((prayer, card) in cards) {
                card.setBackgroundResource(if (prayer == highlight) R.drawable.bg_card_next else R.drawable.bg_card)
            }
        }
    }

    private fun showTestMenu() {
        val target = Prayer.DHUHR
        val items = listOf(
            "🔔  Tester la notification par-dessus l'écran",
            "🔊  Tester l'appel à la prière (adhan)",
            "■  Arrêter l'alerte en cours"
        )
        choose("Tester les alertes", items) {
            when (it) {
                0 -> AlertService.start(this, target, false, System.currentTimeMillis(), AlertMode.NOTIFICATION)
                1 -> AlertService.start(this, target, false, System.currentTimeMillis(), AlertMode.ADHAN)
                2 -> AlertService.stop(this)
            }
            if (it < 2) moveTaskToBack(true) // pour voir l'alerte par-dessus l'écran d'accueil
        }
    }
}
