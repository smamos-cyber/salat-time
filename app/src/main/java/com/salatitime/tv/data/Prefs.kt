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

enum class WakeMode(val label: String) {
    OFF("Non"),
    ADHAN("Oui, pour l'adhan"),
    ALL("Oui, pour l'adhan et les notifications")
}

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

    /** Allumer l'écran de la TV s'il est en veille à l'heure de la prière. */
    var wakeMode: WakeMode
        get() = enumOr(sp.getString("wakeMode", null), WakeMode.ADHAN)
        set(v) = sp.edit { putString("wakeMode", v.name) }

    // ---------- Blocage de la TV pendant la prière ----------
    /** Durée du blocage après la prière, en minutes (0 = désactivé). */
    fun lockMinutes(p: Prayer): Int = sp.getInt("lock_${p.name}", 0)
    fun setLockMinutes(p: Prayer, minutes: Int) = sp.edit { putInt("lock_${p.name}", minutes) }

    /** Heure de fin du blocage en cours (0 = aucun). */
    var lockUntil: Long
        get() = sp.getLong("lockUntil", 0L)
        set(v) = sp.edit(commit = true) { putLong("lockUntil", v) }
    var lockDurationMin: Int
        get() = sp.getInt("lockDuration", 120)
        set(v) = sp.edit(commit = true) { putInt("lockDuration", v) }
    var lockPrayer: String
        get() = sp.getString("lockPrayer", "") ?: ""
        set(v) = sp.edit { putString("lockPrayer", v) }
    var lockMuted: Boolean
        get() = sp.getBoolean("lockMuted", false)
        set(v) = sp.edit(commit = true) { putBoolean("lockMuted", v) }

    /** Code de déblocage d'urgence ("" = aucun, le blocage ne peut pas être levé avant la fin). */
    var unlockCode: String
        get() = sp.getString("unlockCode", "") ?: ""
        set(v) = sp.edit { putString("unlockCode", v) }

    /** Hadith affiché pendant le blocage en cours (index dans assets/hadiths.json). */
    var lockHadith: Int
        get() = sp.getInt("lockHadith", -1)
        set(v) = sp.edit(commit = true) { putInt("lockHadith", v) }
    var showHadith: Boolean
        get() = sp.getBoolean("showHadith", true)
        set(v) = sp.edit { putBoolean("showHadith", v) }

    // ---------- Apparence ----------
    var themeId: String
        get() = sp.getString("theme", "emeraude") ?: "emeraude"
        set(v) = sp.edit { putString("theme", v) }

    /** Fond d'écran : "" = dégradé du thème, "random" = change chaque jour, sinon identifiant de l'image. */
    var wallpaperId: String
        get() = sp.getString("wallpaper", "random") ?: "random"
        set(v) = sp.edit { putString("wallpaper", v) }

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
