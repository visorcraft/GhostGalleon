package com.visorcraft.ghostgalleon.ui

import com.visorcraft.ghostgalleon.rom.SessionPolicy

/**
 * Pure dual-screen paint / thrash policy (host-tested, no Android types).
 * Gates full rebuilds so GPU buffers present real pixels instead of pure black.
 */
object DualPaintPolicy {

    /** Minimum gap between full setContentView rebuilds (ms). */
    const val MIN_FULL_RENDER_GAP_MS = 32L

    /** Minimum gap between companion heal launch attempts (ms). */
    const val MIN_HEAL_GAP_MS = 2_000L

    /** App-wide drawer open/toggle debounce (ms). */
    const val DRAWER_DEBOUNCE_MS = 450L

    /**
     * Whether a full deck rebuild is allowed now.
     * - [rendering] true → never (re-entrancy).
     * - First paint ([hasPainted] false) → always allow (ignore gap).
     * - Within [MIN_FULL_RENDER_GAP_MS] of last full paint → deny coalesce.
     */
    fun allowFullRender(
        rendering: Boolean,
        hasPainted: Boolean,
        nowUptimeMs: Long,
        lastFullRenderUptimeMs: Long,
        minGapMs: Long = MIN_FULL_RENDER_GAP_MS,
    ): Boolean {
        if (rendering) return false
        if (!hasPainted) return true
        return nowUptimeMs - lastFullRenderUptimeMs >= minGapMs
    }

    /**
     * When [allowFullRender] is false, how long to wait before retrying a
     * dropped full paint (SETTINGS browse chips, etc.). Never discard those
     * permanently — stale UI is worse than a short delay.
     * - Nested / re-entrant ([rendering]): 0 → post after current paint.
     * - Coalesce gap: remaining ms until [minGapMs] elapses.
     * Returns null when paint may run immediately.
     */
    fun deferredFullRenderDelayMs(
        rendering: Boolean,
        hasPainted: Boolean,
        nowUptimeMs: Long,
        lastFullRenderUptimeMs: Long,
        minGapMs: Long = MIN_FULL_RENDER_GAP_MS,
    ): Long? {
        if (allowFullRender(
                rendering, hasPainted, nowUptimeMs, lastFullRenderUptimeMs, minGapMs,
            )
        ) {
            return null
        }
        if (rendering || !hasPainted) return 0L
        val elapsed = nowUptimeMs - lastFullRenderUptimeMs
        return (minGapMs - elapsed).coerceAtLeast(0L)
    }

    /**
     * Whether to paint on attach/resume for multi-display.
     * Re-paint when never painted, content epoch moved, or display id changed.
     */
    fun needsPaintForDisplay(
        hasPainted: Boolean,
        paintedDisplayId: Int?,
        currentDisplayId: Int?,
        appliedEpoch: Int,
        contentEpoch: Int,
    ): Boolean {
        if (!hasPainted) return true
        if (appliedEpoch != contentEpoch) return true
        if (currentDisplayId != null && paintedDisplayId != currentDisplayId) return true
        return false
    }

    /**
     * Companion onCreate absorb: keep existing peer on [targetDisplayId]?
     * [peerDisplayId] null = peer not yet attached (still eligible to keep if
     * we only have one peer — caller passes target match only when known).
     */
    fun shouldAbsorbDuplicate(
        hasPeerOnTarget: Boolean,
    ): Boolean = hasPeerOnTarget

    /**
     * Process-wide companion seat: SECONDARY_HOME storms spawn many instances
     * before [ActivityLifecycleCallbacks.onActivityCreated] registers them.
     * Only the seat holder may paint / redirect; all others absorb silently.
     *
     * @param seatHeldByOther true when another non-finishing instance holds
     *   the seat (or claimed it earlier in this process).
     */
    fun shouldAbsorbSeat(seatHeldByOther: Boolean): Boolean = seatHeldByOther

    /**
     * Absorb path must never open All-apps. Only Main deliberate HOME
     * redelivery may open/toggle the drawer.
     */
    fun absorbMayOpenDrawer(): Boolean = false

    /**
     * Drawer request after debounce window:
     * - [drawerAlreadyOpen] + [allowToggle] → close
     * - [drawerAlreadyOpen] + !allowToggle → no-op (storm safe)
     * - closed → open
     */
    enum class DrawerAction { OPEN, CLOSE, NONE }

    fun drawerAction(
        withinDebounce: Boolean,
        drawerAlreadyOpen: Boolean,
        allowToggle: Boolean,
    ): DrawerAction {
        if (withinDebounce) return DrawerAction.NONE
        if (drawerAlreadyOpen) {
            return if (allowToggle) DrawerAction.CLOSE else DrawerAction.NONE
        }
        return DrawerAction.OPEN
    }

    fun withinDebounce(
        nowUptimeMs: Long,
        lastRequestUptimeMs: Long,
        debounceMs: Long = DRAWER_DEBOUNCE_MS,
    ): Boolean = nowUptimeMs - lastRequestUptimeMs < debounceMs

    fun allowHeal(
        nowUptimeMs: Long,
        lastHealUptimeMs: Long,
        minGapMs: Long = MIN_HEAL_GAP_MS,
    ): Boolean = nowUptimeMs - lastHealUptimeMs >= minGapMs

    /**
     * Heal should launch a new companion only when no non-finishing peer
     * claims the secondary target (attached or not).
     */
    fun shouldLaunchCompanion(
        anyPeerOnTarget: Boolean,
    ): Boolean = !anyPeerOnTarget

    /**
     * KEEP must not spawn Companion on the recorded launch display — that
     * panel is the game. [shouldLaunchCompanion] stays “no peer on target”;
     * this extra guard is target != KEEP launch display.
     */
    fun keepHealBlocked(
        policy: SessionPolicy?,
        targetDisplayId: Int?,
        launchDisplayId: Int?,
    ): Boolean = policy == SessionPolicy.KEEP_COMPANION &&
        targetDisplayId != null &&
        targetDisplayId == launchDisplayId

    /**
     * Pure heal decision for dual-screen companion recovery.
     * - [HealAction.NONE] — healthy peer already on target (or not dual).
     * - [HealAction.LAUNCH] — no peer claims target; start Companion.
     * - [HealAction.RESTART] — peer claims target but is not healthy (e.g.
     *   never reached STARTED / stuck pure-black surface); close + relaunch.
     */
    enum class HealAction { NONE, LAUNCH, RESTART }

    fun companionHealAction(
        dualMode: Boolean,
        anyPeerClaiming: Boolean,
        healthyOnTarget: Boolean,
    ): HealAction {
        if (!dualMode) return HealAction.NONE
        if (anyPeerClaiming && !healthyOnTarget) return HealAction.RESTART
        if (!anyPeerClaiming) return HealAction.LAUNCH
        return HealAction.NONE
    }

    /**
     * After an interactive/companion role swap, always recreate Companion so
     * a pure-black secondary buffer is cleared without system Force Stop.
     * (Topology swap alone does not guarantee a new GPU present.)
     */
    fun shouldRestartCompanionAfterSwap(dualMode: Boolean): Boolean = dualMode

    /**
     * What HOME resume should do to the companion surface.
     * - [ResumeCompanionAction.NONE] — stay put (still on HOME, or not dual).
     * - [ResumeCompanionAction.HEAL_IF_MISSING] — launch only if the peer is gone.
     * - [ResumeCompanionAction.RESTART] — recreate companion (yield return, greedy
     *   KEEP return, or no-session return when the pin is not ready).
     */
    enum class ResumeCompanionAction { NONE, HEAL_IF_MISSING, RESTART }

    fun resumeCompanionAction(
        dualMode: Boolean,
        returningFromElsewhere: Boolean,
        policy: SessionPolicy?,
        greedy: Boolean,
        pinReady: Boolean,
    ): ResumeCompanionAction {
        if (!dualMode) return ResumeCompanionAction.NONE
        if (policy == SessionPolicy.YIELD_BOTH) {
            return if (returningFromElsewhere) ResumeCompanionAction.RESTART
            else ResumeCompanionAction.NONE
        }
        if (policy == SessionPolicy.KEEP_COMPANION) {
            if (!returningFromElsewhere) return ResumeCompanionAction.NONE
            if (greedy) return ResumeCompanionAction.RESTART
            return ResumeCompanionAction.HEAL_IF_MISSING
        }
        // No session: today's return-from-app restart (unless pin ready)
        if (returningFromElsewhere && !pinReady) return ResumeCompanionAction.RESTART
        return ResumeCompanionAction.HEAL_IF_MISSING
    }

    /**
     * Role-swap may restart companion except while a YIELD session owns both
     * panels. [allowHeal] timing is unchanged.
     */
    fun allowCompanionRestartDuringSwap(
        dualMode: Boolean,
        policy: SessionPolicy?,
    ): Boolean = dualMode && policy != SessionPolicy.YIELD_BOTH

    /** Interval for live PERF_HUD reading refresh (ms). No full SETTINGS paint. */
    const val PERF_HUD_REFRESH_MS = 1_500L

    /**
     * Hold the first real deck paint until the on-disk ROM index is in
     * memory. Avoids an empty carousel flash plus a second full dual
     * setContentView when [reloadRomEntries] used to land after onCreate.
     */
    fun holdFirstPaintUntilReady(romIndexReady: Boolean): Boolean = !romIndexReady
}
