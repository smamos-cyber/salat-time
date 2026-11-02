package com.salatitime.tv.prayer

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Calcul astronomique hors-ligne des horaires de prière
 * (adaptation Kotlin de l'algorithme PrayTimes.org).
 */
class PrayTimes(
    private val method: CalcMethod,
    /** 1.0 = Asr standard (chafiite, malikite, hanbalite), 2.0 = hanafite */
    private val asrFactor: Double = 1.0,
    private val highLat: HighLatRule = HighLatRule.ANGLE_BASED,
    /** Ajustements manuels en minutes par prière */
    private val offsets: Map<Prayer, Int> = emptyMap()
) {

    fun compute(date: LocalDate, lat: Double, lng: Double, zone: ZoneId): Map<Prayer, ZonedDateTime> {
        val tz = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
        val jDate = julian(date.year, date.monthValue, date.dayOfMonth) - lng / (15.0 * 24.0)

        fun sunPos(t: Double) = sunPosition(jDate + t / 24.0)
        fun midDay(t: Double) = fixHour(12.0 - sunPos(t).second)
        fun sunAngleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sunPos(t).first
            val noon = midDay(t)
            val x = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
            if (x < -1.0 || x > 1.0) return Double.NaN
            val d = darccos(x) / 15.0
            return noon + if (ccw) -d else d
        }
        fun asrTime(t: Double): Double {
            val decl = sunPos(t).first
            val angle = -darccot(asrFactor + dtan(abs(lat - decl)))
            return sunAngleTime(angle, t, false)
        }

        var fajr = sunAngleTime(method.fajrAngle, 5.0, true)
        var sunrise = sunAngleTime(RISE_SET_ANGLE, 6.0, true)
        var dhuhr = midDay(12.0)
        var asr = asrTime(13.0)
        var sunset = sunAngleTime(RISE_SET_ANGLE, 18.0, false)
        var isha = method.ishaAngle?.let { sunAngleTime(it, 18.0, false) } ?: Double.NaN

        // Passage de l'heure solaire locale à l'heure du fuseau
        val adj = tz - lng / 15.0
        fajr += adj; sunrise += adj; dhuhr += adj; asr += adj; sunset += adj; isha += adj
        val maghrib = sunset

        // Latitudes élevées : quand le soleil ne descend pas assez bas
        if (highLat != HighLatRule.NONE && !sunrise.isNaN() && !sunset.isNaN()) {
            val night = timeDiff(sunset, sunrise)
            val fajrPortion = nightPortion(method.fajrAngle, night)
            if (fajr.isNaN() || timeDiff(fajr, sunrise) > fajrPortion) fajr = sunrise - fajrPortion
            method.ishaAngle?.let { angle ->
                val p = nightPortion(angle, night)
                if (isha.isNaN() || timeDiff(sunset, isha) > p) isha = sunset + p
            }
        }
        method.ishaMinutes?.let { isha = maghrib + it / 60.0 }

        val raw = mapOf(
            Prayer.FAJR to fajr,
            Prayer.SUNRISE to sunrise,
            Prayer.DHUHR to dhuhr,
            Prayer.ASR to asr,
            Prayer.MAGHRIB to maghrib,
            Prayer.ISHA to isha
        )
        val result = LinkedHashMap<Prayer, ZonedDateTime>()
        for ((p, h) in raw) {
            if (h.isNaN()) continue
            val minutes = (h * 60.0).roundToLong() + (offsets[p] ?: 0)
            result[p] = date.atStartOfDay().plusMinutes(minutes).atZone(zone)
        }
        return result
    }

    private fun nightPortion(angle: Double, night: Double): Double = when (highLat) {
        HighLatRule.NIGHT_MIDDLE -> night / 2.0
        HighLatRule.ONE_SEVENTH -> night / 7.0
        else -> angle / 60.0 * night
    }

    /** Retourne (déclinaison, équation du temps) */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = darcsin(dsin(e) * dsin(l))
        return decl to eqt
    }

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private companion object {
        const val RISE_SET_ANGLE = 0.833

        fun dsin(d: Double) = sin(Math.toRadians(d))
        fun dcos(d: Double) = cos(Math.toRadians(d))
        fun dtan(d: Double) = tan(Math.toRadians(d))
        fun darcsin(x: Double) = Math.toDegrees(asin(x))
        fun darccos(x: Double) = Math.toDegrees(acos(x))
        fun darctan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
        fun darccot(x: Double) = Math.toDegrees(atan(1.0 / x))
        fun fixAngle(a: Double) = fix(a, 360.0)
        fun fixHour(a: Double) = fix(a, 24.0)
        fun fix(a: Double, b: Double): Double {
            val r = a - b * floor(a / b)
            return if (r < 0) r + b else r
        }
        fun timeDiff(t1: Double, t2: Double) = fixHour(t2 - t1)
    }
}
