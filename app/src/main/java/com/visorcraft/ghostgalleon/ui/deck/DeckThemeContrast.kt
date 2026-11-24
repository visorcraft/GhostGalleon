package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.ThemePack
import kotlin.math.ceil

/**
 * Pure, host-tested WCAG 2.1 contrast helper for Track A focus rings and
 * surface accents across Ghost, Teal, OLED, Neon, and custom theme packs.
 *
 * Guarantees non-text focus borders meet WCAG 1.4.11 (`>= 3.0:1`) over card
 * surfaces without altering accents that already clear the threshold.
 */
object DeckThemeContrast {

    /** WCAG 2.1 SC 1.4.11 minimum contrast for non-text focus indicators. */
    const val MIN_NON_TEXT_CONTRAST: Double = 3.0

    /** Small safety margin so 8-bit integer rounding never dips under 3.0:1. */
    private const val TARGET_NON_TEXT_CONTRAST: Double = 3.15

    /**
     * Return opaque [accent] unchanged when it already meets [minRatio] over
     * [surfaceFill]; otherwise lift along exact RGB brightness (then gently
     * toward white if needed) until it meets [minRatio].
     */
    fun accessibleFocusStroke(
        accent: Int,
        surfaceFill: Int,
        minRatio: Double = MIN_NON_TEXT_CONTRAST,
    ): Int {
        val opaque = (0xFF shl 24) or (accent and 0x00FFFFFF)
        val bg = (0xFF shl 24) or (surfaceFill and 0x00FFFFFF)
        if (ThemePack.contrastRatio(opaque, bg) >= minRatio) return opaque

        val target = maxOf(minRatio, TARGET_NON_TEXT_CONTRAST)
        // If surface itself is light (custom light theme), darken toward black.
        if (ThemePack.relativeLuminance(bg) >= GridTileFeedbackMetrics.LIGHT_SURFACE_LUMINANCE) {
            return darkenUntilContrast(opaque, bg, minRatio, target)
        }
        return liftDarkSurfaceColor(opaque, bg, minRatio, target)
    }

    private fun liftDarkSurfaceColor(
        opaque: Int,
        bg: Int,
        minRatio: Double,
        target: Double,
    ): Int {
        val r0 = (opaque shr 16) and 0xFF
        val g0 = (opaque shr 8) and 0xFF
        val b0 = opaque and 0xFF
        val maxCh = maxOf(r0, maxOf(g0, b0))

        fun pack(r: Int, g: Int, b: Int): Int =
            (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or
                (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

        val base = if (maxCh > 0) {
            val maxScale = 255.0 / maxCh
            val brightest = pack(
                ceil(r0 * maxScale).toInt(),
                ceil(g0 * maxScale).toInt(),
                ceil(b0 * maxScale).toInt(),
            )
            if (ThemePack.contrastRatio(brightest, bg) >= minRatio) {
                var lo = 1.0
                var hi = maxScale
                var best = brightest
                repeat(16) {
                    val mid = (lo + hi) * 0.5
                    val cand = pack(
                        ceil(r0 * mid).toInt(),
                        ceil(g0 * mid).toInt(),
                        ceil(b0 * mid).toInt(),
                    )
                    if (ThemePack.contrastRatio(cand, bg) >= target) {
                        best = cand
                        hi = mid
                    } else {
                        lo = mid
                    }
                }
                return if (ThemePack.contrastRatio(best, bg) >= minRatio) best else brightest
            }
            brightest
        } else {
            opaque
        }

        val br = (base shr 16) and 0xFF
        val bgCh = (base shr 8) and 0xFF
        val bb = base and 0xFF
        var lo = 0.0
        var hi = 1.0
        var best = 0xFFFFFFFF.toInt()
        repeat(16) {
            val mid = (lo + hi) * 0.5
            val cand = pack(
                ceil(br + (255 - br) * mid).toInt(),
                ceil(bgCh + (255 - bgCh) * mid).toInt(),
                ceil(bb + (255 - bb) * mid).toInt(),
            )
            if (ThemePack.contrastRatio(cand, bg) >= target) {
                best = cand
                hi = mid
            } else {
                lo = mid
            }
        }
        return if (ThemePack.contrastRatio(best, bg) >= minRatio) best else 0xFFFFFFFF.toInt()
    }

    private fun darkenUntilContrast(
        opaque: Int,
        bg: Int,
        minRatio: Double,
        target: Double,
    ): Int {
        var lo = 0.0
        var hi = 1.0
        var best = 0xFF000000.toInt()
        repeat(16) {
            val mid = (lo + hi) * 0.5
            val cand = MoveChoreography.blendRgb(opaque, 0xFF000000.toInt(), mid.toFloat())
            if (ThemePack.contrastRatio(cand, bg) >= target) {
                best = cand
                hi = mid
            } else {
                lo = mid
            }
        }
        return if (ThemePack.contrastRatio(best, bg) >= minRatio) best else 0xFF000000.toInt()
    }
}
