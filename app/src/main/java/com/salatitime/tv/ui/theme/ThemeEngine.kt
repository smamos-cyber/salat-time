package com.salatitime.tv.ui.theme

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.salatitime.tv.R
import com.salatitime.tv.data.Prefs
import java.time.LocalDate

/**
 * Applique le thème choisi (couleurs) et le fond d'écran à l'appli, aux dialogues et aux surcouches.
 * Les mises en page XML utilisent les couleurs d'origine ; elles sont remplacées ici à l'exécution.
 */
object ThemeEngine {

    fun theme(ctx: Context): AppTheme = AppTheme.byId(Prefs(ctx).themeId)

    // ---------- Fond d'écran ----------

    /** Fond d'écran actif (null = dégradé du thème). « Aléatoire » change chaque jour. */
    fun wallpaper(ctx: Context): Wallpaper? {
        val id = Prefs(ctx).wallpaperId
        if (id == Wallpapers.RANDOM) {
            val all = Wallpapers.ALL
            if (all.isEmpty()) return null
            return all[(LocalDate.now().toEpochDay() % all.size).toInt()]
        }
        return Wallpapers.ALL.firstOrNull { it.id == id }
    }

    private var cachedId: String? = null
    private var cachedBitmap: Bitmap? = null

    private fun bitmap(ctx: Context, wp: Wallpaper): Bitmap? {
        if (cachedId == wp.id) cachedBitmap?.let { return it }
        val res = ctx.resources
        val resId = res.getIdentifier(wp.resName, "drawable", ctx.packageName)
        if (resId == 0) return null
        val dm = res.displayMetrics
        val target = maxOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(1280)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(res, resId, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
            inScaled = false
        }
        val bmp = runCatching { BitmapFactory.decodeResource(res, resId, opts) }.getOrNull()
        cachedBitmap = bmp
        cachedId = wp.id
        return bmp
    }

    /**
     * Fond complet : photo (recadrée pour remplir l'écran) + voile sombre aux couleurs du thème,
     * ou simple dégradé du thème. [scrim] : opacité du voile (0..1), plus fort pour les écrans chargés.
     */
    fun background(ctx: Context, scrim: Float = 0.62f): Drawable {
        val th = theme(ctx)
        val gradient = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(th.bgTop, th.bgBottom))
        val wp = wallpaper(ctx) ?: return gradient
        val bmp = bitmap(ctx, wp) ?: return gradient
        val a = (scrim * 255).toInt().coerceIn(0, 255)
        val veil = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ColorUtils.setAlphaComponent(th.bgTop, (a * 0.85f).toInt()),
                ColorUtils.setAlphaComponent(th.bgBottom, a),
                ColorUtils.setAlphaComponent(th.bgBottom, (a + 40).coerceAtMost(255))
            )
        )
        return LayerDrawable(arrayOf(CenterCropDrawable(bmp), veil))
    }

    fun applyWindow(activity: Activity, scrim: Float = 0.62f) {
        activity.window.setBackgroundDrawable(background(activity, scrim))
    }

    // ---------- Éléments ----------

    private fun dp(ctx: Context, v: Float) = v * ctx.resources.displayMetrics.density

    private fun rounded(ctx: Context, color: Int, radius: Float, stroke: Int? = null, strokeW: Float = 3f) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(ctx, radius)
            if (stroke != null) setStroke(dp(ctx, strokeW).toInt(), stroke)
        }

    /** Fond des cartes et lignes ; légèrement transparent pour laisser deviner le fond d'écran. */
    fun cardDrawable(ctx: Context, next: Boolean = false): Drawable {
        val th = theme(ctx)
        val alpha = if (wallpaper(ctx) != null) 0xD8 else 0xFF
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded(ctx, th.cardFocus, 18f, th.accent))
            addState(
                intArrayOf(),
                if (next) rounded(ctx, ColorUtils.setAlphaComponent(th.cardNext, alpha), 18f, th.accentSoft, 2f)
                else rounded(ctx, ColorUtils.setAlphaComponent(th.card, alpha), 18f)
            )
        }
    }

    fun buttonDrawable(ctx: Context): Drawable {
        val th = theme(ctx)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded(ctx, th.accent, 28f))
            addState(intArrayOf(android.R.attr.state_pressed), rounded(ctx, th.accent, 28f))
            addState(intArrayOf(), rounded(ctx, ColorUtils.setAlphaComponent(th.card, 0xE6), 28f))
        }
    }

    fun buttonText(ctx: Context): ColorStateList {
        val th = theme(ctx)
        return ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(th.bgTop, th.bgTop, th.text)
        )
    }

    fun overlayDrawable(ctx: Context): Drawable {
        val th = theme(ctx)
        return rounded(ctx, ColorUtils.setAlphaComponent(th.bgTop, 0xEE), 22f, th.accent, 2f)
    }

    fun panelDrawable(ctx: Context): Drawable {
        val th = theme(ctx)
        return rounded(ctx, ColorUtils.setAlphaComponent(th.bgTop, 0xB0), 24f, ColorUtils.setAlphaComponent(th.accent, 0x99), 1.5f)
    }

    /** Recolore une vue et ses enfants (couleurs d'origine → couleurs du thème). */
    fun style(view: View) {
        val ctx = view.context
        val th = theme(ctx)
        val map = mapOf(
            ContextCompat.getColor(ctx, R.color.gold) to th.accent,
            ContextCompat.getColor(ctx, R.color.gold_soft) to th.accentSoft,
            ContextCompat.getColor(ctx, R.color.text) to th.text,
            ContextCompat.getColor(ctx, R.color.text_dim) to th.textDim,
        )
        fun walk(v: View) {
            when (v.tag) {
                "card" -> v.background = cardDrawable(ctx)
                "overlay" -> v.background = overlayDrawable(ctx)
            }
            if (v is Button) {
                v.background = buttonDrawable(ctx)
                v.setTextColor(buttonText(ctx))
            } else if (v is TextView) {
                map[v.currentTextColor]?.let { v.setTextColor(it) }
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(view)
    }

    fun styleDialog(d: Dialog) {
        val ctx = d.context
        val th = theme(ctx)
        d.window?.setBackgroundDrawable(
            android.graphics.drawable.InsetDrawable(rounded(ctx, th.card, 16f, th.accent, 1f), dp(ctx, 16f).toInt())
        )
        if (d is AlertDialog) {
            for (b in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
                d.getButton(b)?.setTextColor(th.accent)
            }
        }
    }

    /** Dessine une image en la recadrant pour remplir la zone (comme centerCrop). */
    private class CenterCropDrawable(private val bmp: Bitmap) : Drawable() {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val matrix = Matrix()

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val scale = maxOf(b.width() / bmp.width.toFloat(), b.height() / bmp.height.toFloat())
            matrix.setScale(scale, scale)
            matrix.postTranslate(
                b.left + (b.width() - bmp.width * scale) / 2f,
                b.top + (b.height() - bmp.height * scale) / 2f
            )
            canvas.drawBitmap(bmp, matrix, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.OPAQUE
    }
}
