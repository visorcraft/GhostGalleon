package com.visorcraft.ghostgalleon.audio

import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundPackIdTest {

    @Test
    fun `fromId parses known ids case-insensitively and falls back to AUTO`() {
        assertEquals(SoundPackId.AUTO, SoundPackId.fromId("auto"))
        assertEquals(SoundPackId.GHOST, SoundPackId.fromId("GHOST"))
        assertEquals(SoundPackId.THREEDS, SoundPackId.fromId("  threeds "))
        assertEquals(SoundPackId.OLED, SoundPackId.fromId("Oled"))
        assertEquals(SoundPackId.NEON, SoundPackId.fromId("neon"))
        assertEquals(SoundPackId.OFF, SoundPackId.fromId("OFF"))

        assertEquals(SoundPackId.AUTO, SoundPackId.fromId(""))
        assertEquals(SoundPackId.AUTO, SoundPackId.fromId("   "))
        assertEquals(SoundPackId.AUTO, SoundPackId.fromId("unknown_pack"))
        assertEquals(SoundPackId.AUTO, SoundPackId.fromId(null))
    }

    @Test
    fun `AUTO resolves to matching built-in ThemePack id and falls back to GHOST`() {
        assertEquals(SoundPackId.GHOST, SoundPackId.AUTO.resolve(ThemePack.GHOST.id))
        assertEquals(SoundPackId.THREEDS, SoundPackId.AUTO.resolve(ThemePack.THREEDS.id))
        assertEquals(SoundPackId.OLED, SoundPackId.AUTO.resolve(ThemePack.OLED.id))
        assertEquals(SoundPackId.NEON, SoundPackId.AUTO.resolve(ThemePack.NEON.id))
        assertEquals(SoundPackId.GHOST, SoundPackId.AUTO.resolve("custom_user_theme"))
        assertEquals(SoundPackId.GHOST, SoundPackId.AUTO.resolve(null))
    }

    @Test
    fun `explicit packs and OFF ignore themeId when resolving`() {
        val explicit = listOf(
            SoundPackId.GHOST,
            SoundPackId.THREEDS,
            SoundPackId.OLED,
            SoundPackId.NEON,
            SoundPackId.OFF,
        )
        for (pack in explicit) {
            for (theme in ThemePack.BUILTINS) {
                assertEquals(pack, pack.resolve(theme.id))
            }
        }
    }

    @Test
    fun `isPlayable and CONCRETE_PACKS cover all four built-in themes`() {
        assertFalse(SoundPackId.OFF.isPlayable)
        assertTrue(SoundPackId.AUTO.isPlayable)
        assertEquals(
            listOf(SoundPackId.GHOST, SoundPackId.THREEDS, SoundPackId.OLED, SoundPackId.NEON),
            SoundPackId.CONCRETE_PACKS,
        )
        for (pack in SoundPackId.CONCRETE_PACKS) {
            assertTrue(pack.isPlayable)
        }
    }

    @Test
    fun `SoundCue catalog contains all 24 semantic cues with valid metadata`() {
        assertEquals(24, SoundCue.entries.size)
        for (cue in SoundCue.entries) {
            assertTrue("priority out of bounds for $cue", cue.priority in 1..10)
            assertTrue("cooldown must be positive for $cue", cue.defaultCooldownMs > 0L)
        }
        assertTrue(SoundCue.LAUNCH_GAME.bypassesNavThrottle)
        assertTrue(SoundCue.LAUNCH_APP.bypassesNavThrottle)
        assertTrue(SoundCue.CONFIRM.bypassesNavThrottle)
        assertTrue(SoundCue.RA_ACHIEVEMENT.bypassesNavThrottle)
        assertFalse(SoundCue.NAV_TICK.bypassesNavThrottle)
    }
}
