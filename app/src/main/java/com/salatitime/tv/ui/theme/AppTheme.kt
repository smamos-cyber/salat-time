package com.salatitime.tv.ui.theme

import android.graphics.Color

/** Palette d'un thème. Tous les thèmes sont sombres : plus lisibles et plus reposants sur une TV. */
data class AppTheme(
    val id: String,
    val name: String,
    val bgTop: Int,
    val bgBottom: Int,
    val card: Int,
    val cardFocus: Int,
    val cardNext: Int,
    val accent: Int,
    val accentSoft: Int,
    val text: Int = c("#F6F1E4"),
    val textDim: Int = c("#A9BAB7"),
) {
    companion object {
        private fun c(hex: String) = Color.parseColor(hex)

        private fun t(
            id: String, name: String, top: String, bottom: String, card: String, focus: String, next: String,
            accent: String, soft: String, text: String = "#F6F1E4", dim: String = "#B4BCC0"
        ) = AppTheme(id, name, c(top), c(bottom), c(card), c(focus), c(next), c(accent), c(soft), c(text), c(dim))

        val ALL: List<AppTheme> = listOf(
            t("emeraude", "Émeraude (d'origine)", "#06141A", "#0E2B2A", "#16303A", "#234A52", "#1F5A47", "#D9B44A", "#E9D18A", dim = "#A9BAB7"),
            t("medine", "Nuit de Médine", "#08122A", "#13284F", "#162A4D", "#21406F", "#1D4B6B", "#E3B04B", "#F0D08C"),
            t("desert", "Désert d'or", "#1E1309", "#43290F", "#3A2614", "#57391D", "#5E4317", "#F0C062", "#F7DDA0", dim = "#C9B79A"),
            t("andalousie", "Andalousie", "#22100D", "#4A1F1A", "#3E1C18", "#5B2A23", "#6A3421", "#E9B872", "#F3D6A8", dim = "#C9AFA6"),
            t("iznik", "Bleu d'Iznik", "#061528", "#0D3260", "#10294C", "#1A3F72", "#13507A", "#4FB3E8", "#9AD3F2"),
            t("ispahan", "Turquoise d'Ispahan", "#041D21", "#0B4249", "#0F353B", "#175058", "#14605D", "#3ED1C4", "#A3ECE5"),
            t("chiraz", "Rose de Chiraz", "#220C19", "#46183A", "#3A1630", "#57224A", "#64264C", "#F28DB2", "#F8C3D7", dim = "#C9AFC0"),
            t("pourpre", "Pourpre royal", "#140B24", "#2E1A52", "#2A1A46", "#3D2766", "#45306E", "#C9A0FF", "#E3CFFF", dim = "#B9AFCB"),
            t("etoiles", "Nuit étoilée", "#04060D", "#121A33", "#141B2E", "#222C48", "#252F52", "#E8E2C8", "#F5F1E2", dim = "#A3A9B8"),
            t("fajr", "Aube de Fajr", "#150F28", "#40284F", "#2E2142", "#45325E", "#5A3A5C", "#FFB38A", "#FFD6BF", dim = "#BFB2C6"),
            t("maghrib", "Coucher de Maghrib", "#200A0A", "#5A1E12", "#421A12", "#61281A", "#743217", "#FF9A4D", "#FFC79A", dim = "#CBB0A6"),
            t("oasis", "Oasis", "#0A1D12", "#1B4428", "#173621", "#235033", "#2C5E2E", "#A8D672", "#D2EDB0", dim = "#AFC0B2"),
            t("marbre", "Marbre noir et or", "#070707", "#1C1C1C", "#1E1E1E", "#2E2E2E", "#33301F", "#D4AF37", "#EBD58A", dim = "#ABABAB"),
            t("ardoise", "Ardoise et argent", "#0E1217", "#232B36", "#1F2630", "#2F3946", "#2E3C4B", "#C0C8D2", "#E2E7EC", dim = "#9EA7B2"),
            t("cedre", "Cèdre", "#0C1A14", "#1E3A2E", "#1A3027", "#284638", "#2E4E36", "#D9C08A", "#ECDDB8"),
            t("fes", "Indigo de Fès", "#080C24", "#1A2466", "#161F52", "#232F73", "#26357A", "#F2C14E", "#F8DD95", dim = "#ABB0CC"),
            t("kairouan", "Bordeaux de Kairouan", "#1C080E", "#43121F", "#38111C", "#521B2A", "#5E2027", "#E7C27A", "#F2DDAF", dim = "#C6AAB1"),
            t("djenne", "Ocre de Djenné", "#1F150E", "#45301F", "#3A291B", "#553C28", "#5C4022", "#D98E48", "#EDC093", dim = "#C4B4A3"),
            t("samarcande", "Jade de Samarcande", "#051914", "#0D3A33", "#0F302A", "#18473F", "#145444", "#6FE3B0", "#B5F0D6"),
            t("lapis", "Lapis-lazuli", "#08143A", "#163273", "#142A5E", "#1F3D80", "#1F4485", "#FFD36E", "#FFE7AE", dim = "#AAB4D0"),
            t("cuivre", "Cuivre", "#170F0B", "#3A2418", "#30201A", "#473026", "#523322", "#E08D5B", "#F0BE9C", dim = "#C2B0A6"),
        )

        val DEFAULT: AppTheme get() = ALL.first()

        fun byId(id: String?): AppTheme = ALL.firstOrNull { it.id == id } ?: DEFAULT
    }
}
