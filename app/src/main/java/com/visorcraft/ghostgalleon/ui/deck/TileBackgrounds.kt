package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import com.visorcraft.ghostgalleon.art.ArtTileMetrics
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.UiDimens

object TileBackgrounds {

    private const val CHROME_BUTTON_FILL_ALPHA = 0xCC
    private const val CHROME_RIPPLE_COLOR = 0x44FFFFFF
    private const val STATUS_PILL_ALPHA = 0xD9

    internal const val CARD_CACHE_MAX_ENTRIES: Int = 12
    internal const val SELECTED_CACHE_MAX_ENTRIES: Int = 16
    internal const val BLANK_SLOT_CACHE_MAX_ENTRIES: Int = 12
    internal const val CHIP_IDLE_CACHE_MAX_ENTRIES: Int = 12
    internal const val SUNKEN_FIELD_CACHE_MAX_ENTRIES: Int = 12
    internal const val PILL_CACHE_MAX_ENTRIES: Int = 8
    internal const val STATUS_PILL_CACHE_MAX_ENTRIES: Int = 8
    internal const val ART_BACKING_CACHE_MAX_ENTRIES: Int = 16

    /**
     * Pure, thread-safe, host-tested bounded LRU cache for immutable templates.
     * Stores only value keys and template instances — never any [Context] or [View].
     */
    internal class BoundedTemplateCache<K : Any, V : Any>(
        val maxEntries: Int,
    ) {
        init {
            require(maxEntries > 0) { "maxEntries must be > 0, got $maxEntries" }
        }

        private val lock = Any()
        private val map = object : LinkedHashMap<K, V>(maxEntries + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
                size > maxEntries
        }
        private var hits = 0L
        private var misses = 0L

        fun getOrPut(key: K, create: () -> V): V = synchronized(lock) {
            val existing = map[key]
            if (existing != null) {
                hits++
                return existing
            }
            misses++
            val created = create()
            map[key] = created
            created
        }

        fun size(): Int = synchronized(lock) { map.size }

        fun hitCount(): Long = synchronized(lock) { hits }

        fun missCount(): Long = synchronized(lock) { misses }

        fun containsKey(key: K): Boolean = synchronized(lock) { map.containsKey(key) }

        fun clear() = synchronized(lock) {
            map.clear()
            hits = 0L
            misses = 0L
        }
    }

    /**
     * Pure cache key for the hot-path [card] drawable template (accent-free so
     * custom accent selections never ping-pong invalidate [card]). Host-tested.
     */
    internal data class CardSurfaceKey(
        val themeId: String,
        val fill: Int,
        val hairline: Int,
        val radiusPx: Float,
        val density: Float,
    )

    /**
     * Pure cache key for the hot-path [selected] drawable template (surface +
     * accent stroke). Separate from [CardSurfaceKey] so `card()` and
     * `selected()` never evict each other on custom accent colors. Host-tested.
     */
    internal data class SelectedSurfaceKey(
        val surface: CardSurfaceKey,
        val accent: Int,
    )

    /**
     * Pure cache key for stroked rounded-rect templates ([blankSlot], [chipIdle],
     * [sunkenField], [statusPill], [artBacking]). Holds only primitive visual
     * tokens — zero [Context] references. Host-tested.
     */
    internal data class RoundedRectTemplateKey(
        val fill: Int,
        val strokeColor: Int,
        val strokeWidthPx: Int,
        val radiusPx: Float,
    )

    private fun normalizeRadiusPx(radiusPx: Float): Float =
        if (radiusPx.isNaN() || radiusPx <= 0f) 0f else radiusPx

    private fun strokePxForDensity(density: Float, dpValue: Int): Int =
        (dpValue * density).toInt().coerceAtLeast(1)

    internal fun cardSurfaceKey(
        tokens: ThemeTokens,
        density: Float,
    ): CardSurfaceKey = CardSurfaceKey(
        themeId = tokens.id,
        fill = ThemePack.cardFill(tokens),
        hairline = ThemePack.hairlineDivider(tokens),
        radiusPx = normalizeRadiusPx(tokens.cardRadiusDp * density),
        density = density,
    )

    internal fun selectedSurfaceKey(
        tokens: ThemeTokens,
        accent: Int,
        density: Float,
    ): SelectedSurfaceKey = SelectedSurfaceKey(
        surface = cardSurfaceKey(tokens, density),
        accent = accent,
    )

    /** Slightly lifted surface fill for focused/selected tile cards ([selected]). */
    internal fun selectedFillColor(tokens: ThemeTokens): Int = ThemePack.surfaceElevated(tokens)

    /** Pure theme-synced visual tokens for [chromeButton]. Host-tested. */
    internal data class ChromeButtonStyle(
        val fill: Int,
        val idleStroke: Int,
        val focusStroke: Int,
    )

    internal fun chromeButtonStyle(
        tokens: ThemeTokens,
        accent: Int = tokens.accentColor,
    ): ChromeButtonStyle {
        val sunken = ThemePack.surfaceSunken(tokens)
        return ChromeButtonStyle(
            fill = ThemePack.withAlpha(sunken, CHROME_BUTTON_FILL_ALPHA),
            idleStroke = ThemePack.hairlineDivider(tokens),
            focusStroke = DeckThemeContrast.accessibleFocusStroke(accent, sunken),
        )
    }

    internal fun pillKey(
        tokens: ThemeTokens,
        density: Float,
    ): RoundedRectTemplateKey = RoundedRectTemplateKey(
        fill = ThemePack.cardFill(tokens),
        strokeColor = ThemePack.hairlineDivider(tokens),
        strokeWidthPx = strokePxForDensity(density, 1),
        radiusPx = normalizeRadiusPx(ThemePack.pillRadiusDp(tokens) * density),
    )

    internal fun blankSlotKey(
        tokens: ThemeTokens,
        density: Float,
        cornerRadiusDp: Int? = null,
    ): RoundedRectTemplateKey = RoundedRectTemplateKey(
        fill = ThemePack.surfaceSunken(tokens),
        strokeColor = ThemePack.HAIRLINE_SUBTLE,
        strokeWidthPx = strokePxForDensity(density, 1),
        radiusPx = normalizeRadiusPx((cornerRadiusDp ?: tokens.cardRadiusDp) * density),
    )

    internal fun chipIdleKey(
        tokens: ThemeTokens,
        density: Float,
        cornerRadiusDp: Int = 12,
    ): RoundedRectTemplateKey = RoundedRectTemplateKey(
        fill = tokens.chipIdle,
        strokeColor = ThemePack.hairlineDivider(tokens),
        strokeWidthPx = strokePxForDensity(density, 1),
        radiusPx = normalizeRadiusPx(cornerRadiusDp * density),
    )

    internal fun sunkenFieldKey(
        tokens: ThemeTokens,
        density: Float,
        cornerRadiusDp: Int = 12,
    ): RoundedRectTemplateKey = RoundedRectTemplateKey(
        fill = ThemePack.surfaceSunken(tokens),
        strokeColor = ThemePack.hairlineDivider(tokens),
        strokeWidthPx = strokePxForDensity(density, 1),
        radiusPx = normalizeRadiusPx(cornerRadiusDp * density),
    )

    internal fun statusPillKey(
        tokens: ThemeTokens,
        density: Float,
    ): RoundedRectTemplateKey = RoundedRectTemplateKey(
        fill = statusPillFill(ThemePack.cardFill(tokens)),
        strokeColor = ThemePack.hairlineDivider(tokens),
        strokeWidthPx = strokePxForDensity(density, 1),
        radiusPx = normalizeRadiusPx(24 * density),
    )

    internal fun artBackingKey(
        tokens: ThemeTokens,
        density: Float,
        cornerRadiusDp: Int = 24,
    ): RoundedRectTemplateKey = artBackingPxKey(
        tokens = tokens,
        density = density,
        cornerRadiusPx = cornerRadiusDp * density,
    )

    internal fun artBackingPxKey(
        tokens: ThemeTokens,
        density: Float,
        cornerRadiusPx: Float,
    ): RoundedRectTemplateKey = RoundedRectTemplateKey(
        fill = ThemePack.surfaceSunken(tokens),
        strokeColor = ThemePack.hairlineDivider(tokens),
        strokeWidthPx = ArtTileMetrics.backingStrokePx(density),
        radiusPx = normalizeRadiusPx(cornerRadiusPx),
    )

    /** Pure translucent status-pill fill derived from the active card fill. */
    internal fun statusPillFill(surfaceColor: Int): Int =
        (STATUS_PILL_ALPHA shl 24) or (surfaceColor and 0x00FFFFFF)

    // Multi-slot bounded LRU template caches (zero Context refs stored):
    // eliminates 1-slot thrashing across dual displays with differing densities,
    // radii, or contrast-adjusted accents while keeping memory strictly bounded.
    private val cardTemplateCache =
        BoundedTemplateCache<CardSurfaceKey, GradientDrawable>(CARD_CACHE_MAX_ENTRIES)
    private val selectedTemplateCache =
        BoundedTemplateCache<SelectedSurfaceKey, GradientDrawable>(SELECTED_CACHE_MAX_ENTRIES)
    private val blankSlotTemplateCache =
        BoundedTemplateCache<RoundedRectTemplateKey, GradientDrawable>(BLANK_SLOT_CACHE_MAX_ENTRIES)
    private val chipIdleTemplateCache =
        BoundedTemplateCache<RoundedRectTemplateKey, GradientDrawable>(CHIP_IDLE_CACHE_MAX_ENTRIES)
    private val sunkenFieldTemplateCache =
        BoundedTemplateCache<RoundedRectTemplateKey, GradientDrawable>(SUNKEN_FIELD_CACHE_MAX_ENTRIES)
    private val pillTemplateCache =
        BoundedTemplateCache<RoundedRectTemplateKey, GradientDrawable>(PILL_CACHE_MAX_ENTRIES)
    private val statusPillTemplateCache =
        BoundedTemplateCache<RoundedRectTemplateKey, GradientDrawable>(STATUS_PILL_CACHE_MAX_ENTRIES)
    private val artBackingTemplateCache =
        BoundedTemplateCache<RoundedRectTemplateKey, GradientDrawable>(ART_BACKING_CACHE_MAX_ENTRIES)

    private fun cloneTemplate(template: GradientDrawable): GradientDrawable =
        template.constantState?.newDrawable()?.mutate() as? GradientDrawable ?: template

    private fun buildRoundedRectTemplate(key: RoundedRectTemplateKey): GradientDrawable =
        GradientDrawable().apply {
            setColor(key.fill)
            cornerRadius = key.radiusPx
            setStroke(key.strokeWidthPx, key.strokeColor)
        }

    private fun tokens(context: Context): ThemeTokens {
        val app = context.applicationContext
        return if (app is com.visorcraft.ghostgalleon.GhostGalleonApp) {
            ThemePack.resolve(app.settings)
        } else {
            ThemePack.GHOST
        }
    }

    private fun ensureCardTemplate(context: Context): GradientDrawable {
        val t = tokens(context)
        val key = cardSurfaceKey(t, context.resources.displayMetrics.density)
        return cardTemplateCache.getOrPut(key) {
            GradientDrawable().apply {
                setColor(key.fill)
                cornerRadius = key.radiusPx
                setStroke(strokePxForDensity(key.density, 1), key.hairline)
            }
        }
    }

    private fun ensureSelectedTemplate(context: Context, accent: Int): GradientDrawable {
        val t = tokens(context)
        val key = selectedSurfaceKey(t, accent, context.resources.displayMetrics.density)
        val liftedFill = selectedFillColor(t)
        return selectedTemplateCache.getOrPut(key) {
            GradientDrawable().apply {
                setColor(liftedFill)
                cornerRadius = key.surface.radiusPx
                setStroke(strokePxForDensity(key.surface.density, 4), key.accent)
            }
        }
    }

    /** Primary card surface fill color for the active theme pack. */
    fun surfaceColor(context: Context): Int = ThemePack.cardFill(tokens(context))

    /** Recessed sunken surface fill color for inputs, tracks, and nav blades. */
    fun surfaceSunken(context: Context): Int = ThemePack.surfaceSunken(tokens(context))

    /** Slightly lifted card highlight surface color for active nav tiles and previews. */
    fun surfaceElevated(context: Context): Int = ThemePack.surfaceElevated(tokens(context))

    /** Subtle 1dp translucent divider/border color for the active theme pack. */
    fun hairlineDivider(context: Context): Int = ThemePack.hairlineDivider(tokens(context))

    /** Text color with legible contrast on top of a filled [accent] surface. */
    fun onAccentText(accent: Int): Int = ThemePack.onFillTextColor(accent)

    /** Text color derived from [accent] lifted to >= 4.5:1 on dark surfaces. */
    fun accentTextColor(
        accent: Int,
        surface: Int = ThemePack.DEFAULT_DARK_SURFACE,
    ): Int = ThemePack.accentTextColor(accent, surface)

    fun card(context: Context): GradientDrawable =
        cloneTemplate(ensureCardTemplate(context))

    fun selected(context: Context, accent: Int): GradientDrawable =
        cloneTemplate(ensureSelectedTemplate(context, accent))

    /** Idle chip fill from the active theme pack. */
    fun chipIdleColor(context: Context): Int = tokens(context).chipIdle

    /**
     * Rounded action chip (role / quick actions). [fill] defaults to theme
     * idle chip; pass accent for selected/primary CTAs.
     */
    fun chip(
        context: Context,
        fill: Int = chipIdleColor(context),
        cornerRadiusDp: Int = 10,
    ): GradientDrawable {
        val t = tokens(context)
        if (fill == t.chipIdle) {
            return chipIdle(context, cornerRadiusDp)
        }
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = UiDimens.dpF(context, cornerRadiusDp)
        }
    }

    /** Rounded idle pill/chip with subtle 1dp hairline border. */
    fun chipIdle(
        context: Context,
        cornerRadiusDp: Int = 12,
    ): GradientDrawable {
        val key = chipIdleKey(tokens(context), context.resources.displayMetrics.density, cornerRadiusDp)
        return cloneTemplate(chipIdleTemplateCache.getOrPut(key) { buildRoundedRectTemplate(key) })
    }

    /** Rounded active/selected pill/chip filled with [accent]. */
    fun chipSelected(
        context: Context,
        accent: Int,
        cornerRadiusDp: Int = 12,
    ): GradientDrawable = GradientDrawable().apply {
        setColor(accent)
        cornerRadius = UiDimens.dpF(context, cornerRadiusDp)
    }

    /**
     * In-place chip restyle for hot browse/role bars: mutates an existing
     * [GradientDrawable] color/stroke without allocating a new drawable on
     * every tap, falling back to [chipSelected]/[chipIdle] on first paint.
     */
    fun applyChipState(
        view: View,
        selected: Boolean,
        accent: Int,
        cornerRadiusDp: Int = 12,
    ) {
        val context = view.context
        val existing = view.background as? GradientDrawable
        if (existing == null) {
            view.background = if (selected) {
                chipSelected(context, accent, cornerRadiusDp)
            } else {
                chipIdle(context, cornerRadiusDp)
            }
            return
        }
        val t = tokens(context)
        existing.mutate()
        existing.cornerRadius = UiDimens.dpF(context, cornerRadiusDp)
        if (selected) {
            existing.setColor(accent)
            existing.setStroke(0, Color.TRANSPARENT)
        } else {
            existing.setColor(t.chipIdle)
            existing.setStroke(UiDimens.strokePx(context, 1), ThemePack.hairlineDivider(t))
        }
    }

    /**
     * Filled accent pill for primary CTAs (e.g. Resume). Pair with
     * [onAccentText] so light accents (OLED `#E0E0E0`) stay legible.
     */
    fun accentPill(context: Context, accent: Int, cornerRadiusDp: Int = 20): GradientDrawable =
        GradientDrawable().apply {
            setColor(accent)
            cornerRadius = UiDimens.dpF(context, cornerRadiusDp)
        }

    /** Rounded strip for dock / status containers with subtle 1dp hairline (LRU-cached). */
    fun pill(context: Context): GradientDrawable {
        val key = pillKey(tokens(context), context.resources.displayMetrics.density)
        return cloneTemplate(pillTemplateCache.getOrPut(key) { buildRoundedRectTemplate(key) })
    }

    /** Translucent floating pill for clock/battery status overlays. */
    fun statusPill(context: Context): GradientDrawable {
        val key = statusPillKey(tokens(context), context.resources.displayMetrics.density)
        return cloneTemplate(statusPillTemplateCache.getOrPut(key) { buildRoundedRectTemplate(key) })
    }

    /**
     * Recessed empty slot tile (grid/dock "+" placeholders) so populated tiles
     * read prominently above unoccupied slots.
     */
    fun blankSlot(
        context: Context,
        cornerRadiusDp: Int? = null,
    ): GradientDrawable {
        val key = blankSlotKey(tokens(context), context.resources.displayMetrics.density, cornerRadiusDp)
        return cloneTemplate(blankSlotTemplateCache.getOrPut(key) { buildRoundedRectTemplate(key) })
    }

    /**
     * Recessed rounded field for search inputs, segmented-control tracks,
     * and Settings navigation blades.
     */
    fun sunkenField(
        context: Context,
        cornerRadiusDp: Int = 12,
    ): GradientDrawable {
        val key = sunkenFieldKey(tokens(context), context.resources.displayMetrics.density, cornerRadiusDp)
        return cloneTemplate(sunkenFieldTemplateCache.getOrPut(key) { buildRoundedRectTemplate(key) })
    }

    /**
     * Cached dark sunken hairline-framed backing behind loaded cover artwork
     * ([com.visorcraft.ghostgalleon.art.ArtTile]), keyed by theme tokens,
     * density, and corner radius.
     */
    fun artBacking(
        context: Context,
        cornerRadiusDp: Int = 24,
    ): GradientDrawable = artBackingPx(context, UiDimens.dpF(context, cornerRadiusDp))

    /**
     * Pixel-radius overload of [artBacking] for tiles whose corner radius was
     * pre-clamped by [ArtTileMetrics.clampedCornerRadiusPx].
     */
    fun artBackingPx(
        context: Context,
        cornerRadiusPx: Float,
    ): GradientDrawable {
        val key = artBackingPxKey(
            tokens = tokens(context),
            density = context.resources.displayMetrics.density,
            cornerRadiusPx = cornerRadiusPx,
        )
        return cloneTemplate(artBackingTemplateCache.getOrPut(key) { buildRoundedRectTemplate(key) })
    }

    /**
     * Crisp accent focus outline for Settings rows, About chips, and modal
     * actions under gamepad navigation.
     */
    fun focusStroke(
        context: Context,
        accent: Int,
        fill: Int = Color.TRANSPARENT,
        cornerRadiusDp: Int = 12,
        strokeDp: Int = 2,
    ): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = UiDimens.dpF(context, cornerRadiusDp)
        setStroke(UiDimens.strokePx(context, strokeDp), accent)
    }

    /**
     * State-list background that shows [focusStroke] over [idleBackground]
     * while gamepad-focused and [idleBackground] (or transparent) otherwise.
     */
    fun focusableRow(
        context: Context,
        accent: Int,
        idleBackground: Drawable? = null,
        cornerRadiusDp: Int = 12,
        strokeDp: Int = 2,
    ): StateListDrawable {
        val ring = focusStroke(
            context = context,
            accent = accent,
            fill = Color.TRANSPARENT,
            cornerRadiusDp = cornerRadiusDp,
            strokeDp = strokeDp,
        )
        val focusedDrawable: Drawable = if (idleBackground != null) {
            val idleCopy = idleBackground.constantState?.newDrawable()?.mutate() ?: idleBackground
            LayerDrawable(arrayOf(idleCopy, ring))
        } else {
            ring
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focusedDrawable)
            addState(intArrayOf(), idleBackground ?: ColorDrawable(Color.TRANSPARENT))
        }
    }

    /**
     * Circular theme-synced translucent scrim + 1dp hairline + WCAG-accessible
     * focus accent ring + touch ripple for floating chrome icon buttons.
     */
    fun chromeButton(
        context: Context,
        accent: Int? = null,
    ): Drawable {
        val t = tokens(context)
        val resolvedAccent = accent ?: run {
            val app = context.applicationContext
            if (app is com.visorcraft.ghostgalleon.GhostGalleonApp) {
                app.settings.accentColor
            } else {
                t.accentColor
            }
        }
        val style = chromeButtonStyle(t, resolvedAccent)
        val idle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(style.fill)
            setStroke(UiDimens.strokePx(context, 1), style.idleStroke)
        }
        val focused = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(style.fill)
            setStroke(UiDimens.strokePx(context, 2), style.focusStroke)
        }
        val content = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), idle)
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(CHROME_RIPPLE_COLOR), content, mask)
    }

    /**
     * Combines [focusableRow] gamepad focus rings with a rounded touch [RippleDrawable]
     * mask so Settings rows, About actions, and Lab buttons give crisp feedback on
     * both touch and controller input.
     */
    fun pressableRow(
        context: Context,
        accent: Int,
        idleBackground: Drawable? = null,
        cornerRadiusDp: Int = 12,
        strokeDp: Int = 2,
    ): Drawable {
        val content = focusableRow(
            context = context,
            accent = accent,
            idleBackground = idleBackground,
            cornerRadiusDp = cornerRadiusDp,
            strokeDp = strokeDp,
        )
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = UiDimens.dpF(context, cornerRadiusDp)
        }
        val rippleColor = ThemePack.accentGlowColor(accent, alpha = 0x38)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    /**
     * Recessed value pill with a subtle luminance-adaptive accent hairline for
     * live slider readouts, counters, and keycap indicators.
     */
    fun valueBadge(
        context: Context,
        accent: Int,
        cornerRadiusDp: Int = UiDimens.RADIUS_SM,
    ): GradientDrawable {
        val t = tokens(context)
        return GradientDrawable().apply {
            setColor(ThemePack.surfaceSunken(t))
            cornerRadius = UiDimens.dpF(context, cornerRadiusDp)
            setStroke(UiDimens.strokePx(context, 1), ThemePack.accentHairline(accent))
        }
    }

    /**
     * Zero-allocation in-place badge restyle for live counters, deadzone pills,
     * and Browse Chrome power-rail counts: mutates an existing [GradientDrawable]
     * stroke without re-allocating on slider drags or toggle ticks.
     */
    fun applyStatusBadgeState(
        view: View,
        active: Boolean,
        accent: Int,
        cornerRadiusDp: Int = UiDimens.RADIUS_SM,
    ) {
        val context = view.context
        val existing = view.background as? GradientDrawable
        if (existing == null) {
            view.background = if (active) {
                valueBadge(context, accent, cornerRadiusDp)
            } else {
                sunkenField(context, cornerRadiusDp)
            }
            return
        }
        val t = tokens(context)
        existing.mutate()
        existing.setColor(ThemePack.surfaceSunken(t))
        existing.cornerRadius = UiDimens.dpF(context, cornerRadiusDp)
        existing.setStroke(
            UiDimens.strokePx(context, 1),
            if (active) ThemePack.accentHairline(accent) else ThemePack.hairlineDivider(t),
        )
    }

    /** Vertical rounded pill bar (e.g. 3dp active indicator on left nav blade). */
    fun activeAccentBar(
        context: Context,
        accent: Int,
        cornerRadiusDp: Int = 2,
    ): GradientDrawable = GradientDrawable().apply {
        setColor(accent)
        cornerRadius = UiDimens.dpF(context, cornerRadiusDp)
    }

    /**
     * Subtle radial or top-down translucent accent aura for brand emblems and
     * hero framing cards.
     */
    fun accentGlow(
        context: Context,
        accent: Int,
        cornerRadiusDp: Int = 24,
        radial: Boolean = true,
        peakAlpha: Int = 0x44,
    ): GradientDrawable = GradientDrawable().apply {
        val peak = ThemePack.accentGlowColor(accent, peakAlpha)
        val transparent = ThemePack.withAlpha(accent, 0)
        colors = intArrayOf(peak, transparent)
        if (radial) {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = UiDimens.dpF(context, cornerRadiusDp * 2).coerceAtLeast(1f)
            setGradientCenter(0.5f, 0.5f)
        } else {
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }
        cornerRadius = UiDimens.dpF(context, cornerRadiusDp)
    }
}
