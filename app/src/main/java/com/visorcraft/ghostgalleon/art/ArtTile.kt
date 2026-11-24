package com.visorcraft.ghostgalleon.art

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.view.children
import com.visorcraft.ghostgalleon.rom.HeroDetail
import com.visorcraft.ghostgalleon.rom.PlatformTile
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.ui.deck.OutlineCache
import com.visorcraft.ghostgalleon.ui.deck.TileBackgrounds

/**
 * ROM tile art with async cache fill: the PlatformTile placeholder with an
 * ImageView overlaid on top. When real artwork arrives (grid art or logo),
 * the bright platform placeholder hides behind a clean dark rounded backing so
 * non-square (e.g. 2:3) covers never reveal colored placeholder bars or text.
 * On reset/miss the placeholder restores immediately. Used by the grid,
 * carousel, dock, and companion hero.
 */
object ArtTile {

    private val FADE_INTERPOLATOR = DecelerateInterpolator()
    private val DETACH_ANIM_CANCEL = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            v.animate().cancel()
        }
    }

    /**
     * ROM tile art view: placeholder + overlay. [targetPx] is the decode
     * target (the view's pixel size); it only matters on the first decode —
     * the downscaled result is then served from the disk cache. [bindNow]
     * starts the async grid-art load immediately; pass false when the
     * caller binds a different art kind right away (the companion hero
     * binds HERO art itself, so a GRID bind here would double-decode).
     */
    fun view(
        context: Context,
        cache: ArtCache,
        rom: RomEntry,
        targetPx: Int,
        cornerRadiusDp: Int = 24,
        bindNow: Boolean = true,
        artOverrides: Map<String, String> = emptyMap(),
    ): View {
        val radiusPx = ArtTileMetrics.clampedCornerRadiusPx(
            cornerRadiusDp = cornerRadiusDp,
            density = context.resources.displayMetrics.density,
            targetPx = targetPx,
        )
        val frame = FrameLayout(context)
        frame.addView(
            PlatformTile.view(context, rom.platformId, cornerRadiusDp),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        val overlay = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            applyRoundedOutline(this, radiusPx)
            addOnAttachStateChangeListener(DETACH_ANIM_CANCEL)
        }
        frame.addView(overlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        if (bindNow) bind(overlay, cache, rom, targetPx, cornerRadiusDp, artOverrides)
        return frame
    }

    /** The overlay ImageView inside a frame built by [view], if any. */
    fun overlay(frame: ViewGroup): ImageView? =
        frame.children.filterIsInstance<ImageView>().firstOrNull()

    /** The [PlatformTile] placeholder TextView inside a frame built by [view], if any. */
    fun placeholder(frame: ViewGroup): TextView? =
        frame.children.filterIsInstance<TextView>().firstOrNull()

    /**
     * (Re)bind an overlay to a ROM: restores the placeholder and clears any
     * stale bitmap, then kicks the async load. The overlay's tag holds the
     * RomEntry id the pending load belongs to; the load is skipped pre-decode
     * and the result applied only when the tag still matches and the view is
     * attached (recycled cells, in-place hero swaps).
     */
    fun bind(
        overlay: ImageView,
        cache: ArtCache,
        rom: RomEntry,
        targetPx: Int,
        cornerRadiusDp: Int = 24,
        artOverrides: Map<String, String> = emptyMap(),
    ) {
        val context = overlay.context
        val radiusPx = ArtTileMetrics.clampedCornerRadiusPx(
            cornerRadiusDp = cornerRadiusDp,
            density = context.resources.displayMetrics.density,
            targetPx = targetPx,
        )
        if (overlay.tag == rom.id && overlay.drawable != null) {
            overlay.animate().cancel()
            overlay.alpha = 1f
            return
        }
        overlay.animate().cancel()
        overlay.alpha = 1f
        overlay.scaleType = ImageView.ScaleType.FIT_CENTER
        overlay.setPadding(0, 0, 0, 0)
        releaseDisplayed(overlay)
        overlay.setImageDrawable(null)
        overlay.tag = rom.id
        applyRoundedOutline(overlay, radiusPx)

        var syncGrid = true
        var syncPainted = false
        cache.load(
            context, rom, targetPx,
            artOverrides = artOverrides,
            isStillValid = { overlay.tag == rom.id },
        ) onGrid@{ bitmap ->
            // onResult is already main-thread; allow immediate paint on a
            // synchronous memory hit before first attach, guard async hits.
            if (overlay.tag != rom.id || (!syncGrid && !overlay.isAttachedToWindow)) {
                return@onGrid
            }
            if (bitmap != null) {
                val mode = ArtTileMetrics.revealMode(
                    wasSyncMemoryHit = syncGrid,
                    isAttached = overlay.isAttachedToWindow,
                )
                applyRounded(
                    overlay = overlay,
                    context = context,
                    bitmap = bitmap,
                    radiusPx = radiusPx,
                    targetPx = targetPx,
                    frameKind = ArtTileMetrics.ArtFrameKind.COVER_ART,
                    revealMode = mode,
                )
                if (syncGrid) syncPainted = true
                return@onGrid
            }
            // Logo fallback on the decode thread (no main-thread disk stat).
            var syncLogo = true
            cache.load(
                context, rom, targetPx,
                kind = ArtCache.ArtKind.LOGO,
                isStillValid = { overlay.tag == rom.id },
            ) onLogo@{ logoBmp ->
                if (overlay.tag != rom.id || (!syncLogo && !overlay.isAttachedToWindow)) {
                    return@onLogo
                }
                if (logoBmp != null) {
                    val mode = ArtTileMetrics.revealMode(
                        wasSyncMemoryHit = syncLogo,
                        isAttached = overlay.isAttachedToWindow,
                    )
                    applyRounded(
                        overlay = overlay,
                        context = context,
                        bitmap = logoBmp,
                        radiusPx = radiusPx,
                        targetPx = targetPx,
                        frameKind = ArtTileMetrics.ArtFrameKind.LOGO_OVERLAY,
                        revealMode = mode,
                    )
                    if (syncGrid && syncLogo) syncPainted = true
                    return@onLogo
                }
                val logo = HeroDetail.logoUri(rom) ?: return@onLogo
                var syncLocal = true
                cache.loadUri(
                    context,
                    key = "${rom.id}.logo",
                    uriString = logo,
                    maxDimension = targetPx,
                    isStillValid = { overlay.tag == rom.id },
                ) { local ->
                    if (local != null && overlay.tag == rom.id &&
                        (syncLocal || overlay.isAttachedToWindow)
                    ) {
                        val mode = ArtTileMetrics.revealMode(
                            wasSyncMemoryHit = syncLocal,
                            isAttached = overlay.isAttachedToWindow,
                        )
                        applyRounded(
                            overlay = overlay,
                            context = context,
                            bitmap = local,
                            radiusPx = radiusPx,
                            targetPx = targetPx,
                            frameKind = ArtTileMetrics.ArtFrameKind.LOGO_OVERLAY,
                            revealMode = mode,
                        )
                        if (syncGrid && syncLogo && syncLocal) syncPainted = true
                    }
                }
                syncLocal = false
            }
            syncLogo = false
        }
        syncGrid = false
        if (!syncPainted) {
            showPlaceholder(overlay)
        }
    }

    private fun placeholderOf(overlay: ImageView): TextView? =
        (overlay.parent as? ViewGroup)?.children?.filterIsInstance<TextView>()?.firstOrNull()

    private fun showPlaceholder(overlay: ImageView) {
        val placeholder = placeholderOf(overlay)
        if (placeholder != null && placeholder.visibility != View.VISIBLE) {
            placeholder.visibility = View.VISIBLE
        }
        val parent = overlay.parent as? View
        if (parent != null && parent.background != null) {
            parent.background = null
        }
    }

    private fun hidePlaceholderWithBacking(
        overlay: ImageView,
        context: Context,
        radiusPx: Float,
    ) {
        // INVISIBLE (not GONE) preserves layout bounds and findViewWithTag
        // lookup stability (e.g. CompanionPanel TAG_HERO_ICON).
        val placeholder = placeholderOf(overlay)
        if (placeholder != null && placeholder.visibility != View.INVISIBLE) {
            placeholder.visibility = View.INVISIBLE
        }
        val parent = overlay.parent as? View ?: return
        val wantRadius = if (radiusPx.isNaN() || radiusPx <= 0f) 0f else radiusPx
        val wantFill = TileBackgrounds.surfaceSunken(context)
        val existing = parent.background as? GradientDrawable
        if (existing != null &&
            existing.cornerRadius == wantRadius &&
            existing.color?.defaultColor == wantFill
        ) {
            return
        }
        parent.background = TileBackgrounds.artBackingPx(context, radiusPx)
    }

    private fun applyRoundedOutline(overlay: ImageView, radiusPx: Float) {
        val target = OutlineCache.roundedRect(radiusPx)
        if (overlay.outlineProvider !== target) {
            overlay.outlineProvider = target
        }
        if (!overlay.clipToOutline) {
            overlay.clipToOutline = true
        }
    }

    private fun applyRounded(
        overlay: ImageView,
        context: Context,
        bitmap: Bitmap,
        radiusPx: Float,
        targetPx: Int,
        frameKind: ArtTileMetrics.ArtFrameKind,
        revealMode: ArtTileMetrics.RevealMode,
    ) {
        overlay.animate().cancel()
        releaseDisplayed(overlay)
        ArtCache.acquireDisplay(bitmap)
        val density = context.resources.displayMetrics.density
        val framing = ArtTileMetrics.framingFor(
            kind = frameKind,
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height,
            targetPx = targetPx,
            density = density,
        )
        overlay.scaleType = if (framing.centerCrop) {
            ImageView.ScaleType.CENTER_CROP
        } else {
            ImageView.ScaleType.FIT_CENTER
        }
        overlay.setPadding(
            framing.insetPaddingPx,
            framing.insetPaddingPx,
            framing.insetPaddingPx,
            framing.insetPaddingPx,
        )
        overlay.setImageDrawable(
            RoundedBitmapDrawableFactory.create(context.resources, bitmap).apply {
                cornerRadius = if (framing.roundBitmapCorners) radiusPx else 0f
            },
        )
        hidePlaceholderWithBacking(overlay, context, radiusPx)
        val durationMs = ArtTileMetrics.revealDurationMs(
            mode = revealMode,
            animationsEnabled = ValueAnimator.areAnimatorsEnabled(),
        )
        if (durationMs > 0L) {
            overlay.alpha = 0f
            overlay.animate()
                .alpha(1f)
                .setDuration(durationMs)
                .setInterpolator(FADE_INTERPOLATOR)
                .start()
        } else {
            overlay.alpha = 1f
        }
    }

    private fun releaseDisplayed(overlay: ImageView) {
        displayedBitmap(overlay.drawable)?.let { ArtCache.releaseDisplay(it) }
    }

    private fun displayedBitmap(drawable: Drawable?): Bitmap? = when (drawable) {
        is RoundedBitmapDrawable -> drawable.bitmap
        is BitmapDrawable -> drawable.bitmap
        else -> null
    }
}
