package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckAccessibilityTest {

    @Test
    fun `joinParts trims blanks and deduplicates case-insensitively`() {
        assertEquals(
            "Chrono Trigger, SNES, Favorite, 3/12",
            DeckAccessibility.joinParts("  Chrono Trigger ", null, "", "   ", "SNES", "Favorite", "favorite", "3/12"),
        )
        assertEquals("", DeckAccessibility.joinParts(null, "", "   "))
    }

    @Test
    fun `gridSlotDescription covers ROM, folder, blank, missing, and move states cleanly`() {
        assertEquals(
            "Hollow Knight, Favorite, Dock, 1/12",
            DeckAccessibility.gridSlotDescription(
                primaryLabel = "Hollow Knight",
                favoriteLabel = "Favorite",
                dockLabel = "Dock",
                positionLabel = "1/12",
            ),
        )
        assertEquals(
            "RPGs, 5 items, 2/12",
            DeckAccessibility.gridSlotDescription(
                primaryLabel = "RPGs",
                secondaryDetail = "5 items",
                positionLabel = "2/12",
            ),
        )
        assertEquals(
            "Add to grid, 7/12",
            DeckAccessibility.gridSlotDescription(
                primaryLabel = "Add to grid",
                positionLabel = "7/12",
            ),
        )
        assertEquals(
            "Super Metroid, SNES, 4/12, Moving - A drop · B cancel",
            DeckAccessibility.gridSlotDescription(
                primaryLabel = "Super Metroid",
                secondaryDetail = "SNES",
                positionLabel = "4/12",
                movingLabel = "Moving - A drop · B cancel",
            ),
        )
    }

    @Test
    fun `dockSlotDescription composes item, position, and moving cue`() {
        assertEquals(
            "RetroArch, 1/5",
            DeckAccessibility.dockSlotDescription(primaryLabel = "RetroArch", positionLabel = "1/5"),
        )
        assertEquals(
            "Add to dock, 5/5",
            DeckAccessibility.dockSlotDescription(primaryLabel = "Add to dock", positionLabel = "5/5"),
        )
        assertEquals(
            "RetroArch, 2/5, Moving - A drop · B cancel",
            DeckAccessibility.dockSlotDescription(
                primaryLabel = "RetroArch",
                positionLabel = "2/5",
                movingLabel = "Moving - A drop · B cancel",
            ),
        )
    }
}
