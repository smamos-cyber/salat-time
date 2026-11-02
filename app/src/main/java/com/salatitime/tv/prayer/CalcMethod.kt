package com.salatitime.tv.prayer

/**
 * Méthodes de calcul usuelles.
 * fajrAngle / ishaAngle : angle du soleil sous l'horizon (degrés).
 * ishaMinutes : si renseigné, Icha = Maghrib + n minutes (méthodes Oumm al-Qura, Golfe).
 */
enum class CalcMethod(
    val label: String,
    val fajrAngle: Double,
    val ishaAngle: Double? = null,
    val ishaMinutes: Int? = null
) {
    MWL("Ligue islamique mondiale (18° / 17°)", 18.0, 17.0),
    UOIF("UOIF – France (12° / 12°)", 12.0, 12.0),
    FRANCE_15("France 15° / 15°", 15.0, 15.0),
    FRANCE_18("France 18° / 18°", 18.0, 18.0),
    ISNA("ISNA – Amérique du Nord (15° / 15°)", 15.0, 15.0),
    EGYPT("Égypte (19,5° / 17,5°)", 19.5, 17.5),
    MAKKAH("Oumm al-Qura – La Mecque (18,5° / 90 min)", 18.5, null, 90),
    KARACHI("Karachi (18° / 18°)", 18.0, 18.0),
    MOROCCO("Maroc (19° / 17°)", 19.0, 17.0),
    ALGERIA("Algérie (18° / 17°)", 18.0, 17.0),
    TUNISIA("Tunisie (18° / 18°)", 18.0, 18.0),
    TURKEY("Turquie – Diyanet (18° / 17°)", 18.0, 17.0),
    GULF("Golfe (19,5° / 90 min)", 19.5, null, 90),
    SINGAPORE("Singapour / Malaisie (20° / 18°)", 20.0, 18.0)
}
