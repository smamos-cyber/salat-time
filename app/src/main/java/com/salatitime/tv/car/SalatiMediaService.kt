package com.salatitime.tv.car

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.MediaBrowserServiceCompat
import com.salatitime.tv.R
import com.salatitime.tv.SalatiApp
import com.salatitime.tv.data.AdhanRepository
import com.salatitime.tv.data.Prefs
import com.salatitime.tv.prayer.Prayer
import com.salatitime.tv.ui.Format
import com.salatitime.tv.ui.MainActivity
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Salati Time dans Android Auto (catégorie « média ») :
 * horaires du jour et adhans, à écouter sur les haut-parleurs de la voiture.
 */
class SalatiMediaService : MediaBrowserServiceCompat() {

    private lateinit var session: MediaSessionCompat
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var current: MediaDescriptionCompat? = null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    /** Les horaires affichés (prochaine prière, temps restant) sont rafraîchis chaque minute. */
    private val refresher = object : Runnable {
        override fun run() {
            notifyChildrenChanged(TIMES)
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        session = MediaSessionCompat(this, "SalatiTime").apply {
            setCallback(callback)
            setSessionActivity(
                PendingIntent.getActivity(
                    this@SalatiMediaService, 400, Intent(this@SalatiMediaService, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            isActive = true
        }
        sessionToken = session.sessionToken
        setState(PlaybackStateCompat.STATE_NONE)
        handler.postDelayed(refresher, 60_000L)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        if (clientPackageName in CAR_PACKAGES) CarState.connectedAt = System.currentTimeMillis()
        val extras = Bundle().apply {
            // Présentation en liste (et non en grille) dans Android Auto
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
        }
        return BrowserRoot(ROOT, extras)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        result.sendResult(
            when (parentId) {
                ROOT -> mutableListOf(
                    browsable(TIMES, "🕌 Horaires du jour", Prefs(this).placeName.ifBlank { "Ma position" }),
                    browsable(ADHANS, "🔊 Adhans", "Écouter les appels à la prière"),
                )
                TIMES -> timesItems()
                ADHANS -> adhanItems()
                else -> mutableListOf()
            }
        )
    }

    // ---------- Contenu ----------

    private fun browsable(id: String, title: String, subtitle: String) = MediaItem(
        MediaDescriptionCompat.Builder().setMediaId(id).setTitle(title).setSubtitle(subtitle).build(),
        MediaItem.FLAG_BROWSABLE
    )

    private fun playable(id: String, title: String, subtitle: String) = MediaItem(
        MediaDescriptionCompat.Builder().setMediaId(id).setTitle(title).setSubtitle(subtitle).build(),
        MediaItem.FLAG_PLAYABLE
    )

    private fun timesItems(): MutableList<MediaItem> {
        val prefs = Prefs(this)
        val now = ZonedDateTime.now()
        val times = prefs.timesFor(LocalDate.now())
            ?: return mutableListOf(playable("none", "Lieu non défini", "Ouvrez Salati Time sur le téléphone pour choisir votre ville"))
        val next = Prayer.entries.filter { it.isSalah }.firstOrNull { times[it]?.isAfter(now) == true }
        return Prayer.entries.map { p ->
            val t = times[p]
            val time = t?.let { Format.hm(it) } ?: "--:--"
            val subtitle = when {
                p == next && t != null -> "$time  ·  prochaine prière, dans ${remaining(Duration.between(now, t))}"
                t != null && t.isBefore(now) -> "$time  ·  passée"
                else -> time
            }
            playable("$PRAYER${p.name}", "${p.label}  ${p.arabic}", subtitle)
        }.toMutableList()
    }

    private fun adhanItems(): MutableList<MediaItem> {
        AdhanRepository.ensureBundledDefault(this)
        return AdhanRepository.list(this).map { playable("$ADHAN${it.id}", it.name, "Adhan") }.toMutableList()
    }

    private fun remaining(d: Duration): String {
        val m = d.toMinutes().coerceAtLeast(0)
        return if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)}" else "$m min"
    }

    // ---------- Lecture ----------

    private val callback = object : MediaSessionCompat.Callback() {
        override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) = playId(mediaId)

        override fun onPlay() {
            val p = player
            if (p != null) {
                if (requestFocus()) { p.start(); setState(PlaybackStateCompat.STATE_PLAYING) }
            } else {
                // « Lecture » sans choix : adhan de la prochaine prière
                playId(PRAYER + (nextSalah()?.name ?: Prayer.FAJR.name))
            }
        }

        override fun onPause() {
            player?.takeIf { it.isPlaying }?.pause()
            setState(PlaybackStateCompat.STATE_PAUSED)
        }

        override fun onStop() = stopPlayback()
    }

    private fun nextSalah(): Prayer? {
        val now = ZonedDateTime.now()
        val times = Prefs(this).timesFor(LocalDate.now()) ?: return null
        return Prayer.entries.filter { it.isSalah }.firstOrNull { times[it]?.isAfter(now) == true }
    }

    private fun playId(mediaId: String) {
        val prefs = Prefs(this)
        val (soundId, title, subtitle) = when {
            mediaId.startsWith(PRAYER) -> {
                val p = Prayer.entries.firstOrNull { it.name == mediaId.removePrefix(PRAYER) } ?: return
                val t = prefs.timesFor(LocalDate.now())?.get(p)?.let { Format.hm(it) } ?: ""
                Triple(prefs.soundIdFor(p), "${p.label}  ${p.arabic}", "Adhan · $t")
            }
            mediaId.startsWith(ADHAN) -> {
                val id = mediaId.removePrefix(ADHAN)
                Triple(id, AdhanRepository.nameFor(this, id), "Adhan")
            }
            else -> return
        }
        val uri: Uri = AdhanRepository.fileFor(this, soundId)?.let { Uri.fromFile(it) }
            ?: Uri.parse("android.resource://$packageName/${R.raw.adhan_default}")
        releasePlayer()
        if (!requestFocus()) return
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(this@SalatiMediaService, uri)
                setOnCompletionListener { stopPlayback() }
                setOnErrorListener { _, _, _ -> stopPlayback(); true }
                prepare()
                start()
            }
        } catch (e: Exception) {
            setState(PlaybackStateCompat.STATE_ERROR); return
        }
        current = MediaDescriptionCompat.Builder().setMediaId(mediaId).setTitle(title).setSubtitle(subtitle).build()
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player?.duration?.toLong() ?: -1L)
                .build()
        )
        setState(PlaybackStateCompat.STATE_PLAYING)
        goForeground(title)
    }

    private fun stopPlayback() {
        releasePlayer()
        abandonFocus()
        setState(PlaybackStateCompat.STATE_STOPPED)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun releasePlayer() {
        player?.runCatching { stop() }
        player?.release()
        player = null
    }

    private fun setState(state: Int) {
        val pos = player?.runCatching { currentPosition.toLong() }?.getOrNull() ?: 0L
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                )
                .setState(state, pos, 1f)
                .build()
        )
    }

    private fun requestFocus(): Boolean {
        val am = getSystemService(AudioManager::class.java) ?: return true
        val req = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    callback.onPause()
                }
            }
            .build().also { focus = it }
        return am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val am = getSystemService(AudioManager::class.java) ?: return
        focus?.let { runCatching { am.abandonAudioFocusRequest(it) } }
        focus = null
    }

    /** Notification de lecture (obligatoire pour continuer à jouer en arrière-plan). */
    private fun goForeground(title: String) {
        val n: Notification = NotificationCompat.Builder(this, SalatiApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText("Salati Time")
            .setContentIntent(session.controller.sessionActivity)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(session.sessionToken))
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        runCatching { ServiceCompat.startForeground(this, FG_ID, n, type) }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        abandonFocus()
        session.release()
        CarState.connectedAt = 0L
        super.onDestroy()
    }

    companion object {
        private const val ROOT = "root"
        private const val TIMES = "times"
        private const val ADHANS = "adhans"
        private const val PRAYER = "prayer:"
        private const val ADHAN = "adhan:"
        private const val FG_ID = 44
        private val CAR_PACKAGES = setOf("com.google.android.projection.gearhead", "com.google.android.carassistant")
    }
}
