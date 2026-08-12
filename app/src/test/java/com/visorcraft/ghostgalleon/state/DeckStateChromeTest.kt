package com.visorcraft.ghostgalleon.state

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckStateChromeTest {

    @Test
    fun `notifyChromeRefresh tags CHROME not SETTINGS`() {
        val state = DeckState()
        var last: DeckState.Change? = null
        state.addListener { last = it.lastChange }
        state.notifyChromeRefresh()
        assertEquals(DeckState.Change.CHROME, last)
        assertEquals(DeckState.Change.CHROME, state.lastChange)
    }

    @Test
    fun `notifyChanged still tags SETTINGS`() {
        val state = DeckState()
        state.notifyChromeRefresh()
        state.notifyChanged()
        assertEquals(DeckState.Change.SETTINGS, state.lastChange)
    }
}
