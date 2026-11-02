package com.salatitime.tv.prayer

enum class Prayer(val label: String, val arabic: String, val isSalah: Boolean) {
    FAJR("Fajr", "الفجر", true),
    SUNRISE("Chourouk", "الشروق", false),
    DHUHR("Dhuhr", "الظهر", true),
    ASR("Asr", "العصر", true),
    MAGHRIB("Maghrib", "المغرب", true),
    ISHA("Icha", "العشاء", true)
}

enum class AlertMode(val label: String, val icon: String) {
    NONE("Désactivé", "🔕"),
    NOTIFICATION("Notification", "🔔"),
    ADHAN("Appel à la prière (adhan)", "🔊")
}

/** Règle d'ajustement pour les latitudes élevées (été en Europe du Nord, etc.). */
enum class HighLatRule(val label: String) {
    ANGLE_BASED("Basée sur l'angle (recommandé)"),
    NIGHT_MIDDLE("Milieu de la nuit"),
    ONE_SEVENTH("Septième de la nuit"),
    NONE("Aucune")
}
