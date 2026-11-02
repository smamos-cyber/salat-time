package com.salatitime.tv.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.salatitime.tv.youtube.YoutubeAudioImporter
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

data class AdhanSound(val id: String, val name: String, val fileName: String, val source: String)

/** Bibliothèque des appels à la prière importés (stockés dans le stockage privé de l'appli). */
object AdhanRepository {
    private const val KEY = "adhans"
    private const val MAX_BYTES = 60L * 1024 * 1024
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "webm", "flac")

    private fun dir(ctx: Context) = File(ctx.filesDir, "adhans").apply { mkdirs() }
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("salati", Context.MODE_PRIVATE)

    @Synchronized
    fun list(ctx: Context): List<AdhanSound> {
        val arr = JSONArray(sp(ctx).getString(KEY, "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            AdhanSound(o.getString("id"), o.getString("name"), o.getString("file"), o.optString("source"))
        }
    }

    @Synchronized
    private fun save(ctx: Context, items: List<AdhanSound>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("file", it.fileName).put("source", it.source))
        }
        sp(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    fun get(ctx: Context, id: String): AdhanSound? = if (id.isEmpty()) null else list(ctx).firstOrNull { it.id == id }

    fun fileFor(ctx: Context, id: String): File? =
        get(ctx, id)?.let { File(dir(ctx), it.fileName) }?.takeIf { it.exists() && it.length() > 0 }

    fun nameFor(ctx: Context, id: String): String = get(ctx, id)?.name ?: "Son système"

    fun rename(ctx: Context, id: String, newName: String) =
        save(ctx, list(ctx).map { if (it.id == id) it.copy(name = newName) else it })

    fun delete(ctx: Context, id: String) {
        val items = list(ctx)
        items.firstOrNull { it.id == id }?.let { File(dir(ctx), it.fileName).delete() }
        save(ctx, items.filter { it.id != id })
        val prefs = Prefs(ctx)
        if (prefs.defaultAdhanId == id) prefs.defaultAdhanId = ""
        com.salatitime.tv.prayer.Prayer.entries.forEach { if (prefs.adhanOverride(it) == id) prefs.setAdhanOverride(it, "") }
    }

    private fun register(ctx: Context, name: String, file: File, source: String): AdhanSound {
        val sound = AdhanSound(file.nameWithoutExtension, name.ifBlank { "Adhan" }, file.name, source)
        save(ctx, list(ctx) + sound)
        if (Prefs(ctx).defaultAdhanId.isEmpty()) Prefs(ctx).defaultAdhanId = sound.id
        return sound
    }

    private fun newFile(ctx: Context, ext: String) =
        File(dir(ctx), UUID.randomUUID().toString() + "." + ext.lowercase().ifBlank { "mp3" })

    /** Copie un fichier choisi via le sélecteur Android ou MediaStore (à appeler hors du thread UI). */
    fun importFromUri(ctx: Context, uri: Uri, displayName: String? = null): AdhanSound {
        val cr = ctx.contentResolver
        var name = displayName
        if (name == null) {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0)
            }
        }
        val fullName = name ?: "Adhan"
        val ext = fullName.substringAfterLast('.', "").lowercase().takeIf { it in AUDIO_EXT }
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(cr.getType(uri)) ?: "mp3"
        val out = newFile(ctx, ext)
        cr.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
            ?: throw IOException("Impossible de lire le fichier")
        return register(ctx, fullName.substringBeforeLast('.'), out, "fichier")
    }

    /** Lien YouTube → audio seul ; sinon lien direct vers un fichier audio. */
    fun importFromUrl(ctx: Context, url: String, progress: (Int) -> Unit): AdhanSound {
        val clean = url.trim()
        if (YoutubeAudioImporter.isYoutubeUrl(clean)) return YoutubeAudioImporter.import(ctx, clean, progress)
        val path = Uri.parse(clean).lastPathSegment ?: "adhan.mp3"
        val ext = path.substringAfterLast('.', "").lowercase().takeIf { it in AUDIO_EXT } ?: "mp3"
        return downloadToLibrary(ctx, clean, path.substringBeforeLast('.'), ext, "lien", emptyMap(), progress)
    }

    fun downloadToLibrary(
        ctx: Context, url: String, name: String, ext: String, source: String,
        headers: Map<String, String>, progress: (Int) -> Unit
    ): AdhanSound {
        val builder = Request.Builder().url(url).header("User-Agent", Http.BROWSER_UA)
        headers.forEach { (k, v) -> builder.header(k, v) }
        val out = newFile(ctx, ext)
        try {
            Http.client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Téléchargement impossible (HTTP ${resp.code})")
                val body = resp.body ?: throw IOException("Réponse vide")
                val total = body.contentLength()
                if (total > MAX_BYTES) throw IOException("Fichier trop volumineux")
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (done > MAX_BYTES) throw IOException("Fichier trop volumineux")
                            if (total > 0) progress((done * 100 / total).toInt())
                        }
                    }
                }
            }
            if (out.length() == 0L) throw IOException("Fichier vide")
        } catch (e: Exception) {
            out.delete()
            throw e
        }
        return register(ctx, name, out, source)
    }
}
