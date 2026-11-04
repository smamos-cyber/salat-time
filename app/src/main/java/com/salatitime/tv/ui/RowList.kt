package com.salatitime.tv.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.salatitime.tv.R
import com.salatitime.tv.ui.theme.ThemeEngine

/** Liste verticale de lignes « Titre … Valeur », navigable à la télécommande (D-pad). */
class RowList(private val container: LinearLayout) {
    private val inflater = LayoutInflater.from(container.context)
    private val refreshers = mutableListOf<() -> Unit>()

    fun header(text: String) {
        val v = inflater.inflate(R.layout.item_header, container, false) as TextView
        v.text = text
        ThemeEngine.style(v)
        container.addView(v)
    }

    fun row(title: String, value: () -> String = { "" }, onClick: () -> Unit): View {
        val v = inflater.inflate(R.layout.item_row, container, false)
        val titleTv = v.findViewById<TextView>(R.id.rowTitle)
        val valueTv = v.findViewById<TextView>(R.id.rowValue)
        titleTv.text = title
        val refresh = { valueTv.text = value() }
        refresh()
        refreshers += refresh
        v.setOnClickListener { onClick() }
        ThemeEngine.style(v)
        container.addView(v)
        return v
    }

    fun refresh() = refreshers.forEach { it() }

    fun clear() {
        container.removeAllViews()
        refreshers.clear()
    }
}
