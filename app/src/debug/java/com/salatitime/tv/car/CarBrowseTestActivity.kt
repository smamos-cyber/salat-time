package com.salatitime.tv.car

import android.app.Activity
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.widget.ScrollView
import android.widget.TextView

/** Version de test : parcourt le service audio comme Android Auto, puis lance un adhan. */
class CarBrowseTestActivity : Activity() {
    private lateinit var out: TextView
    private lateinit var browser: MediaBrowserCompat
    private val log = StringBuilder()

    private fun add(s: String) { log.append(s).append('\n'); out.text = log }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; setPadding(32, 96, 32, 32) }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#101418")); addView(out) })
        browser = MediaBrowserCompat(this, ComponentName(this, SalatiMediaService::class.java),
            object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    add("✅ Connecté au service (racine : ${browser.root})")
                    listAll(browser.root, 0)
                    Handler(Looper.getMainLooper()).postDelayed({ play() }, 2500)
                }
                override fun onConnectionFailed() = add("❌ Connexion impossible")
            }, null)
        browser.connect()
    }

    private fun listAll(id: String, depth: Int) {
        browser.subscribe(id, object : MediaBrowserCompat.SubscriptionCallback() {
            override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
                browser.unsubscribe(parentId)
                add("\n📂 $parentId (${children.size})")
                for (c in children) {
                    add("   ${if (c.isBrowsable) "▸" else "♪"} ${c.description.title}  —  ${c.description.subtitle}")
                    if (c.isBrowsable) listAll(c.mediaId!!, depth + 1)
                }
            }
        })
    }

    private fun play() {
        val c = MediaControllerCompat(this, browser.sessionToken)
        c.transportControls.playFromMediaId("prayer:ASR", null)
        Handler(Looper.getMainLooper()).postDelayed({
            add("\n▶ Lecture « prayer:ASR » → état ${c.playbackState?.state} (3 = en lecture)")
            add("   Titre : ${c.metadata?.description?.title} · ${c.metadata?.description?.subtitle}")
            c.transportControls.stop()
        }, 3000)
    }

    override fun onDestroy() { browser.disconnect(); super.onDestroy() }
}
