package com.visorcraft.ghostgalleon.settings

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ActionLabelTest {

    @Test
    fun `remappable actions have user-friendly labels`() {
        assertEquals(text(R.string.action_confirm_launch), Action.CONFIRM.label())
        assertEquals(text(R.string.action_back), Action.BACK.label())
        assertEquals(text(R.string.action_swap_screens), Action.SWAP_SCREENS.label())
        assertEquals(text(R.string.action_toggle_mode), Action.TOGGLE_MODE.label())
        assertEquals(text(R.string.action_open_settings), Action.OPEN_SETTINGS.label())
        assertEquals(text(R.string.action_page_left), Action.PAGE_PREV.label())
        assertEquals(text(R.string.action_page_right), Action.PAGE_NEXT.label())
        assertEquals(text(R.string.action_open_quick_panel), Action.OPEN_QUICK_PANEL.label())
    }

    @Test
    fun `no user-visible label is a raw enum name`() {
        Action.entries.forEach { action ->
            assertNotEquals(action.name, action.label())
        }
    }
}
