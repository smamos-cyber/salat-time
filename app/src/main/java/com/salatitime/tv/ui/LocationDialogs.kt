package com.salatitime.tv.ui

import android.text.InputType
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.salatitime.tv.alarm.PrayerScheduler
import com.salatitime.tv.data.GeoPlace
import com.salatitime.tv.data.LocationRepository
import com.salatitime.tv.data.Prefs
import kotlinx.coroutines.launch

object LocationDialogs {

    fun show(a: AppCompatActivity, prefs: Prefs, onDone: () -> Unit) {
        val items = listOf(
            "📡  Détecter automatiquement (via la connexion internet)",
            "🔎  Rechercher une ville",
            "✏️  Saisir latitude / longitude"
        )
        a.choose("Localisation actuelle : ${prefs.placeLabel()}", items) { which ->
            when (which) {
                0 -> autoDetect(a, prefs, onDone)
                1 -> searchCity(a, prefs, onDone)
                2 -> manual(a, prefs, onDone)
            }
        }
    }

    fun autoDetect(a: AppCompatActivity, prefs: Prefs, onDone: () -> Unit) {
        val dlg = a.progressDialog("Détection de votre position…")
        a.lifecycleScope.launch {
            try {
                val place = LocationRepository.detectByIp()
                save(a, prefs, place, auto = true, onDone)
            } catch (e: Exception) {
                a.info("Échec de la détection", "Vérifiez la connexion internet ou recherchez votre ville.\n\n${e.message ?: ""}")
            } finally {
                dlg.dismiss()
            }
        }
    }

    /** Mise à jour quotidienne sans fenêtre (utile si la TV déménage / change de réseau). */
    fun autoDetectSilently(a: AppCompatActivity, prefs: Prefs, onDone: () -> Unit) {
        a.lifecycleScope.launch {
            runCatching { LocationRepository.detectByIp() }.onSuccess { place ->
                val moved = prefs.latitude?.let { kotlin.math.abs(it - place.lat) > 0.05 } ?: true ||
                    prefs.longitude?.let { kotlin.math.abs(it - place.lng) > 0.05 } ?: true
                if (moved) save(a, prefs, place, auto = true, onDone)
            }
        }
    }

    private fun searchCity(a: AppCompatActivity, prefs: Prefs, onDone: () -> Unit) {
        a.askText("Rechercher une ville", "ex : Marseille, France") { query ->
            val dlg = a.progressDialog("Recherche de « $query »…")
            a.lifecycleScope.launch {
                try {
                    val results = LocationRepository.search(query)
                    if (results.isEmpty()) {
                        a.info("Aucun résultat", "Essayez avec « ville, pays ».")
                    } else {
                        a.choose("Choisissez le lieu", results.map { it.name }) {
                            save(a, prefs, results[it], auto = false, onDone)
                        }
                    }
                } catch (e: Exception) {
                    a.info("Erreur", e.message ?: "Recherche impossible")
                } finally {
                    dlg.dismiss()
                }
            }
        }
    }

    private fun manual(a: AppCompatActivity, prefs: Prefs, onDone: () -> Unit) {
        val numType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        a.askText("Latitude", "ex : 48.8566", prefs.latitude?.toString() ?: "", numType) { latStr ->
            a.askText("Longitude", "ex : 2.3522", prefs.longitude?.toString() ?: "", numType) { lngStr ->
                val lat = latStr.replace(',', '.').toDoubleOrNull()
                val lng = lngStr.replace(',', '.').toDoubleOrNull()
                if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
                    a.info("Coordonnées invalides", "Latitude entre -90 et 90, longitude entre -180 et 180.")
                    return@askText
                }
                a.askText("Nom du lieu", "ex : Mosquée de mon quartier", prefs.placeName.ifBlank { "Mon lieu" }) { name ->
                    save(a, prefs, GeoPlace(name, lat, lng), auto = false, onDone)
                }
            }
        }
    }

    private fun save(a: AppCompatActivity, prefs: Prefs, place: GeoPlace, auto: Boolean, onDone: () -> Unit) {
        prefs.setPlace(place, auto)
        PrayerScheduler.schedule(a)
        onDone()
    }
}
