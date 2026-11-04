package com.salatitime.tv.data

import android.content.Context
import org.json.JSONObject
import kotlin.random.Random

/** Hadith authentique : texte arabe, traduction française et référence (recueil, numéro). */
data class Hadith(val arabic: String, val french: String, val reference: String)

/**
 * Hadiths sélectionnés dans Sahih al-Bukhari, Sahih Muslim, les 40 hadiths an-Nawawi et les hadiths qudsi
 * (fichier assets/hadiths.json, généré par tools/build_hadith.py à partir de fawazahmed0/hadith-api,
 * domaine public).
 */
object HadithRepository {

    @Volatile private var cache: List<Hadith>? = null

    fun all(ctx: Context): List<Hadith> {
        cache?.let { return it }
        val list = runCatching {
            val json = ctx.assets.open("hadiths.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONObject(json).getJSONArray("hadiths")
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Hadith(o.getString("ar"), o.getString("fr"), o.getString("ref"))
            }
        }.getOrDefault(emptyList())
        cache = list
        return list
    }

    fun get(ctx: Context, index: Int): Hadith? = all(ctx).getOrNull(index)

    /** Index d'un hadith au hasard, différent du précédent. */
    fun randomIndex(ctx: Context, previous: Int): Int {
        val n = all(ctx).size
        if (n <= 1) return 0
        var i: Int
        do { i = Random.nextInt(n) } while (i == previous)
        return i
    }
}
