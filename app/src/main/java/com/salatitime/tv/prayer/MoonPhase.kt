package com.salatitime.tv.prayer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Phase de la Lune calculée astronomiquement (positions écliptiques du Soleil et de la Lune,
 * principaux termes périodiques de la théorie lunaire — précision de l'ordre de 0,3°).
 */
object MoonPhase {

    data class Info(
        /** Élongation Lune–Soleil en degrés (0 = nouvelle lune, 180 = pleine lune). */
        val elongation: Double,
        /** Fraction éclairée du disque, 0..1. */
        val illumination: Double,
        /** Âge de la lunaison en jours. */
        val ageDays: Double,
        val name: String,
        val emoji: String,
        val waxing: Boolean
    )

    private const val SYNODIC = 29.530588853

    fun at(instant: Instant): Info {
        val e = elongation(instant)
        val illum = (1 - cos(Math.toRadians(e))) / 2
        val idx = (floor((e + 22.5) / 45.0).toInt()) % 8
        val names = listOf(
            "Nouvelle lune", "Premier croissant", "Premier quartier", "Lune gibbeuse croissante",
            "Pleine lune", "Lune gibbeuse décroissante", "Dernier quartier", "Dernier croissant"
        )
        val emojis = listOf("🌑", "🌒", "🌓", "🌔", "🌕", "🌖", "🌗", "🌘")
        return Info(e, illum, e / 360.0 * SYNODIC, names[idx], emojis[idx], e < 180.0)
    }

    /** Prochain événement remarquable : pleine lune ou nouvelle lune, selon le plus proche. */
    fun nextMajor(from: ZonedDateTime): Pair<String, ZonedDateTime> {
        val start = from.toInstant()
        val e0 = elongation(start)
        val (target, label) = if (e0 < 180.0) 180.0 to "Pleine lune" else 360.0 to "Nouvelle lune"
        // Recherche par pas d'une heure, puis affinage à la minute
        var t = start
        var prev = unwrap(e0, e0)
        for (h in 1..(31 * 24)) {
            val next = t.plusSeconds(3600)
            val e = unwrap(elongation(next), e0)
            if (prev < target && e >= target) {
                var lo = t; var hi = next
                repeat(12) {
                    val mid = lo.plusMillis((hi.toEpochMilli() - lo.toEpochMilli()) / 2)
                    if (unwrap(elongation(mid), e0) < target) lo = mid else hi = mid
                }
                return label to hi.atZone(from.zone)
            }
            prev = e; t = next
        }
        return label to from.plusDays(15)
    }

    /** Évite le saut 360 → 0 pendant la recherche de la nouvelle lune. */
    private fun unwrap(e: Double, e0: Double) = if (e < e0 - 90.0) e + 360.0 else e

    fun elongation(instant: Instant): Double {
        val jd = instant.toEpochMilli() / 86_400_000.0 + 2440587.5
        val d = jd - 2451545.0
        // Soleil
        val ms = fix(357.5291 + 0.98560028 * d)
        val lsMean = fix(280.459 + 0.98564736 * d)
        val lambdaSun = lsMean + 1.915 * dsin(ms) + 0.020 * dsin(2 * ms)
        // Lune (éléments moyens)
        val l0 = fix(218.316 + 13.176396 * d)
        val mm = fix(134.963 + 13.064993 * d)
        val dd = fix(l0 - lsMean) // élongation moyenne
        val lambdaMoon = l0 +
            6.289 * dsin(mm) -
            1.274 * dsin(mm - 2 * dd) +
            0.658 * dsin(2 * dd) -
            0.186 * dsin(ms) -
            0.059 * dsin(2 * mm - 2 * dd) -
            0.057 * dsin(mm - 2 * dd + ms) +
            0.053 * dsin(mm + 2 * dd) +
            0.046 * dsin(2 * dd - ms) +
            0.041 * dsin(mm - ms) -
            0.035 * dsin(dd) -
            0.031 * dsin(mm + ms) -
            0.015 * dsin(2 * fix(93.272 + 13.229350 * d) - 2 * dd) +
            0.011 * dsin(mm - 4 * dd)
        return fix(lambdaMoon - lambdaSun)
    }

    /**
     * Dessine la Lune telle qu'elle apparaît (partie éclairée exacte).
     * Hémisphère sud : l'image est inversée gauche-droite.
     */
    fun bitmap(info: Info, sizePx: Int, southern: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = sizePx / 2f - 1f
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 200, 200, 210) }
        val lit = Paint().apply { color = Color.rgb(246, 236, 200); strokeWidth = 1.2f }
        c.drawCircle(cx, cy, r, dark)
        val k = cos(Math.toRadians(info.elongation)).toFloat()
        var y = -r
        while (y <= r) {
            val w = sqrt((r * r - y * y).coerceAtLeast(0f))
            var x1: Float; var x2: Float
            if (info.waxing) { x1 = w * k; x2 = w } else { x1 = -w; x2 = -w * k }
            if (southern) { val a = -x2; x2 = -x1; x1 = a }
            if (x2 > x1) c.drawLine(cx + x1, cy + y, cx + x2, cy + y, lit)
            y += 1f
        }
        // Bord légèrement lissé
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.argb(90, 255, 255, 255)
        }
        c.drawCircle(cx, cy, r, rim)
        return bmp
    }

    fun now(zone: ZoneId = ZoneId.systemDefault()): Info = at(ZonedDateTime.now(zone).toInstant())

    private fun dsin(deg: Double) = sin(Math.toRadians(deg))
    private fun fix(a: Double): Double {
        val r = a - 360.0 * floor(a / 360.0)
        return if (r < 0) r + 360.0 else r
    }
}
