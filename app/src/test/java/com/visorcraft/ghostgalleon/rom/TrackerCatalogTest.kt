package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerCatalogTest {

    private val block = LensBlock(0x7EF340, 2, "bitfield", emptyList())
    private val spec = LensSpec(
        id = "fix",
        title = "fix",
        platformId = "snes",
        hashes = setOf("abc"),
        romIds = emptySet(),
        intervalMs = 200,
        blocks = listOf(block),
        surface = "tracker",
        widgets = listOf(
            TrackerWidget(TrackerKind.BITS, 0, 8, listOf("bow", "boomerang")),
        ),
    )

    @Test
    fun `acceptable widgets stay inside the lens budget`() {
        assertTrue(LensCatalog.acceptable(spec))
        assertTrue(TrackerCatalog.acceptable(spec))
        val badIndex = spec.copy(
            widgets = listOf(TrackerWidget(TrackerKind.BITS, 3, 8, listOf("x"))),
        )
        assertFalse(TrackerCatalog.acceptable(badIndex))
        val tooManyBits = spec.copy(
            widgets = listOf(TrackerWidget(TrackerKind.BITS, 0, 8, List(17) { "b$it" })),
        )
        assertFalse(TrackerCatalog.acceptable(tooManyBits))
        val line = spec.copy(surface = "line", widgets = emptyList())
        assertTrue(TrackerCatalog.acceptable(line))
    }

    @Test
    fun `bitOn reads little-endian bit index`() {
        val bytes = byteArrayOf(0x02, 0x00) // bit 1
        assertFalse(TrackerCatalog.bitOn(bytes, 0))
        assertTrue(TrackerCatalog.bitOn(bytes, 1))
        assertFalse(TrackerCatalog.bitOn(bytes, 16))
        assertEquals(2, TrackerCatalog.meterValue(bytes))
        assertEquals(0, TrackerCatalog.meterValue(byteArrayOf()))
    }
}
