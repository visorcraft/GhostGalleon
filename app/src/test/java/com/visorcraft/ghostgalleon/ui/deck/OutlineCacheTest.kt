package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlineCacheTest {

    @Test
    fun `quantizeRadiusPx clamps extremes and buckets integer and float radii consistently`() {
        assertEquals(0, OutlineCache.quantizeRadiusPx(-10))
        assertEquals(0, OutlineCache.quantizeRadiusPx(0))
        assertEquals(2, OutlineCache.quantizeRadiusPx(1))
        assertEquals(2, OutlineCache.quantizeRadiusPx(2))
        assertEquals(24, OutlineCache.quantizeRadiusPx(23))
        assertEquals(24, OutlineCache.quantizeRadiusPx(24))
        assertEquals(26, OutlineCache.quantizeRadiusPx(25))
        assertEquals(OutlineCache.MAX_RADIUS_PX, OutlineCache.quantizeRadiusPx(OutlineCache.MAX_RADIUS_PX))
        assertEquals(OutlineCache.MAX_RADIUS_PX, OutlineCache.quantizeRadiusPx(10_000))

        // Float edge cases and sub-pixel density jitter.
        assertEquals(0, OutlineCache.quantizeRadiusPx(Float.NaN))
        assertEquals(0, OutlineCache.quantizeRadiusPx(Float.NEGATIVE_INFINITY))
        assertEquals(0, OutlineCache.quantizeRadiusPx(-0.0f))
        assertEquals(0, OutlineCache.quantizeRadiusPx(0.4f))
        assertEquals(2, OutlineCache.quantizeRadiusPx(0.6f))
        assertEquals(24, OutlineCache.quantizeRadiusPx(23.4f))
        assertEquals(24, OutlineCache.quantizeRadiusPx(24.0f))
        assertEquals(24, OutlineCache.quantizeRadiusPx(24.4f))
        assertEquals(OutlineCache.MAX_RADIUS_PX, OutlineCache.quantizeRadiusPx(Float.POSITIVE_INFINITY))
        assertEquals(OutlineCache.MAX_RADIUS_PX, OutlineCache.quantizeRadiusPx(9999.5f))
    }

    @Test
    fun `RoundedOutlineStore reuses quantized providers and enforces strict LRU cache bound`() {
        var creations = 0
        val store = OutlineCache.RoundedOutlineStore(maxEntries = OutlineCache.MAX_ROUNDED_RECT_ENTRIES) { q ->
            creations++
            "provider:$q"
        }

        val p24 = store.get(24)
        assertSame(p24, store.get(23))
        assertSame(p24, store.get(23.6f))
        assertSame(p24, store.get(24.4f))
        assertEquals(1, creations)
        assertEquals(1L, store.missCount())
        assertEquals(3L, store.hitCount())

        // Zero and negative collapse to shared 0px provider.
        val p0 = store.get(0)
        assertSame(p0, store.get(-5))
        assertSame(p0, store.get(Float.NaN))
        assertEquals(2, creations)

        // Flooding far past capacity stays strictly bounded at MAX_ROUNDED_RECT_ENTRIES
        // and preserves the hottest recently accessed bucket.
        store.clear()
        creations = 0
        val smallStore = OutlineCache.RoundedOutlineStore(maxEntries = 8) { q ->
            creations++
            "p:$q"
        }
        for (i in 0 until 8) {
            smallStore.get(i * 2)
        }
        // Touch bucket 0 so bucket 2 is LRU.
        smallStore.get(0)
        smallStore.get(16)
        assertEquals(8, smallStore.size())
        assertTrue(smallStore.containsQuantizedRadius(0))
        assertFalse(smallStore.containsQuantizedRadius(2))
        assertTrue(smallStore.containsQuantizedRadius(16))

        for (r in 0..500) {
            store.get(r)
            assertTrue(
                "outline store exceeded cap: ${store.size()}",
                store.size() <= OutlineCache.MAX_ROUNDED_RECT_ENTRIES,
            )
        }
        assertEquals(OutlineCache.MAX_ROUNDED_RECT_ENTRIES, store.size())
    }
}
