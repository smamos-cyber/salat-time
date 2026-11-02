package com.salatitime.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class GeoPlace(val name: String, val lat: Double, val lng: Double)

/**
 * Les TV n'ont généralement pas de GPS : on localise par l'adresse IP,
 * ou par recherche de ville (OpenStreetMap / Nominatim), ou par saisie manuelle.
 */
object LocationRepository {

    suspend fun detectByIp(): GeoPlace = withContext(Dispatchers.IO) {
        runCatching {
            val j = JSONObject(fetch("https://ipwho.is/"))
            if (!j.optBoolean("success", true)) throw IOException(j.optString("message"))
            GeoPlace(
                joinName(j.optString("city"), j.optString("country")),
                j.getDouble("latitude"), j.getDouble("longitude")
            )
        }.recoverCatching {
            val j = JSONObject(fetch("https://ipapi.co/json/"))
            GeoPlace(
                joinName(j.optString("city"), j.optString("country_name")),
                j.getDouble("latitude"), j.getDouble("longitude")
            )
        }.getOrThrow()
    }

    suspend fun search(query: String): List<GeoPlace> = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("limit", "8")
            .addQueryParameter("accept-language", "fr")
            .build()
        val arr = JSONArray(fetch(url.toString()))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            GeoPlace(o.getString("display_name"), o.getString("lat").toDouble(), o.getString("lon").toDouble())
        }
    }

    private fun joinName(vararg parts: String) = parts.filter { it.isNotBlank() }.joinToString(", ")

    private fun fetch(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", Http.APP_UA).build()
        Http.client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return r.body?.string() ?: throw IOException("Réponse vide")
        }
    }
}
