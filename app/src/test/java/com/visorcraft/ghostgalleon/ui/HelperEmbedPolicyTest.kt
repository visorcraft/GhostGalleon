package com.visorcraft.ghostgalleon.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperEmbedPolicyTest {

    @Test
    fun `rom helper wins then global`() {
        assertEquals(
            "org.wiki",
            HelperEmbedPolicy.resolvePackage("snes:a", mapOf("snes:a" to "org.wiki"), "org.maps"),
        )
        assertEquals("org.maps", HelperEmbedPolicy.resolvePackage("snes:a", emptyMap(), "org.maps"))
        assertNull(HelperEmbedPolicy.resolvePackage("snes:a", emptyMap(), null))
    }

    @Test
    fun `embed refuses session package yield and missing api`() {
        assertTrue(
            HelperEmbedPolicy.mayEmbed(true, false, "org.wiki", "com.retroarch.aarch64", true, false),
        )
        assertFalse(
            HelperEmbedPolicy.mayEmbed(true, false, "com.retroarch.aarch64", "com.retroarch.aarch64", true, false),
        )
        assertFalse(
            HelperEmbedPolicy.mayEmbed(true, true, "org.wiki", "com.retroarch.aarch64", true, false),
        )
        assertFalse(
            HelperEmbedPolicy.mayEmbed(false, false, "org.wiki", "com.retroarch.aarch64", true, false),
        )
        assertFalse(
            HelperEmbedPolicy.mayEmbed(true, false, "org.wiki", "com.retroarch.aarch64", false, false),
        )
        assertFalse(
            HelperEmbedPolicy.mayEmbed(true, false, "org.wiki", "com.retroarch.aarch64", true, true),
        )
        assertFalse(HelperEmbedPolicy.mayLaunchOnHostDisplay())
    }
}
