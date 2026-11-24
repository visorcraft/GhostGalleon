package com.visorcraft.ghostgalleon.settings

import java.util.concurrent.ConcurrentHashMap

/**
 * Visual theme tokens applied across decks/settings. Pure; host-tested.
 * Built-in packs cover Ghost (current), Teal, OLED pure black, Neon.
 */
data class ThemeTokens(
    val id: String,
    val displayName: String,
    val accentColor: Int,
    val cardRadiusDp: Int = 24,
    val panelLift: Int = 0xFF202028.toInt(),
    val chipIdle: Int = 0xFF2A2A32.toInt(),
    val heroRain: Boolean = true,
    val fontScale: Float = 1f,
)

object ThemePack {

    val GHOST = ThemeTokens(
        id = "ghost",
        displayName = "Ghost",
        accentColor = 0xFF3F51B5.toInt(),
        heroRain = true,
    )
    val THREEDS = ThemeTokens(
        id = "threeds",
        displayName = "Teal",
        accentColor = 0xFF00A8A0.toInt(),
        panelLift = 0xFF0D2A2A.toInt(),
        chipIdle = 0xFF143838.toInt(),
        heroRain = false,
    )
    val OLED = ThemeTokens(
        id = "oled",
        displayName = "OLED Black",
        accentColor = 0xFFE0E0E0.toInt(),
        panelLift = 0xFF101010.toInt(),
        chipIdle = 0xFF1A1A1A.toInt(),
        cardRadiusDp = 12,
        heroRain = false,
    )
    val NEON = ThemeTokens(
        id = "neon",
        displayName = "Neon",
        accentColor = 0xFFFF2D95.toInt(),
        panelLift = 0xFF1A0A20.toInt(),
        chipIdle = 0xFF2A1038.toInt(),
        heroRain = true,
        fontScale = 1.05f,
    )

    val BUILTINS: List<ThemeTokens> = listOf(GHOST, THREEDS, OLED, NEON)

    /** Default card surface fill for the built-in Ghost theme. */
    const val DEFAULT_CARD_FILL: Int = 0xFF1C1C22.toInt()

    /** Baseline dark surface used when checking accent-on-dark legibility. */
    const val DEFAULT_DARK_SURFACE: Int = DEFAULT_CARD_FILL

    /** Standard subtle 1dp hairline on dark cards/pills (~10% white). */
    const val HAIRLINE_LIGHT: Int = 0x1AFFFFFF

    /** Faint outline for recessed blank slots and secondary dividers (~8% white). */
    const val HAIRLINE_SUBTLE: Int = 0x14FFFFFF

    /** WCAG AA minimum contrast ratio for normal-size text. */
    const val MIN_TEXT_CONTRAST: Double = 4.5

    /** Relative luminance cutoff above which accent glows/tints dampen alpha (e.g. OLED `#E0E0E0`). */
    const val HIGH_LUMINANCE_THRESHOLD: Double = 0.55

    /** Upper bound on memoized `(accent, surface) -> color` entries in [accentTextColor]. */
    internal const val ACCENT_TEXT_CACHE_MAX_SIZE: Int = 256

    // Precomputed sRGB channel linearization table (bit-exact with WCAG 2.1 formula;
    // eliminates runtime Math.pow calls across all contrast & luminance helpers).
    private val SRGB_LUMINANCE_LUT: DoubleArray = DoubleArray(256) { c ->
        val s = c / 255.0
        if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }

    private class ResolvedCacheEntry(
        val themePackId: String,
        val themeCustomJson: String?,
        val tokens: ThemeTokens,
    )

    @Volatile
    private var lastResolved: ResolvedCacheEntry? = null

    private val accentTextCache = ConcurrentHashMap<Long, Int>()

    internal fun accentTextCacheSize(): Int = accentTextCache.size

    internal fun clearCachesForTest() {
        lastResolved = null
        accentTextCache.clear()
    }

    fun byId(id: String?): ThemeTokens =
        BUILTINS.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: GHOST

    /**
     * Text on a filled chip. Light fills (OLED accent) keep black;
     * mid/dark accents (Ghost, Teal, Neon) get white.
     */
    fun onFillTextColor(fill: Int): Int {
        val r = (fill shr 16) and 0xFF
        val g = (fill shr 8) and 0xFF
        val b = fill and 0xFF
        val luma = (r + r + b + g + g + g) / 6
        return if (luma >= 150) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }

    /** WCAG 2.1 relative luminance in `[0.0, 1.0]` for the opaque RGB channels. */
    fun relativeLuminance(color: Int): Double {
        val r = SRGB_LUMINANCE_LUT[(color shr 16) and 0xFF]
        val g = SRGB_LUMINANCE_LUT[(color shr 8) and 0xFF]
        val b = SRGB_LUMINANCE_LUT[color and 0xFF]
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** WCAG 2.1 contrast ratio in `[1.0, 21.0]` between opaque [fg] and [bg]. */
    fun contrastRatio(fg: Int, bg: Int): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * Legible accent color for text drawn over a dark [surface] (defaults to
     * [DEFAULT_DARK_SURFACE] `#1C1C22`).
     *
     * Accents already meeting WCAG AA (`>= 4.5:1`) are returned opaque as-is.
     * Darker accents (e.g. Ghost `#3F51B5`) are lifted first along brightness
     * (preserving HSV hue + saturation) and then linearly toward white
     * (preserving exact HSV hue) until contrast reaches at least `4.5:1`.
     */
    fun accentTextColor(accent: Int, surface: Int = DEFAULT_DARK_SURFACE): Int {
        val opaque = (0xFF shl 24) or (accent and 0x00FFFFFF)
        val bg = (0xFF shl 24) or (surface and 0x00FFFFFF)
        val cacheKey = (opaque.toLong() shl 32) or (bg.toLong() and 0xFFFFFFFFL)
        accentTextCache[cacheKey]?.let { return it }

        val computed = computeAccentTextColor(opaque, bg)
        if (accentTextCache.size >= ACCENT_TEXT_CACHE_MAX_SIZE) {
            accentTextCache.clear()
        }
        accentTextCache[cacheKey] = computed
        return computed
    }

    private fun computeAccentTextColor(opaque: Int, bg: Int): Int {
        val bgLum = relativeLuminance(bg)
        fun ratioWithBg(fg: Int): Double {
            val l1 = relativeLuminance(fg)
            val hi = maxOf(l1, bgLum)
            val lo = minOf(l1, bgLum)
            return (hi + 0.05) / (lo + 0.05)
        }

        if (ratioWithBg(opaque) >= MIN_TEXT_CONTRAST) return opaque

        val r0 = (opaque shr 16) and 0xFF
        val g0 = (opaque shr 8) and 0xFF
        val b0 = opaque and 0xFF
        val maxCh = maxOf(r0, maxOf(g0, b0))
        // Aim slightly above 4.5 so integer channel quantization stays >= 4.5.
        val target = 4.6

        fun packRgb(r: Int, g: Int, b: Int): Int =
            (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or
                (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

        // Stage 1: pure brightness lift (exact HSV hue & saturation preserved).
        val baseColor = if (maxCh > 0) {
            val maxScale = 255.0 / maxCh
            val brightest = packRgb(
                kotlin.math.ceil(r0 * maxScale).toInt(),
                kotlin.math.ceil(g0 * maxScale).toInt(),
                kotlin.math.ceil(b0 * maxScale).toInt(),
            )
            if (ratioWithBg(brightest) >= MIN_TEXT_CONTRAST) {
                var lo = 1.0
                var hi = maxScale
                var best = brightest
                repeat(18) {
                    val mid = (lo + hi) * 0.5
                    val cand = packRgb(
                        kotlin.math.ceil(r0 * mid).toInt(),
                        kotlin.math.ceil(g0 * mid).toInt(),
                        kotlin.math.ceil(b0 * mid).toInt(),
                    )
                    if (ratioWithBg(cand) >= target) {
                        best = cand
                        hi = mid
                    } else {
                        lo = mid
                    }
                }
                return if (ratioWithBg(best) >= MIN_TEXT_CONTRAST) best else brightest
            }
            brightest
        } else {
            opaque
        }

        // Stage 2: linear tint toward white (exact HSV hue preserved).
        val br = (baseColor shr 16) and 0xFF
        val bgCh = (baseColor shr 8) and 0xFF
        val bb = baseColor and 0xFF
        var lo = 0.0
        var hi = 1.0
        var best = 0xFFFFFFFF.toInt()
        repeat(18) {
            val mid = (lo + hi) * 0.5
            val cand = packRgb(
                kotlin.math.ceil(br + (255 - br) * mid).toInt(),
                kotlin.math.ceil(bgCh + (255 - bgCh) * mid).toInt(),
                kotlin.math.ceil(bb + (255 - bb) * mid).toInt(),
            )
            if (ratioWithBg(cand) >= target) {
                best = cand
                hi = mid
            } else {
                lo = mid
            }
        }
        return if (ratioWithBg(best) >= MIN_TEXT_CONTRAST) best else 0xFFFFFFFF.toInt()
    }

    /** Primary card fill for [tokens] (preserves Ghost's `#1C1C22` card fill). */
    fun cardFill(tokens: ThemeTokens): Int =
        if (tokens.id == GHOST.id && tokens.panelLift == GHOST.panelLift) {
            DEFAULT_CARD_FILL
        } else {
            tokens.panelLift
        }

    /**
     * Recessed sunken surface derived from [cardFill] for search fields,
     * segmented tracks, nav blades, blank slots, and art backing.
     */
    fun surfaceSunken(tokens: ThemeTokens): Int {
        val fill = cardFill(tokens)
        val scale = if (tokens.id == OLED.id) 0.50f else 0.58f
        val r = (((fill shr 16) and 0xFF) * scale).toInt().coerceIn(0, 255)
        val g = (((fill shr 8) and 0xFF) * scale).toInt().coerceIn(0, 255)
        val b = ((fill and 0xFF) * scale).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Subtle 1dp translucent border/divider color for [tokens]. */
    fun hairlineDivider(tokens: ThemeTokens): Int =
        if (tokens.id == OLED.id) 0x22FFFFFF else HAIRLINE_LIGHT

    /** Corner radius (dp) for dock/status pills derived from [tokens]. */
    fun pillRadiusDp(tokens: ThemeTokens): Int =
        (tokens.cardRadiusDp + 4).coerceAtMost(32)

    /** Corner radius (dp) for compact chips derived from [tokens]. */
    fun chipRadiusDp(tokens: ThemeTokens): Int =
        (tokens.cardRadiusDp / 2).coerceIn(8, 14)

    /** Replaces the alpha byte of [color] with [alpha] clamped to `0..255`. */
    fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    /**
     * Optical concentric corner radius for a nested child inset by [insetDp]
     * inside a container with [outerRadiusDp], clamped to at least [minDp].
     */
    fun nestedRadiusDp(outerRadiusDp: Int, insetDp: Int, minDp: Int = 4): Int =
        (outerRadiusDp - insetDp.coerceAtLeast(0)).coerceAtLeast(minDp.coerceAtLeast(0))

    /**
     * Opaque linear RGB blend from [base] toward [overlay] by [overlayFraction]
     * (`0f` = [base], `1f` = [overlay]). Pure; host-tested.
     */
    fun blendColors(base: Int, overlay: Int, overlayFraction: Float): Int {
        val f = overlayFraction.coerceIn(0f, 1f)
        val inv = 1f - f
        val r = (((base shr 16) and 0xFF) * inv + ((overlay shr 16) and 0xFF) * f)
            .toInt().coerceIn(0, 255)
        val g = (((base shr 8) and 0xFF) * inv + ((overlay shr 8) and 0xFF) * f)
            .toInt().coerceIn(0, 255)
        val b = ((base and 0xFF) * inv + (overlay and 0xFF) * f)
            .toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Slightly lifted surface derived from [cardFill] for active nav tiles,
     * preview cards, and interactive highlighted panels.
     */
    fun surfaceElevated(tokens: ThemeTokens): Int {
        val fill = cardFill(tokens)
        val lift = if (tokens.id == OLED.id) 0.06f else 0.08f
        return blendColors(fill, 0xFFFFFFFF.toInt(), lift)
    }

    /** True when [accent] is bright enough (like OLED `#E0E0E0`) to dampen aura/tint alpha. */
    fun isHighLuminanceAccent(accent: Int): Boolean =
        relativeLuminance(accent) > HIGH_LUMINANCE_THRESHOLD

    /** Root OLED screen backdrop color sitting beneath [surfaceSunken] and [cardFill]. */
    @Suppress("UNUSED_PARAMETER")
    fun backdropColor(tokens: ThemeTokens): Int = 0xFF000000.toInt()

    /**
     * Luminance-adaptive translucent aura derived from [accent]. Bright accents
     * (such as OLED `#E0E0E0`) scale down alpha so glows stay refined on dark cards.
     */
    fun accentGlowColor(accent: Int, alpha: Int = 0x44): Int {
        val clamped = alpha.coerceIn(0, 255)
        val scaled = if (isHighLuminanceAccent(accent)) (clamped * 0.65).toInt() else clamped
        return withAlpha(accent, scaled)
    }

    /**
     * Luminance-adaptive translucent accent border for value badges, active
     * nav pills, and preview highlights.
     */
    fun accentHairline(accent: Int, alpha: Int = 0x66): Int {
        val clamped = alpha.coerceIn(0, 255)
        val scaled = if (isHighLuminanceAccent(accent)) (clamped * 0.72).toInt() else clamped
        return withAlpha(accent, scaled)
    }

    /**
     * Parse a theme pack JSON object. Accepts:
     * `{ "id":"…", "displayName":"…", "accentColor":"#FF2D95" or long,
     *    "cardRadiusDp":24, "panelLift":…, "chipIdle":…, "heroRain":true,
     *    "fontScale":1.0 }`
     * Returns null on unusable input.
     */
    fun parseJson(json: String): ThemeTokens? {
        val o = try {
            org.json.JSONObject(json)
        } catch (_: Exception) {
            return null
        }
        val id = o.optString("id", "").trim()
        if (id.isEmpty()) return null
        val accent = parseColor(o, "accentColor") ?: return null
        return ThemeTokens(
            id = id,
            displayName = o.optString("displayName", id).ifBlank { id },
            accentColor = accent,
            cardRadiusDp = o.optInt("cardRadiusDp", 24).coerceIn(4, 48),
            panelLift = parseColor(o, "panelLift") ?: 0xFF202028.toInt(),
            chipIdle = parseColor(o, "chipIdle") ?: 0xFF2A2A32.toInt(),
            heroRain = o.optBoolean("heroRain", true),
            fontScale = o.optDouble("fontScale", 1.0).toFloat().coerceIn(0.85f, 1.3f),
        )
    }

    /**
     * Apply [tokens] onto [base] settings: theme pack id + accent color.
     * Other visual knobs live on the tokens object for UI consumers.
     */
    fun applyToSettings(base: Settings, tokens: ThemeTokens): Settings =
        base.copy(
            themePackId = tokens.id,
            accentColor = tokens.accentColor,
            themeCustomJson = null,
        )

    /**
     * Apply imported custom tokens: store JSON for re-load and set id/accent.
     */
    fun applyCustom(base: Settings, tokens: ThemeTokens, rawJson: String): Settings =
        base.copy(
            themePackId = tokens.id,
            accentColor = tokens.accentColor,
            themeCustomJson = rawJson,
        )

    /**
     * Resolve tokens for ([themePackId], [themeCustomJson]) with a thread-safe
     * 1-entry volatile cache so repeated hot-path calls never re-parse custom JSON.
     */
    fun resolve(themePackId: String, themeCustomJson: String?): ThemeTokens {
        val cached = lastResolved
        if (cached != null &&
            cached.themePackId == themePackId &&
            cached.themeCustomJson == themeCustomJson
        ) {
            return cached.tokens
        }
        val resolved = themeCustomJson?.let { parseJson(it) } ?: byId(themePackId)
        lastResolved = ResolvedCacheEntry(themePackId, themeCustomJson, resolved)
        return resolved
    }

    /** Resolve tokens for current settings (custom JSON wins when valid). */
    fun resolve(settings: Settings): ThemeTokens =
        resolve(settings.themePackId, settings.themeCustomJson)

    private fun parseColor(o: org.json.JSONObject, key: String): Int? {
        if (!o.has(key) || o.isNull(key)) return null
        return when (val v = o.get(key)) {
            is Number -> v.toLong().toInt()
            is String -> {
                val s = v.trim().removePrefix("#")
                when (s.length) {
                    6 -> ("FF$s").toLongOrNull(16)?.toInt()
                    8 -> s.toLongOrNull(16)?.toInt()
                    else -> null
                }
            }
            else -> null
        }
    }
}
