package com.salatitime.tv.data

import android.content.Context
import androidx.core.content.edit
import com.salatitime.tv.prayer.AlertMode
import com.salatitime.tv.prayer.CalcMethod
import com.salatitime.tv.prayer.HighLatRule
import com.salatitime.tv.prayer.PrayTimes
import com.salatitime.tv.prayer.Prayer
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("salati", Context.MODE_PRIVATE)

    // ---------- Localisation ----------
    val latitude: Double? get() = sp.getString("lat", null)?.toDoubleOrNull()
    val longitude: Double? get() = sp.getString("lng", null)?.toDoubleOrNull()
    val placeName: String get() = sp.getString("place", "") ?: ""
    val autoLocation: Boolean get() = sp.getBoolean("autoLoc", true)
    var lastAutoLocationDay: Long
        get() = sp.getLong("autoLocDay", -1L)
        set(v) = sp.edit { putLong("autoLocDay", v) }

    fun hasLocation() = latitude != null && longitude != null

    fun setPlace(place: GeoPlace, auto: Boolean) = sp.edit {
        putString("lat", place.lat.toString())
        putString("lng", place.lng.toString())
        putString("place", place.name)
        putBoolean("autoLoc", auto)
        if (auto) putLong("autoLocDay", LocalDate.now().toEpochDay())
    }

    fun placeLabel(): String {
        if (!hasLocation()) return "Non définie"
        val name = placeName.ifBlank { "%.4f, %.4f".format(latitude, longitude) }
        return name + if (autoLocation) "  (auto)" else ""
    }

    // ---------- Calcul ----------
    var method: CalcMethod
        get() = enumOr(sp.getString("method", null), CalcMethod.MWL)
        set(v) = sp.edit { putString("method", v.name) }

    var asrHanafi: Boolean
        get() = sp.getBoolean("asrHanafi", false)
        set(v) = sp.edit { putBoolean("asrHanafi", v) }

    var highLat: HighLatRule
        get() = enumOr(sp.getString("highLat", null), HighLatRule.ANGLE_BASED)
        set(v) = sp.edit { putString("highLat", v.name) }

    fun offset(p: Prayer): Int = sp.getInt("offset_${p.name}", 0)
    fun setOffset(p: Prayer, minutes: Int) = sp.edit { putInt("offset_${p.name}", minutes) }

    // ---------- Alertes ----------
    fun alertMode(p: Prayer): AlertMode = enumOr(
        sp.getString("mode_${p.name}", null),
        if (p == Prayer.SUNRISE) AlertMode.NONE else AlertMode.NOTIFICATION
    )
    fun setAlertMode(p: Prayer, m: AlertMode) = sp.edit { putString("mode_${p.name}", m.name) }

    var preAlertMinutes: Int
        get() = sp.getInt("preAlert", 0)
        set(v) = sp.edit { putInt("preAlert", v) }

    var volume: Int
        get() = sp.getInt("volume", 80)
        set(v) = sp.edit { putInt("volume", v) }

    /** Son par défaut pour toutes les prières ("" = son système). */
    var defaultAdhanId: String
        get() = sp.getString("adhanDefault", "") ?: ""
        set(v) = sp.edit { putString("adhanDefault", v) }

    /** Son spécifique à une prière ("" = utiliser le son par défaut). */
    fun adhanOverride(p: Prayer): String = sp.getString("adhan_${p.name}", "") ?: ""
    fun setAdhanOverride(p: Prayer, id: String) = sp.edit { putString("adhan_${p.name}", id) }
    fun soundIdFor(p: Prayer): String = adhanOverride(p).ifEmpty { defaultAdhanId }

    var overlayHintShown: Boolean
        get() = sp.getBoolean("overlayHint", false)
        set(v) = sp.edit { putBoolean("overlayHint", v) }

    // ---------- Horaires ----------
    fun calculator() = PrayTimes(
        method = method,
        asrFactor = if (asrHanafi) 2.0 else 1.0,
        highLat = highLat,
        offsets = Prayer.entries.associateWith { offset(it) }
    )

    fun timesFor(date: LocalDate): Map<Prayer, ZonedDateTime>? {
        val lat = latitude ?: return null
        val lng = longitude ?: return null
        return calculator().compute(date, lat, lng, ZoneId.systemDefault())
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, def: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: def
}
