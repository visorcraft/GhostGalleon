package com.visorcraft.ghostgalleon.ui.deck

/**
 * Pure, host-tested cache keys and diff guards eliminating unnecessary
 * `GradientDrawable` / `LayoutParams` allocations on hot GridDeck, DockBar,
 * and FolderPanel selection and scroll paths.
 */
object DeckHotPathCache {

    /** Immutable key identifying a cached surface/accent/radius/density drawable template. */
    data class TemplateKey(
        val surfaceFill: Int,
        val accent: Int,
        val radiusDp: Int,
        val density: Float,
    )

    /** True when cached [currentKey] already matches [nextKey]. */
    fun isCacheHit(currentKey: TemplateKey?, nextKey: TemplateKey): Boolean =
        currentKey != null && currentKey == nextKey

    /** True when an existing view's LayoutParams already match the desired pixel bounds. */
    fun layoutParamsUnchanged(
        currentWidth: Int,
        currentHeight: Int,
        wantWidth: Int,
        wantHeight: Int,
    ): Boolean = currentWidth == wantWidth && currentHeight == wantHeight
}
