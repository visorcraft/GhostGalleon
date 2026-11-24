package com.visorcraft.ghostgalleon.ui.deck

/**
 * Pure layout sizes for the dual-screen companion hero. Host-tested.
 *
 * Bottom Sugar panel is short (~500–560dp usable height). A fixed 240dp art
 * tile + 32sp title + chrome (role chips, actions, hints) clipped the title
 * mid-glyph. Scale art/name down so the title always fits above the action row.
 */
object CompanionHeroMetrics {

    data class Spec(
        /** Square art / platform tile edge in dp. */
        val artSizeDp: Int,
        /** Hero title size in sp. */
        val nameSp: Float,
        /** Top padding on the title in dp. */
        val nameTopPadDp: Int,
        /** Side padding on the title in dp. */
        val nameSidePadDp: Int,
        /** Max title lines. */
        val nameMaxLines: Int,
        /**
         * Banner height as a fraction of panel height when wide HERO art is
         * shown (0 = never show banner).
         */
        val bannerHeightFraction: Float,
        /** Show screenshot / video snaps under the title. */
        val showExtraMedia: Boolean,
        /** Show Open-with / Favorite quick chips under ROM meta. */
        val showQuickChips: Boolean,
    )

    /** @param panelHeightDp usable window height in dp (prefer app bounds). */
    fun forPanel(panelHeightDp: Float): Spec {
        val h = panelHeightDp.coerceAtLeast(1f)
        // Compact: Sugar secondary / short dual panel.
        // Medium: tall dual companion or phone landscape.
        // Expanded: full top panel style.
        return when {
            h < 500f -> Spec(
                artSizeDp = 132,
                nameSp = 20f,
                nameTopPadDp = 8,
                nameSidePadDp = 16,
                nameMaxLines = 2,
                bannerHeightFraction = 0.22f,
                showExtraMedia = false,
                showQuickChips = false,
            )
            h < 600f -> Spec(
                artSizeDp = 160,
                nameSp = 22f,
                nameTopPadDp = 10,
                nameSidePadDp = 20,
                nameMaxLines = 2,
                bannerHeightFraction = 0.28f,
                showExtraMedia = false,
                showQuickChips = true,
            )
            else -> Spec(
                artSizeDp = 240,
                nameSp = 32f,
                nameTopPadDp = 24,
                nameSidePadDp = 24,
                nameMaxLines = 2,
                bannerHeightFraction = 0.40f,
                showExtraMedia = true,
                showQuickChips = true,
            )
        }.let { spec ->
            // Never let art exceed ~half the panel height (title needs room).
            val maxArt = (h * 0.42f).toInt().coerceIn(100, 240)
            if (spec.artSizeDp <= maxArt) spec
            else spec.copy(artSizeDp = maxArt)
        }
    }

    /** Banner height in px from panel height and [Spec.bannerHeightFraction]. */
    fun bannerHeightPx(panelHeightPx: Int, spec: Spec): Int {
        if (spec.bannerHeightFraction <= 0f) return 0
        return (panelHeightPx * spec.bannerHeightFraction).toInt().coerceAtLeast(0)
    }

    /** Panel-scaled ROM wordmark/wheel logo box in dp (`0×0` hides logo on ultra-compact panels). */
    data class LogoSizeDp(
        val widthDp: Int,
        val heightDp: Int,
    ) {
        val visible: Boolean get() = widthDp > 0 && heightDp > 0
    }

    /**
     * Scale the ROM wordmark logo cleanly by panel tier:
     * - Ultra-compact (`< 500dp`): hidden so title and art keep breathing room.
     * - Medium (`500..599dp`, e.g. ~540dp Sugar): `140×34dp`.
     * - Tall (`>= 600dp`): full `200×48dp`.
     */
    fun logoSizeDp(spec: Spec): LogoSizeDp = when {
        !spec.showQuickChips -> LogoSizeDp(widthDp = 0, heightDp = 0)
        !spec.showExtraMedia -> LogoSizeDp(widthDp = 140, heightDp = 34)
        else -> LogoSizeDp(widthDp = 200, heightDp = 48)
    }

    /**
     * Cap hero subline to a single line on compact/medium panels when the Resume
     * chip is visible so quick-action pills never crowd [HintBar].
     */
    fun sublineMaxLines(spec: Spec, hasResumeChip: Boolean): Int =
        if (hasResumeChip && !spec.showExtraMedia) 1 else 2

    // --- Empty-selection brand scene (clouds / sea / ship) -----------------
    // Full-panel 3:2 sky/water stack. Ship size scales with panel height
    // (any dual / single device) and is placed so the hull rests on the
    // waterline. Must NOT reuse [forPanel] game-hero art sizes — those are
    // for selected ROM/app tiles under chrome, not the brand scene.

    /**
     * Horizon as fraction of panel height for clouds:sea weight 3:2
     * (sky 60%, water 40%).
     */
    const val BRAND_WATERLINE_FRACTION = 0.60f

    /**
     * Where the hull sits in the ship bitmap (0 = top, 1 = bottom). Masts
     * above, hull on the waterline.
     */
    const val BRAND_SHIP_HULL_FRACTION = 0.55f

    /** Ship edge as a fraction of panel height (before min/max clamps). */
    const val BRAND_SHIP_HEIGHT_FRACTION = 0.44f

    /** Min / max ship edge in dp so tiny and huge panels stay sane. */
    const val BRAND_SHIP_MIN_DP = 120
    const val BRAND_SHIP_MAX_DP = 280

    data class BrandShipLayout(
        val sizePx: Int,
        /** Top margin so hull Y ≈ waterline. */
        val topMarginPx: Int,
    )

    /**
     * Size + top margin for the brand galleon on a panel of [panelHeightPx].
     * [density] is display density (px = dp * density) for min/max clamps.
     */
    fun brandShipLayout(panelHeightPx: Int, density: Float): BrandShipLayout {
        if (panelHeightPx <= 0 || density <= 0f) {
            return BrandShipLayout(sizePx = 0, topMarginPx = 0)
        }
        val minPx = (BRAND_SHIP_MIN_DP * density).toInt().coerceAtLeast(1)
        val maxPx = (BRAND_SHIP_MAX_DP * density).toInt().coerceAtLeast(minPx)
        val size = (panelHeightPx * BRAND_SHIP_HEIGHT_FRACTION)
            .toInt()
            .coerceIn(minPx, maxPx)
        val waterline = panelHeightPx * BRAND_WATERLINE_FRACTION
        val top = (waterline - size * BRAND_SHIP_HULL_FRACTION)
            .toInt()
            .coerceAtLeast(0)
        return BrandShipLayout(sizePx = size, topMarginPx = top)
    }

    /** Top margin only (tests / callers that already know size). */
    fun brandShipTopMarginPx(panelHeightPx: Int, shipSizePx: Int): Int {
        if (panelHeightPx <= 0 || shipSizePx <= 0) return 0
        val waterline = panelHeightPx * BRAND_WATERLINE_FRACTION
        return (waterline - shipSizePx * BRAND_SHIP_HULL_FRACTION)
            .toInt()
            .coerceAtLeast(0)
    }

    /** Minimum vertical clearance (dp) below the ship required to show the idle prompt above HintBar. */
    const val BRAND_CAPTION_MIN_CLEARANCE_DP = 56

    /**
     * True when the empty-selection brand scene has enough room beneath the
     * ship silhouette to display the idle prompt cleanly above [HintBar].
     */
    fun brandCaptionFitsBelowShip(
        panelHeightPx: Int,
        shipLayout: BrandShipLayout,
        density: Float,
    ): Boolean {
        if (panelHeightPx <= 0 || density <= 0f) return false
        val shipBottomPx = shipLayout.topMarginPx + shipLayout.sizePx
        val remainingDp = (panelHeightPx - shipBottomPx) / density
        return remainingDp >= BRAND_CAPTION_MIN_CLEARANCE_DP
    }

    // --- Role bar clearance & PERF_HUD card grid ----------------------------

    /** Width (dp) at or above which centered role chips clear the top-end StatusPill. */
    const val ROLE_BAR_WIDE_PANEL_DP = 680f

    /** End clearance (dp) inside content padding on narrow panels hosting large StatusPill. */
    const val ROLE_BAR_STATUS_CLEARANCE_DP = 148

    /**
     * Right-side padding (dp) on [CompanionPanel]'s role chip row so chips never
     * collide with the top-end large [StatusPill] on narrow companion panels
     * (e.g. Sugar ~540dp). Clamped to 40% of ultra-narrow panels; zero when
     * StatusPill is hidden or panel is wide.
     */
    fun roleBarEndClearanceDp(hasStatusPill: Boolean, panelWidthDp: Float): Int =
        if (hasStatusPill && panelWidthDp < ROLE_BAR_WIDE_PANEL_DP) {
            minOf(
                ROLE_BAR_STATUS_CLEARANCE_DP,
                (panelWidthDp.coerceAtLeast(0f) * 0.40f).toInt(),
            )
        } else {
            0
        }

    /** Responsive horizontal content padding (dp) scaling across ultra-narrow, Sugar, and wide panels. */
    fun panelHorizontalPaddingDp(panelWidthDp: Float): Int = when {
        panelWidthDp < 420f -> 14
        panelWidthDp < 600f -> 20
        else -> 24
    }

    /** Column count for PERF_HUD metric cards: 2 normally, gracefully collapsing to 1 under 360dp. */
    fun perfGridColumns(panelWidthDp: Float): Int = if (panelWidthDp < 360f) 1 else 2

    /** Column span for item [index] in a [columns]-column PERF_HUD metric card grid. */
    fun perfCardColumnSpan(index: Int, totalCount: Int, columns: Int = 2): Int =
        if (columns >= 2 && totalCount > 0 && totalCount % 2 == 1 && index == totalCount - 1) 2 else 1

    /**
     * Conservative vertical budget estimate (dp) for the companion hero stack
     * (role bar + optional resume chip + art/banner + optional scaled logo +
     * 2-line title + compact subline + RA line/meter + quick chips + hint bar)
     * so short ~540dp Sugar panels never clip mid-glyph.
     */
    fun estimatedHeroStackDp(
        spec: Spec,
        includeRaBar: Boolean = true,
        includeLogo: Boolean = false,
        hasResumeChip: Boolean = false,
        useBanner: Boolean = false,
        panelHeightDp: Float = 0f,
    ): Int {
        val roleBarAndTopPad = 56
        val resumeDp = if (hasResumeChip) 54 else 0
        val mediaDp = if (useBanner && panelHeightDp > 0f && spec.bannerHeightFraction > 0f) {
            (panelHeightDp * spec.bannerHeightFraction).toInt()
        } else {
            spec.artSizeDp
        }
        val logo = if (includeLogo) logoSizeDp(spec) else LogoSizeDp(0, 0)
        val logoDp = if (logo.visible) logo.heightDp + 6 else 0
        // `includeFontPadding = false` on hero title tightens vertical line metric cleanly.
        val titleLinesDp = (spec.nameSp * 1.20f * spec.nameMaxLines).toInt() + spec.nameTopPadDp + 4
        val subLines = sublineMaxLines(spec, hasResumeChip)
        val sublineDp = if (subLines <= 1) 20 else 32
        val raDp = if (includeRaBar) 24 else 18
        val quickChipsDp = if (spec.showQuickChips) 44 else 0
        val hintBarDp = 36
        return roleBarAndTopPad + resumeDp + mediaDp + logoDp + titleLinesDp +
            sublineDp + raDp + quickChipsDp + hintBarDp
    }
}
