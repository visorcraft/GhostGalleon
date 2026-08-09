package com.visorcraft.ghostgalleon.ui

import android.content.Context
import android.widget.Toast
import com.visorcraft.ghostgalleon.i18n.UiText

fun Context.toast(message: String, long: Boolean = false) {
    Toast.makeText(
        this,
        message,
        if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
    ).show()
}

fun Context.toast(message: UiText, long: Boolean = false) =
    toast(resolveText(message), long)

fun Context.toast(resId: Int, vararg args: Any) =
    toast(getString(resId, *args))

fun Context.toastLong(resId: Int, vararg args: Any) =
    toast(getString(resId, *args), long = true)
