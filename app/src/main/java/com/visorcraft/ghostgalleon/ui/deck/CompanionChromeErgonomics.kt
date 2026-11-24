package com.visorcraft.ghostgalleon.ui.deck

/**
 * Pure ergonomics, auto-scroll, touch-target, and tactile-feedback policy for
 * Companion chrome controls (role switcher, quick-action chips, Resume CTA,
 * and floating chrome icon buttons). Host-tested.
 */
object CompanionChromeErgonomics {

    /** Minimum accessible touch target edge (dp) for handheld tap ergonomics. */
    const val MIN_TOUCH_TARGET_DP: Int = 44

    /** Visual pill height (dp) inside a 44dp touch-height role or quick chip. */
    const val CHIP_VISUAL_HEIGHT_DP: Int = 34

    /** Top/bottom transparent inset (dp) expanding a 34dp visual pill to a 44dp hit target. */
    const val CHIP_TOUCH_INSET_V_DP: Int = (MIN_TOUCH_TARGET_DP - CHIP_VISUAL_HEIGHT_DP) / 2

    /** Tactile press scale for pill chips. */
    const val CHIP_PRESS_SCALE: Float = 0.95f

    /** Gamepad focus lift scale for pill chips. */
    const val CHIP_FOCUS_SCALE: Float = 1.04f

    /** Tactile press scale for circular chrome icon buttons. */
    const val ICON_PRESS_SCALE: Float = 0.92f

    /** Gamepad focus lift scale for circular chrome icon buttons. */
    const val ICON_FOCUS_SCALE: Float = 1.08f

    /** Duration (ms) for tactile press/focus micro-scale animations. */
    const val SCALE_ANIM_MS: Long = 90L

    /** Width threshold (dp) below which role chips tighten horizontal padding to avoid clipping. */
    const val ROLE_CHIP_NARROW_PANEL_DP: Float = 560f

    /** Horizontal fading edge width (dp) signalling scrollable role chips. */
    const val ROLE_CHIP_FADE_EDGE_DP: Int = 24

    /** Elevation (dp) lifting the currently selected role chip above idle siblings. */
    const val ROLE_CHIP_ACTIVE_ELEVATION_DP: Float = 2f

    /**
     * Horizontal pill padding (dp) for companion role chips: tightened to `10dp`
     * on narrow `< 560dp` panels (e.g. ~540dp Sugar with StatusPill clearance)
     * so all 4 chips fit cleanly when possible, keeping `12dp` on wider panels.
     */
    fun roleChipPadHDp(panelWidthDp: Float): Int =
        if (panelWidthDp < ROLE_CHIP_NARROW_PANEL_DP) 10 else 12

    /**
     * Compute the target `scrollX` to bring `[chipLeft, chipRight]` comfortably
     * into view inside a [android.widget.HorizontalScrollView] whose usable width
     * is `viewportWidth - endClearancePx`.
     *
     * Returns `null` when content fits without scrolling or the chip is already
     * fully inside the usable viewport.
     */
    fun horizontalScrollTarget(
        chipLeft: Int,
        chipRight: Int,
        viewportWidth: Int,
        endClearancePx: Int,
        contentWidth: Int,
        currentScrollX: Int,
    ): Int? {
        if (viewportWidth <= 0 || contentWidth <= 0 || chipRight <= chipLeft) return null
        val usableWidth = (viewportWidth - endClearancePx.coerceAtLeast(0)).coerceAtLeast(1)
        val maxScroll = (contentWidth - usableWidth).coerceAtLeast(0)
        if (maxScroll <= 0) return null
        val visibleStart = currentScrollX.coerceIn(0, maxScroll)
        val visibleEnd = visibleStart + usableWidth
        if (chipLeft >= visibleStart && chipRight <= visibleEnd) return null
        val chipCenter = (chipLeft + chipRight) / 2
        return (chipCenter - usableWidth / 2).coerceIn(0, maxScroll)
    }

    /** Compute resting scale given current pressed and focused states. */
    fun targetScale(
        pressed: Boolean,
        focused: Boolean,
        pressScale: Float = CHIP_PRESS_SCALE,
        focusScale: Float = CHIP_FOCUS_SCALE,
    ): Float = when {
        pressed -> pressScale
        focused -> focusScale
        else -> 1f
    }

    /**
     * Visible text for the Favorite quick chip: prepends the non-translatable
     * star glyph when active while keeping TalkBack's `contentDescription` plain.
     */
    fun favoriteChipText(starGlyph: String, label: String, isFavorite: Boolean): String =
        if (isFavorite && starGlyph.isNotBlank()) "$starGlyph $label" else label

    /**
     * Subtle active wash for toggled quick-action chips (82% idle chip surface +
     * 18% theme accent) so active pills stand out cleanly while preserving WCAG AA
     * contrast for accent and gold star foregrounds.
     */
    fun activeChipFill(idleSurface: Int, accentColor: Int): Int {
        val r = (((idleSurface shr 16) and 0xFF) * 82 + ((accentColor shr 16) and 0xFF) * 18) / 100
        val g = (((idleSurface shr 8) and 0xFF) * 82 + ((accentColor shr 8) and 0xFF) * 18) / 100
        val b = ((idleSurface and 0xFF) * 82 + (accentColor and 0xFF) * 18) / 100
        return (0xFF shl 24) or
            (r.coerceIn(0, 255) shl 16) or
            (g.coerceIn(0, 255) shl 8) or
            b.coerceIn(0, 255)
    }

    /** Minimum alpha during horizontal drag before swipe-dismiss completes. */
    const val RESUME_SWIPE_MIN_ALPHA: Float = 0.25f

    /** Gentle drag damping so the Resume pill tracks the finger with tactile weight. */
    const val RESUME_SWIPE_DAMPING: Float = 0.85f

    /** Duration (ms) for Resume pill slide-out dismiss or snap-back settle. */
    const val RESUME_SWIPE_ANIM_MS: Long = 120L

    /** Tactile scale pull signaling the finger crossed the swipe-to-dismiss commit threshold. */
    const val RESUME_SWIPE_COMMIT_SCALE: Float = 0.94f

    data class SwipeFeedback(
        val translationX: Float,
        val alpha: Float,
        val shouldDismiss: Boolean,
        val crossedThreshold: Boolean = shouldDismiss,
        val scale: Float = 1f,
    )

    /**
     * Pure finger-tracking drag physics for the Resume CTA pill's horizontal
     * swipe-to-dismiss gesture. Uses raw deltas so translating the view never
     * causes coordinate feedback jitter.
     */
    fun resumeSwipeFeedback(
        dx: Float,
        dy: Float,
        dismissSlopPx: Float,
        touchSlopPx: Float = 8f,
    ): SwipeFeedback {
        if (dismissSlopPx <= 0f) {
            return SwipeFeedback(0f, 1f, shouldDismiss = false, crossedThreshold = false, scale = 1f)
        }
        val absX = kotlin.math.abs(dx)
        val absY = kotlin.math.abs(dy)
        if (absX < touchSlopPx || absY > absX) {
            return SwipeFeedback(0f, 1f, shouldDismiss = false, crossedThreshold = false, scale = 1f)
        }
        val maxTravel = dismissSlopPx * 2.2f
        val tx = (dx * RESUME_SWIPE_DAMPING).coerceIn(-maxTravel, maxTravel)
        val progress = (absX / (dismissSlopPx * 1.35f)).coerceIn(0f, 1f)
        val alpha = (1f - progress * (1f - RESUME_SWIPE_MIN_ALPHA))
            .coerceIn(RESUME_SWIPE_MIN_ALPHA, 1f)
        val crossed = absX >= dismissSlopPx && absX > absY
        val scale = if (crossed) RESUME_SWIPE_COMMIT_SCALE else 1f
        return SwipeFeedback(
            translationX = tx,
            alpha = alpha,
            shouldDismiss = crossed,
            crossedThreshold = crossed,
            scale = scale,
        )
    }

    /** Edge trigger emitting a single commit tick when crossing `false -> true` with haptics enabled. */
    fun shouldTriggerSwipeHaptic(
        wasCrossed: Boolean,
        nowCrossed: Boolean,
        hapticsEnabled: Boolean,
    ): Boolean = hapticsEnabled && !wasCrossed && nowCrossed

    /** Cold-start hold crest breathing alpha range (when system animators enabled). */
    const val COLD_START_ALPHA_MIN: Float = 0.24f
    const val COLD_START_ALPHA_MAX: Float = 0.44f
    const val COLD_START_ALPHA_STATIC: Float = 0.32f
    const val COLD_START_SCALE_MIN: Float = 0.98f
    const val COLD_START_SCALE_MAX: Float = 1.02f
    const val COLD_START_BREATHE_MS: Long = 1_100L

    /** Subtle radial halo behind the cold-start ship emblem (~14% alpha theme tint). */
    fun coldStartGlowColor(accentColor: Int): Int =
        (0x24 shl 24) or (accentColor and 0x00FFFFFF)
}
