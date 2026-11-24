package com.salatitime.tv.widget

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Affiche les deux widgets tels qu'ils apparaissent sur l'écran d'accueil (captures de test). */
class WidgetPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#5B6E8C"))
            setPadding((16 * d).toInt(), (48 * d).toInt(), (16 * d).toInt(), 0)
        }
        val (times, next) = PrayerWidgets.previewViews(this)
        val host1 = FrameLayout(this)
        host1.addView(times.apply(this, host1))
        col.addView(host1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (150 * d).toInt()))
        val host2 = FrameLayout(this)
        host2.addView(next.apply(this, host2))
        col.addView(host2, LinearLayout.LayoutParams((150 * d).toInt(), (150 * d).toInt()).apply { topMargin = (24 * d).toInt() })
        setContentView(col)
    }
}
