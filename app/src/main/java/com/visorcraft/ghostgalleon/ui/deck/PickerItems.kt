package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.library.AppEntry
import com.visorcraft.ghostgalleon.library.HiddenRoms
import com.visorcraft.ghostgalleon.library.LibraryBrowse
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.RomEntry
import java.util.Arrays

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

    private class RomSortKey(
        val platformDisplay: String,
        val nameLower: String,
        val rom: RomEntry,
    )

    /**
     * Thread-safe, epoch-keyed single-entry cache for [sortedRoms] so hosts
     * (AppPicker, slot/dock pickers, all-apps drawer) never re-sort 6k–20k
     * ROMs on the UI thread when `contentEpoch`, ROM list, and `hiddenRomIds`
     * are unchanged. Pure; host-tested.
     */
    class SortedRomsCache {
        private class Entry(
            val contentEpoch: Int,
            val romsRef: List<RomEntry>,
            val hiddenRomIds: Set<String>,
            val sorted: List<RomEntry>,
        )

        @Volatile
        private var entry: Entry? = null

        fun get(
            contentEpoch: Int,
            roms: List<RomEntry>,
            hiddenRomIds: Set<String>,
        ): List<RomEntry> {
            val cached = entry
            if (cached != null &&
                cached.contentEpoch == contentEpoch &&
                cached.romsRef === roms &&
                cached.hiddenRomIds == hiddenRomIds
            ) {
                return cached.sorted
            }
            val built = sortedRoms(roms, hiddenRomIds)
            entry = Entry(contentEpoch, roms, hiddenRomIds, built)
            return built
        }

        fun invalidate() {
            entry = null
        }
    }

    /** ROM display order everywhere (picker, carousel): platform display
     *  name, then ROM name, both case-insensitive. Entries flagged
     *  `visibleInUi = false` (deduped Switch updates/DLC) or user-hidden
     *  via [hiddenRomIds] are excluded — they stay in the library but
     *  never appear in UI lists. */
    fun sortedRoms(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
    ): List<RomEntry> {
        if (roms.isEmpty()) return emptyList()
        val platformNames = HashMap<String, String>(32)
        val keyed = ArrayList<RomSortKey>(roms.size)
        for (rom in roms) {
            if (!HiddenRoms.isListed(rom, hiddenRomIds)) continue
            val pid = rom.platformId
            val platDisplay = platformNames.getOrPut(pid) {
                Platforms.byId(pid)?.displayName ?: pid
            }
            keyed.add(RomSortKey(platDisplay, LibraryBrowse.fastLowercase(rom.name), rom))
        }
        val n = keyed.size
        if (n == 0) return emptyList()
        if (n == 1) return listOf(keyed[0].rom)
        val arr = keyed.toTypedArray()
        Arrays.sort(arr) { a, b ->
            val cmp = a.platformDisplay.compareTo(b.platformDisplay)
            if (cmp != 0) cmp else a.nameLower.compareTo(b.nameLower)
        }
        val out = ArrayList<RomEntry>(n)
        for (k in arr) out.add(k.rom)
        return out
    }

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

        // Fast path for empty query: no filtering, exact-sized single allocation.
        if (q.isEmpty()) {
            val sorted = preSortedRoms ?: sortedRoms(roms, hiddenRomIds)
            val total = (if (apps.isNotEmpty()) apps.size + 1 else 0) +
                (if (sorted.isNotEmpty()) sorted.size + 1 else 0)
            if (total == 0) return emptyList()
            val items = ArrayList<PickerItem>(total)
            if (apps.isNotEmpty()) {
                items.add(PickerItem.Header(PickerItem.Header.Section.APPS))
                for (app in apps) items.add(PickerItem.App(app))
            }
            if (sorted.isNotEmpty()) {
                items.add(PickerItem.Header(PickerItem.Header.Section.ROMS))
                for (rom in sorted) items.add(PickerItem.Rom(rom))
            }
            return items
        }

        // Precompute matching platform ids once (O(platforms) instead of O(roms)).
        val matchingPlatforms = matchingPlatformIds(q)

        // Narrowing path: filter previousItems directly in one pass without
        // re-sorting all ROMs or reallocating PickerItem wrappers.
        if (previousItems != null && canNarrow(previousQuery, q)) {
            if (previousItems.isEmpty()) return emptyList()
            val items = ArrayList<PickerItem>(minOf(previousItems.size, 64))
            var addedAppsHeader = false
            var addedRomsHeader = false
            for (item in previousItems) {
                when (item) {
                    is PickerItem.Header -> Unit
                    is PickerItem.App -> {
                        val a = item.entry
                        if (a.label.contains(q, ignoreCase = true) ||
                            a.packageName.contains(q, ignoreCase = true)
                        ) {
                            if (!addedAppsHeader) {
                                items.add(PickerItem.Header(PickerItem.Header.Section.APPS))
                                addedAppsHeader = true
                            }
                            items.add(item)
                        }
                    }
                    is PickerItem.Rom -> {
                        val r = item.entry
                        if (romMatchesPrecomputed(r, q, matchingPlatforms)) {
                            if (!addedRomsHeader) {
                                items.add(PickerItem.Header(PickerItem.Header.Section.ROMS))
                                addedRomsHeader = true
                            }
                            items.add(item)
                        }
                    }
                }
            }
            return items
        }

        // Full non-empty query: stream matches directly into result list.
        val sorted = preSortedRoms ?: sortedRoms(roms, hiddenRomIds)
        val items = ArrayList<PickerItem>(64)
        var addedAppsHeader = false
        for (a in apps) {
            if (a.label.contains(q, ignoreCase = true) ||
                a.packageName.contains(q, ignoreCase = true)
            ) {
                if (!addedAppsHeader) {
                    items.add(PickerItem.Header(PickerItem.Header.Section.APPS))
                    addedAppsHeader = true
                }
                items.add(PickerItem.App(a))
            }
        }
        var addedRomsHeader = false
        for (r in sorted) {
            if (romMatchesPrecomputed(r, q, matchingPlatforms)) {
                if (!addedRomsHeader) {
                    items.add(PickerItem.Header(PickerItem.Header.Section.ROMS))
                    addedRomsHeader = true
                }
                items.add(PickerItem.Rom(r))
            }
        }
        return items
    }

    private fun matchingPlatformIds(query: String): Set<String> {
        var matched: HashSet<String>? = null
        for (platform in Platforms.ALL) {
            if (platform.displayName.contains(query, ignoreCase = true)) {
                if (matched == null) matched = HashSet()
                matched.add(platform.id)
            }
        }
        return matched ?: emptySet()
    }

    private fun romMatchesPrecomputed(
        rom: RomEntry,
        query: String,
        matchingPlatforms: Set<String>,
    ): Boolean {
        if (matchingPlatforms.isNotEmpty() && rom.platformId in matchingPlatforms) return true
        return rom.name.contains(query, ignoreCase = true)
    }

    internal fun romMatches(rom: RomEntry, query: String): Boolean {
        if (rom.name.contains(query, ignoreCase = true)) return true
        val platform = Platforms.byId(rom.platformId)?.displayName ?: return false
        return platform.contains(query, ignoreCase = true)
    }
}
