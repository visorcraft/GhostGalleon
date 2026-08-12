package com.visorcraft.ghostgalleon.display

import com.visorcraft.ghostgalleon.settings.Settings

/**
 * First-run layout suggestions for single-display windows. Dual / Sugar
 * keep factory defaults; we only stamp [Settings.layoutSeeded] so a later
 * phone install still seeds. Pure; host-tested.
 */
object LayoutSeed {

    fun stillFactoryLayout(s: Settings): Boolean =
        s.gridColumns == Settings.DEFAULT.gridColumns &&
            s.cardSizeDp == Settings.DEFAULT.cardSizeDp &&
            s.iconSizeDp == Settings.DEFAULT.iconSizeDp

    /**
     * True when this process should write suggested columns / card / icon
     * sizes (single-display, factory numbers, not yet seeded).
     */
    fun shouldApplySuggestions(
        layoutSeeded: Boolean,
        topologyMode: SurfaceMode,
        stillFactory: Boolean,
    ): Boolean = !layoutSeeded && topologyMode == SurfaceMode.SINGLE && stillFactory

    fun apply(s: Settings, metrics: LayoutMetrics): Settings =
        s.copy(
            gridColumns = metrics.suggestedGridColumns,
            cardSizeDp = metrics.suggestedCardSizeDp,
            iconSizeDp = metrics.suggestedDockSlotDp.coerceIn(48, 128),
            layoutSeeded = true,
        )

    /** Dual / already-custom: mark seeded without changing numbers. */
    fun markSeeded(s: Settings): Settings =
        if (s.layoutSeeded) s else s.copy(layoutSeeded = true)
}
