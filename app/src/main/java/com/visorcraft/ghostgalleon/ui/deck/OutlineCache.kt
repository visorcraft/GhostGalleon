package com.visorcraft.ghostgalleon.ui.deck

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.round

/**
 * Shared bounded cache of stateless [ViewOutlineProvider] instances for rounded
 * rectangles and ovals.
 *
 * Eliminates per-bind anonymous [ViewOutlineProvider] allocations across
 * `ArtTile`, `CustomIcon`, `GameDeck`, and `CompanionPanel`. Holds zero
 * [android.content.Context] or [View] references.
 */
object OutlineCache {

    /** Strict upper bound on cached quantized rounded-rect providers. */
    internal const val MAX_ROUNDED_RECT_ENTRIES: Int = 64

    /**
     * Quantization bucket size in pixels. 2px quantization keeps max corner delta
     * `<= 1px` (`<= 0.5dp` on 2x+ handhelds) while collapsing density/clamping
     * jitter into shared provider instances.
     */
    internal const val QUANTIZE_STEP_PX: Int = 2

    /** Upper clamp for quantized radii (px) so extreme values never thrash cache slots. */
    internal const val MAX_RADIUS_PX: Int = 512

    /**
     * Pure, host-tested quantization from pixel radius to cache bucket key.
     * Non-positive radii collapse to `0`; positive radii round up to an even
     * bucket in `[2, MAX_RADIUS_PX]`.
     */
    fun quantizeRadiusPx(radiusPx: Int): Int {
        if (radiusPx <= 0) return 0
        val clamped = radiusPx.coerceAtMost(MAX_RADIUS_PX)
        return if (QUANTIZE_STEP_PX <= 1) {
            clamped
        } else {
            ((clamped + QUANTIZE_STEP_PX - 1) / QUANTIZE_STEP_PX) * QUANTIZE_STEP_PX
        }
    }

    /**
     * Pure, host-tested quantization for floating-point pixel radii (e.g. from
     * `UiDimens.dpF` or `ArtTileMetrics.clampedCornerRadiusPx`).
     */
    fun quantizeRadiusPx(radiusPx: Float): Int {
        if (radiusPx.isNaN() || radiusPx <= 0f) return 0
        if (radiusPx >= MAX_RADIUS_PX.toFloat()) return MAX_RADIUS_PX
        return quantizeRadiusPx(round(radiusPx).toInt())
    }

    /**
     * Pure, host-tested bounded LRU store backing [roundedRect], parameterized
     * over provider type so JVM unit tests can verify quantization, reuse, and
     * eviction bounds without Android framework stubs.
     */
    internal class RoundedOutlineStore<P : Any>(
        val maxEntries: Int = MAX_ROUNDED_RECT_ENTRIES,
        private val createProvider: (Int) -> P,
    ) {
        private val cache = TileBackgrounds.BoundedTemplateCache<Int, P>(maxEntries)

        fun get(radiusPx: Int): P {
            val key = quantizeRadiusPx(radiusPx)
            return cache.getOrPut(key) { createProvider(key) }
        }

        fun get(radiusPx: Float): P = get(quantizeRadiusPx(radiusPx))

        fun size(): Int = cache.size()

        fun hitCount(): Long = cache.hitCount()

        fun missCount(): Long = cache.missCount()

        fun containsQuantizedRadius(quantizedPx: Int): Boolean = cache.containsKey(quantizedPx)

        fun clear() = cache.clear()
    }

    // Lazy publication keeps OutlineCache.<clinit> free of Android framework calls
    // so pure JVM unit tests can exercise quantization and store invariants directly.
    /** Shared oval/circular outline provider (zero Context/View references). */
    val OVAL: ViewOutlineProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
        object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
    }

    private val roundedStore =
        RoundedOutlineStore(MAX_ROUNDED_RECT_ENTRIES, ::createRoundedRectProvider)

    /**
     * Return a shared rounded-rectangle [ViewOutlineProvider] for [radiusPx]
     * (quantized and cached in a bounded LRU map with zero Context/View refs).
     */
    fun roundedRect(radiusPx: Int): ViewOutlineProvider = roundedStore.get(radiusPx)

    /**
     * Float convenience overload of [roundedRect] for `dpF` / clamped pixel radii.
     */
    fun roundedRect(radiusPx: Float): ViewOutlineProvider = roundedStore.get(radiusPx)

    private fun createRoundedRectProvider(quantizedRadiusPx: Int): ViewOutlineProvider {
        val radiusF = quantizedRadiusPx.toFloat()
        return object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (radiusF <= 0f) {
                    outline.setRect(0, 0, view.width, view.height)
                } else {
                    outline.setRoundRect(0, 0, view.width, view.height, radiusF)
                }
            }
        }
    }
}
