package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for Iteration 8 [CompanionPanel] hot-path guards (accent
 * repaint diffing, off-UI animated WebP token validation, and shared
 * [AppIconLoader] / [IconDensityMetrics] eviction keys).
 */
class CompanionPanelPerfLogicTest {

    @Test
    fun `shouldRepaintAccent skips redundant panel and divider repaints when accent unchanged`() {
        assertTrue(CompanionPanel.shouldRepaintAccent(hasPaintedAccent = false, previousAccent = 0, nextAccent = 0))
        assertFalse(
            CompanionPanel.shouldRepaintAccent(
                hasPaintedAccent = true,
                previousAccent = 0xFF3F51B5.toInt(),
                nextAccent = 0xFF3F51B5.toInt(),
            ),
        )
        assertTrue(
            CompanionPanel.shouldRepaintAccent(
                hasPaintedAccent = true,
                previousAccent = 0xFF3F51B5.toInt(),
                nextAccent = 0xFF00BCD4.toInt(),
            ),
        )
    }

    @Test
    fun `shouldApplyDecodedAnim requires both window attachment and exact bind token match`() {
        val token = "anim:hero_ship_anim:42"
        assertTrue(
            CompanionPanel.shouldApplyDecodedAnim(
                isAttached = true,
                currentToken = token,
                expectedToken = token,
            ),
        )
        // Detached view drops decoded WebP even if token still matches.
        assertFalse(
            CompanionPanel.shouldApplyDecodedAnim(
                isAttached = false,
                currentToken = token,
                expectedToken = token,
            ),
        )
        // Cleared or replaced token drops stale decode even while attached.
        assertFalse(
            CompanionPanel.shouldApplyDecodedAnim(
                isAttached = true,
                currentToken = null,
                expectedToken = token,
            ),
        )
        assertFalse(
            CompanionPanel.shouldApplyDecodedAnim(
                isAttached = true,
                currentToken = "anim:hero_ship_anim:43",
                expectedToken = token,
            ),
        )
    }

    @Test
    fun `invalidateShared is a safe no-op before shared cache initialization`() {
        // Pure JVM host test: calling invalidateShared before any Context initializes
        // sharedInstance must never throw.
        AppIconLoader.invalidateShared()
        AppIconLoader.invalidateShared("com.example.game")
        assertTrue(IconDensityMetrics.matchesPackage(IconDensityMetrics.cacheKey("com.example.game", 192), "com.example.game"))
        assertFalse(IconDensityMetrics.matchesPackage(IconDensityMetrics.cacheKey("com.example.game.extra", 192), "com.example.game"))
    }
}
