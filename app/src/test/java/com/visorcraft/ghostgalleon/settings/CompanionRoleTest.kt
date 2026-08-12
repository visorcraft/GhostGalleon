package com.visorcraft.ghostgalleon.settings

import com.visorcraft.ghostgalleon.rom.SessionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CompanionRoleTest {

    @Test
    fun `parse accepts names case-insensitively and blanks to HERO`() {
        assertEquals(CompanionRole.HERO, CompanionRole.parse("HERO"))
        assertEquals(CompanionRole.NOW_PLAYING, CompanionRole.parse("now_playing"))
        assertEquals(CompanionRole.PERF_HUD, CompanionRole.parse(" Perf_Hud "))
        assertEquals(CompanionRole.PINNED_APP, CompanionRole.parse("PINNED_APP"))
        assertEquals(CompanionRole.HERO, CompanionRole.parse(null))
        assertEquals(CompanionRole.HERO, CompanionRole.parse(""))
        assertEquals(CompanionRole.HERO, CompanionRole.parse("nope"))
    }

    @Test
    fun `effective keeps HERO and PERF_HUD as preferred`() {
        assertEquals(
            CompanionRole.HERO,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(preferred = CompanionRole.HERO),
            ),
        )
        assertEquals(
            CompanionRole.PERF_HUD,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(preferred = CompanionRole.PERF_HUD),
            ),
        )
    }

    @Test
    fun `effective NOW_PLAYING needs an open session else HERO`() {
        assertEquals(
            CompanionRole.NOW_PLAYING,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.NOW_PLAYING,
                    openSessionKey = "rom:snes:x.sfc",
                ),
            ),
        )
        assertEquals(
            CompanionRole.HERO,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(preferred = CompanionRole.NOW_PLAYING),
            ),
        )
    }

    @Test
    fun `effective PINNED_APP when package set and installed`() {
        assertEquals(
            CompanionRole.PINNED_APP,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.PINNED_APP,
                    pinnedPackage = "com.example.pin",
                    pinnedPackageInstalled = true,
                ),
            ),
        )
    }

    @Test
    fun `effective PINNED_APP dual-claim stays PINNED_APP for honesty CTA`() {
        // Must not silently demote to HERO/NOW_PLAYING — CompanionPanel shows
        // deck_pin_dual_claim only when effective role is still PINNED_APP.
        assertEquals(
            CompanionRole.PINNED_APP,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.PINNED_APP,
                    pinnedPackage = "com.example.pin",
                    openSessionKey = "rom:nds:mario.nds",
                    sessionPolicy = SessionPolicy.YIELD_BOTH,
                ),
            ),
        )
        assertEquals(
            CompanionRole.PINNED_APP,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.PINNED_APP,
                    pinnedPackage = "com.example.pin",
                    sessionPolicy = SessionPolicy.YIELD_BOTH,
                    openSessionKey = null,
                ),
            ),
        )
        assertEquals(
            CompanionRoleResolve.PinHonesty.DUAL_CLAIM,
            CompanionRoleResolve.pinHonesty(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.PINNED_APP,
                    pinnedPackage = "com.example.pin",
                    pinnedPackageInstalled = true,
                    openSessionKey = "rom:nds:mario.nds",
                    sessionPolicy = SessionPolicy.YIELD_BOTH,
                ),
            ),
        )
    }

    @Test
    fun `effective PINNED_APP empty or missing stays PINNED_APP for honest CTA`() {
        assertEquals(
            CompanionRole.PINNED_APP,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.PINNED_APP,
                    pinnedPackage = null,
                ),
            ),
        )
        assertEquals(
            CompanionRole.PINNED_APP,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.PINNED_APP,
                    pinnedPackage = "   ",
                ),
            ),
        )
        assertEquals(
            CompanionRole.PINNED_APP,
            CompanionRoleResolve.effective(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.PINNED_APP,
                    pinnedPackage = "com.example.gone",
                    pinnedPackageInstalled = false,
                ),
            ),
        )
    }

    @Test
    fun `pinHonesty reports empty missing ready and dual-claim`() {
        assertEquals(
            CompanionRoleResolve.PinHonesty.EMPTY,
            CompanionRoleResolve.pinHonesty(
                CompanionRoleResolve.Context(preferred = CompanionRole.PINNED_APP),
            ),
        )
        assertEquals(
            CompanionRoleResolve.PinHonesty.MISSING,
            CompanionRoleResolve.pinHonesty(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.PINNED_APP,
                    pinnedPackage = "com.example.gone",
                    pinnedPackageInstalled = false,
                ),
            ),
        )
        assertEquals(
            CompanionRoleResolve.PinHonesty.READY,
            CompanionRoleResolve.pinHonesty(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.PINNED_APP,
                    pinnedPackage = "com.example.pin",
                    pinnedPackageInstalled = true,
                ),
            ),
        )
        assertEquals(
            CompanionRoleResolve.PinHonesty.DUAL_CLAIM,
            CompanionRoleResolve.pinHonesty(
                CompanionRoleResolve.Context(
                    preferred = CompanionRole.PINNED_APP,
                    pinnedPackage = "com.example.pin",
                    pinnedPackageInstalled = true,
                    sessionPolicy = SessionPolicy.YIELD_BOTH,
                    openSessionKey = "rom:nds:x.nds",
                ),
            ),
        )
    }

    @Test
    fun `pinHonesty openSession NDS with KEEP_COMPANION is not DUAL_CLAIM`() {
        val honesty = CompanionRoleResolve.pinHonesty(
            CompanionRoleResolve.Context(
                preferred = CompanionRole.PINNED_APP,
                pinnedPackage = "com.example.pin",
                pinnedPackageInstalled = true,
                openSessionKey = "rom:nds:mario.nds",
                sessionPolicy = SessionPolicy.KEEP_COMPANION,
            ),
        )
        assertNotEquals(CompanionRoleResolve.PinHonesty.DUAL_CLAIM, honesty)
        assertEquals(CompanionRoleResolve.PinHonesty.READY, honesty)
    }
}
