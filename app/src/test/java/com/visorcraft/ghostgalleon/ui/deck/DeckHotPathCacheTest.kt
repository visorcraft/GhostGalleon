package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckHotPathCacheTest {

    @Test
    fun `TemplateKey hits only on exact surface, accent, radius, and density match`() {
        val base = DeckHotPathCache.TemplateKey(
            surfaceFill = 0xFF1C1C22.toInt(),
            accent = 0xFF3F51B5.toInt(),
            radiusDp = 24,
            density = 2.0f,
        )
        assertFalse(DeckHotPathCache.isCacheHit(null, base))
        assertTrue(DeckHotPathCache.isCacheHit(base, base.copy()))
        assertFalse(DeckHotPathCache.isCacheHit(base, base.copy(accent = 0xFF00A8A0.toInt())))
        assertFalse(DeckHotPathCache.isCacheHit(base, base.copy(surfaceFill = 0xFF101010.toInt())))
        assertFalse(DeckHotPathCache.isCacheHit(base, base.copy(radiusDp = 12)))
        assertFalse(DeckHotPathCache.isCacheHit(base, base.copy(density = 2.5f)))
    }

    @Test
    fun `layoutParamsUnchanged skips reallocation only when both width and height match`() {
        assertTrue(DeckHotPathCache.layoutParamsUnchanged(180, 210, 180, 210))
        assertFalse(DeckHotPathCache.layoutParamsUnchanged(180, 210, 180, 220))
        assertFalse(DeckHotPathCache.layoutParamsUnchanged(180, 210, 190, 210))
        assertFalse(DeckHotPathCache.layoutParamsUnchanged(-1, -1, 180, 210))
    }
}
