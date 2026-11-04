package com.salatitime.tv.ui

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.salatitime.tv.R
import com.salatitime.tv.ui.theme.ThemeEngine
import com.salatitime.tv.alarm.PrayerScheduler
import com.salatitime.tv.data.AdhanRepository
import com.salatitime.tv.data.AdhanSound
import com.salatitime.tv.data.Prefs
import com.salatitime.tv.prayer.Prayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bibliothèque des appels à la prière : import MP3, fichiers de la TV / clé USB, YouTube, lien direct. */
class AdhanActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var rows: RowList
    private var preview: MediaPlayer? = null

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importUri(it, null) }
    }
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importUri(it, null) }
    }
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickFromDevice() else info("Autorisation refusée", "Impossible de lister les fichiers audio sans cette autorisation.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        ThemeEngine.applyWindow(this, scrim = 0.78f)
        ThemeEngine.style(findViewById(android.R.id.content))
        findViewById<TextView>(R.id.screenTitle).text = "Appels à la prière"
        prefs = Prefs(this)
        rows = RowList(findViewById<LinearLayout>(R.id.container))
        build()
    }

    override fun onPause() {
        stopPreview()
        super.onPause()
    }

    private fun build() {
        rows.clear()
        rows.header("Ajouter un adhan")
        rows.row("▶️  Lien YouTube (audio uniquement)", { "youtube.com / youtu.be" }) {
            askText("Lien YouTube", "https://www.youtube.com/watch?v=…", inputType = URL_INPUT) { url ->
                importUrl(url)
            }
        }.requestFocus()
        rows.row("📁  Importer un fichier MP3", { "sélecteur de fichiers" }) { openPicker() }
        rows.row("💾  Fichiers audio de la TV / clé USB", { "liste des musiques" }) { requestDeviceAudio() }
        rows.row("🌐  Lien direct vers un fichier MP3", { "https://…/adhan.mp3" }) {
            askText("Lien direct", "https://exemple.com/adhan.mp3", inputType = URL_INPUT) { url ->
                importUrl(url)
            }
        }

        val sounds = AdhanRepository.list(this)
        rows.header("Ma bibliothèque (${sounds.size})")
        if (sounds.isEmpty()) {
            rows.row("Aucun adhan importé", { "le son système sera utilisé" }) { }
        }
        for (s in sounds) {
            rows.row("♪  ${s.name}", { badges(s) }) { soundActions(s) }
        }
    }

    private fun badges(s: AdhanSound): String {
        val tags = mutableListOf<String>()
        if (prefs.defaultAdhanId == s.id) tags += "★ Par défaut"
        Prayer.entries.filter { prefs.adhanOverride(it) == s.id }.forEach { tags += it.label }
        tags += s.source
        return tags.joinToString(" · ")
    }

    private fun soundActions(s: AdhanSound) {
        val items = listOf(
            "▶  Écouter",
            "■  Arrêter l'écoute",
            "★  Utiliser par défaut pour toutes les prières",
            "🌙  Utiliser pour Fajr uniquement",
            "✏️  Renommer",
            "🗑  Supprimer"
        )
        choose(s.name, items) {
            when (it) {
                0 -> playPreview(s)
                1 -> stopPreview()
                2 -> { prefs.defaultAdhanId = s.id; changed() }
                3 -> { prefs.setAdhanOverride(Prayer.FAJR, s.id); changed() }
                4 -> askText("Renommer", "Nom", s.name) { n -> AdhanRepository.rename(this, s.id, n); changed() }
                5 -> { stopPreview(); AdhanRepository.delete(this, s.id); changed() }
            }
        }
    }

    private fun changed() {
        PrayerScheduler.schedule(this)
        build()
    }

    // ---------- Écoute ----------

    private fun playPreview(s: AdhanSound) {
        stopPreview()
        val file = AdhanRepository.fileFor(this, s.id) ?: return
        try {
            preview = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                setDataSource(this@AdhanActivity, Uri.fromFile(file))
                val v = prefs.volume / 100f
                setVolume(v, v)
                setOnCompletionListener { stopPreview() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            info("Lecture impossible", "Ce format audio n'est pas pris en charge par la TV.\n${e.message ?: ""}")
        }
    }

    private fun stopPreview() {
        preview?.let { runCatching { it.stop() }; it.release() }
        preview = null
    }

    // ---------- Imports ----------

    private fun openPicker() {
        try {
            openDocument.launch(arrayOf("audio/*"))
        } catch (e: Exception) {
            try {
                getContent.launch("audio/*")
            } catch (e2: Exception) {
                info(
                    "Pas de sélecteur de fichiers",
                    "Votre TV n'a pas de sélecteur de fichiers. Utilisez « Fichiers audio de la TV / clé USB » " +
                        "ou installez un gestionnaire de fichiers."
                )
            }
        }
    }

    private fun requestDeviceAudio() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) pickFromDevice()
        else audioPermission.launch(perm)
    }

    private fun pickFromDevice() {
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) { listDeviceAudio() }
            if (files.isEmpty()) {
                info("Aucun fichier audio", "Copiez vos MP3 sur une clé USB branchée à la TV, puis réessayez.")
                return@launch
            }
            choose("Choisissez un fichier", files.map { it.first }) { importUri(files[it].second, files[it].first) }
        }
    }

    private fun listDeviceAudio(): List<Pair<String, Uri>> {
        val out = mutableListOf<Pair<String, Uri>>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME)
        runCatching {
            contentResolver.query(collection, projection, null, null, MediaStore.Audio.Media.DISPLAY_NAME + " ASC")?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                while (c.moveToNext()) {
                    out += (c.getString(nameCol) ?: "audio") to ContentUris.withAppendedId(collection, c.getLong(idCol))
                }
            }
        }
        return out
    }

    private fun importUri(uri: Uri, name: String?) {
        val dlg = progressDialog("Import du fichier…")
        lifecycleScope.launch {
            try {
                val s = withContext(Dispatchers.IO) { AdhanRepository.importFromUri(this@AdhanActivity, uri, name) }
                done(s)
            } catch (e: Exception) {
                info("Échec de l'import", e.message ?: "Erreur inconnue")
            } finally {
                dlg.dismiss()
            }
        }
    }

    private fun importUrl(url: String) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            info("Lien invalide", "Le lien doit commencer par https://")
            return
        }
        val dlg = progressDialog("Récupération de l'audio…")
        lifecycleScope.launch {
            try {
                val s = withContext(Dispatchers.IO) {
                    AdhanRepository.importFromUrl(this@AdhanActivity, url) { pct ->
                        runOnUiThread { dlg.setMessage("Téléchargement de l'audio… $pct %") }
                    }
                }
                done(s)
            } catch (e: Exception) {
                info(
                    "Échec de l'import",
                    (e.message ?: e.javaClass.simpleName) +
                        "\n\nPour YouTube : vérifiez le lien ; si le problème persiste, une mise à jour de l'application peut être nécessaire."
                )
            } finally {
                dlg.dismiss()
            }
        }
    }

    private companion object {
        val URL_INPUT = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
    }

    private fun done(s: AdhanSound) {
        Toast.makeText(this, "« ${s.name} » ajouté", Toast.LENGTH_LONG).show()
        changed()
    }
}
