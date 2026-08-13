package com.visorcraft.ghostgalleon.settings

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.input.InputOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `play surface actions have user-friendly labels`() {
        assertEquals(text(R.string.action_open_session_switcher), Action.OPEN_SESSION_SWITCHER.label())
        assertEquals(text(R.string.action_toggle_play_hud), Action.TOGGLE_PLAY_HUD.label())
    }

    @Test
    fun `input owner actions have user-friendly labels`() {
        assertEquals(text(R.string.action_claim_host), Action.CLAIM_HOST.label())
        assertEquals(text(R.string.action_release_host), Action.RELEASE_HOST.label())
    }

    @Test
    fun `input owner chips have user-friendly labels`() {
        assertEquals(text(R.string.input_owner_game), InputOwner.GAME.hint())
        assertEquals(text(R.string.input_owner_host), InputOwner.HOST.hint())
        assertNull(InputOwner.NONE.hint())
    }

    @Test
    fun `toggle seat has a user-friendly label`() {
        assertEquals(text(R.string.action_toggle_seat), Action.TOGGLE_SEAT.label())
    }
}
