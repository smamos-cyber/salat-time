package com.salatitime.tv.ui

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog

fun Context.choose(title: String, items: List<String>, checked: Int = -1, onPick: (Int) -> Unit) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setSingleChoiceItems(items.toTypedArray(), checked) { d, which ->
            d.dismiss()
            onPick(which)
        }
        .setNegativeButton("Annuler", null)
        .show()
}

fun Context.askText(
    title: String,
    hint: String,
    initial: String = "",
    inputType: Int = InputType.TYPE_CLASS_TEXT,
    onOk: (String) -> Unit
) {
    val pad = (24 * resources.displayMetrics.density).toInt()
    val input = EditText(this).apply {
        this.hint = hint
        this.inputType = inputType
        setText(initial)
        setSelection(initial.length)
        isSingleLine = true
    }
    val box = FrameLayout(this).apply {
        setPadding(pad, pad / 2, pad, 0)
        addView(input)
    }
    AlertDialog.Builder(this)
        .setTitle(title)
        .setView(box)
        .setPositiveButton("OK") { _, _ ->
            val v = input.text.toString().trim()
            if (v.isNotEmpty()) onOk(v)
        }
        .setNegativeButton("Annuler", null)
        .show()
    input.requestFocus()
}

fun Context.info(title: String, message: String) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("OK", null)
        .show()
}

fun Context.progressDialog(message: String): AlertDialog =
    AlertDialog.Builder(this)
        .setTitle("Veuillez patienter")
        .setMessage(message)
        .setCancelable(false)
        .show()
