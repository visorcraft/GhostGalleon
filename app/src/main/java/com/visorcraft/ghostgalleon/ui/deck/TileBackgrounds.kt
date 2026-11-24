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
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.UiDimens

object TileBackgrounds {

    private const val CHROME_BUTTON_FILL = 0xB316161C.toInt()
    private const val CHROME_BUTTON_STROKE = 0x33FFFFFF
    private const val CHROME_RIPPLE_COLOR = 0x44FFFFFF
    private const val STATUS_PILL_ALPHA = 0xD9
    private const val STATUS_PILL_STROKE = 0x28FFFFFF

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

    internal fun cardSurfaceKey(
        tokens: ThemeTokens,
        density: Float,
    ): CardSurfaceKey = CardSurfaceKey(
        themeId = tokens.id,
        fill = ThemePack.cardFill(tokens),
        hairline = ThemePack.hairlineDivider(tokens),
        radiusPx = tokens.cardRadiusDp * density,
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

    /** Pure translucent status-pill fill derived from the active card fill. */
    internal fun statusPillFill(surfaceColor: Int): Int =
        (STATUS_PILL_ALPHA shl 24) or (surfaceColor and 0x00FFFFFF)

    // Selection is the hottest path (every NAV tick). Cache card and selected
    // templates independently per theme surface / accent / density; return
    // constantState copies so views never share mutable bounds/alpha.
    private var cachedCardKey: CardSurfaceKey? = null
    private var cachedCard: GradientDrawable? = null
    private var cachedSelectedKey: SelectedSurfaceKey? = null
    private var cachedSelected: GradientDrawable? = null

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
        val existing = cachedCard
        if (cachedCardKey == key && existing != null) {
            return existing
        }
        val built = GradientDrawable().apply {
            setColor(key.fill)
            cornerRadius = key.radiusPx
            setStroke(UiDimens.strokePx(context, 1), key.hairline)
        }
        cachedCardKey = key
        cachedCard = built
        return built
    }

    private fun ensureSelectedTemplate(context: Context, accent: Int): GradientDrawable {
        val t = tokens(context)
        val key = selectedSurfaceKey(t, accent, context.resources.displayMetrics.density)
        val existing = cachedSelected
        if (cachedSelectedKey == key && existing != null) {
            return existing
        }
        val built = GradientDrawable().apply {
            setColor(key.surface.fill)
            cornerRadius = key.surface.radiusPx
            setStroke(UiDimens.strokePx(context, 4), accent)
        }
        cachedSelectedKey = key
        cachedSelected = built
        return built
    }

    /** Primary card surface fill color for the active theme pack. */
    fun surfaceColor(context: Context): Int = ThemePack.cardFill(tokens(context))

    /** Recessed sunken surface fill color for inputs, tracks, and nav blades. */
    fun surfaceSunken(context: Context): Int = ThemePack.surfaceSunken(tokens(context))

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
        ensureCardTemplate(context).constantState!!.newDrawable().mutate() as GradientDrawable

    fun selected(context: Context, accent: Int): GradientDrawable =
        ensureSelectedTemplate(context, accent).constantState!!.newDrawable().mutate() as GradientDrawable

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
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = UiDimens.dpF(context, cornerRadiusDp)
            if (fill == t.chipIdle) {
                setStroke(UiDimens.strokePx(context, 1), ThemePack.hairlineDivider(t))
            }
        }
    }

    /** Rounded idle pill/chip with subtle 1dp hairline border. */
    fun chipIdle(
        context: Context,
        cornerRadiusDp: Int = 12,
    ): GradientDrawable {
        val t = tokens(context)
        return GradientDrawable().apply {
            setColor(t.chipIdle)
            cornerRadius = UiDimens.dpF(context, cornerRadiusDp)
            setStroke(UiDimens.strokePx(context, 1), ThemePack.hairlineDivider(t))
        }
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

    /** Rounded strip for dock / status containers with subtle 1dp hairline. */
    fun pill(context: Context): GradientDrawable {
        val t = tokens(context)
        return GradientDrawable().apply {
            setColor(ThemePack.cardFill(t))
            cornerRadius = UiDimens.dpF(context, ThemePack.pillRadiusDp(t))
            setStroke(UiDimens.strokePx(context, 1), ThemePack.hairlineDivider(t))
        }
    }

    /** Translucent floating pill for clock/battery status overlays. */
    fun statusPill(context: Context): GradientDrawable {
        val t = tokens(context)
        return GradientDrawable().apply {
            setColor(statusPillFill(ThemePack.cardFill(t)))
            cornerRadius = UiDimens.dpF(context, 24)
            setStroke(UiDimens.strokePx(context, 1), STATUS_PILL_STROKE)
        }
    }

    /**
     * Recessed empty slot tile (grid/dock "+" placeholders) so populated tiles
     * read prominently above unoccupied slots.
     */
    fun blankSlot(
        context: Context,
        cornerRadiusDp: Int? = null,
    ): GradientDrawable {
        val t = tokens(context)
        val radius = cornerRadiusDp ?: t.cardRadiusDp
        return GradientDrawable().apply {
            setColor(ThemePack.surfaceSunken(t))
            cornerRadius = UiDimens.dpF(context, radius)
            setStroke(UiDimens.strokePx(context, 1), ThemePack.HAIRLINE_SUBTLE)
        }
    }

    /**
     * Recessed rounded field for search inputs, segmented-control tracks,
     * and Settings navigation blades.
     */
    fun sunkenField(
        context: Context,
        cornerRadiusDp: Int = 12,
    ): GradientDrawable {
        val t = tokens(context)
        return GradientDrawable().apply {
            setColor(ThemePack.surfaceSunken(t))
            cornerRadius = UiDimens.dpF(context, cornerRadiusDp)
            setStroke(UiDimens.strokePx(context, 1), ThemePack.hairlineDivider(t))
        }
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
     * Circular translucent scrim + 1dp hairline + focus accent ring + touch
     * ripple for floating chrome icon buttons (Swap, Settings, header icons).
     */
    fun chromeButton(
        context: Context,
        accent: Int? = null,
    ): Drawable {
        val resolvedAccent = accent ?: run {
            val app = context.applicationContext
            if (app is com.visorcraft.ghostgalleon.GhostGalleonApp) {
                app.settings.accentColor
            } else {
                ThemePack.GHOST.accentColor
            }
        }
        val idle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(CHROME_BUTTON_FILL)
            setStroke(UiDimens.strokePx(context, 1), CHROME_BUTTON_STROKE)
        }
        val focused = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(CHROME_BUTTON_FILL)
            setStroke(UiDimens.strokePx(context, 2), resolvedAccent)
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
}
