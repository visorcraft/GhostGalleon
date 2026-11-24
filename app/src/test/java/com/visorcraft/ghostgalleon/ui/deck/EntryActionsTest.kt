package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.rom.LaunchFace
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.PlayerResolver
import com.visorcraft.ghostgalleon.rom.SessionPolicy
import com.visorcraft.ghostgalleon.rom.StagePlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryActionsTest {

    @Test
    fun `playerProfileRows marks installed status, active profile, and platform default sentinel`() {
        val snes = Platforms.byId("snes")!!
        val players = snes.players
        assertTrue(players.isNotEmpty())
        val firstPkg = PlayerResolver.packageName(players.first())

        val withDefault = EntryActionChoices.playerProfileRows(
            players = players,
            currentProfileId = null,
            isInstalled = { it == firstPkg },
        )
        assertEquals(players.size + 1, withDefault.size)
        assertTrue(withDefault.first().installed)
        assertFalse(withDefault.first().selected)
        assertTrue(withDefault.last().isPlatformDefault)
        assertNull(withDefault.last().playerId)
        assertTrue(withDefault.last().selected)

        val withOverride = EntryActionChoices.playerProfileRows(
            players = players,
            currentProfileId = players.first().id,
            isInstalled = { false },
        )
        assertTrue(withOverride.first().selected)
        assertFalse(withOverride.first().installed)
        assertFalse(withOverride.last().selected)
    }

    @Test
    fun `preferredPlayerId prioritizes per-ROM profile over platform default`() {
        assertEquals(
            "custom_player",
            EntryActionChoices.preferredPlayerId(
                romId = "snes:zelda",
                platformId = "snes",
                romProfiles = mapOf("snes:zelda" to "custom_player"),
                defaultPlayers = mapOf("snes" to "platform_default"),
            ),
        )
        assertEquals(
            "platform_default",
            EntryActionChoices.preferredPlayerId(
                romId = "snes:mario",
                platformId = "snes",
                romProfiles = emptyMap(),
                defaultPlayers = mapOf("snes" to "platform_default"),
            ),
        )
    }

    @Test
    fun `stagePlotRows builds dual and single options with exact selection and policy labels`() {
        val dualDefault = EntryActionChoices.stagePlotRows(dual = true, current = null)
        assertEquals(5, dualDefault.size)
        assertTrue(dualDefault[0].clear && dualDefault[0].selected)
        assertEquals(1, dualDefault.count { it.selected })

        val dualOther = EntryActionChoices.stagePlotRows(
            dual = true,
            current = StagePlot(SessionPolicy.KEEP_COMPANION, LaunchFace.OTHER),
        )
        assertEquals(1, dualOther.count { it.selected })
        val selectedDual = dualOther.single { it.selected }
        assertEquals(SessionPolicy.KEEP_COMPANION, selectedDual.policy)
        assertEquals(LaunchFace.OTHER, selectedDual.face)

        val singleYield = EntryActionChoices.stagePlotRows(
            dual = false,
            current = StagePlot(SessionPolicy.YIELD_BOTH, LaunchFace.AUTO),
        )
        assertEquals(3, singleYield.size)
        assertTrue(singleYield.last().selected)
        assertEquals(SessionPolicy.YIELD_BOTH, singleYield.last().policy)

        assertEquals(
            R.string.settings_stage_yield,
            EntryActionChoices.builtInPolicyLabelRes(SessionPolicy.YIELD_BOTH),
        )
        assertEquals(
            R.string.settings_stage_keep,
            EntryActionChoices.builtInPolicyLabelRes(SessionPolicy.KEEP_COMPANION),
        )
    }

    @Test
    fun `selectedRowIndex finds active emulator, profile, or stage-plot row and returns -1 when absent`() {
        val snes = Platforms.byId("snes")!!
        val profileRows = EntryActionChoices.playerProfileRows(
            players = snes.players,
            currentProfileId = null,
            isInstalled = { true },
        )
        assertEquals(
            profileRows.lastIndex,
            EntryActionChoices.selectedRowIndex(profileRows) { it.selected },
        )

        val dualOther = EntryActionChoices.stagePlotRows(
            dual = true,
            current = StagePlot(SessionPolicy.KEEP_COMPANION, LaunchFace.OTHER),
        )
        assertEquals(3, EntryActionChoices.selectedRowIndex(dualOther) { it.selected })

        assertEquals(
            -1,
            EntryActionChoices.selectedRowIndex(snes.players) { it.id == "nonexistent-player" },
        )
        assertEquals(
            -1,
            EntryActionChoices.selectedRowIndex(emptyList<String>()) { true },
        )
    }
}
