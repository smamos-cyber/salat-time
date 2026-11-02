package com.salatitime.tv.ui

import android.icu.util.IslamicCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val HM = DateTimeFormatter.ofPattern("HH:mm")
    private val HMS = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)

    private val HIJRI_MONTHS = listOf(
        "Mouharram", "Safar", "Rabi' al-awwal", "Rabi' ath-thani", "Joumada al-oula", "Joumada ath-thania",
        "Rajab", "Cha'bane", "Ramadan", "Chawwal", "Dhou al-qi'da", "Dhou al-hijja"
    )

    fun time(millis: Long): String = HM.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    fun hm(t: java.time.temporal.TemporalAccessor): String = HM.format(t)
    fun hms(t: java.time.temporal.TemporalAccessor): String = HMS.format(t)
    fun date(d: LocalDate): String = DATE.format(d).replaceFirstChar { it.uppercase() }

    fun hijri(d: LocalDate): String = try {
        val cal = IslamicCalendar()
        cal.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA)
        cal.timeInMillis = d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val day = cal.get(android.icu.util.Calendar.DAY_OF_MONTH)
        val month = HIJRI_MONTHS.getOrElse(cal.get(android.icu.util.Calendar.MONTH)) { "" }
        val year = cal.get(android.icu.util.Calendar.YEAR)
        "$day $month $year H"
    } catch (e: Exception) {
        ""
    }

    fun countdown(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    fun offset(min: Int): String = when {
        min > 0 -> "+$min min"
        min < 0 -> "$min min"
        else -> "aucun"
    }
}
