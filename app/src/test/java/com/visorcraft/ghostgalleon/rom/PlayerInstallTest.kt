package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerInstallTest {

    @Test
    fun `missingPrimaries lists distinct default packages not installed`() {
        val installed = setOf("com.retroarch.aarch64")
        val missing = PlayerInstall.missingPrimaries { it in installed }
        assertTrue(missing.none { it.packageName == "com.retroarch.aarch64" })
        assertTrue(missing.any { it.packageName == "org.azahar_emu.azahar" })
        assertEquals(missing.size, missing.distinctBy { it.packageName }.size)
        assertTrue(missing.isNotEmpty())
    }

    @Test
    fun `missingPrimaries empty when everything is installed`() {
        assertTrue(PlayerInstall.missingPrimaries { true }.isEmpty())
    }

    @Test
    fun `store uris are play store links`() {
        val pkg = "org.ppsspp.ppsspp"
        assertEquals("market://details?id=$pkg", PlayerInstall.marketUri(pkg))
        assertEquals(
            "https://play.google.com/store/apps/details?id=$pkg",
            PlayerInstall.webStoreUri(pkg),
        )
        assertFalse(PlayerInstall.marketUri(pkg).contains(" "))
    }
}
