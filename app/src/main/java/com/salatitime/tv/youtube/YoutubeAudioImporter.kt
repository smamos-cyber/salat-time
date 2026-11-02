package com.salatitime.tv.youtube

import android.content.Context
import android.net.Uri
import com.salatitime.tv.data.AdhanRepository
import com.salatitime.tv.data.AdhanSound
import com.salatitime.tv.data.Http
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException

/**
 * Récupère uniquement la piste audio d'une vidéo YouTube et l'enregistre dans la bibliothèque,
 * pour qu'elle soit jouée hors-ligne à l'heure de la prière (pas de vidéo, pas de pub, fiable).
 */
object YoutubeAudioImporter {
    private const val MAX_DURATION_S = 20 * 60

    @Volatile private var initialized = false

    private fun ensureInit() {
        if (!initialized) synchronized(this) {
            if (!initialized) {
                NewPipe.init(OkHttpDownloader(Http.client))
                initialized = true
            }
        }
    }

    fun isYoutubeUrl(url: String): Boolean {
        val host = runCatching { Uri.parse(url.trim()).host?.lowercase() }.getOrNull() ?: return false
        return host == "youtu.be" || host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com")
    }

    /** Bloquant : à appeler depuis un thread d'arrière-plan. */
    fun import(ctx: Context, url: String, progress: (Int) -> Unit): AdhanSound {
        ensureInit()
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)
        if (info.duration > MAX_DURATION_S) {
            throw IOException("Vidéo trop longue (${info.duration / 60} min). Maximum : 20 min.")
        }
        val streams = info.audioStreams.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.content.isNullOrBlank()
        }
        // M4A/AAC est lu par tous les appareils Android ; sinon on prend le meilleur flux disponible
        val best = streams.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: streams.maxByOrNull { it.averageBitrate }
            ?: throw IOException("Aucune piste audio trouvée pour cette vidéo")

        val ext = best.format?.suffix ?: "m4a"
        return AdhanRepository.downloadToLibrary(
            ctx = ctx,
            url = best.content,
            name = info.name,
            ext = ext,
            source = "youtube",
            headers = mapOf("Referer" to "https://www.youtube.com/", "Origin" to "https://www.youtube.com"),
            progress = progress
        )
    }
}
