package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.quantityText
import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselMultiSelectLogicTest {

    @Test
    fun `cardCheckStyle transitions cleanly between hidden, unchecked, and checked`() {
        assertEquals(
            CarouselMultiSelectLogic.CardCheckStyle.HIDDEN,
            CarouselMultiSelectLogic.cardCheckStyle(multiSelectEnabled = false, multiSelected = false),
        )
        assertEquals(
            CarouselMultiSelectLogic.CardCheckStyle.HIDDEN,
            CarouselMultiSelectLogic.cardCheckStyle(multiSelectEnabled = false, multiSelected = true),
        )
        assertEquals(
            CarouselMultiSelectLogic.CardCheckStyle.UNCHECKED,
            CarouselMultiSelectLogic.cardCheckStyle(multiSelectEnabled = true, multiSelected = false),
        )
        assertEquals(
            CarouselMultiSelectLogic.CardCheckStyle.CHECKED,
            CarouselMultiSelectLogic.cardCheckStyle(multiSelectEnabled = true, multiSelected = true),
        )
    }

    @Test
    fun `bannerCountText clamps negative values and uses count_selected plural`() {
        assertEquals(
            quantityText(R.plurals.count_selected, 0, 0),
            CarouselMultiSelectLogic.bannerCountText(-3),
        )
        assertEquals(
            quantityText(R.plurals.count_selected, 1, 1),
            CarouselMultiSelectLogic.bannerCountText(1),
        )
        assertEquals(
            quantityText(R.plurals.count_selected, 7, 7),
            CarouselMultiSelectLogic.bannerCountText(7),
        )
    }

    @Test
    fun `showSelectAllPill hides when rail is empty or all items are already selected`() {
        assertFalse(CarouselMultiSelectLogic.showSelectAllPill(selectedCount = 0, railTotal = 0))
        assertTrue(CarouselMultiSelectLogic.showSelectAllPill(selectedCount = 0, railTotal = 5))
        assertTrue(CarouselMultiSelectLogic.showSelectAllPill(selectedCount = 4, railTotal = 5))
        assertFalse(CarouselMultiSelectLogic.showSelectAllPill(selectedCount = 5, railTotal = 5))
        assertFalse(CarouselMultiSelectLogic.showSelectAllPill(selectedCount = 8, railTotal = 5))
    }

    @Test
    fun `banner tint helpers preserve RGB channels with expected translucent alphas`() {
        val accent = ThemePack.GHOST.accentColor
        val fill = CarouselMultiSelectLogic.bannerTintFill(accent)
        val stroke = CarouselMultiSelectLogic.bannerTintStroke(accent)
        assertEquals(accent and 0x00FFFFFF, fill and 0x00FFFFFF)
        assertEquals(accent and 0x00FFFFFF, stroke and 0x00FFFFFF)
        assertEquals(CarouselMultiSelectLogic.BANNER_FILL_ALPHA, (fill ushr 24) and 0xFF)
        assertEquals(CarouselMultiSelectLogic.BANNER_STROKE_ALPHA, (stroke ushr 24) and 0xFF)
    }
}
