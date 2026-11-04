package com.salatitime.tv.lock

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.util.TypedValue
import androidx.core.graphics.ColorUtils
import androidx.core.widget.TextViewCompat
import com.salatitime.tv.data.Hadith
import com.salatitime.tv.ui.theme.ThemeEngine

/**
 * Écran plein écran affiché par-dessus toutes les applications.
 * Il capte toutes les touches de la télécommande : rien n'est utilisable derrière.
 *
 * Code de déblocage (si configuré) : OK pour l'afficher, puis
 * ▲▼ pour changer le chiffre, ◀▶ pour se déplacer, OK pour valider
 * (ou touches numériques de la télécommande).
 */
class LockOverlay(
    context: Context,
    private val onCode: (String) -> Boolean,
    private val onEmergency: () -> Unit = {},
) {

    private val ctx = context.applicationContext
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val root = KeyLayout(ctx)
    private val th = ThemeEngine.theme(ctx)
    private val titleTv = text(24f, th.accentSoft)
    private val countdownTv = text(58f, th.text, bold = true)
    private val untilTv = text(18f, th.textDim)
    private val hintTv = text(16f, th.textDim)
    private val pinTv = text(40f, th.accent, bold = true)
    private val hadithBox = LinearLayout(ctx)
    private val spacer = View(ctx)
    private val arabicTv = text(26f, th.text)
    private val frenchTv = text(20f, th.text)
    private val refTv = text(16f, th.accent)
    private var attached = false
    private var hasCode = false
    private var hasHadith = false

    /** Téléphone / tablette : saisie au doigt et bouton d'appel d'urgence. */
    private val touch = !ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) &&
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    private val portrait = ctx.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    private val keypad = LinearLayout(ctx)
    private val emergencyTv = text(16f, th.text)

    // Saisie du code : chiffres validés + chiffre en cours de réglage (▲▼)
    private var pinMode = false
    private val digits = mutableListOf<Int>()
    private var current = 0
    private var editing = false
    private val maxDigits = 8

    init {
        root.background = ThemeEngine.background(ctx, scrim = 0.80f)
        root.isFocusable = true
        root.isFocusableInTouchMode = true
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // zone sûre des TV (overscan) ; marges réduites sur téléphone
            if (portrait) setPadding(dp(16), dp(40), dp(16), dp(20)) else setPadding(dp(56), dp(28), dp(56), dp(24))
        }
        col.addView(text(if (portrait) 28f else 34f, th.accent).apply { this.text = "🕌  حي على الصلاة" })
        col.addView(titleTv.apply { setPadding(0, dp(4), 0, dp(10)) })

        // Hadith : arabe (de droite à gauche), traduction, référence ; la taille s'adapte à la longueur
        hadithBox.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = ThemeEngine.panelDrawable(ctx)
            if (portrait) setPadding(dp(14), dp(12), dp(14), dp(10)) else setPadding(dp(36), dp(18), dp(36), dp(14))
            visibility = View.GONE
        }
        arabicTv.apply {
            textDirection = View.TEXT_DIRECTION_RTL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setLineSpacing(0f, 1.25f)
        }
        frenchTv.setLineSpacing(0f, 1.15f)
        hadithBox.addView(arabicTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.15f))
        hadithBox.addView(View(ctx).apply { setBackgroundColor(ColorUtils.setAlphaComponent(th.accent, 0x66)) },
            LinearLayout.LayoutParams(dp(220), dp(1)).apply { setMargins(0, dp(10), 0, dp(10)); gravity = Gravity.CENTER })
        hadithBox.addView(frenchTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        hadithBox.addView(refTv.apply { setPadding(0, dp(8), 0, 0) })
        col.addView(hadithBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        // Sans hadith, l'espace vide centre le compte à rebours
        col.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        col.addView(text(20f, th.text).apply {
            this.text = if (touch) "Pause pendant la prière. Disponible dans :" else "La TV est en pause. Elle sera disponible dans :"
            setPadding(0, dp(14), 0, 0)
        })
        col.addView(countdownTv)
        col.addView(untilTv)
        col.addView(pinTv.apply { setPadding(0, dp(10), 0, 0); visibility = View.GONE })
        if (touch) {
            buildKeypad()
            col.addView(keypad, LinearLayout.LayoutParams(dp(280), LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
            hintTv.setOnClickListener {
                if (hasCode && !pinMode) { pinMode = true; resetPin(); renderPin() }
            }
        }
        col.addView(hintTv.apply { setPadding(0, dp(10), 0, dp(4)) })
        if (touch) {
            col.addView(emergencyTv.apply {
                text = "📞  Appel d'urgence"
                background = ThemeEngine.buttonDrawable(ctx)
                setPadding(dp(22), dp(10), dp(22), dp(10))
                setOnClickListener { onEmergency() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8)
            })
        }
        root.addView(col, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        root.onKey = ::handleKey
    }

    /** Pavé numérique tactile pour le code de déblocage (téléphone). */
    private fun buildKeypad() {
        keypad.orientation = LinearLayout.VERTICAL
        keypad.visibility = View.GONE
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
        for (r in rows) {
            val line = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in r) {
                val key = text(24f, th.text, bold = true).apply {
                    text = k
                    background = ThemeEngine.buttonDrawable(ctx)
                    setPadding(0, dp(10), 0, dp(10))
                    setOnClickListener { touchKey(k) }
                }
                line.addView(key, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(5), dp(5), dp(5), dp(5))
                })
            }
            keypad.addView(line)
        }
        keypad.addView(text(16f, th.textDim).apply {
            text = "Annuler"
            setPadding(0, dp(8), 0, dp(4))
            setOnClickListener { pinMode = false; renderPin() }
        })
    }

    private fun touchKey(k: String) {
        when (k) {
            "⌫" -> if (digits.isNotEmpty()) digits.removeAt(digits.lastIndex)
            "OK" -> {
                if (!onCode(digits.joinToString(""))) { resetPin(); renderPin("❌ Code incorrect") }
                return
            }
            else -> if (digits.size < maxDigits) digits += k.toInt()
        }
        renderPin()
    }

    /** Affiche (ou masque, si null) le hadith. */
    fun setHadith(h: Hadith?) {
        hasHadith = h != null
        if (h == null) {
            hadithBox.visibility = View.GONE
            spacer.visibility = View.VISIBLE
            return
        }
        hadithBox.visibility = View.VISIBLE
        spacer.visibility = View.GONE
        arabicTv.text = h.arabic
        frenchTv.text = h.french
        refTv.text = h.reference
        // La taille du texte s'ajuste pour que le hadith tienne entièrement dans son cadre
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(arabicTv, 14, 34, 1, TypedValue.COMPLEX_UNIT_SP)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(frenchTv, 12, 26, 1, TypedValue.COMPLEX_UNIT_SP)
    }

    fun isShown(): Boolean = attached && root.isAttachedToWindow

    fun show(codeConfigured: Boolean) {
        hasCode = codeConfigured
        if (!Settings.canDrawOverlays(ctx)) return
        if (attached) runCatching { wm.removeView(root) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        attached = try {
            wm.addView(root, lp); true
        } catch (e: Exception) {
            false
        }
        root.post { root.requestFocus() }
        renderPin()
    }

    fun hide() {
        if (attached) runCatching { wm.removeView(root) }
        attached = false
    }

    fun update(title: String, countdown: String, until: String) {
        titleTv.text = title
        countdownTv.text = countdown
        untilTv.text = until
    }

    // ---------- Touches ----------

    private fun handleKey(e: KeyEvent): Boolean {
        val code = e.keyCode
        // Laisser passer uniquement le volume (géré par la TV)
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (e.action != KeyEvent.ACTION_DOWN || !hasCode) return true
        if (!pinMode) {
            if (isOk(code)) { pinMode = true; resetPin(); renderPin() }
            return true
        }
        when {
            code == KeyEvent.KEYCODE_BACK -> { pinMode = false; renderPin() }
            code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                commitCurrent()
                if (digits.size < maxDigits) digits += code - KeyEvent.KEYCODE_0
                renderPin()
            }
            code == KeyEvent.KEYCODE_DPAD_UP -> { current = (current + 1) % 10; editing = true; renderPin() }
            code == KeyEvent.KEYCODE_DPAD_DOWN -> { current = (current + 9) % 10; editing = true; renderPin() }
            code == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // valide le chiffre affiché et passe au suivant
                editing = true; commitCurrent(); renderPin()
            }
            code == KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (editing) { editing = false; current = 0 } else if (digits.isNotEmpty()) digits.removeAt(digits.lastIndex)
                renderPin()
            }
            isOk(code) -> {
                commitCurrent()
                val entered = digits.joinToString("")
                if (!onCode(entered)) {
                    resetPin()
                    renderPin("❌ Code incorrect")
                }
            }
        }
        return true
    }

    private fun commitCurrent() {
        if (editing && digits.size < maxDigits) digits += current
        editing = false
        current = 0
    }

    private fun isOk(code: Int) = code == KeyEvent.KEYCODE_DPAD_CENTER ||
        code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun resetPin() {
        digits.clear(); current = 0; editing = false
    }

    private fun renderPin(error: String? = null) {
        if (touch) {
            // Pendant la saisie au doigt, le pavé prend la place du hadith
            keypad.visibility = if (pinMode) View.VISIBLE else View.GONE
            hadithBox.visibility = if (hasHadith && !pinMode) View.VISIBLE else View.GONE
            spacer.visibility = if (hasHadith && !pinMode) View.GONE else View.VISIBLE
            pinTv.visibility = if (pinMode) View.VISIBLE else View.GONE
            pinTv.text = if (digits.isEmpty()) "_ _ _ _" else digits.joinToString("  ") { "•" }
            hintTv.text = when {
                !hasCode -> ""
                pinMode -> (error ?: "Saisissez le code de déblocage")
                else -> "🔑  Touchez ici pour saisir le code de déblocage"
            }
            return
        }
        if (!hasCode) {
            pinTv.visibility = View.GONE
            hintTv.text = ""
            return
        }
        if (!pinMode) {
            pinTv.visibility = View.GONE
            hintTv.text = "Code de déblocage : appuyez sur OK"
            return
        }
        pinTv.visibility = View.VISIBLE
        // Les chiffres déjà saisis sont masqués, seul le chiffre en cours est visible
        pinTv.text = (List(digits.size) { "•" } + (if (editing) "[$current]" else "[ _ ]")).joinToString("  ")
        hintTv.text = (error?.let { "$it\n" } ?: "") +
            "▲▼ choisir le chiffre   ▶ chiffre suivant   ◀ effacer   OK valider   Retour annuler"
    }

    // ---------- Vues ----------

    private fun text(sizeSp: Float, color: Int, bold: Boolean = false) = TextView(ctx).apply {
        textSize = sizeSp
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    /** Capte toutes les touches de la télécommande. */
    private class KeyLayout(context: Context) : FrameLayout(context) {
        var onKey: ((KeyEvent) -> Boolean)? = null
        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            onKey?.invoke(event) ?: true
    }
}
