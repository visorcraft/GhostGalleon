package com.visorcraft.ghostgalleon.i18n

/**
 * Translation-safe text emitted by Android-free domain modules.
 *
 * Resource ids keep source text in Android's compiled resource table while
 * domain logic stays host-testable. [Dynamic] is only for user/library data
 * that must not be translated. [Join] composes independently translated
 * fragments without exposing Android APIs to pure modules. [LocalizedList]
 * delegates human list punctuation/conjunctions to Android ICU.
 */
sealed interface UiText {
    data class Resource(
        val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class Quantity(
        val id: Int,
        val quantity: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class Dynamic(val value: String) : UiText

    data class Join(
        val parts: List<UiText>,
        val separator: String,
    ) : UiText

    data class LocalizedList(val items: List<UiText>) : UiText
}

fun text(id: Int, vararg args: Any): UiText = UiText.Resource(id, args.toList())

fun quantityText(id: Int, quantity: Int, vararg args: Any): UiText =
    UiText.Quantity(id, quantity, args.toList())

fun dynamicText(value: String): UiText = UiText.Dynamic(value)

fun joinText(parts: List<UiText>, separator: String): UiText =
    UiText.Join(parts, separator)

fun localizedListText(items: List<UiText>): UiText = UiText.LocalizedList(items)
