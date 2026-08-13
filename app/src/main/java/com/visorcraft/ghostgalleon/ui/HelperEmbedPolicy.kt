package com.visorcraft.ghostgalleon.ui

object HelperEmbedPolicy {
    private val REFUSED_PACKAGES = setOf(
        "me.magnum.melondualds",
        "org.azahar_emu.azahar",
    )

    fun resolvePackage(romId: String?, romHelpers: Map<String, String>, global: String?): String? {
        val rom = romId?.let { romHelpers[it] }?.takeIf { it.isNotBlank() }
        if (rom != null) return rom
        return global?.takeIf { it.isNotBlank() }
    }

    /** Dual-surface emulators must never be the helper embed. */
    fun refused(packageName: String?): Boolean {
        val pkg = packageName?.trim().orEmpty()
        return pkg.isNotEmpty() && pkg in REFUSED_PACKAGES
    }

    fun mayEmbed(
        playHostAllowed: Boolean,
        sessionOwnsCompanion: Boolean,
        helperPackage: String?,
        sessionPackage: String?,
        embedAvailable: Boolean,
        cockpit: Boolean,
    ): Boolean {
        if (!playHostAllowed || sessionOwnsCompanion || cockpit) return false
        if (helperPackage.isNullOrBlank()) return false
        if (helperPackage == sessionPackage) return false
        return embedAvailable
    }

    fun mayLaunchOnHostDisplay(): Boolean = false
}
