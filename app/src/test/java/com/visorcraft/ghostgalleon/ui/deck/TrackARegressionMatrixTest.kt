package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.DockSlots
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.UiDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * End-to-end cross-viewport, multi-theme, and WCAG regression matrix pinning
 * every Track A (GridDeck, DockBar, FolderPanel, SystemChromeIcons, Icons)
 * design invariant across Sugar (~540dp), compact 360dp, 720p, and Odin 2 Portal.
 */
class TrackARegressionMatrixTest {

    private data class Viewport(
        val name: String,
        val widthPx: Int,
        val heightPx: Int,
        val density: Float,
        val suggestedDockDp: Int,
    )

    private val viewports = listOf(
        Viewport("compact-360", 720, 720, 2.0f, 44),
        Viewport("sugar-540", 1080, 1080, 2.0f, 48),
        Viewport("handheld-720p", 1280, 720, 2.0f, 56),
        Viewport("odin-768dp", 1920, 1080, 2.5f, 64),
        Viewport("odin-960dp", 1920, 1080, 2.0f, 72),
    )

    private val adversarialThemes = listOf(
        ThemeTokens(
            id = "dark-navy",
            displayName = "Dark Navy",
            accentColor = 0xFF0D1B4C.toInt(),
            cardRadiusDp = 20,
            panelLift = 0xFF141620.toInt(),
            chipIdle = 0xFF1E2230.toInt(),
        ),
        ThemeTokens(
            id = "sharp-flat",
            displayName = "Sharp Flat",
            accentColor = 0xFF00E5FF.toInt(),
            cardRadiusDp = 4,
            panelLift = 0xFF121216.toInt(),
            chipIdle = 0xFF1D1D24.toInt(),
        ),
        ThemeTokens(
            id = "light-custom",
            displayName = "Light Custom",
            accentColor = 0xFF3F51B5.toInt(),
            cardRadiusDp = 18,
            panelLift = 0xFFF0F0F5.toInt(),
            chipIdle = 0xFFE2E2EA.toInt(),
        ),
    )

    private val allThemes = ThemePack.BUILTINS + adversarialThemes

    @Test
    fun `optical concentric corner chain holds across all 7 themes and viewports`() {
        for (tokens in allThemes) {
            val pillRadius = ThemePack.pillRadiusDp(tokens)
            for (vp in viewports) {
                for (iconDp in listOf(40, 64, 96, 128)) {
                    val gridInner = DeckCornerMetrics.gridTileInnerRadiusDp(tokens, 8, iconDp)
                    assertTrue(
                        "${tokens.id}@${vp.name}: gridInner($gridInner) > card(${tokens.cardRadiusDp})",
                        gridInner in 1..tokens.cardRadiusDp,
                    )
                    val mosaic = FolderMosaicMetrics.compute(
                        iconSizePx = (iconDp * vp.density).toInt(),
                        outerCornerRadiusPx = gridInner * vp.density,
                        density = vp.density,
                    )
                    assertTrue(
                        "${tokens.id}@${vp.name}: mosaic mini radius > outer",
                        mosaic.miniCornerRadiusPx <= gridInner * vp.density + 0.05f,
                    )
                }

                val dockSpec = DockBarMetrics.compute(
                    windowWidthPx = vp.widthPx,
                    density = vp.density,
                    suggestedSlotDp = vp.suggestedDockDp,
                    visibleSlots = 6,
                    hasChromeIcons = true,
                )
                val slotDp = (dockSpec.slotSizePx / vp.density).toInt()
                val iconDp = (dockSpec.iconSizePx() / vp.density).toInt()
                val dockSlotR = DeckCornerMetrics.dockSlotRadiusDp(tokens, 8, slotDp)
                val dockInnerR = DeckCornerMetrics.dockInnerContentRadiusDp(tokens, 8, slotDp, iconDp)
                assertTrue(
                    "${tokens.id}@${vp.name}: pill($pillRadius) >= slot($dockSlotR) >= inner($dockInnerR)",
                    pillRadius >= dockSlotR && dockSlotR >= dockInnerR && dockInnerR > 0,
                )

                val folderCellR = DeckCornerMetrics.folderCellRadiusDp(tokens)
                val folderThumbR = DeckCornerMetrics.folderThumbRadiusDp(tokens, 4, 72)
                assertTrue(
                    "${tokens.id}@${vp.name}: card(${tokens.cardRadiusDp}) >= folderCell($folderCellR) >= thumb($folderThumbR)",
                    tokens.cardRadiusDp >= folderCellR && folderCellR >= folderThumbR && folderThumbR > 0,
                )
            }
        }
    }

    @Test
    fun `dock and system chrome never collide and stay centerline-aligned across 5 viewports and 4 to 9 slots`() {
        for (vp in viewports) {
            for (slots in DockSlots.MIN_VISIBLE..DockSlots.CAPACITY) {
                for (hasChrome in listOf(false, true)) {
                    val dock = DockBarMetrics.compute(
                        windowWidthPx = vp.widthPx,
                        density = vp.density,
                        suggestedSlotDp = vp.suggestedDockDp,
                        visibleSlots = slots,
                        hasChromeIcons = hasChrome,
                    )
                    val totalFootprint = dock.barWidthPx(slots) + dock.sideInsetPx * 2
                    assertTrue(
                        "${vp.name} slots=$slots chrome=$hasChrome overflow: $totalFootprint > ${vp.widthPx}",
                        totalFootprint <= vp.widthPx,
                    )
                    assertTrue(dock.iconSizePx() in 1 until dock.slotSizePx)
                    val plusSp = DockBarMetrics.blankPlusTextSp((dock.slotSizePx / vp.density).toInt())
                    assertTrue(plusSp in DockBarMetrics.MIN_BLANK_PLUS_SP..DockBarMetrics.MAX_BLANK_PLUS_SP)

                    if (hasChrome) {
                        val chrome = SystemChromeMetrics.compute(dock, vp.density)
                        val dockHeight = dock.slotSizePx + (SystemChromeMetrics.DOCK_BAR_PAD_V_DP * 2 * vp.density).toInt()
                        val dockCenter = dock.bottomMarginPx + dockHeight / 2
                        val chromeCenter = chrome.bottomMarginPx + chrome.buttonSizePx / 2
                        assertTrue(
                            "${vp.name} slots=$slots centerline drift: dock=$dockCenter chrome=$chromeCenter",
                            abs(dockCenter - chromeCenter) <= 1,
                        )
                        val chromeEnd = chrome.edgeMarginPx + chrome.buttonSizePx
                        val dockStart = (vp.widthPx - dock.barWidthPx(slots)) / 2
                        assertTrue(
                            "${vp.name} slots=$slots chrome collision: dockStart=$dockStart chromeEnd=$chromeEnd",
                            dockStart - chromeEnd >= (SystemChromeMetrics.MIN_HORIZONTAL_GAP_DP * vp.density).toInt(),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `grid cell layout, labels, badges, folder panel, and density buckets never overflow`() {
        for (vp in viewports) {
            val padH = (16 * vp.density).toInt()
            val padV = (16 * vp.density).toInt()
            val padTop = padV + (8 * vp.density).toInt()
            val spacing = (12 * vp.density).toInt()
            val cellPad = (8 * vp.density).toInt()
            val gridHeight = (vp.heightPx * 0.78f).toInt()

            for (cols in 3..8) {
                val cellW = (vp.widthPx - padH * 2 - spacing * (cols - 1)) / cols
                if (cellW <= cellPad * 3) continue
                for (sliderDp in listOf(48, 72, 96, 128)) {
                    for (showLabels in listOf(false, true)) {
                        val guessLabel = if (showLabels) (24 * vp.density).toInt() else 0
                        val guessIcon = GridIconMetrics.iconSizePx(cellW, sliderDp, vp.density)
                        val guessCellH = guessIcon + guessLabel + cellPad * 2
                        val visibleRows = ((gridHeight - padTop - padV + spacing) / (guessCellH + spacing))
                            .coerceAtLeast(1)
                        val cellH = (gridHeight - padTop - padV - spacing * (visibleRows - 1)) / visibleRows
                        val labelSpec = GridLabelMetrics.compute(
                            cellWidthDp = (cellW / vp.density).toInt(),
                            cellHeightDp = (cellH / vp.density).toInt(),
                            iconSizeDp = (guessIcon / vp.density).toInt(),
                            showLabels = showLabels,
                            hasWallpaper = true,
                        )
                        val labelPx = (labelSpec.reservedHeightDp * vp.density).toInt()
                        val iconPx = GridIconMetrics.iconSizePx(
                            cellWidthPx = cellW,
                            cellHeightPx = cellH,
                            cellPaddingPx = cellPad,
                            labelHeightPx = labelPx,
                            iconSizeDp = sliderDp,
                            density = vp.density,
                        )
                        assertTrue("icon <= cellW", iconPx <= cellW)
                        assertTrue(
                            "icon($iconPx) + label($labelPx) + pad(${cellPad * 2}) > cellH($cellH) on ${vp.name} cols=$cols",
                            iconPx + labelPx + cellPad * 2 <= cellH + 1,
                        )
                        val badge = GridBadgeMetrics.compute(cellW, ThemePack.GHOST.cardRadiusDp, vp.density)
                        assertTrue(badge.badgeSizePx * 2 <= cellW + badge.insetPx * 2)
                        assertTrue(IconDensityMetrics.targetDensityDpi(iconPx) in listOf(160, 240, 320, 480, 640))
                    }
                }
            }

            for (members in listOf(0, 1, 2, 5, 7, 16)) {
                val folder = FolderPanelMetrics.compute(vp.widthPx, vp.heightPx, vp.density, members)
                assertTrue(folder.cardWidthPx <= vp.widthPx)
                assertTrue(folder.maxScrollHeightPx <= vp.heightPx)
                assertTrue(folder.thumbSizePx >= 1)
                val expectedCols = if (members in 1..2) members..members else 3..4
                assertTrue(folder.columns in expectedCols)
            }
        }
    }

    @Test
    fun `WCAG contrast invariants hold across all built-in and adversarial themes`() {
        for (tokens in allThemes) {
            val cardFill = ThemePack.cardFill(tokens)
            val sunkenFill = ThemePack.surfaceSunken(tokens)
            val hairline = ThemePack.hairlineDivider(tokens)

            // Focus ring >= 3.0:1 (WCAG 1.4.11) on every surface including light custom.
            val focusStroke = DeckThemeContrast.accessibleFocusStroke(tokens.accentColor, cardFill)
            assertTrue(
                "${tokens.id} focus contrast < 3.0",
                ThemePack.contrastRatio(focusStroke, cardFill) >= DeckThemeContrast.MIN_NON_TEXT_CONTRAST,
            )

            // Dark built-ins + dark custom themes: verify lifted border, Mirror CTA, medallion, badges, chrome.
            if (ThemePack.relativeLuminance(cardFill) < GridTileFeedbackMetrics.LIGHT_SURFACE_LUMINANCE) {
                val liftedBorder = MoveChoreography.liftedBorderColor(tokens.accentColor, cardFill)
                assertTrue(
                    "${tokens.id} lifted border < 4.5",
                    ThemePack.contrastRatio(liftedBorder, cardFill) >= ThemePack.MIN_TEXT_CONTRAST,
                )
                val mirror = FolderPanelMetrics.mirrorButtonStyle(
                    focused = true,
                    accent = tokens.accentColor,
                    surfaceFill = cardFill,
                    chipIdleFill = tokens.chipIdle,
                    hairlineColor = hairline,
                )
                assertTrue(
                    "${tokens.id} mirror text < 4.5",
                    ThemePack.contrastRatio(mirror.textColor, mirror.fillColor) >= ThemePack.MIN_TEXT_CONTRAST,
                )
                val medallion = FolderPanelMetrics.emptyMedallionTint(tokens.accentColor, sunkenFill)
                assertTrue(
                    "${tokens.id} medallion < 4.5",
                    ThemePack.contrastRatio(medallion, sunkenFill) >= ThemePack.MIN_TEXT_CONTRAST,
                )
                val ripple = GridTileFeedbackMetrics.rippleColorForSurface(cardFill, tokens.accentColor)
                assertEquals(GridTileFeedbackMetrics.DARK_SURFACE_RIPPLE_ALPHA, (ripple ushr 24) and 0xFF)
            } else {
                val ripple = GridTileFeedbackMetrics.rippleColorForSurface(cardFill, tokens.accentColor)
                assertEquals(GridTileFeedbackMetrics.LIGHT_SURFACE_RIPPLE_ALPHA, (ripple ushr 24) and 0xFF)
            }

            // Active page pill over black & chrome swap tint over chrome dark scrim.
            val pillColor = GridPageIndicatorMetrics.activePillColor(tokens.accentColor)
            assertTrue(
                "${tokens.id} page pill < 4.5",
                ThemePack.contrastRatio(pillColor, 0xFF000000.toInt()) >= ThemePack.MIN_TEXT_CONTRAST,
            )
            val swapTint = SystemChromeMetrics.swapIconTint(tokens.accentColor)
            assertTrue(
                "${tokens.id} swap tint < 4.5",
                ThemePack.contrastRatio(swapTint, SystemChromeMetrics.CHROME_SCRIM_RGB) >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `navigation, paging, scroll feedback, reduced motion, and TalkBack stay mutually consistent`() {
        val nav = GridNavigation(itemCount = 24, columns = 5, visibleRows = 2)
        assertEquals(3, nav.pageCount())
        val afterR1 = nav.move(3, Action.PAGE_NEXT)
        assertEquals(13, afterR1)
        assertEquals(1, nav.pageOf(afterR1))
        assertEquals(
            GridPageIndicatorMetrics.targetSlotForPage(1, currentSlot = 3, slotCount = 24, cellsPerPage = 10),
            afterR1,
        )
        assertEquals(
            GridScrollFeedback.Cue.PageFlipped(+1),
            GridScrollFeedback.classify(Action.PAGE_NEXT, 3, afterR1, cellsPerPage = 10, slotCount = 24),
        )
        assertEquals(
            GridScrollFeedback.Cue.EdgeBump(-1),
            GridScrollFeedback.classify(Action.PAGE_PREV, 0, 0, cellsPerPage = 10, slotCount = 24),
        )

        // Reduced motion strictly suppresses continuous pulses and transient transitions.
        assertFalse(DeckMotionPolicy.shouldPulse(animatorsEnabled = false))
        assertFalse(DeckMotionPolicy.shouldAnimate(animatorsEnabled = false, isLaidOut = true))

        // TalkBack omits blanks and preserves localized details cleanly.
        val desc = DeckAccessibility.gridSlotDescription(
            primaryLabel = "Metroid Prime",
            secondaryDetail = "GC",
            favoriteLabel = "Favorite",
            dockLabel = "Dock",
            positionLabel = "1/24",
        )
        assertEquals("Metroid Prime, GC, Favorite, Dock, 1/24", desc)
        assertTrue(UiDimens.ALPHA_PRIMARY == 0xFFFFFFFF.toInt())
    }
}
