package com.visorcraft.ghostgalleon.settings

/**
 * Companion (non-interactive) panel presentation mode. Pure; host-tested.
 *
 * Preferred [PINNED_APP] stays [PINNED_APP]; [pinHonesty] drives empty /
 * missing / dual-claim (NDS/3DS) CTAs in the companion panel.
 */
enum class CompanionRole {
    HERO,
    NOW_PLAYING,
    PERF_HUD,
    PINNED_APP,
    ;

    companion object {
        fun parse(raw: String?): CompanionRole =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
                ?: HERO
    }
}

object CompanionRoleResolve {

    /**
     * Platforms that typically claim both displays; pinned apps must not
     * fight them — fall back to Now Playing when a session is open, else Hero.
     */
    val DUAL_CLAIM_PLATFORMS: Set<String> = setOf("nds", "3ds")

    data class Context(
        val preferred: CompanionRole,
        val openSessionKey: String? = null,
        val pinnedPackage: String? = null,
        /** Platform id of the open session ROM, if known. */
        val openSessionPlatformId: String? = null,
        val pinnedPackageInstalled: Boolean = true,
    )

    /**
     * Why [PINNED_APP] cannot show a live pin surface (honest CTA / dual-claim).
     * Pure; host-tested. Null when pin is ready to show launch chrome.
     */
    enum class PinHonesty {
        /** No package chosen yet — show picker CTA. */
        EMPTY,
        /** Package set but not installed. */
        MISSING,
        /** NDS/3DS (etc.) session owns both displays — do not fight them. */
        DUAL_CLAIM,
        /** Pin package is set and installed. */
        READY,
    }

    fun pinHonesty(ctx: Context): PinHonesty {
        val dualClaim = ctx.openSessionPlatformId?.lowercase() in DUAL_CLAIM_PLATFORMS
        if (dualClaim && ctx.openSessionKey != null) return PinHonesty.DUAL_CLAIM
        if (dualClaim) return PinHonesty.DUAL_CLAIM
        if (ctx.pinnedPackage.isNullOrBlank()) return PinHonesty.EMPTY
        if (!ctx.pinnedPackageInstalled) return PinHonesty.MISSING
        return PinHonesty.READY
    }

    /**
     * Effective role to render.
     * Preferred [PINNED_APP] always stays [PINNED_APP] (including empty,
     * missing, and dual-claim) so the companion panel can show an honest CTA
     * — picker, missing package, or dual-claim pause — never a silent HERO swap.
     * Dual-claim emulators are not launched from the pin surface; the panel
     * explains the pause instead of fighting for the secondary display.
     */
    fun effective(ctx: Context): CompanionRole {
        return when (ctx.preferred) {
            CompanionRole.HERO -> CompanionRole.HERO
            CompanionRole.NOW_PLAYING ->
                if (ctx.openSessionKey != null) CompanionRole.NOW_PLAYING
                else CompanionRole.HERO
            CompanionRole.PERF_HUD -> CompanionRole.PERF_HUD
            CompanionRole.PINNED_APP -> CompanionRole.PINNED_APP
        }
    }
}
