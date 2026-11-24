package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchDialogLogicTest {

    @Test
    fun `plan shows inline clear whenever draft is non-empty even when History holds neutral slot`() {
        val withQueryAndHistory = SearchDialogLogic.plan(draftText = "zelda", hasHistory = true)
        assertTrue(withQueryAndHistory.showInlineClear)
        assertEquals(SearchDialogLogic.NeutralButtonKind.HISTORY, withQueryAndHistory.neutralButton)

        val emptyWithHistory = SearchDialogLogic.plan(draftText = "", hasHistory = true)
        assertFalse(emptyWithHistory.showInlineClear)
        assertEquals(SearchDialogLogic.NeutralButtonKind.HISTORY, emptyWithHistory.neutralButton)

        val emptyNoHistory = SearchDialogLogic.plan(draftText = "", hasHistory = false)
        assertFalse(emptyNoHistory.showInlineClear)
        assertEquals(SearchDialogLogic.NeutralButtonKind.CLEAR, emptyNoHistory.neutralButton)

        val queryNoHistory = SearchDialogLogic.plan(draftText = "mario", hasHistory = false)
        assertTrue(queryNoHistory.showInlineClear)
        assertEquals(SearchDialogLogic.NeutralButtonKind.CLEAR, queryNoHistory.neutralButton)
    }

    @Test
    fun `shouldSubmitFromEditor handles IME search-done-go and Enter key down only`() {
        assertTrue(SearchDialogLogic.shouldSubmitFromEditor(SearchDialogLogic.IME_ACTION_SEARCH, null, false))
        assertTrue(SearchDialogLogic.shouldSubmitFromEditor(SearchDialogLogic.IME_ACTION_DONE, null, false))
        assertTrue(SearchDialogLogic.shouldSubmitFromEditor(SearchDialogLogic.IME_ACTION_GO, null, false))
        assertTrue(SearchDialogLogic.shouldSubmitFromEditor(0, SearchDialogLogic.KEYCODE_ENTER, isKeyDown = true))
        assertTrue(
            SearchDialogLogic.shouldSubmitFromEditor(0, SearchDialogLogic.KEYCODE_NUMPAD_ENTER, isKeyDown = true),
        )

        assertFalse(SearchDialogLogic.shouldSubmitFromEditor(0, SearchDialogLogic.KEYCODE_ENTER, isKeyDown = false))
        assertFalse(SearchDialogLogic.shouldSubmitFromEditor(0, 29, isKeyDown = true))
        assertFalse(SearchDialogLogic.shouldSubmitFromEditor(0, null, isKeyDown = false))
    }

    @Test
    fun `normalizeQuery trims whitespace cleanly`() {
        assertEquals("metroid", SearchDialogLogic.normalizeQuery("  metroid \n"))
        assertEquals("", SearchDialogLogic.normalizeQuery("   "))
    }

    @Test
    fun `inline clear button fits strictly inside input end padding without text overlap`() {
        val occupiedEndDp =
            SearchDialogLogic.INLINE_CLEAR_END_MARGIN_DP + SearchDialogLogic.INLINE_CLEAR_SIZE_DP
        assertTrue(
            "clear button ($occupiedEndDp dp) exceeds input end padding (${SearchDialogLogic.INPUT_END_PADDING_DP} dp)",
            occupiedEndDp <= SearchDialogLogic.INPUT_END_PADDING_DP,
        )
    }
}
