package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionDialogsTest {

    @Test
    fun `buildAddOptions reports member counts and exact membership for single and bulk keys`() {
        val collections = mapOf(
            "RPGs" to listOf("rom:snes:ct", "rom:snes:ff6"),
            "Favorites" to listOf("rom:snes:ct"),
            "Empty" to emptyList(),
        )

        val single = CollectionDialogRows.buildAddOptions(collections, listOf("rom:snes:ct"))
        val byName = single.associateBy { it.name }
        assertEquals(2, byName.getValue("RPGs").memberCount)
        assertTrue(byName.getValue("RPGs").alreadyContainsAll)
        assertTrue(byName.getValue("RPGs").alreadyContainsSome)
        assertEquals(1, byName.getValue("Favorites").memberCount)
        assertTrue(byName.getValue("Favorites").alreadyContainsAll)

        // Bulk add: RPGs contains both keys; Favorites only contains one of two.
        val bulk = CollectionDialogRows.buildAddOptions(
            collections,
            listOf("rom:snes:ct", "rom:snes:ff6"),
        ).associateBy { it.name }
        assertTrue(bulk.getValue("RPGs").alreadyContainsAll)
        assertFalse(bulk.getValue("Favorites").alreadyContainsAll)
        assertTrue(bulk.getValue("Favorites").alreadyContainsSome)
    }

    @Test
    fun `normalizeCollectionName trims whitespace and rejects blank input`() {
        assertNull(CollectionDialogRows.normalizeCollectionName(""))
        assertNull(CollectionDialogRows.normalizeCollectionName("   \t "))
        assertEquals("Co-op Nights", CollectionDialogRows.normalizeCollectionName("  Co-op Nights  "))
    }
}
