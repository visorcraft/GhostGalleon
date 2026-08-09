package com.visorcraft.ghostgalleon.i18n

/** Structural assertions keep host tests locale-independent and Android-free. */
fun UiText.resourceIds(): List<Int> = buildList { collectResourceIds(this@resourceIds) }

fun UiText.dynamicValues(): List<String> = buildList { collectDynamicValues(this@dynamicValues) }

fun UiText.literalArgs(): List<String> = buildList { collectLiteralArgs(this@literalArgs) }

private fun MutableList<Int>.collectResourceIds(value: Any?) {
    when (value) {
        is UiText.Resource -> {
            add(value.id)
            value.args.forEach(::collectResourceIds)
        }
        is UiText.Quantity -> {
            add(value.id)
            value.args.forEach(::collectResourceIds)
        }
        is UiText.Join -> value.parts.forEach(::collectResourceIds)
        is UiText.LocalizedList -> value.items.forEach(::collectResourceIds)
    }
}

private fun MutableList<String>.collectLiteralArgs(value: Any?) {
    when (value) {
        is String -> add(value)
        is UiText.Dynamic -> add(value.value)
        is UiText.Resource -> value.args.forEach(::collectLiteralArgs)
        is UiText.Quantity -> value.args.forEach(::collectLiteralArgs)
        is UiText.Join -> value.parts.forEach(::collectLiteralArgs)
        is UiText.LocalizedList -> value.items.forEach(::collectLiteralArgs)
    }
}

private fun MutableList<String>.collectDynamicValues(value: Any?) {
    when (value) {
        is UiText.Dynamic -> add(value.value)
        is UiText.Resource -> value.args.forEach(::collectDynamicValues)
        is UiText.Quantity -> value.args.forEach(::collectDynamicValues)
        is UiText.Join -> value.parts.forEach(::collectDynamicValues)
        is UiText.LocalizedList -> value.items.forEach(::collectDynamicValues)
    }
}
