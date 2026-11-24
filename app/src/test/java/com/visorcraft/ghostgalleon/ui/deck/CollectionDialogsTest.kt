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

    @Test
    fun `availableMemberActions omits no-op and redundant reorder options at boundaries`() {
        val m = CollectionDialogRows.MemberAction::class.java
        assertTrue(m.isEnum)

        // Single-item collection: remove only.
        assertEquals(
            listOf(CollectionDialogRows.MemberAction.REMOVE),
            CollectionDialogRows.availableMemberActions(index = 0, totalCount = 1),
        )
        // Out of bounds: safe remove fallback.
        assertEquals(
            listOf(CollectionDialogRows.MemberAction.REMOVE),
            CollectionDialogRows.availableMemberActions(index = 5, totalCount = 2),
        )
        // Two items: index 0 can only move down; index 1 can only move up.
        assertEquals(
            listOf(
                CollectionDialogRows.MemberAction.MOVE_DOWN,
                CollectionDialogRows.MemberAction.REMOVE,
            ),
            CollectionDialogRows.availableMemberActions(index = 0, totalCount = 2),
        )
        assertEquals(
            listOf(
                CollectionDialogRows.MemberAction.MOVE_UP,
                CollectionDialogRows.MemberAction.REMOVE,
            ),
            CollectionDialogRows.availableMemberActions(index = 1, totalCount = 2),
        )
        // Five items: index 0 has down+end; index 1 has up+down+end (no redundant top);
        // index 2 has all four moves; index 3 has top+up+down (no redundant end); index 4 has top+up.
        assertEquals(
            listOf(
                CollectionDialogRows.MemberAction.MOVE_DOWN,
                CollectionDialogRows.MemberAction.MOVE_TO_END,
                CollectionDialogRows.MemberAction.REMOVE,
            ),
            CollectionDialogRows.availableMemberActions(index = 0, totalCount = 5),
        )
        assertEquals(
            listOf(
                CollectionDialogRows.MemberAction.MOVE_UP,
                CollectionDialogRows.MemberAction.MOVE_DOWN,
                CollectionDialogRows.MemberAction.MOVE_TO_END,
                CollectionDialogRows.MemberAction.REMOVE,
            ),
            CollectionDialogRows.availableMemberActions(index = 1, totalCount = 5),
        )
        assertEquals(
            listOf(
                CollectionDialogRows.MemberAction.MOVE_TO_TOP,
                CollectionDialogRows.MemberAction.MOVE_UP,
                CollectionDialogRows.MemberAction.MOVE_DOWN,
                CollectionDialogRows.MemberAction.MOVE_TO_END,
                CollectionDialogRows.MemberAction.REMOVE,
            ),
            CollectionDialogRows.availableMemberActions(index = 2, totalCount = 5),
        )
        assertEquals(
            listOf(
                CollectionDialogRows.MemberAction.MOVE_TO_TOP,
                CollectionDialogRows.MemberAction.MOVE_UP,
                CollectionDialogRows.MemberAction.MOVE_DOWN,
                CollectionDialogRows.MemberAction.REMOVE,
            ),
            CollectionDialogRows.availableMemberActions(index = 3, totalCount = 5),
        )
        assertEquals(
            listOf(
                CollectionDialogRows.MemberAction.MOVE_TO_TOP,
                CollectionDialogRows.MemberAction.MOVE_UP,
                CollectionDialogRows.MemberAction.REMOVE,
            ),
            CollectionDialogRows.availableMemberActions(index = 4, totalCount = 5),
        )
    }
}
