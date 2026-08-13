package com.visorcraft.ghostgalleon.ui

object HelperEmbedPolicy {
    fun resolvePackage(romId: String?, romHelpers: Map<String, String>, global: String?): String? {
        val rom = romId?.let { romHelpers[it] }?.takeIf { it.isNotBlank() }
        if (rom != null) return rom
        return global?.takeIf { it.isNotBlank() }
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
