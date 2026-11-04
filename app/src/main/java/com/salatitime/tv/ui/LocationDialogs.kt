package com.salatitime.tv.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.text.InputType
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.salatitime.tv.alarm.PrayerScheduler
import com.salatitime.tv.data.GeoPlace
import com.salatitime.tv.data.LocationRepository
import com.salatitime.tv.data.Prefs
import kotlinx.coroutines.launch

object LocationDialogs {

    fun show(a: AppCompatActivity, prefs: Prefs, onDone: () -> Unit) {
        val hasGps = a.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)
        val items = mutableListOf<Pair<String, () -> Unit>>()
        if (hasGps) items += "🛰  Position du téléphone (GPS)" to { gps(a, prefs, onDone) }
        items += "📡  Détecter automatiquement (via la connexion internet)" to { autoDetect(a, prefs, onDone) }
        items += "🔎  Rechercher une ville" to { searchCity(a, prefs, onDone) }
        items += "✏️  Saisir latitude / longitude" to { manual(a, prefs, onDone) }
        a.choose("Localisation actuelle : ${prefs.placeLabel()}", items.map { it.first }) { items[it].second() }
    }

    // ---------- GPS (téléphone, tablette) ----------

    private const val REQ_LOCATION = 4711
    private var pending: (() -> Unit)? = null

    fun gps(a: AppCompatActivity, prefs: Prefs, onDone: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(a, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            pending = { gps(a, prefs, onDone) }
            ActivityCompat.requestPermissions(
                a, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), REQ_LOCATION
            )
            return
        }
        val dlg = a.progressDialog("Recherche de votre position…")
        a.lifecycleScope.launch {
            try {
                val loc = currentLocation(a) ?: throw IllegalStateException(
                    "Position indisponible. Activez la localisation du téléphone, puis réessayez."
                )
                val name = withContext(Dispatchers.IO) { cityName(a, loc.latitude, loc.longitude) }
                save(a, prefs, GeoPlace(name, loc.latitude, loc.longitude), auto = false, onDone)
            } catch (e: Exception) {
                a.info("Échec de la localisation", e.message ?: "Erreur inconnue")
            } finally {
                dlg.dismiss()
            }
        }
    }

    /** À appeler depuis onRequestPermissionsResult des écrans qui affichent ce dialogue. */
    fun onPermissionResult(a: AppCompatActivity, requestCode: Int, grantResults: IntArray) {
        if (requestCode != REQ_LOCATION) return
        val action = pending
        pending = null
        if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) action?.invoke()
        else a.info("Localisation refusée", "Vous pouvez rechercher votre ville à la place.")
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(a: AppCompatActivity): Location? {
        val lm = a.getSystemService(LocationManager::class.java) ?: return null
        val providers = lm.getProviders(true)
        // Position récente déjà connue (moins d'une heure)
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 3_600_000L }
            .maxByOrNull { it.time }?.let { return it }
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstOrNull { it in providers } ?: return null
        return withTimeoutOrNull(20_000L) {
            suspendCancellableCoroutine { cont ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    lm.getCurrentLocation(provider, null, a.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc) }
                } else {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, { loc -> if (cont.isActive) cont.resume(loc) }, Looper.getMainLooper())
                }
            }
        }
    }

    private fun cityName(a: AppCompatActivity, lat: Double, lng: Double): String {
        val fallback = "Position du téléphone"
        if (!Geocoder.isPresent()) return fallback
        return runCatching {
            @Suppress("DEPRECATION")
            val ad = Geocoder(a, Locale.FRANCE).getFromLocation(lat, lng, 1)?.firstOrNull()
            listOfNotNull(ad?.locality ?: ad?.subAdminArea, ad?.countryName).joinToString(", ").ifBlank { fallback }
        }.getOrDefault(fallback)
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
