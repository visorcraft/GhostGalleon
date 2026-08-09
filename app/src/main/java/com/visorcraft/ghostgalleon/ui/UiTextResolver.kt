package com.visorcraft.ghostgalleon.ui

import android.content.Context
import com.visorcraft.ghostgalleon.i18n.UiText

/** Resolve a pure [UiText] through Android's locale-aware resource table. */
fun Context.resolveText(value: UiText): String = when (value) {
    is UiText.Resource -> getString(value.id, *resolveArgs(value.args))
    is UiText.Quantity -> resources.getQuantityString(
        value.id,
        value.quantity,
        *resolveArgs(value.args),
    )
    is UiText.Dynamic -> value.value
    is UiText.Join -> value.parts.joinToString(value.separator) { resolveText(it) }
    is UiText.LocalizedList -> android.icu.text.ListFormatter
        .getInstance(resources.configuration.locales[0])
        .format(value.items.map(::resolveText))
}

private fun Context.resolveArgs(args: List<Any>): Array<out Any> =
    args.map { if (it is UiText) resolveText(it) else it }.toTypedArray()
