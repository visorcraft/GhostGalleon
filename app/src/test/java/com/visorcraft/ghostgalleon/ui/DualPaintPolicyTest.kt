package com.visorcraft.ghostgalleon.ui

import com.visorcraft.ghostgalleon.rom.SessionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualPaintPolicyTest {

    @Test
    fun `re-entrancy blocks full render`() {
        assertFalse(
            DualPaintPolicy.allowFullRender(
                rendering = true,
                hasPainted = false,
                nowUptimeMs = 1000,
                lastFullRenderUptimeMs = 0,
            ),
        )
    }

    @Test
    fun `hold first paint until the ROM index is ready`() {
        assertTrue(DualPaintPolicy.holdFirstPaintUntilReady(romIndexReady = false))
        assertFalse(DualPaintPolicy.holdFirstPaintUntilReady(romIndexReady = true))
    }

    @Test
    fun `first paint always allowed when not rendering`() {
        assertTrue(
            DualPaintPolicy.allowFullRender(
                rendering = false,
                hasPainted = false,
                nowUptimeMs = 1000,
                lastFullRenderUptimeMs = 999,
            ),
        )
    }

    @Test
    fun `coalesce denies paint inside gap`() {
        assertFalse(
            DualPaintPolicy.allowFullRender(
                rendering = false,
                hasPainted = true,
                nowUptimeMs = 1000,
                lastFullRenderUptimeMs = 990,
                minGapMs = 32,
            ),
        )
        assertTrue(
            DualPaintPolicy.allowFullRender(
                rendering = false,
                hasPainted = true,
                nowUptimeMs = 1032,
                lastFullRenderUptimeMs = 1000,
                minGapMs = 32,
            ),
        )
    }

    @Test
    fun `deferred delay is null when paint allowed`() {
        assertEquals(
            null,
            DualPaintPolicy.deferredFullRenderDelayMs(
                rendering = false,
                hasPainted = true,
                nowUptimeMs = 1100,
                lastFullRenderUptimeMs = 1000,
                minGapMs = 32,
            ),
        )
    }

    @Test
    fun `deferred delay posts after nest and remaining coalesce gap`() {
        assertEquals(
            0L,
            DualPaintPolicy.deferredFullRenderDelayMs(
                rendering = true,
                hasPainted = true,
                nowUptimeMs = 1000,
                lastFullRenderUptimeMs = 900,
                minGapMs = 32,
            ),
        )
        assertEquals(
            22L,
            DualPaintPolicy.deferredFullRenderDelayMs(
                rendering = false,
                hasPainted = true,
                nowUptimeMs = 1010,
                lastFullRenderUptimeMs = 1000,
                minGapMs = 32,
            ),
        )
    }

    @Test
    fun `needsPaintForDisplay on epoch or display change`() {
        assertTrue(
            DualPaintPolicy.needsPaintForDisplay(
                hasPainted = false,
                paintedDisplayId = null,
                currentDisplayId = 1,
                appliedEpoch = -1,
                contentEpoch = 0,
            ),
        )
        assertTrue(
            DualPaintPolicy.needsPaintForDisplay(
                hasPainted = true,
                paintedDisplayId = 0,
                currentDisplayId = 1,
                appliedEpoch = 3,
                contentEpoch = 3,
            ),
        )
        assertTrue(
            DualPaintPolicy.needsPaintForDisplay(
                hasPainted = true,
                paintedDisplayId = 1,
                currentDisplayId = 1,
                appliedEpoch = 2,
                contentEpoch = 3,
            ),
        )
        assertFalse(
            DualPaintPolicy.needsPaintForDisplay(
                hasPainted = true,
                paintedDisplayId = 1,
                currentDisplayId = 1,
                appliedEpoch = 3,
                contentEpoch = 3,
            ),
        )
    }

    @Test
    fun `absorb never opens drawer`() {
        assertFalse(DualPaintPolicy.absorbMayOpenDrawer())
    }

    @Test
    fun `seat claim absorbs when another holder exists`() {
        assertTrue(DualPaintPolicy.shouldAbsorbSeat(seatHeldByOther = true))
        assertFalse(DualPaintPolicy.shouldAbsorbSeat(seatHeldByOther = false))
    }

    @Test
    fun `drawer open-only never closes during storm`() {
        assertEquals(
            DualPaintPolicy.DrawerAction.NONE,
            DualPaintPolicy.drawerAction(
                withinDebounce = false,
                drawerAlreadyOpen = true,
                allowToggle = false,
            ),
        )
        assertEquals(
            DualPaintPolicy.DrawerAction.CLOSE,
            DualPaintPolicy.drawerAction(
                withinDebounce = false,
                drawerAlreadyOpen = true,
                allowToggle = true,
            ),
        )
        assertEquals(
            DualPaintPolicy.DrawerAction.OPEN,
            DualPaintPolicy.drawerAction(
                withinDebounce = false,
                drawerAlreadyOpen = false,
                allowToggle = false,
            ),
        )
        assertEquals(
            DualPaintPolicy.DrawerAction.NONE,
            DualPaintPolicy.drawerAction(
                withinDebounce = true,
                drawerAlreadyOpen = false,
                allowToggle = true,
            ),
        )
    }

    @Test
    fun `heal debounced and launch only when no peer on target`() {
        assertFalse(DualPaintPolicy.allowHeal(1000, 500, minGapMs = 2000))
        assertTrue(DualPaintPolicy.allowHeal(3000, 500, minGapMs = 2000))
        assertFalse(DualPaintPolicy.shouldLaunchCompanion(anyPeerOnTarget = true))
        assertTrue(DualPaintPolicy.shouldLaunchCompanion(anyPeerOnTarget = false))
    }

    @Test
    fun `companionHealAction restarts unhealthy peers and launches when empty`() {
        assertEquals(
            DualPaintPolicy.HealAction.NONE,
            DualPaintPolicy.companionHealAction(
                dualMode = false,
                anyPeerClaiming = false,
                healthyOnTarget = false,
            ),
        )
        assertEquals(
            DualPaintPolicy.HealAction.NONE,
            DualPaintPolicy.companionHealAction(
                dualMode = true,
                anyPeerClaiming = true,
                healthyOnTarget = true,
            ),
        )
        assertEquals(
            DualPaintPolicy.HealAction.LAUNCH,
            DualPaintPolicy.companionHealAction(
                dualMode = true,
                anyPeerClaiming = false,
                healthyOnTarget = false,
            ),
        )
        assertEquals(
            DualPaintPolicy.HealAction.RESTART,
            DualPaintPolicy.companionHealAction(
                dualMode = true,
                anyPeerClaiming = true,
                healthyOnTarget = false,
            ),
        )
        assertTrue(DualPaintPolicy.shouldRestartCompanionAfterSwap(dualMode = true))
        assertFalse(DualPaintPolicy.shouldRestartCompanionAfterSwap(dualMode = false))
        assertTrue(DualPaintPolicy.PERF_HUD_REFRESH_MS in 500L..5_000L)
    }

    @Test
    fun `resumeCompanionAction follows SessionPolicy return table`() {
        assertEquals(
            DualPaintPolicy.ResumeCompanionAction.NONE,
            DualPaintPolicy.resumeCompanionAction(
                dualMode = false,
                returningFromElsewhere = true,
                policy = SessionPolicy.YIELD_BOTH,
                greedy = true,
                pinReady = false,
            ),
        )
        assertEquals(
            DualPaintPolicy.ResumeCompanionAction.RESTART,
            DualPaintPolicy.resumeCompanionAction(
                dualMode = true,
                returningFromElsewhere = true,
                policy = SessionPolicy.YIELD_BOTH,
                greedy = false,
                pinReady = true,
            ),
        )
        assertEquals(
            DualPaintPolicy.ResumeCompanionAction.NONE,
            DualPaintPolicy.resumeCompanionAction(
                dualMode = true,
                returningFromElsewhere = false,
                policy = SessionPolicy.YIELD_BOTH,
                greedy = true,
                pinReady = false,
            ),
        )
        assertEquals(
            DualPaintPolicy.ResumeCompanionAction.HEAL_IF_MISSING,
            DualPaintPolicy.resumeCompanionAction(
                dualMode = true,
                returningFromElsewhere = true,
                policy = SessionPolicy.KEEP_COMPANION,
                greedy = false,
                pinReady = false,
            ),
        )
        assertEquals(
            DualPaintPolicy.ResumeCompanionAction.HEAL_IF_MISSING,
            DualPaintPolicy.resumeCompanionAction(
                dualMode = true,
                returningFromElsewhere = true,
                policy = SessionPolicy.KEEP_COMPANION,
                greedy = false,
                pinReady = true,
            ),
        )
        assertEquals(
            DualPaintPolicy.ResumeCompanionAction.RESTART,
            DualPaintPolicy.resumeCompanionAction(
                dualMode = true,
                returningFromElsewhere = true,
                policy = SessionPolicy.KEEP_COMPANION,
                greedy = true,
                pinReady = true,
            ),
        )
        assertEquals(
            DualPaintPolicy.ResumeCompanionAction.NONE,
            DualPaintPolicy.resumeCompanionAction(
                dualMode = true,
                returningFromElsewhere = false,
                policy = SessionPolicy.KEEP_COMPANION,
                greedy = true,
                pinReady = false,
            ),
        )
        assertEquals(
            DualPaintPolicy.ResumeCompanionAction.RESTART,
            DualPaintPolicy.resumeCompanionAction(
                dualMode = true,
                returningFromElsewhere = true,
                policy = null,
                greedy = false,
                pinReady = false,
            ),
        )
        assertEquals(
            DualPaintPolicy.ResumeCompanionAction.HEAL_IF_MISSING,
            DualPaintPolicy.resumeCompanionAction(
                dualMode = true,
                returningFromElsewhere = true,
                policy = null,
                greedy = false,
                pinReady = true,
            ),
        )
        assertEquals(
            DualPaintPolicy.ResumeCompanionAction.HEAL_IF_MISSING,
            DualPaintPolicy.resumeCompanionAction(
                dualMode = true,
                returningFromElsewhere = false,
                policy = null,
                greedy = true,
                pinReady = false,
            ),
        )
    }

    @Test
    fun `keepHealBlocked only when KEEP target equals launch display`() {
        assertTrue(
            DualPaintPolicy.keepHealBlocked(
                policy = SessionPolicy.KEEP_COMPANION,
                targetDisplayId = 10,
                launchDisplayId = 10,
            ),
        )
        assertFalse(
            DualPaintPolicy.keepHealBlocked(
                policy = SessionPolicy.KEEP_COMPANION,
                targetDisplayId = 20,
                launchDisplayId = 10,
            ),
        )
        assertFalse(
            DualPaintPolicy.keepHealBlocked(
                policy = SessionPolicy.YIELD_BOTH,
                targetDisplayId = 10,
                launchDisplayId = 10,
            ),
        )
        assertFalse(
            DualPaintPolicy.keepHealBlocked(
                policy = null,
                targetDisplayId = 10,
                launchDisplayId = 10,
            ),
        )
        assertFalse(
            DualPaintPolicy.keepHealBlocked(
                policy = SessionPolicy.KEEP_COMPANION,
                targetDisplayId = null,
                launchDisplayId = 10,
            ),
        )
        assertFalse(
            DualPaintPolicy.keepHealBlocked(
                policy = SessionPolicy.KEEP_COMPANION,
                targetDisplayId = 10,
                launchDisplayId = null,
            ),
        )
        assertFalse(
            DualPaintPolicy.keepHealBlocked(
                policy = SessionPolicy.KEEP_COMPANION,
                targetDisplayId = null,
                launchDisplayId = null,
            ),
        )
    }

    @Test
    fun `allowCompanionRestartDuringSwap blocked when yielding both`() {
        assertFalse(
            DualPaintPolicy.allowCompanionRestartDuringSwap(
                dualMode = true,
                policy = SessionPolicy.YIELD_BOTH,
            ),
        )
        assertTrue(
            DualPaintPolicy.allowCompanionRestartDuringSwap(
                dualMode = true,
                policy = SessionPolicy.KEEP_COMPANION,
            ),
        )
        assertTrue(
            DualPaintPolicy.allowCompanionRestartDuringSwap(
                dualMode = true,
                policy = null,
            ),
        )
        assertFalse(
            DualPaintPolicy.allowCompanionRestartDuringSwap(
                dualMode = false,
                policy = SessionPolicy.KEEP_COMPANION,
            ),
        )
    }
}
