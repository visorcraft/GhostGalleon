package com.visorcraft.ghostgalleon.settings

/**
 * Search index over Settings pages. [pageId] matches [SettingsPage] names.
 * Pure; host-tested.
 */
data class SettingsJump(
    val pageId: String,
    val label: String,
    val keywords: String,
)

object SettingsCatalog {

    const val PAGE_DISPLAY = "DISPLAY_GRID"
    const val PAGE_APPS = "APPS"
    const val PAGE_CONTROLS = "CONTROLS"
    const val PAGE_LIBRARY = "LIBRARY"
    const val PAGE_ART = "ART_DATA"
    const val PAGE_STATS = "STATS"
    const val PAGE_SYSTEM = "SYSTEM"
    const val PAGE_ABOUT = "ABOUT"

    fun matches(query: String, items: List<SettingsJump>): List<SettingsJump> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return items.filter { item ->
            item.label.lowercase().contains(needle) ||
                item.keywords.lowercase().contains(needle) ||
                item.pageId.lowercase().contains(needle)
        }
    }
}
