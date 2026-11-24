package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.text
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.SlotKey
import java.util.Arrays

/**
 * Pure library browse ops for Game Mode / pickers: platform filter, text
 * search, recents / today / week / month / most-played / recently-installed /
 * games / A–Z / unplayed ordering, letter jump index. Host-tested; no Android types.
 */
object LibraryBrowse {

    /** Default rolling window for [Mode.PLAYED_TODAY] (24 hours). */
    const val DAY_WINDOW_MS: Long = 24L * 60L * 60L * 1000L

    /** Default rolling window for [Mode.PLAYED_THIS_WEEK] (7 days). */
    const val WEEK_WINDOW_MS: Long = 7L * 24L * 60L * 60L * 1000L

    /** Default rolling window for [Mode.PLAYED_THIS_MONTH] (30 days). */
    const val MONTH_WINDOW_MS: Long = 30L * 24L * 60L * 60L * 1000L

    /**
     * Identity of a Game Mode carousel rebuild. Equal keys mean
     * [com.visorcraft.ghostgalleon.ui.deck.GameDeck] can reuse the last
     * entry list instead of walking thousands of ROMs again.
     */
    fun browseRebuildKey(
        mode: String,
        text: String,
        platformId: String?,
        genre: String?,
        developer: String?,
        yearDecade: String?,
        collectionName: String?,
        sort: String,
        contentEpoch: Int,
        hiddenCount: Int,
        favoriteCount: Int,
        lastLaunchCount: Int,
        playtimeCount: Int,
        romCount: Int,
        appCount: Int,
        nowBucket: Long,
    ): String = listOf(
        mode, text, platformId.orEmpty(), genre.orEmpty(), developer.orEmpty(),
        yearDecade.orEmpty(), collectionName.orEmpty(), sort, contentEpoch,
        hiddenCount, favoriteCount, lastLaunchCount, playtimeCount,
        romCount, appCount, nowBucket,
    ).joinToString("\u001f")

    private class StrKeyed<T>(val key: String, val item: T)
    private class LongKeyed<T>(val key: Long, val item: T)
    private class PairKeyed<T>(val k1: String, val k2: String, val item: T)
    private class CountBucket(val key: String, val display: String, var count: Int = 0)

    private val EMPTY_BUCKET = CountBucket("", "")

    private val DECADE_LABELS: Array<String> = Array(13) { i -> "${1970 + i * 10}s" }

    /**
     * Locale.ROOT-equivalent ASCII fast-path lowercase. Returns [s] unchanged
     * (zero allocations) when already lowercase ASCII, and falls back to
     * [String.lowercase] for non-ASCII strings so Unicode parity is exact.
     */
    internal fun fastLowercase(s: String): String {
        val len = s.length
        var i = 0
        while (i < len) {
            val c = s[i]
            if (c >= '\u0080') return s.lowercase()
            if (c in 'A'..'Z') {
                val chars = s.toCharArray()
                chars[i] = (c.code + 32).toChar()
                var j = i + 1
                while (j < len) {
                    val cj = chars[j]
                    if (cj >= '\u0080') return s.lowercase()
                    if (cj in 'A'..'Z') chars[j] = (cj.code + 32).toChar()
                    j++
                }
                return String(chars)
            }
            i++
        }
        return s
    }

    private fun isGenreDelimiter(c: Char): Boolean =
        c == ',' || c == '/' || c == '|' || c == ';'

    private inline fun forEachGenreRange(raw: String?, onToken: (start: Int, end: Int) -> Unit) {
        if (raw.isNullOrEmpty()) return
        val len = raw.length
        var i = 0
        while (i < len) {
            var segStart = i
            while (i < len && !isGenreDelimiter(raw[i])) i++
            var segEnd = i
            while (segStart < segEnd && raw[segStart].isWhitespace()) segStart++
            while (segEnd > segStart && raw[segEnd - 1].isWhitespace()) segEnd--
            if (segStart < segEnd) {
                onToken(segStart, segEnd)
            }
            i++
        }
    }

    private fun matchesGenreTrimmed(raw: String?, needle: String): Boolean {
        if (raw.isNullOrEmpty()) return false
        val needleLen = needle.length
        forEachGenreRange(raw) { start, end ->
            if (end - start == needleLen &&
                raw.regionMatches(start, needle, 0, needleLen, ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    /** Extract the decade start year (e.g. `"1990s"` -> `1990`) or -1 if non-standard. */
    private fun parseDecadeStart(decade: String): Int {
        if (decade.length != 5) return -1
        val last = decade[4]
        if (last != 's' && last != 'S') return -1
        var y = 0
        for (i in 0..3) {
            val c = decade[i]
            if (c !in '0'..'9') return -1
            y = y * 10 + (c.code - '0'.code)
        }
        return if (y in 1970..2090 && y % 10 == 0) y else -1
    }

    private fun extractPositiveRomIds(stamps: Map<String, Long>): Set<String> {
        if (stamps.isEmpty()) return emptySet()
        var set: HashSet<String>? = null
        for ((k, v) in stamps) {
            if (v > 0L) {
                val id = SlotKey.romId(k) ?: continue
                if (set == null) set = HashSet()
                set.add(id)
            }
        }
        return set ?: emptySet()
    }

    private fun extractRomStampsById(stamps: Map<String, Long>): Map<String, Long> {
        if (stamps.isEmpty()) return emptyMap()
        val map = HashMap<String, Long>(stamps.size)
        for ((k, v) in stamps) {
            val id = SlotKey.romId(k) ?: continue
            map[id] = v
        }
        return map
    }

    private fun <T> sortLongDesc(items: List<T>, scoreOf: (T) -> Long): List<T> {
        val n = items.size
        if (n <= 1) return items
        val arr = Array(n) { i ->
            val item = items[i]
            LongKeyed(scoreOf(item), item)
        }
        Arrays.sort(arr) { a, b -> b.key.compareTo(a.key) }
        val out = ArrayList<T>(n)
        for (entry in arr) out.add(entry.item)
        return out
    }

    enum class Mode {
        ALL,
        RECENT,
        /**
         * Titles launched within the last 24 hours (rolling), newest first.
         * Empty when nothing has been played in the window.
         */
        PLAYED_TODAY,
        /**
         * Titles launched within the last week (rolling), newest first.
         * Empty when nothing has been played in the window.
         */
        PLAYED_THIS_WEEK,
        /**
         * Titles launched within the last 30 days (rolling), newest first.
         * Empty when nothing has been played in the window.
         */
        PLAYED_THIS_MONTH,
        FAVORITES,
        COLLECTION,
        MOST_PLAYED,
        /** Apps ordered by package install time (newest first). ROMs omitted. */
        RECENTLY_INSTALLED,
        /**
         * Games catalog: Android CATEGORY_GAME apps + all listed ROMs.
         * ROM side uses the same listing as [ALL] (platform/search still apply).
         */
        GAMES,
        ALPHA,
        UNPLAYED,
    }

    /**
     * Optional carousel sort for catalog-style rails ([Mode.ALL], Games,
     * Favorites, A–Z, New). Time-ordered rails (Recent / Today / Week /
     * Month / Top / Installed) keep their inherent order. Default is mode
     * order (apps then ROMs for All, name for Games/A–Z/New, …).
     * Long-press the All chip to pick; not always-on chrome.
     */
    enum class Sort {
        DEFAULT,
        NAME,
        LAST_PLAYED,
        MOST_PLAYED,
        PLATFORM,
    }

    data class BrowseQuery(
        val mode: Mode = Mode.ALL,
        val platformId: String? = null,
        val text: String = "",
        /** When [mode] is COLLECTION, members of this named list. */
        val collectionName: String? = null,
        /**
         * Optional genre token (from gamelist meta). ROM-only filter; matches
         * any slash/comma segment of [RomEntry.genre] case-insensitively.
         */
        val genre: String? = null,
        /**
         * Optional developer / publisher filter (gamelist meta). ROM-only;
         * case-insensitive exact match on trimmed [RomEntry.developer].
         */
        val developer: String? = null,
        /**
         * Optional release-year decade filter (e.g. `"1990s"`). ROM-only;
         * derived from [RomEntry.year] via [yearDecadeOf].
         */
        val yearDecade: String? = null,
        /**
         * Optional sort for catalog rails. Ignored when [allowsCustomSort]
         * is false for [mode]. Cleared when the user taps All (full reset).
         */
        val sort: Sort = Sort.DEFAULT,
    )

    /**
     * True when any non-rail filter is set: platform, genre, developer,
     * year decade, or text search. Does not count mode/collection rails.
     * Pure; host-tested. Drives the contextual Clear-filters chip.
     */
    fun hasActiveMetaFilters(q: BrowseQuery): Boolean =
        q.platformId != null ||
            !q.genre.isNullOrBlank() ||
            !q.developer.isNullOrBlank() ||
            !q.yearDecade.isNullOrBlank() ||
            q.text.isNotBlank()

    /** All chip highlight: unrestricted catalog rail. */
    fun isAllChipSelected(q: BrowseQuery): Boolean =
        q.mode == Mode.ALL && q.collectionName == null && !hasActiveMetaFilters(q)

    /**
     * Letter-jump strip identity: empty when the mode has no strip, otherwise
     * present letters + counts. Mode-only chip taps that do not change this
     * (Recent ↔ Today) leave filter chrome structure intact.
     */
    fun letterJumpStructureKey(mode: Mode, labels: List<String>): String {
        if (mode != Mode.ALPHA && mode != Mode.UNPLAYED) return ""
        return presentLetterCounts(labels).joinToString(",") { "${it.first}:${it.second}" }
    }

    /**
     * Filter-chrome structure (views + labels), excluding which chip is
     * highlighted. Equal keys → restyle selection colors instead of
     * removeAllViews. Pure; host-tested.
     */
    fun filterChromeStructureKey(
        platformBadge: String,
        genreBadge: String,
        developerBadge: String,
        yearBadge: String,
        letterJump: String,
        clearFilters: Boolean,
        searchText: String,
        sort: String,
        chromeFlags: String,
        countsSig: String,
        continueName: String,
        selectSig: String,
    ): String = listOf(
        platformBadge,
        genreBadge,
        developerBadge,
        yearBadge,
        letterJump,
        if (clearFilters) "1" else "0",
        searchText,
        sort,
        chromeFlags,
        countsSig,
        continueName,
        selectSig,
    ).joinToString("\u001f")

    /**
     * Count of active meta filters (for chip labels like "Clear filters · 2").
     */
    fun activeMetaFilterCount(q: BrowseQuery): Int {
        var n = 0
        if (q.platformId != null) n++
        if (!q.genre.isNullOrBlank()) n++
        if (!q.developer.isNullOrBlank()) n++
        if (!q.yearDecade.isNullOrBlank()) n++
        if (q.text.isNotBlank()) n++
        return n
    }

    /**
     * Drop platform / genre / developer / year / text filters; keep mode,
     * collectionName, and [BrowseQuery.sort] so the user stays on the
     * current rail with the same presentation order.
     */
    fun clearMetaFilters(q: BrowseQuery): BrowseQuery =
        q.copy(
            platformId = null,
            genre = null,
            developer = null,
            yearDecade = null,
            text = "",
        )

    /**
     * Catalog-style rails accept a custom [Sort]. Time / install rails and
     * ordered collections keep inherent order (sort field ignored).
     */
    fun allowsCustomSort(mode: Mode): Boolean = when (mode) {
        Mode.ALL,
        Mode.GAMES,
        Mode.FAVORITES,
        Mode.UNPLAYED,
        Mode.ALPHA,
        -> true
        Mode.RECENT,
        Mode.PLAYED_TODAY,
        Mode.PLAYED_THIS_WEEK,
        Mode.PLAYED_THIS_MONTH,
        Mode.MOST_PLAYED,
        Mode.RECENTLY_INSTALLED,
        Mode.COLLECTION,
        -> false
    }

    /** Short translated suffix for the All chip, or null. */
    fun sortChipSuffix(sort: Sort): UiText? = when (sort) {
        Sort.DEFAULT -> null
        Sort.NAME -> text(R.string.label_alpha_sort)
        Sort.LAST_PLAYED -> text(R.string.label_last_sort_short)
        Sort.MOST_PLAYED -> text(R.string.label_top)
        Sort.PLATFORM -> text(R.string.label_platform_sort_short)
    }

    fun allChipLabel(sort: Sort): UiText {
        val suffix = sortChipSuffix(sort) ?: return text(R.string.label_all)
        return text(R.string.format_dot_pair, text(R.string.label_all), suffix)
    }

    fun sortDisplayName(sort: Sort): UiText = text(when (sort) {
        Sort.DEFAULT -> R.string.label_default_order
        Sort.NAME -> R.string.label_name_sort
        Sort.LAST_PLAYED -> R.string.label_last_played_sort
        Sort.MOST_PLAYED -> R.string.label_most_played_sort
        Sort.PLATFORM -> R.string.label_platform_sort
    })

    /**
     * Reorder [items] by [sort]. [Sort.DEFAULT] or size ≤ 1 → unchanged.
     * Missing last-played / playtime stamps sort last (stable). Platform
     * sort is platform id then name (apps with null platform group first).
     * Pure; host-tested.
     */
    fun <T> applySort(
        items: List<T>,
        sort: Sort,
        keyOf: (T) -> String,
        labelOf: (T) -> String,
        platformOf: (T) -> String?,
        lastLaunchedMs: Map<String, Long> = emptyMap(),
        playtimeMs: Map<String, Long> = emptyMap(),
    ): List<T> {
        val n = items.size
        if (sort == Sort.DEFAULT || n <= 1) return items
        return when (sort) {
            Sort.DEFAULT -> items
            Sort.NAME -> orderByName(items, labelOf)
            Sort.LAST_PLAYED -> {
                if (lastLaunchedMs.isEmpty()) return items
                sortLongDesc(items) { lastLaunchedMs[keyOf(it)] ?: Long.MIN_VALUE }
            }
            Sort.MOST_PLAYED -> {
                if (playtimeMs.isEmpty()) return items
                sortLongDesc(items) { playtimeMs[keyOf(it)] ?: Long.MIN_VALUE }
            }
            Sort.PLATFORM -> {
                val platformCache = HashMap<String, String>(32)
                val arr = Array(n) { i ->
                    val item = items[i]
                    val rawPlat = platformOf(item)
                    val platKey = if (rawPlat.isNullOrEmpty()) {
                        ""
                    } else {
                        platformCache.getOrPut(rawPlat) { fastLowercase(rawPlat.trim()) }
                    }
                    PairKeyed(platKey, fastLowercase(labelOf(item)), item)
                }
                Arrays.sort(arr) { a, b ->
                    val cmp = a.k1.compareTo(b.k1)
                    if (cmp != 0) cmp else a.k2.compareTo(b.k2)
                }
                val out = ArrayList<T>(n)
                for (entry in arr) out.add(entry.item)
                out
            }
        }
    }

    /**
     * Apply [BrowseQuery.sort] when the mode allows it; otherwise return
     * [items] unchanged. Convenience for Game Mode merge + browseRoms.
     */
    fun <T> applyQuerySort(
        items: List<T>,
        query: BrowseQuery,
        keyOf: (T) -> String,
        labelOf: (T) -> String,
        platformOf: (T) -> String?,
        lastLaunchedMs: Map<String, Long> = emptyMap(),
        playtimeMs: Map<String, Long> = emptyMap(),
    ): List<T> {
        if (!allowsCustomSort(query.mode) || query.sort == Sort.DEFAULT) return items
        return applySort(
            items, query.sort, keyOf, labelOf, platformOf,
            lastLaunchedMs, playtimeMs,
        )
    }

    /**
     * When the user picks a sort from a time-ordered rail, switch to [Mode.ALL]
     * so the sort is visible; catalog rails keep their mode. Pure.
     */
    fun queryWithSort(q: BrowseQuery, sort: Sort): BrowseQuery {
        if (allowsCustomSort(q.mode)) return q.copy(sort = sort)
        return q.copy(mode = Mode.ALL, sort = sort, collectionName = null)
    }

    /**
     * One row for the long-press platform-chip menu: human [label] and the
     * [query] to apply. No always-on chrome — depth of core platform chips.
     */
    enum class PlatformChipActionKind { FILTER, CLEAR, SORT_NAME, SORT_LAST_PLAYED }

    data class PlatformChipAction(
        val kind: PlatformChipActionKind,
        val shortName: String,
        val query: BrowseQuery,
    )

    /**
     * Long-press platform chip actions for [platformId]:
     * - Filter · {short} when not already filtering that platform
     * - Clear platform filter when any platform filter is active
     * - Sort A–Z / Last played scoped to that platform (All rail)
     * Blank [platformId] → empty. Pure; host-tested.
     */
    fun platformChipActions(
        q: BrowseQuery,
        platformId: String,
        shortName: String = platformId,
    ): List<PlatformChipAction> {
        val pid = platformId.trim()
        if (pid.isEmpty()) return emptyList()
        val short = shortName.trim().ifEmpty { pid }
        val baseAll = q.copy(mode = Mode.ALL, collectionName = null)
        return buildList {
            if (q.platformId != pid) {
                add(PlatformChipAction(
                    PlatformChipActionKind.FILTER,
                    short,
                    baseAll.copy(platformId = pid),
                ))
            }
            if (q.platformId != null) {
                add(
                    PlatformChipAction(
                        PlatformChipActionKind.CLEAR,
                        short,
                        q.copy(platformId = null),
                    ),
                )
            }
            add(
                PlatformChipAction(
                    PlatformChipActionKind.SORT_NAME,
                    short,
                    queryWithSort(baseAll.copy(platformId = pid), Sort.NAME),
                ),
            )
            add(
                PlatformChipAction(
                    PlatformChipActionKind.SORT_LAST_PLAYED,
                    short,
                    queryWithSort(baseAll.copy(platformId = pid), Sort.LAST_PLAYED),
                ),
            )
        }
    }

    /** Listed ROMs only (scanner-visible, not user-hidden), optional platform. */
    fun filterByPlatform(
        roms: List<RomEntry>,
        platformId: String?,
        hiddenRomIds: Set<String> = emptySet(),
    ): List<RomEntry> {
        val visible = HiddenRoms.listed(roms, hiddenRomIds)
        if (platformId == null) return visible
        return visible.filter { it.platformId == platformId }
    }

    /**
     * Split a raw genre string into tokens (comma / slash / pipe / semicolon).
     * Empty or blank → empty list.
     */
    fun genreTokens(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        var out: ArrayList<String>? = null
        forEachGenreRange(raw) { start, end ->
            if (out == null) out = ArrayList(4)
            out!!.add(if (start == 0 && end == raw.length) raw else raw.substring(start, end))
        }
        return out ?: emptyList()
    }

    /** True when [rom] matches optional [genre] token (any segment). */
    fun matchesGenre(rom: RomEntry, genre: String?): Boolean {
        val needle = genre?.trim().orEmpty()
        if (needle.isEmpty()) return true
        return matchesGenreTrimmed(rom.genre, needle)
    }

    fun filterByGenre(roms: List<RomEntry>, genre: String?): List<RomEntry> {
        val needle = genre?.trim().orEmpty()
        if (needle.isEmpty()) return roms
        return roms.filter { matchesGenreTrimmed(it.genre, needle) }
    }

    /** True when [rom] matches optional [developer] (case-insensitive exact). */
    fun matchesDeveloper(rom: RomEntry, developer: String?): Boolean {
        val needle = developer?.trim().orEmpty()
        if (needle.isEmpty()) return true
        val raw = rom.developer?.trim().orEmpty()
        return raw.isNotEmpty() && raw.equals(needle, ignoreCase = true)
    }

    fun filterByDeveloper(roms: List<RomEntry>, developer: String?): List<RomEntry> {
        val needle = developer?.trim().orEmpty()
        if (needle.isEmpty()) return roms
        return roms.filter { rom ->
            val raw = rom.developer?.trim()
            !raw.isNullOrEmpty() && raw.equals(needle, ignoreCase = true)
        }
    }

    /**
     * Parse a 4-digit release year from gamelist-style strings
     * (`"1991"`, `"1991-03"`, `"© 2017"`). Out-of-range → null.
     */
    fun parseYear(raw: String?): Int? {
        if (raw.isNullOrEmpty()) return null
        val len = raw.length
        var run = 0
        var value = 0
        for (i in 0 until len) {
            val c = raw[i]
            if (c in '0'..'9') {
                value = value * 10 + (c.code - '0'.code)
                run++
                if (run == 4) {
                    return if (value in 1970..2099) value else null
                }
            } else {
                run = 0
                value = 0
            }
        }
        return null
    }

    /** Decade label for a calendar year: `1991` → `"1990s"`. */
    fun yearDecadeLabel(year: Int): String {
        val idx = (year - 1970) / 10
        if (year in 1970..2099 && idx in DECADE_LABELS.indices) return DECADE_LABELS[idx]
        val start = (year / 10) * 10
        return "${start}s"
    }

    /** Decade label for a raw year string, or null when unparseable. */
    fun yearDecadeOf(raw: String?): String? =
        parseYear(raw)?.let { yearDecadeLabel(it) }

    /** Localized display text for an internal decade token such as `1990s`. */
    fun decadeText(decade: String): UiText {
        val start = decade.trim().removeSuffix("s").toIntOrNull()
        return if (start != null) {
            text(R.string.format_decade_value, start)
        } else {
            dynamicText(decade)
        }
    }

    /** True when [rom] falls in optional [decade] (e.g. `"1990s"`). */
    fun matchesYearDecade(rom: RomEntry, decade: String?): Boolean {
        val d = decade?.trim().orEmpty()
        if (d.isEmpty()) return true
        val targetStart = parseDecadeStart(d)
        if (targetStart >= 0) {
            val y = parseYear(rom.year) ?: return false
            return (y / 10) * 10 == targetStart
        }
        return yearDecadeOf(rom.year)?.equals(d, ignoreCase = true) == true
    }

    fun filterByYearDecade(roms: List<RomEntry>, decade: String?): List<RomEntry> {
        val d = decade?.trim().orEmpty()
        if (d.isEmpty()) return roms
        val targetStart = parseDecadeStart(d)
        if (targetStart >= 0) {
            return roms.filter { rom ->
                val y = parseYear(rom.year)
                y != null && (y / 10) * 10 == targetStart
            }
        }
        return roms.filter { yearDecadeOf(it.year)?.equals(d, ignoreCase = true) == true }
    }

    /**
     * Keep ROMs whose [RomEntry.platformId] is in [launchablePlatformIds].
     * [launchablePlatformIds] null → no filter (pass-through).
     * Empty set → empty result (no emulator packages installed).
     * Does not re-apply hide filtering — callers pass already-listed ROMs
     * or use with [HiddenRoms.listed] first. Pure; host-tested.
     */
    fun filterByLaunchablePlatforms(
        roms: List<RomEntry>,
        launchablePlatformIds: Set<String>?,
    ): List<RomEntry> {
        if (launchablePlatformIds == null) return roms
        return roms.filter { it.platformId in launchablePlatformIds }
    }

    /**
     * Platform ids that have at least one installed player package.
     * [playersByPlatform] maps platformId → package names that can run it.
     * [installedPackages] is the set of installed package names on device.
     * Pure; host-tested.
     */
    fun launchablePlatformIds(
        playersByPlatform: Map<String, List<String>>,
        installedPackages: Set<String>,
    ): Set<String> =
        playersByPlatform.mapNotNull { (platformId, packages) ->
            if (packages.any { it in installedPackages }) platformId else null
        }.toSet()

    /**
     * Distinct release decades present in the listed library with ROM counts,
     * sorted by decade ascending (oldest first). [limit] caps chip bar length
     * (default 8). Used for labeled chips like `"1990s · 12"`.
     */
    fun presentYearDecadeCounts(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
        limit: Int = 8,
    ): List<Pair<String, Int>> {
        val cap = limit.coerceAtLeast(0)
        if (cap == 0 || roms.isEmpty()) return emptyList()
        val decadeCounts = IntArray(DECADE_LABELS.size)
        for (rom in roms) {
            if (!HiddenRoms.isListed(rom, hiddenRomIds)) continue
            val y = parseYear(rom.year) ?: continue
            decadeCounts[(y - 1970) / 10]++
        }
        val out = ArrayList<Pair<String, Int>>(minOf(cap, DECADE_LABELS.size))
        for (i in decadeCounts.indices) {
            val n = decadeCounts[i]
            if (n > 0) {
                out.add(DECADE_LABELS[i] to n)
                if (out.size >= cap) break
            }
        }
        return out
    }

    /**
     * Distinct developers present in the listed library with ROM counts,
     * sorted by count descending then name. [limit] caps chip bar length
     * (default 10). Used for labeled chips like "Nintendo · 42".
     */
    fun presentDeveloperCounts(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
        limit: Int = 10,
    ): List<Pair<String, Int>> {
        val cap = limit.coerceAtLeast(0)
        if (cap == 0 || roms.isEmpty()) return emptyList()
        val buckets = HashMap<String, CountBucket>()
        val rawCache = HashMap<String, CountBucket>()
        for (rom in roms) {
            if (!HiddenRoms.isListed(rom, hiddenRomIds)) continue
            val raw = rom.developer ?: continue
            val bucket = rawCache.getOrPut(raw) {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) {
                    EMPTY_BUCKET
                } else {
                    val key = fastLowercase(trimmed)
                    buckets.getOrPut(key) { CountBucket(key, trimmed) }
                }
            }
            if (bucket !== EMPTY_BUCKET) bucket.count++
        }
        return buckets.values
            .sortedWith(compareByDescending<CountBucket> { it.count }.thenBy { it.key })
            .take(cap)
            .map { it.display to it.count }
    }

    /**
     * Distinct genre tokens present in the listed library with per-token
     * ROM counts, sorted by count descending then name. [limit] caps chip
     * bar length (default 12). Used for labeled chips like "Action · 4".
     */
    fun presentGenreCounts(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
        limit: Int = 12,
    ): List<Pair<String, Int>> {
        val cap = limit.coerceAtLeast(0)
        if (cap == 0 || roms.isEmpty()) return emptyList()
        val buckets = HashMap<String, CountBucket>()
        val rawCache = HashMap<String, Array<CountBucket>>()
        val emptyBuckets = emptyArray<CountBucket>()
        for (rom in roms) {
            if (!HiddenRoms.isListed(rom, hiddenRomIds)) continue
            val raw = rom.genre ?: continue
            val resolved = rawCache.getOrPut(raw) {
                var list: ArrayList<CountBucket>? = null
                forEachGenreRange(raw) { start, end ->
                    val token = if (start == 0 && end == raw.length) raw else raw.substring(start, end)
                    val key = fastLowercase(token)
                    val b = buckets.getOrPut(key) { CountBucket(key, token) }
                    if (list == null) list = ArrayList(3)
                    list!!.add(b)
                }
                list?.toTypedArray() ?: emptyBuckets
            }
            for (b in resolved) b.count++
        }
        return buckets.values
            .sortedWith(compareByDescending<CountBucket> { it.count }.thenBy { it.key })
            .take(cap)
            .map { it.display to it.count }
    }

    /**
     * Distinct genre tokens present in the listed library, sorted by count
     * descending then name. [limit] caps chip bar length (default 12).
     */
    fun presentGenres(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
        limit: Int = 12,
    ): List<String> = presentGenreCounts(roms, hiddenRomIds, limit).map { it.first }

    /**
     * True when [rom] matches a search [needle] (already lowercased, non-empty).
     * Hits name, id, platform, genre, developer, year, and description.
     */
    fun romMatchesSearch(rom: RomEntry, needle: String): Boolean {
        if (needle.isEmpty()) return true
        fun hit(raw: String?): Boolean =
            !raw.isNullOrBlank() && raw.contains(needle, ignoreCase = true)
        return hit(rom.name) ||
            hit(rom.id) ||
            hit(rom.platformId) ||
            hit(rom.genre) ||
            hit(rom.developer) ||
            hit(rom.year) ||
            hit(rom.description)
    }

    /**
     * Case-insensitive substring search across ROM name, id, platform, genre,
     * developer, year, and description (gamelist meta when present).
     */
    fun searchRoms(
        roms: List<RomEntry>,
        query: String,
        hiddenRomIds: Set<String> = emptySet(),
    ): List<RomEntry> {
        val listed = HiddenRoms.listed(roms, hiddenRomIds)
        val q = query.trim()
        if (q.isEmpty()) return listed
        val needle = q.lowercase()
        return listed.filter { romMatchesSearch(it, needle) }
    }

    /**
     * Order keys by last-launch time descending. Keys with no timestamp fall
     * to the end, preserving relative input order among unknowns.
     */
    fun orderByRecent(keys: List<String>, lastLaunchedMs: Map<String, Long>): List<String> {
        if (keys.size <= 1 || lastLaunchedMs.isEmpty()) return keys
        return sortLongDesc(keys) { lastLaunchedMs[it] ?: Long.MIN_VALUE }
    }

    /**
     * Order keys by accumulated playtime descending. Keys with zero/missing
     * playtime fall to the end, preserving relative input order among ties.
     */
    fun orderByPlaytime(keys: List<String>, playtimeMs: Map<String, Long>): List<String> {
        if (keys.size <= 1 || playtimeMs.isEmpty()) return keys
        return sortLongDesc(keys) { playtimeMs[it] ?: Long.MIN_VALUE }
    }

    /**
     * Order keys by install time descending (newest installs first). Missing
     * or non-positive stamps fall to the end, stable on input order.
     */
    fun orderByInstallTime(keys: List<String>, firstInstallMs: Map<String, Long>): List<String> {
        if (keys.size <= 1 || firstInstallMs.isEmpty()) return keys
        return sortLongDesc(keys) { firstInstallMs[it] ?: Long.MIN_VALUE }
    }

    /**
     * Case-insensitive A–Z by [labelOf], stable on input order for ties.
     * Precomputes lowercase sort keys once (Schwartzian transform) and relies
     * on JVM TimSort stability to avoid O(N log N) string allocations.
     */
    fun <T> orderByName(items: List<T>, labelOf: (T) -> String): List<T> {
        val n = items.size
        if (n <= 1) return items
        val arr = Array(n) { i ->
            val item = items[i]
            StrKeyed(fastLowercase(labelOf(item)), item)
        }
        Arrays.sort(arr) { a, b -> a.key.compareTo(b.key) }
        val out = ArrayList<T>(n)
        for (entry in arr) out.add(entry.item)
        return out
    }

    /** True when [key] has never been launched (missing or non-positive stamp). */
    fun isUnplayed(key: String, lastLaunchedMs: Map<String, Long>): Boolean =
        (lastLaunchedMs[key] ?: 0L) <= 0L

    /**
     * True when [key] was last launched at or after [sinceMs] (inclusive).
     * Missing / non-positive stamps → false.
     */
    fun isPlayedSince(
        key: String,
        lastLaunchedMs: Map<String, Long>,
        sinceMs: Long,
    ): Boolean {
        val t = lastLaunchedMs[key] ?: return false
        return t > 0L && t >= sinceMs
    }

    /** Start of a rolling window of [windowMs] ending at [nowMs]. */
    fun windowStartMs(nowMs: Long, windowMs: Long = WEEK_WINDOW_MS): Long =
        nowMs - windowMs.coerceAtLeast(0L)

    /**
     * Keys launched within [nowMs - windowMs, nowMs], newest first.
     * Keys outside the window or with missing stamps are dropped.
     * Non-positive [nowMs] → empty (caller must supply a real clock).
     */
    fun filterPlayedInWindow(
        keys: List<String>,
        lastLaunchedMs: Map<String, Long>,
        nowMs: Long,
        windowMs: Long = WEEK_WINDOW_MS,
    ): List<String> {
        if (nowMs <= 0L) return emptyList()
        val since = windowStartMs(nowMs, windowMs)
        val inWindow = keys.filter { isPlayedSince(it, lastLaunchedMs, since) }
        return orderByRecent(inWindow, lastLaunchedMs)
    }

    /**
     * Filter Android apps marked as games ([AppEntry.isGame]). Pure helper for
     * the Games rail (host can pass any list of packages with a flag).
     */
    fun <T> filterGameApps(items: List<T>, isGame: (T) -> Boolean): List<T> =
        items.filter(isGame)

    /**
     * Prefer [filteredKeys] when non-empty for Random (stay in current rail /
     * filter); otherwise fall back to [fullKeys]. Pure; host-tested.
     */
    fun randomPool(filteredKeys: List<String>, fullKeys: List<String>): List<String> =
        if (filteredKeys.isNotEmpty()) filteredKeys else fullKeys

    /**
     * Full browse pipeline: mode → platform → genre → developer → year decade
     * → text. Recents / week / month / most-played / favorites / A–Z /
     * unplayed are applied by restricting first, then filter/search.
     *
     * [nowMs] is used by [Mode.PLAYED_TODAY], [Mode.PLAYED_THIS_WEEK], and
     * [Mode.PLAYED_THIS_MONTH] (default 0 → empty window).
     *
     * [romById] is an optional O(1) index matching [roms] by [RomEntry.id]
     * (e.g. `GhostGalleonApp.romById`) so RECENT / window / MOST_PLAYED /
     * COLLECTION resolve in O(K) without scanning or copying the full library.
     */
    fun browseRoms(
        roms: List<RomEntry>,
        query: BrowseQuery,
        lastLaunchedMs: Map<String, Long> = emptyMap(),
        favorites: Set<String> = emptySet(),
        collections: Map<String, List<String>> = emptyMap(),
        playtimeMs: Map<String, Long> = emptyMap(),
        hiddenRomIds: Set<String> = emptySet(),
        nowMs: Long = 0L,
        /**
         * When non-null, only ROMs on these platforms are kept (launchable
         * filter). Null → no launchability gate.
         */
        launchablePlatformIds: Set<String>? = null,
        romById: Map<String, RomEntry>? = null,
    ): List<RomEntry> {
        if (roms.isEmpty() || query.mode == Mode.RECENTLY_INSTALLED) return emptyList()
        if (launchablePlatformIds != null && launchablePlatformIds.isEmpty()) return emptyList()

        val platFilter = query.platformId
        val genreNeedle = query.genre?.trim().orEmpty()
        val devNeedle = query.developer?.trim().orEmpty()
        val decadeNeedle = query.yearDecade?.trim().orEmpty()
        val decadeStart = if (decadeNeedle.isNotEmpty()) parseDecadeStart(decadeNeedle) else -1
        val searchNeedle = fastLowercase(query.text.trim())

        fun matchesFilters(rom: RomEntry): Boolean {
            if (platFilter != null && rom.platformId != platFilter) return false
            if (launchablePlatformIds != null && rom.platformId !in launchablePlatformIds) return false
            if (genreNeedle.isNotEmpty() && !matchesGenreTrimmed(rom.genre, genreNeedle)) return false
            if (devNeedle.isNotEmpty()) {
                val rawDev = rom.developer?.trim()
                if (rawDev.isNullOrEmpty() || !rawDev.equals(devNeedle, ignoreCase = true)) return false
            }
            if (decadeNeedle.isNotEmpty()) {
                if (decadeStart >= 0) {
                    val y = parseYear(rom.year)
                    if (y == null || (y / 10) * 10 != decadeStart) return false
                } else if (yearDecadeOf(rom.year)?.equals(decadeNeedle, ignoreCase = true) != true) {
                    return false
                }
            }
            if (searchNeedle.isNotEmpty() && !romMatchesSearch(rom, searchNeedle)) return false
            return true
        }

        fun resolveWindow(windowMs: Long): List<RomEntry> {
            if (nowMs <= 0L || lastLaunchedMs.isEmpty()) return emptyList()
            val since = windowStartMs(nowMs, windowMs)
            if (romById != null) {
                val hits = ArrayList<LongKeyed<RomEntry>>()
                for ((k, ts) in lastLaunchedMs) {
                    if (ts <= 0L || ts < since) continue
                    val id = SlotKey.romId(k) ?: continue
                    val rom = romById[id] ?: continue
                    if (HiddenRoms.isListed(rom, hiddenRomIds) && matchesFilters(rom)) {
                        hits.add(LongKeyed(ts, rom))
                    }
                }
                if (hits.size <= 1) return hits.map { it.item }
                val arr = hits.toTypedArray()
                Arrays.sort(arr) { a, b -> b.key.compareTo(a.key) }
                val out = ArrayList<RomEntry>(arr.size)
                for (e in arr) out.add(e.item)
                return out
            }
            val needed = HashMap<String, Long>()
            for ((k, ts) in lastLaunchedMs) {
                if (ts <= 0L || ts < since) continue
                val id = SlotKey.romId(k) ?: continue
                needed[id] = ts
            }
            if (needed.isEmpty()) return emptyList()
            val hits = ArrayList<LongKeyed<RomEntry>>(needed.size)
            for (rom in roms) {
                val ts = needed[rom.id] ?: continue
                if (HiddenRoms.isListed(rom, hiddenRomIds) && matchesFilters(rom)) {
                    hits.add(LongKeyed(ts, rom))
                }
            }
            if (hits.size <= 1) return hits.map { it.item }
            val arr = hits.toTypedArray()
            Arrays.sort(arr) { a, b -> b.key.compareTo(a.key) }
            val out = ArrayList<RomEntry>(arr.size)
            for (e in arr) out.add(e.item)
            return out
        }

        val hasMetaFilters = platFilter != null ||
            launchablePlatformIds != null ||
            genreNeedle.isNotEmpty() ||
            devNeedle.isNotEmpty() ||
            decadeNeedle.isNotEmpty() ||
            searchNeedle.isNotEmpty()
        val bulkInitCap = if (hasMetaFilters) minOf(roms.size, 64) else roms.size

        val filtered: List<RomEntry> = when (query.mode) {
            Mode.ALL, Mode.GAMES -> {
                val out = ArrayList<RomEntry>(bulkInitCap)
                for (rom in roms) {
                    if (HiddenRoms.isListed(rom, hiddenRomIds) && matchesFilters(rom)) {
                        out.add(rom)
                    }
                }
                out
            }
            Mode.ALPHA -> {
                val out = ArrayList<RomEntry>(bulkInitCap)
                for (rom in roms) {
                    if (HiddenRoms.isListed(rom, hiddenRomIds) && matchesFilters(rom)) {
                        out.add(rom)
                    }
                }
                val sorted = orderByName(out) { it.name }
                if (query.sort == Sort.NAME || query.sort == Sort.DEFAULT) return sorted
                sorted
            }
            Mode.UNPLAYED -> {
                val playedRomIds = extractPositiveRomIds(lastLaunchedMs)
                val out = ArrayList<RomEntry>(bulkInitCap)
                for (rom in roms) {
                    if (HiddenRoms.isListed(rom, hiddenRomIds) &&
                        (playedRomIds.isEmpty() || rom.id !in playedRomIds) &&
                        matchesFilters(rom)
                    ) {
                        out.add(rom)
                    }
                }
                val sorted = orderByName(out) { it.name }
                if (query.sort == Sort.NAME || query.sort == Sort.DEFAULT) return sorted
                sorted
            }
            Mode.RECENT -> {
                if (lastLaunchedMs.isEmpty()) return emptyList()
                val hits: ArrayList<LongKeyed<RomEntry>>
                if (romById != null) {
                    hits = ArrayList(lastLaunchedMs.size)
                    for ((k, ts) in lastLaunchedMs) {
                        val id = SlotKey.romId(k) ?: continue
                        val rom = romById[id] ?: continue
                        if (HiddenRoms.isListed(rom, hiddenRomIds) && matchesFilters(rom)) {
                            hits.add(LongKeyed(ts, rom))
                        }
                    }
                } else {
                    val neededIds = HashSet<String>(lastLaunchedMs.size)
                    for (k in lastLaunchedMs.keys) {
                        SlotKey.romId(k)?.let { neededIds.add(it) }
                    }
                    if (neededIds.isEmpty()) return emptyList()
                    val found = HashMap<String, RomEntry>(neededIds.size)
                    for (rom in roms) {
                        if (rom.id in neededIds &&
                            HiddenRoms.isListed(rom, hiddenRomIds) &&
                            matchesFilters(rom)
                        ) {
                            found[rom.id] = rom
                        }
                    }
                    if (found.isEmpty()) return emptyList()
                    hits = ArrayList(found.size)
                    for ((k, ts) in lastLaunchedMs) {
                        val id = SlotKey.romId(k) ?: continue
                        val rom = found[id] ?: continue
                        hits.add(LongKeyed(ts, rom))
                    }
                }
                if (hits.size <= 1) return hits.map { it.item }
                val arr = hits.toTypedArray()
                Arrays.sort(arr) { a, b -> b.key.compareTo(a.key) }
                val out = ArrayList<RomEntry>(arr.size)
                for (e in arr) out.add(e.item)
                return out
            }
            Mode.PLAYED_TODAY -> return resolveWindow(DAY_WINDOW_MS)
            Mode.PLAYED_THIS_WEEK -> return resolveWindow(WEEK_WINDOW_MS)
            Mode.PLAYED_THIS_MONTH -> return resolveWindow(MONTH_WINDOW_MS)
            Mode.MOST_PLAYED -> {
                if (playtimeMs.isEmpty()) return emptyList()
                val hits: ArrayList<LongKeyed<RomEntry>>
                if (romById != null) {
                    hits = ArrayList(playtimeMs.size)
                    for ((k, v) in playtimeMs) {
                        if (v <= 0L) continue
                        val id = SlotKey.romId(k) ?: continue
                        val rom = romById[id] ?: continue
                        if (HiddenRoms.isListed(rom, hiddenRomIds) && matchesFilters(rom)) {
                            hits.add(LongKeyed(v, rom))
                        }
                    }
                } else {
                    val neededIds = HashSet<String>(playtimeMs.size)
                    for ((k, v) in playtimeMs) {
                        if (v > 0L) SlotKey.romId(k)?.let { neededIds.add(it) }
                    }
                    if (neededIds.isEmpty()) return emptyList()
                    val found = HashMap<String, RomEntry>(neededIds.size)
                    for (rom in roms) {
                        if (rom.id in neededIds &&
                            HiddenRoms.isListed(rom, hiddenRomIds) &&
                            matchesFilters(rom)
                        ) {
                            found[rom.id] = rom
                        }
                    }
                    if (found.isEmpty()) return emptyList()
                    hits = ArrayList(found.size)
                    for ((k, v) in playtimeMs) {
                        if (v <= 0L) continue
                        val id = SlotKey.romId(k) ?: continue
                        val rom = found[id] ?: continue
                        hits.add(LongKeyed(v, rom))
                    }
                }
                if (hits.size <= 1) return hits.map { it.item }
                val arr = hits.toTypedArray()
                Arrays.sort(arr) { a, b -> b.key.compareTo(a.key) }
                val out = ArrayList<RomEntry>(arr.size)
                for (e in arr) out.add(e.item)
                return out
            }
            Mode.RECENTLY_INSTALLED -> return emptyList()
            Mode.FAVORITES -> {
                if (favorites.isEmpty()) return emptyList()
                val favIds = HashSet<String>(favorites.size)
                for (key in favorites) {
                    SlotKey.romId(key)?.let { favIds.add(it) }
                }
                if (favIds.isEmpty()) return emptyList()
                val out = ArrayList<RomEntry>(minOf(favIds.size, roms.size))
                for (rom in roms) {
                    if (rom.id in favIds &&
                        HiddenRoms.isListed(rom, hiddenRomIds) &&
                        matchesFilters(rom)
                    ) {
                        out.add(rom)
                    }
                }
                out
            }
            Mode.COLLECTION -> {
                val name = query.collectionName?.trim().orEmpty()
                val members = collections[name]
                if (members.isNullOrEmpty()) return emptyList()
                if (romById != null) {
                    val out = ArrayList<RomEntry>(members.size)
                    for (key in members) {
                        val id = SlotKey.romId(key) ?: continue
                        val rom = romById[id] ?: continue
                        if (HiddenRoms.isListed(rom, hiddenRomIds) && matchesFilters(rom)) {
                            out.add(rom)
                        }
                    }
                    return out
                }
                val neededIds = HashSet<String>(members.size)
                for (key in members) {
                    SlotKey.romId(key)?.let { neededIds.add(it) }
                }
                if (neededIds.isEmpty()) return emptyList()
                val found = HashMap<String, RomEntry>(neededIds.size)
                for (rom in roms) {
                    if (rom.id in neededIds &&
                        HiddenRoms.isListed(rom, hiddenRomIds) &&
                        matchesFilters(rom)
                    ) {
                        found[rom.id] = rom
                    }
                }
                if (found.isEmpty()) return emptyList()
                val out = ArrayList<RomEntry>(members.size)
                for (key in members) {
                    val id = SlotKey.romId(key) ?: continue
                    found[id]?.let { out.add(it) }
                }
                return out
            }
        }

        if (!allowsCustomSort(query.mode) || query.sort == Sort.DEFAULT || filtered.size <= 1) {
            return filtered
        }
        return when (query.sort) {
            Sort.LAST_PLAYED -> {
                val romLast = extractRomStampsById(lastLaunchedMs)
                if (romLast.isEmpty()) filtered else sortLongDesc(filtered) { romLast[it.id] ?: Long.MIN_VALUE }
            }
            Sort.MOST_PLAYED -> {
                val romPlay = extractRomStampsById(playtimeMs)
                if (romPlay.isEmpty()) filtered else sortLongDesc(filtered) { romPlay[it.id] ?: Long.MIN_VALUE }
            }
            else -> applySort(
                filtered,
                query.sort,
                keyOf = { it.id },
                labelOf = { it.name },
                platformOf = { it.platformId },
                lastLaunchedMs = lastLaunchedMs,
                playtimeMs = playtimeMs,
            )
        }
    }

    /** Distinct platform ids present in the listed library, sorted. */
    fun presentPlatforms(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
    ): List<String> {
        if (roms.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        for (rom in roms) {
            if (HiddenRoms.isListed(rom, hiddenRomIds)) seen.add(rom.platformId)
        }
        return seen.sorted()
    }

    /**
     * Platforms with listed ROM counts (sorted by platform id). Empty library
     * → empty list. Used for chip labels like "SNES · 12".
     */
    fun presentPlatformCounts(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
    ): List<Pair<String, Int>> {
        if (roms.isEmpty()) return emptyList()
        val counts = HashMap<String, CountBucket>()
        for (rom in roms) {
            if (!HiddenRoms.isListed(rom, hiddenRomIds)) continue
            val pid = rom.platformId
            val b = counts.getOrPut(pid) { CountBucket(pid, pid) }
            b.count++
        }
        return counts.values
            .sortedBy { it.key }
            .map { it.key to it.count }
    }

    /**
     * Chip label with optional positive count suffix: "Fav · 3", "SNES · 12".
     * Non-positive [count] → [base] unchanged.
     */
    fun labeledChip(base: UiText, count: Int): UiText = when {
        count <= 0 -> base
        base is UiText.Dynamic && base.value.isBlank() -> text(R.string.format_count_only, count)
        else -> text(R.string.format_dot_pair, base, count)
    }

    /** Keys with a positive last-launch stamp (Recent rail size proxy). */
    fun recentCount(lastLaunchedMs: Map<String, Long>): Int {
        var n = 0
        for (v in lastLaunchedMs.values) if (v > 0L) n++
        return n
    }

    /**
     * Keys launched inside a rolling window ending at [nowMs]
     * (Week/Month chip size proxy). Non-positive [nowMs] → 0.
     */
    fun playedInWindowCount(
        lastLaunchedMs: Map<String, Long>,
        nowMs: Long,
        windowMs: Long = WEEK_WINDOW_MS,
    ): Int {
        if (nowMs <= 0L || lastLaunchedMs.isEmpty()) return 0
        val since = windowStartMs(nowMs, windowMs)
        var count = 0
        for (t in lastLaunchedMs.values) {
            if (t > 0L && t >= since) count++
        }
        return count
    }

    /** Keys with positive playtime (Top rail size proxy). */
    fun topPlayedCount(playtimeMs: Map<String, Long>): Int {
        var n = 0
        for (v in playtimeMs.values) if (v > 0L) n++
        return n
    }

    /**
     * Listed ROMs with no positive last-launch stamp (New rail size proxy).
     * Hidden ROMs are excluded.
     */
    fun unplayedRomCount(
        roms: List<RomEntry>,
        lastLaunchedMs: Map<String, Long>,
        hiddenRomIds: Set<String> = emptySet(),
    ): Int {
        if (roms.isEmpty()) return 0
        val playedRomIds = extractPositiveRomIds(lastLaunchedMs)
        var count = 0
        for (rom in roms) {
            if (HiddenRoms.isListed(rom, hiddenRomIds) &&
                (playedRomIds.isEmpty() || rom.id !in playedRomIds)
            ) {
                count++
            }
        }
        return count
    }

    /** Listed (non-hidden) ROM count — A–Z / Games catalog size proxy half. */
    fun listedRomCount(
        roms: List<RomEntry>,
        hiddenRomIds: Set<String> = emptySet(),
    ): Int {
        var count = 0
        for (rom in roms) {
            if (HiddenRoms.isListed(rom, hiddenRomIds)) count++
        }
        return count
    }

    /**
     * Collision-free value key for host caching of [BrowseChipSnapshot].
     * Includes `contentEpoch`, `launchableOnly`, `launchablePlatformIds`, and
     * exact settings stamps so hits are O(1) via reference fast-path and
     * invalidation is airtight on any toggle or mutation. Pure; host-tested.
     */
    data class BrowseChipCacheKey(
        val contentEpoch: Int,
        val romCount: Int,
        val launchableOnly: Boolean,
        val launchablePlatformIds: Set<String>?,
        val lastLaunchedMs: Map<String, Long>,
        val playtimeMs: Map<String, Long>,
        val hiddenRomIds: Set<String>,
        val favorites: Set<String>,
        val collections: Map<String, List<String>>,
        val romNames: Map<String, String>,
        val customNames: Map<String, String>,
        val nowMinuteBucket: Long,
    )

    /** One-pass chip counts so browse chrome rebuilds do not rewalk the library. */
    data class BrowseChipSnapshot(
        val recent: Int,
        val today: Int,
        val week: Int,
        val month: Int,
        val top: Int,
        val listedRoms: Int,
        val unplayed: Int,
        val platforms: List<Pair<String, Int>>,
        val genres: List<Pair<String, Int>>,
        val developers: List<Pair<String, Int>>,
        val years: List<Pair<String, Int>>,
    )

    fun browseChipSnapshot(
        roms: List<RomEntry>,
        lastLaunchedMs: Map<String, Long>,
        playtimeMs: Map<String, Long>,
        hiddenRomIds: Set<String>,
        nowMs: Long,
        launchablePlatformIds: Set<String>?,
        genreLimit: Int = 12,
        developerLimit: Int = 10,
        yearLimit: Int = 8,
    ): BrowseChipSnapshot {
        val playedRomIds = extractPositiveRomIds(lastLaunchedMs)
        val platformCounts = HashMap<String, CountBucket>(32)
        val genreBuckets = HashMap<String, CountBucket>(32)
        val rawGenreCache = HashMap<String, Array<CountBucket>>(64)
        val emptyGenreBuckets = emptyArray<CountBucket>()
        val devBuckets = HashMap<String, CountBucket>(64)
        val rawDevCache = HashMap<String, CountBucket>(64)
        val decadeCounts = IntArray(DECADE_LABELS.size)
        var listedCount = 0
        var unplayed = 0

        // Single zero-copy pass over all ROMs (no HiddenRoms.listed / launchable list copies,
        // no per-ROM SlotKey.rom allocations, no Regex, and cached raw genre/developer parsing).
        for (rom in roms) {
            if (!HiddenRoms.isListed(rom, hiddenRomIds)) continue
            listedCount++
            if (playedRomIds.isEmpty() || rom.id !in playedRomIds) unplayed++

            val pid = rom.platformId
            if (launchablePlatformIds == null || pid in launchablePlatformIds) {
                val pb = platformCounts.getOrPut(pid) { CountBucket(pid, pid) }
                pb.count++
            }

            val rawGenre = rom.genre
            if (!rawGenre.isNullOrEmpty()) {
                val resolved = rawGenreCache.getOrPut(rawGenre) {
                    var list: ArrayList<CountBucket>? = null
                    forEachGenreRange(rawGenre) { start, end ->
                        val token = if (start == 0 && end == rawGenre.length) {
                            rawGenre
                        } else {
                            rawGenre.substring(start, end)
                        }
                        val key = fastLowercase(token)
                        val b = genreBuckets.getOrPut(key) { CountBucket(key, token) }
                        if (list == null) list = ArrayList(3)
                        list!!.add(b)
                    }
                    list?.toTypedArray() ?: emptyGenreBuckets
                }
                for (b in resolved) b.count++
            }

            val rawDev = rom.developer
            if (!rawDev.isNullOrEmpty()) {
                val bucket = rawDevCache.getOrPut(rawDev) {
                    val trimmed = rawDev.trim()
                    if (trimmed.isEmpty()) {
                        EMPTY_BUCKET
                    } else {
                        val key = fastLowercase(trimmed)
                        devBuckets.getOrPut(key) { CountBucket(key, trimmed) }
                    }
                }
                if (bucket !== EMPTY_BUCKET) bucket.count++
            }

            val y = parseYear(rom.year)
            if (y != null) {
                decadeCounts[(y - 1970) / 10]++
            }
        }

        // Single pass over lastLaunchedMs for recent / today / week / month counts (zero sort).
        var recent = 0
        var today = 0
        var week = 0
        var month = 0
        if (lastLaunchedMs.isNotEmpty()) {
            val validNow = nowMs > 0L
            val daySince = if (validNow) windowStartMs(nowMs, DAY_WINDOW_MS) else Long.MAX_VALUE
            val weekSince = if (validNow) windowStartMs(nowMs, WEEK_WINDOW_MS) else Long.MAX_VALUE
            val monthSince = if (validNow) windowStartMs(nowMs, MONTH_WINDOW_MS) else Long.MAX_VALUE
            for (t in lastLaunchedMs.values) {
                if (t > 0L) {
                    recent++
                    if (validNow) {
                        if (t >= monthSince) {
                            month++
                            if (t >= weekSince) {
                                week++
                                if (t >= daySince) today++
                            }
                        }
                    }
                }
            }
        }

        val yCap = yearLimit.coerceAtLeast(0)
        val years = ArrayList<Pair<String, Int>>(minOf(yCap, DECADE_LABELS.size))
        if (yCap > 0) {
            for (i in decadeCounts.indices) {
                val n = decadeCounts[i]
                if (n > 0) {
                    years.add(DECADE_LABELS[i] to n)
                    if (years.size >= yCap) break
                }
            }
        }

        return BrowseChipSnapshot(
            recent = recent,
            today = today,
            week = week,
            month = month,
            top = topPlayedCount(playtimeMs),
            listedRoms = listedCount,
            unplayed = unplayed,
            platforms = platformCounts.values.sortedBy { it.key }.map { it.key to it.count },
            genres = genreBuckets.values
                .sortedWith(compareByDescending<CountBucket> { it.count }.thenBy { it.key })
                .take(genreLimit.coerceAtLeast(0))
                .map { it.display to it.count },
            developers = devBuckets.values
                .sortedWith(compareByDescending<CountBucket> { it.count }.thenBy { it.key })
                .take(developerLimit.coerceAtLeast(0))
                .map { it.display to it.count },
            years = years,
        )
    }

    /**
     * Games rail size proxy: game apps + listed ROMs.
     * Non-positive parts clamp to 0.
     */
    fun gamesCatalogCount(gameAppCount: Int, listedRomCount: Int): Int =
        gameAppCount.coerceAtLeast(0) + listedRomCount.coerceAtLeast(0)

    /**
     * A–Z rail size proxy: curated apps + listed ROMs.
     * Non-positive parts clamp to 0.
     */
    fun alphaCatalogCount(appCount: Int, listedRomCount: Int): Int =
        appCount.coerceAtLeast(0) + listedRomCount.coerceAtLeast(0)

    /**
     * Continue chip label: `"Continue · Eden"` when a target name is known,
     * truncated to [maxNameLen] (default 14). Blank target → plain [base].
     * Pure; host-tested. No new always-on chrome — depth of core Continue.
     */
    fun continueChipLabel(
        targetLabel: String?,
        maxNameLen: Int = 14,
    ): UiText {
        val name = targetLabel?.trim().orEmpty()
        if (name.isEmpty()) return text(R.string.label_continue)
        val cap = maxNameLen.coerceAtLeast(1)
        val short = if (name.length <= cap) name else name.take(cap - 1) + "…"
        return text(R.string.format_dot_pair, text(R.string.label_continue), short)
    }

    /** Named collection titles suitable for Game Mode rails (stable sort). */
    fun presentCollectionRails(collections: Map<String, List<String>>): List<String> =
        collections.keys.filter { it.isNotBlank() }.sortedBy { it.lowercase() }

    /**
     * Pick one item at random from [items]. [nextIndex] is called with the
     * list size and must return an index in `0 until size` (injectable RNG
     * for host tests). Empty list → null.
     */
    fun <T> pickRandom(items: List<T>, nextIndex: (size: Int) -> Int): T? {
        if (items.isEmpty()) return null
        val i = nextIndex(items.size)
        if (i !in items.indices) return null
        return items[i]
    }

    /**
     * Continue target: the most recently launched key that still exists in
     * [availableKeys], or null when none.
     *
     * For **cold-start hero seed**, use [coldStartKey] instead — last-launched
     * is an explicit Continue/Recent action, not the home landing selection.
     */
    fun continueKey(
        availableKeys: List<String>,
        lastLaunchedMs: Map<String, Long>,
    ): String? = continueCandidates(
        availableKeys = availableKeys,
        lastLaunchedMs = lastLaunchedMs,
        excludeKey = null,
    ).firstOrNull()

    /**
     * Resume-chip candidates newest-first: keys present in [availableKeys]
     * with a last-launched stamp, excluding [excludeKey] (usually the current
     * hero selection so Resume never points at what's already shown).
     */
    fun continueCandidates(
        availableKeys: List<String>,
        lastLaunchedMs: Map<String, Long>,
        excludeKey: String? = null,
    ): List<String> {
        if (availableKeys.isEmpty() || lastLaunchedMs.isEmpty()) return emptyList()
        val present = availableKeys.toSet()
        return orderByRecent(
            lastLaunchedMs.keys.filter { it in present && it != excludeKey },
            lastLaunchedMs,
        )
    }

    /**
     * Long-press Continue / Recent history: newest-first candidates capped at
     * [limit] (default 20). Empty when nothing is continue-able.
     */
    fun continueHistory(
        availableKeys: List<String>,
        lastLaunchedMs: Map<String, Long>,
        limit: Int = 20,
    ): List<String> {
        if (limit <= 0) return emptyList()
        return continueCandidates(
            availableKeys = availableKeys,
            lastLaunchedMs = lastLaunchedMs,
            excludeKey = null,
        ).take(limit)
    }

    /**
     * Long-press Recent chip history — same pool as [continueHistory]
     * (newest first). Alias for call-site clarity. Pure; host-tested.
     */
    fun recentHistory(
        availableKeys: List<String>,
        lastLaunchedMs: Map<String, Long>,
        limit: Int = 20,
    ): List<String> = continueHistory(availableKeys, lastLaunchedMs, limit)

    /**
     * Dialog/chip line for a history row: `"Zelda · 2h ago"` when last-played
     * is known, otherwise just [label]. Pure; host-tested.
     */
    fun continueHistoryLine(
        label: String,
        lastMs: Long?,
        nowMs: Long,
    ): UiText {
        val name = label.trim().takeIf { it.isNotEmpty() }
            ?.let(::dynamicText) ?: text(R.string.glyph_dash)
        val ago = SessionMath.formatLastPlayed(lastMs, nowMs) ?: return name
        return text(R.string.format_dot_pair, name, ago)
    }

    fun recentHistoryLine(
        label: String,
        lastMs: Long?,
        nowMs: Long,
    ): UiText = continueHistoryLine(label, lastMs, nowMs)

    /**
     * Swipe the Resume chip. [delta] +1 = older (typically fling left),
     * −1 = newer (fling right). Past either end → null (clear that resume).
     */
    fun continueAfterSwipe(
        candidates: List<String>,
        current: String,
        delta: Int,
    ): String? {
        if (candidates.isEmpty() || delta == 0) return current.takeIf { it in candidates }
        val i = candidates.indexOf(current).let { if (it < 0) 0 else it }
        val next = i + delta
        if (next < 0 || next >= candidates.size) return null
        return candidates[next]
    }

    /**
     * Apply a Resume swipe to [lastLaunchedMs].
     * - [next] null → drop [current] (chip may go away or show the next newest)
     * - [next] other → promote that key to newest so [continueKey] returns it
     */
    fun applyContinueSwipe(
        lastLaunchedMs: Map<String, Long>,
        current: String,
        next: String?,
        nowMs: Long,
    ): Map<String, Long> {
        if (next == null) return lastLaunchedMs - current
        if (next == current) return lastLaunchedMs
        val peak = (lastLaunchedMs.values.maxOrNull() ?: 0L) + 1L
        return lastLaunchedMs + (next to maxOf(peak, nowMs))
    }

    /**
     * Cold-start / first-paint selection for the hero panel.
     *
     * Prefer the first filled grid slot (user-curated home order — e.g. Eden
     * in slot 0). Only fall back to [continueKey] when the grid has no filled
     * slots. Never pick a random last-launched utility app over the grid.
     */
    fun coldStartKey(
        gridSlots: List<String?>,
        dockSlots: List<String?> = emptyList(),
        lastLaunchedMs: Map<String, Long> = emptyMap(),
    ): String? {
        gridSlots.firstOrNull { !it.isNullOrBlank() }?.let { return it }
        dockSlots.firstOrNull { !it.isNullOrBlank() }?.let { return it }
        val available = buildList {
            addAll(gridSlots.filterNotNull())
            addAll(dockSlots.filterNotNull())
            addAll(lastLaunchedMs.keys)
        }
        return continueKey(available, lastLaunchedMs)
    }

    /**
     * Highest playtime key (positive only), or null when none. Used by Quick
     * Panel → Top to jump selection after switching to the Most Played rail.
     */
    fun topPlayedKey(playtimeMs: Map<String, Long>): String? {
        val keys = playtimeMs.filter { it.value > 0L }.keys.toList()
        if (keys.isEmpty()) return null
        return orderByPlaytime(keys, playtimeMs).firstOrNull()
    }

    /** Browse query for a Game Mode rail (Quick Panel shortcuts). */
    fun railQuery(mode: Mode): BrowseQuery =
        BrowseQuery(mode = mode)

    /**
     * A–Z letter bucket for a display label: first significant character
     * uppercased into A–Z, or '#' for empty / non-letter (digits, symbols).
     * Used by the Game Mode letter jump strip under A–Z / New rails.
     */
    fun letterBucket(label: String): Char {
        val len = label.length
        var i = 0
        while (i < len && label[i].isWhitespace()) i++
        if (i >= len) return '#'
        val c = label[i].uppercaseChar()
        return if (c in 'A'..'Z') c else '#'
    }

    /**
     * Letter buckets with counts for the A–Z jump strip (A–Z then optional
     * '#'). Empty input → empty list. Used for labels like `"M · 12"`.
     */
    fun presentLetterCounts(labels: List<String>): List<Pair<Char, Int>> {
        if (labels.isEmpty()) return emptyList()
        val counts = IntArray(27)
        for (label in labels) {
            val b = letterBucket(label)
            if (b == '#') counts[26]++ else counts[b - 'A']++
        }
        val out = ArrayList<Pair<Char, Int>>(27)
        for (i in 0 until 26) {
            val n = counts[i]
            if (n > 0) out.add(('A' + i) to n)
        }
        if (counts[26] > 0) out.add('#' to counts[26])
        return out
    }

    /**
     * Distinct letter buckets present in [labels], A–Z then optional '#'.
     * Empty input → empty list (no strip).
     */
    fun presentLetterIndex(labels: List<String>): List<Char> =
        presentLetterCounts(labels).map { it.first }

    /**
     * First index in [labels] whose [letterBucket] equals [letter]
     * (case-insensitive A–Z; anything else maps to '#'). Missing → -1.
     */
    fun firstIndexForLetter(labels: List<String>, letter: Char): Int {
        val bucket = letter.uppercaseChar().let { c ->
            if (c in 'A'..'Z') c else '#'
        }
        return labels.indexOfFirst { letterBucket(it) == bucket }
    }
}
