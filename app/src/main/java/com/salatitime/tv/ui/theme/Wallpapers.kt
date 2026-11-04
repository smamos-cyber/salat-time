package com.salatitime.tv.ui.theme

/** Fond d'écran : photo libre de droits (Wikimedia Commons), téléchargée à la compilation. */
data class Wallpaper(val id: String, val name: String, val credit: String) {
    val resName: String get() = "wp_$id"
}

/** Généré à partir de tools/wallpapers.json. */
object Wallpapers {
    const val RANDOM = "random"

    private val LIST = listOf(
        Wallpaper("al_aqsa", "Mosquée al-Aqsa, Jérusalem", "Godot13 — CC BY-SA 4.0"),
        Wallpaper("dome_rocher", "Dôme du Rocher, Jérusalem", "Godot13 — CC BY-SA 4.0"),
        Wallpaper("dome_rocher_faience", "Faïences du Dôme du Rocher, Jérusalem", "Godot13 — CC BY-SA 4.0"),
        Wallpaper("mosquee_bleue_cour", "Mosquée bleue au crépuscule, Istanbul", "Benh LIEU SONG (Flickr) — CC BY-SA 3.0"),
        Wallpaper("mosquee_bleue", "Mosquée bleue, Istanbul", "Moonik — CC BY-SA 3.0"),
        Wallpaper("suleymaniye_coupole", "Coupole de la Süleymaniye, Istanbul", "Arild Vågen — CC BY-SA 3.0"),
        Wallpaper("suleymaniye_calligraphie", "Calligraphie en céramique, Süleymaniye, Istanbul", "Moonik — CC BY-SA 3.0"),
        Wallpaper("selimiye", "Mosquée Selimiye, Edirne", "BuhaM — CC BY-SA 4.0"),
        Wallpaper("hassan2", "Mosquée Hassan II au lever du soleil, Casablanca", "Milamber's portfolio — CC BY 2.0"),
        Wallpaper("alhambra", "L'Alhambra le soir, Grenade", "Jebulon — Public domain"),
        Wallpaper("cordoue", "Mosquée de Cordoue", "CEphoto, Uwe Aranas — CC BY-SA 3.0"),
        Wallpaper("fes_bab_boujeloud", "Bab Bou Jeloud, Fès", "Timothy A. Gonsalves — CC BY-SA 4.0"),
        Wallpaper("zellige_marrakech", "Zellige de la médersa Ben Youssef, Marrakech", "Elena Tatiana Chis — CC BY-SA 4.0"),
        Wallpaper("ispahan", "Mosquée de l'Imam, Ispahan", "Diego Delso — CC BY-SA 4.0"),
        Wallpaper("nasir_vitraux", "Vitraux de la mosquée Nasir al-Molk, Chiraz", "Diego Delso — CC BY-SA 4.0"),
        Wallpaper("nasir_plafond", "Voûte de la mosquée Nasir al-Molk, Chiraz", "Diego Delso — CC BY-SA 4.0"),
        Wallpaper("yazd", "Coupole de la mosquée Jameh, Yazd", "Mohammad Reza Domiri Ganji — CC BY-SA 4.0"),
        Wallpaper("badshahi", "Mosquée Badshahi, Lahore", "Ali Imran — CC BY-SA 2.5"),
        Wallpaper("wazir_khan", "Mosquée Wazir Khan, Lahore", "Taahaa Khalid — CC BY-SA 4.0"),
        Wallpaper("kairouan", "Grande Mosquée de Kairouan", "IssamBarhoumi — CC BY-SA 4.0"),
        Wallpaper("djenne", "Grande Mosquée de Djenné", "BluesyPete — CC BY-SA 3.0"),
        Wallpaper("sultan_qaboos", "Mosquée Sultan Qaboos au couchant, Mascate", "Riyadh Al Balushi — CC BY 2.0"),
        Wallpaper("faisal", "Mosquée Faisal, Islamabad", "Ali Mujtaba — CC BY-SA 4.0"),
        Wallpaper("registan", "Le Registan la nuit, Samarcande", "Kraftabbas — CC BY-SA 4.0"),
        Wallpaper("mohamed_ali", "Mosquée Mohamed Ali, Le Caire", "kallerna — CC BY-SA 3.0"),
        Wallpaper("putra", "Coupole de la mosquée Putra, Putrajaya", "CEphoto, Uwe Aranas — CC BY-SA 3.0"),
        Wallpaper("taj_mahal", "Taj Mahal, Agra", "Antrix3 — CC BY-SA 4.0"),
        Wallpaper("kul_sharif", "Mosquée Kul-Sharif, Kazan", "Mike1979 Russia — CC BY-SA 3.0"),
        Wallpaper("cristal", "Mosquée de cristal, Kuala Terengganu", "Ikhwan Salleh — CC BY-SA 4.0"),
        Wallpaper("sahara", "Sahara, Jebil (Tunisie)", "El Golli Mohamed — CC BY-SA 4.0"),
        Wallpaper("wadi_rum", "Désert du Wadi Rum, Jordanie", "Peter Chisholm — CC0"),
    )

    /** Seulement les images réellement présentes dans l'appli. */
    val ALL: List<Wallpaper> by lazy { LIST.filter { available(it) } }

    private fun available(w: Wallpaper): Boolean =
        runCatching { com.salatitime.tv.R.drawable::class.java.getField(w.resName) }.isSuccess

    fun credits(): String = "Photos : Wikimedia Commons, licences libres.\n\n" +
        ALL.joinToString("\n") { "• ${it.name} : ${it.credit}" }
}
