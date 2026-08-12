package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.graphics.drawable.GradientDrawable
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.UiDimens

object TileBackgrounds {

    private const val FALLBACK_FILL = 0xFF1C1C22.toInt()

    // Selection is the hottest path (every NAV tick). Cache templates per
    // theme/accent; return constantState copies so views never share.
    private var cachedThemeId: String? = null
    private var cachedAccent: Int = 0
    private var cachedRadius: Float = -1f
    private var cachedCard: GradientDrawable? = null
    private var cachedSelected: GradientDrawable? = null

    private fun tokens(context: Context): ThemeTokens {
        val app = context.applicationContext
        return if (app is com.visorcraft.ghostgalleon.GhostGalleonApp) {
            ThemePack.resolve(app.settings)
        } else {
            ThemePack.GHOST
        }
    }

    private fun ensureCardCache(context: Context, accent: Int) {
        val t = tokens(context)
        val radius = UiDimens.dpF(context, t.cardRadiusDp)
        if (cachedThemeId == t.id &&
            cachedAccent == accent &&
            cachedRadius == radius &&
            cachedCard != null &&
            cachedSelected != null
        ) {
            return
        }
        val fill = if (t.id == ThemePack.GHOST.id) FALLBACK_FILL else t.panelLift
        cachedCard = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
        }
        cachedSelected = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            setStroke(UiDimens.dpF(context, 4).toInt(), accent)
        }
        cachedThemeId = t.id
        cachedAccent = accent
        cachedRadius = radius
    }

    fun card(context: Context): GradientDrawable {
        val t = tokens(context)
        ensureCardCache(context, t.accentColor)
        return cachedCard!!.constantState!!.newDrawable().mutate() as GradientDrawable
    }

    fun selected(context: Context, accent: Int): GradientDrawable {
        ensureCardCache(context, accent)
        return cachedSelected!!.constantState!!.newDrawable().mutate() as GradientDrawable
    }

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
    ): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = UiDimens.dpF(context, cornerRadiusDp)
    }

    /**
     * Filled accent pill for primary CTAs (e.g. Resume). White text on top.
     * Avoids the old "dark card + accent stroke + black text" which was
     * unreadable on the secondary OLED.
     */
    fun accentPill(context: Context, accent: Int, cornerRadiusDp: Int = 20): GradientDrawable =
        chip(context, fill = accent, cornerRadiusDp = cornerRadiusDp)

    /** Rounded strip for dock / status containers. */
    fun pill(context: Context): GradientDrawable {
        val t = tokens(context)
        return GradientDrawable().apply {
            setColor(if (t.id == ThemePack.GHOST.id) FALLBACK_FILL else t.panelLift)
            cornerRadius = UiDimens.dpF(context, (t.cardRadiusDp + 4).coerceAtMost(32))
        }
    }
}
