package com.visorcraft.ghostgalleon.ui.deck

import android.text.InputType
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.DeckDialogTokens
import com.visorcraft.ghostgalleon.ui.ToastCoalescer
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.settings.SetupCardNav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-theme, custom-accent, and multi-viewport regression suite covering all
 * Track D modal, overlay, dialog, picker, and toast primitives.
 */
class TrackDModalRegressionTest {

    private val customAccents = listOf(
        0xFF000000.toInt(), // pitch black custom accent
        0xFFFFFFFF.toInt(), // pure white custom accent
        0xFFFFEE00.toInt(), // bright neon yellow
        0xFF101840.toInt(), // deep dark navy
        0xFF00FF88.toInt(), // electric mint
    )

    private val allThemeAccents: List<Pair<ThemeTokens, Int>> =
        ThemePack.BUILTINS.map { it to it.accentColor } +
            ThemePack.BUILTINS.flatMap { tokens -> customAccents.map { tokens to it } }

    @Test
    fun `every modal text and focus stroke meets WCAG AA and non-text contrast across built-in and extreme custom accents`() {
        for ((tokens, accent) in allThemeAccents) {
            val surface = ThemePack.cardFill(tokens)
            val sunken = ThemePack.surfaceSunken(tokens)
            val tag = "theme=${tokens.id} accent=${Integer.toHexString(accent)}"

            // Focus ring stroke >= 3.0:1 (WCAG 1.4.11) on card and sunken surfaces.
            val cardStroke = OverlayPrimitives.accessibleFocusStroke(accent, surface)
            assertTrue(
                "Low card focus stroke contrast on $tag",
                ThemePack.contrastRatio(cardStroke, surface) >= OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
            )
            val sunkenStroke = OverlayPrimitives.accessibleFocusStroke(accent, sunken)
            assertTrue(
                "Low sunken focus stroke contrast on $tag",
                ThemePack.contrastRatio(sunkenStroke, sunken) >= OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
            )

            // AppPicker search match highlights >= 4.5:1.
            assertTrue(
                "Low AppPicker title match contrast on $tag",
                ThemePack.contrastRatio(AppPickerMatch.titleHighlightColor(accent, tokens), surface) >=
                    ThemePack.MIN_TEXT_CONTRAST,
            )
            assertTrue(
                "Low AppPicker badge match contrast on $tag",
                ThemePack.contrastRatio(AppPickerMatch.badgeHighlightColor(accent, tokens), sunken) >=
                    ThemePack.MIN_TEXT_CONTRAST,
            )

            // SlotMenu row text >= 4.5:1 & focused stroke >= 3.0:1.
            for (tone in SlotMenuNav.RowTone.entries) {
                for (selected in listOf(false, true)) {
                    val spec = SlotMenuNav.rowVisual(tone, selected, accent, tokens)
                    val fg = SlotMenuNav.blendOverOpaque(spec.textColor, spec.effectiveSurface)
                    assertTrue(
                        "Low SlotMenu contrast on $tag tone=$tone sel=$selected",
                        ThemePack.contrastRatio(fg, spec.effectiveSurface) >= ThemePack.MIN_TEXT_CONTRAST,
                    )
                    if (selected) {
                        assertTrue(
                            "Low SlotMenu focus stroke contrast on $tag tone=$tone",
                            ThemePack.contrastRatio(spec.strokeColor, spec.effectiveSurface) >=
                                OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
                        )
                    }
                }
            }

            // QuickPanel tile text >= 4.5:1.
            for (role in QuickPanelNav.TileRole.entries) {
                for (selected in listOf(false, true)) {
                    val (bg, fgRaw) = QuickPanelNav.tileColors(role, selected, accent, tokens)
                    val fg = SlotMenuNav.blendOverOpaque(fgRaw, bg)
                    assertTrue(
                        "Low QuickPanel contrast on $tag role=$role sel=$selected",
                        ThemePack.contrastRatio(fg, bg) >= ThemePack.MIN_TEXT_CONTRAST,
                    )
                }
            }

            // SessionSwitcherView active & background row text >= 4.5:1, focus border >= 3.0:1.
            for (isActive in listOf(false, true)) {
                for (selected in listOf(false, true)) {
                    val visual = SessionSwitcherNav.rowVisual(isActive, selected, accent, tokens)
                    val titleFg = SlotMenuNav.blendOverOpaque(visual.titleColor, visual.fillColor)
                    val subFg = SlotMenuNav.blendOverOpaque(visual.subtitleColor, visual.fillColor)
                    assertTrue(
                        "Low SessionSwitcher title contrast on $tag active=$isActive sel=$selected",
                        ThemePack.contrastRatio(titleFg, visual.fillColor) >= ThemePack.MIN_TEXT_CONTRAST,
                    )
                    assertTrue(
                        "Low SessionSwitcher subtitle contrast on $tag active=$isActive sel=$selected",
                        ThemePack.contrastRatio(subFg, visual.fillColor) >= ThemePack.MIN_TEXT_CONTRAST,
                    )
                    if (selected) {
                        assertTrue(
                            "Low SessionSwitcher focus stroke on $tag active=$isActive",
                            ThemePack.contrastRatio(visual.strokeColor, visual.fillColor) >=
                                OverlayPrimitives.MIN_FOCUS_STROKE_CONTRAST,
                        )
                    }
                }
            }

            // SetupCard buttons & checklist rows >= 4.5:1.
            for (role in SetupCardNav.ButtonRole.entries) {
                for (selected in listOf(false, true)) {
                    val (bg, fg) = SetupCardNav.buttonColors(role, selected, accent, tokens)
                    assertTrue(
                        "Low SetupCard button contrast on $tag role=$role sel=$selected",
                        ThemePack.contrastRatio(fg, bg) >= ThemePack.MIN_TEXT_CONTRAST,
                    )
                }
            }
            for (tone in SetupCardNav.CheckRowTone.entries) {
                val fg = SlotMenuNav.blendOverOpaque(
                    SetupCardNav.checkRowTextColor(tone, accent, tokens),
                    sunken,
                )
                assertTrue(
                    "Low SetupCard checklist contrast on $tag tone=$tone",
                    ThemePack.contrastRatio(fg, sunken) >= ThemePack.MIN_TEXT_CONTRAST,
                )
            }

            // DeckDialogs buttons & chooseItem list focus fill >= 4.5:1.
            val dialogPalette = DeckDialogTokens.palette(accent, tokens)
            assertTrue(
                "Low dialog positive contrast on $tag",
                ThemePack.contrastRatio(dialogPalette.positiveText, dialogPalette.surfaceFill) >=
                    ThemePack.MIN_TEXT_CONTRAST,
            )
            val listFocusBg = DeckDialogTokens.listFocusedFill(accent, tokens)
            assertTrue(
                "Low dialog list focus contrast on $tag",
                ThemePack.contrastRatio(UiDimens.ALPHA_PRIMARY, listFocusBg) >=
                    ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `modal sizing and IME clamping hold valid bounds across Sugar, Odin, and compact viewports`() {
        data class Viewport(val name: String, val w: Int, val h: Int, val density: Float, val imeH: Int)
        val viewports = listOf(
            Viewport("sugar-bottom", 960, 540, 2f, 240),
            Viewport("sugar-top", 1280, 720, 2f, 300),
            Viewport("odin-1080p", 1920, 1080, 3f, 440),
            Viewport("compact-vga", 640, 480, 2f, 200),
            Viewport("narrow-portrait", 720, 1280, 2f, 460),
        )

        for (vp in viewports) {
            val cardW = OverlayPrimitives.responsiveCardWidthPx(vp.w, vp.density)
            assertTrue("${vp.name} cardW=$cardW", cardW in 1..vp.w)

            val maxH = OverlayPrimitives.maxModalHeightPx(vp.h, vp.density)
            assertTrue("${vp.name} maxH=$maxH", maxH in 1..vp.h)

            val imeSafeBottom = OverlayPrimitives.safeBottomInsetPx(vp.imeH, navBarBottomPx = 0, extraMarginPx = 8)
            val drawerClosed = OverlayPrimitives.imeAwareCardHeightPx(vp.h, 0.88f, 0, 24, 200)
            val drawerOpen = OverlayPrimitives.imeAwareCardHeightPx(vp.h, 0.88f, imeSafeBottom, 24, 200)
            assertTrue("${vp.name} drawerClosed=$drawerClosed", drawerClosed in 1..vp.h)
            assertTrue("${vp.name} drawerOpen=$drawerOpen", drawerOpen in 1..drawerClosed)

            val cardDp = (cardW / vp.density).toInt()
            val cols = QuickPanelNav.columnsForCardWidthDp(cardDp)
            assertTrue(cols == QuickPanelNav.NARROW_COLUMNS || cols == QuickPanelNav.DEFAULT_COLUMNS)
            assertTrue(QuickPanelNav.tileHeightDp(tileCount = 12, columns = cols) > 0)
        }
    }

    @Test
    fun `core Track D invariants hold under rapid bursts and password input resolution`() {
        // Password variation preserved when singleLine = true.
        val pw = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertEquals(pw, DeckDialogTokens.resolvedInputType(pw or InputType.TYPE_TEXT_FLAG_MULTI_LINE, true))

        // Rapid toast burst never queues stale duplicates or fails on blank inputs.
        val coalescer = ToastCoalescer()
        assertEquals(ToastCoalescer.Decision.SHOW_FRESH, coalescer.decide("A", false, 100L))
        assertEquals(ToastCoalescer.Decision.SUPPRESS_DUPLICATE, coalescer.decide("A", false, 200L))
        assertEquals(ToastCoalescer.Decision.CANCEL_AND_REPLACE, coalescer.decide("B", false, 250L))
        assertEquals(ToastCoalescer.Decision.IGNORE_BLANK, coalescer.decide("   ", false, 300L))
    }
}
