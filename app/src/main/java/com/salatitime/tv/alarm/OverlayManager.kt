package com.salatitime.tv.alarm

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.salatitime.tv.R
import com.salatitime.tv.ui.theme.ThemeEngine

/**
 * Affiche une fenêtre par-dessus n'importe quelle application (Netflix, YouTube, chaîne TV…).
 * Sur Android TV, les notifications classiques ne s'affichent pas toujours à l'écran :
 * cette surcouche est donc le moyen fiable d'avertir l'utilisateur.
 */
class OverlayManager(context: Context) {
    private val ctx = ContextThemeWrapper(context.applicationContext, R.style.Theme_Salati)
    private val wm = context.getSystemService(WindowManager::class.java)
    private var current: View? = null

    fun canDraw(): Boolean = Settings.canDrawOverlays(ctx)

    /** Petite bannière en haut à droite, ne prend pas le focus de la télécommande. */
    fun showBanner(title: String, subtitle: String): Boolean {
        if (!canDraw()) return false
        hide()
        val v = LayoutInflater.from(ctx).inflate(R.layout.overlay_banner, null)
        v.findViewById<TextView>(R.id.overlayTitle).text = title
        v.findViewById<TextView>(R.id.overlaySubtitle).text = subtitle
        ThemeEngine.style(v)
        val lp = baseParams(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(40); y = dp(32)
        }
        return add(v, lp)
    }

    /** Panneau d'adhan avec bouton « Arrêter » sélectionné (OK ou Retour pour couper). */
    fun showAdhan(title: String, subtitle: String, onStop: () -> Unit): Boolean {
        if (!canDraw()) return false
        hide()
        val root = KeyCatcherLayout(ctx).apply { onBack = onStop }
        LayoutInflater.from(ctx).inflate(R.layout.overlay_adhan, root, true)
        root.findViewById<TextView>(R.id.overlayTitle).text = title
        root.findViewById<TextView>(R.id.overlaySubtitle).text = subtitle
        ThemeEngine.style(root)
        val stop = root.findViewById<Button>(R.id.overlayStop)
        stop.setOnClickListener { onStop() }
        val lp = baseParams(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON // l'écran reste allumé pendant l'adhan
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }
        if (!add(root, lp)) return false
        stop.post { stop.requestFocus() }
        return true
    }

    fun hide() {
        current?.let { runCatching { wm.removeView(it) } }
        current = null
    }

    private fun baseParams(flags: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT
    )

    private fun add(v: View, lp: WindowManager.LayoutParams): Boolean = try {
        wm.addView(v, lp)
        current = v
        true
    } catch (e: Exception) {
        false
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
}

/** Intercepte la touche Retour de la télécommande pour arrêter l'adhan. */
class KeyCatcherLayout(context: Context) : FrameLayout(context) {
    var onBack: (() -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
