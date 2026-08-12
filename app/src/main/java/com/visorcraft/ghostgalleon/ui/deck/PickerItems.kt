package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.library.AppEntry
import com.visorcraft.ghostgalleon.library.HiddenRoms
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.RomEntry

// Pure row model for the picker list: an "Apps" section followed by a
// "ROMs" section, each headed by a dim header row (omitted when empty).
// The search query filters both sections. Host-tested in PickerItemsTest;
// the Android list adapter in AppPicker only renders these.
sealed interface PickerItem {
    data class Header(val section: Section) : PickerItem {
        enum class Section { APPS, ROMS }
    }
    data class App(val entry: AppEntry) : PickerItem
    data class Rom(val entry: RomEntry) : PickerItem
}

object PickerItems {

    /** ROM display order everywhere (picker, carousel): platform display
     *  name, then ROM name, both case-insensitive. Entries flagged
     *  `visibleInUi = false` (deduped Switch updates/DLC) or user-hidden
     *  via [hiddenRomIds] are excluded — they stay in the library but
     *  never appear in UI lists. */
    fun sortedRoms(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
    ): List<RomEntry> = HiddenRoms.listed(roms, hiddenRomIds)
        .sortedWith(
            compareBy(
                { Platforms.byId(it.platformId)?.displayName ?: it.platformId },
                { it.name.lowercase() },
            )
        )

    /**
     * True when [nextQuery] is a longer prefix of [previousQuery], so a
     * previous match list can be filtered instead of the full library.
     */
    /** Stable row identity for highlight keep-across-filter. */
    fun rowKey(item: PickerItem): String? = when (item) {
        is PickerItem.Header -> null
        is PickerItem.App -> item.entry.packageName
        is PickerItem.Rom -> item.entry.id
    }

    /** Stable DiffUtil identity, including headers. */
    fun itemId(item: PickerItem): String = when (item) {
        is PickerItem.Header -> "h:${item.section.name}"
        is PickerItem.App -> "a:${item.entry.packageName}"
        is PickerItem.Rom -> "r:${item.entry.id}"
    }

    fun canNarrow(previousQuery: String, nextQuery: String): Boolean {
        val prev = previousQuery.trim()
        val next = nextQuery.trim()
        if (prev.isEmpty() || next.isEmpty()) return false
        return next.startsWith(prev, ignoreCase = true)
    }

    /**
     * @param preSortedRoms when non-null, used as the ROM base (already
     *   filtered for hidden + sorted). Avoids re-sorting thousands of ROMs
     *   on every search keystroke in AppPicker.
     * @param previousQuery / [previousItems] let a longer prefix query
     *   filter the last hit list instead of walking the whole library.
     */
    fun build(
        apps: List<AppEntry>,
        roms: List<RomEntry>,
        query: String,
        hiddenRomIds: Set<String> = emptySet(),
        preSortedRoms: List<RomEntry>? = null,
        previousQuery: String = "",
        previousItems: List<PickerItem>? = null,
    ): List<PickerItem> {
        val q = query.trim()
        val reuse = previousItems != null && canNarrow(previousQuery, q)
        val appSrc = if (reuse) {
            previousItems!!.mapNotNull { (it as? PickerItem.App)?.entry }
        } else {
            apps
        }
        val matchedApps = if (q.isEmpty()) {
            apps
        } else {
            appSrc.filter {
                it.label.contains(q, ignoreCase = true) ||
                    it.packageName.contains(q, ignoreCase = true)
            }
        }
        val sorted = preSortedRoms ?: sortedRoms(roms, hiddenRomIds)
        val romSrc = if (reuse) {
            previousItems!!.mapNotNull { (it as? PickerItem.Rom)?.entry }
        } else {
            sorted
        }
        val matchedRoms = if (q.isEmpty()) {
            sorted
        } else {
            romSrc.filter { romMatches(it, q) }
        }
        val items = mutableListOf<PickerItem>()
        if (matchedApps.isNotEmpty()) {
            items += PickerItem.Header(PickerItem.Header.Section.APPS)
            matchedApps.forEach { items += PickerItem.App(it) }
        }
        if (matchedRoms.isNotEmpty()) {
            items += PickerItem.Header(PickerItem.Header.Section.ROMS)
            matchedRoms.forEach { items += PickerItem.Rom(it) }
        }
        return items
    }

    internal fun romMatches(rom: RomEntry, query: String): Boolean {
        if (rom.name.contains(query, ignoreCase = true)) return true
        val platform = Platforms.byId(rom.platformId)?.displayName ?: return false
        return platform.contains(query, ignoreCase = true)
    }
}
