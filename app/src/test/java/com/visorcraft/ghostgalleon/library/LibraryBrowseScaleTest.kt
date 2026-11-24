package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.ui.deck.PickerItem
import com.visorcraft.ghostgalleon.ui.deck.PickerItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 10k–20k ROM scale parity and JVM throughput benchmarks for [LibraryBrowse]
 * and [PickerItems]. Pure host tests; zero Android framework dependencies.
 */
class LibraryBrowseScaleTest {

    private val platforms = listOf(
        "snes", "nes", "gba", "gb", "gbc", "nds", "3ds", "n64",
        "gc", "wii", "switch", "ps1", "ps2", "psp", "genesis", "dreamcast",
    )
    private val genres = listOf(
        "Action / Adventure",
        "Role-Playing",
        "Platform",
        "Action, Platform",
        "Sports | Racing",
        "Puzzle; Strategy",
        "Fighting / Arcade",
        "Shooter",
        "action / rpg",
        null,
    )
    private val developers = listOf(
        "Nintendo",
        "Capcom",
        "Konami",
        "Square Enix",
        "Sega",
        "Namco",
        "Atlus",
        "nintendo",
        "  Rare  ",
        null,
    )
    private val years = listOf(
        "1985",
        "1991",
        "19940319T000000",
        "© 1998",
        "2004-11-21",
        "2013",
        "2017",
        "2023",
        "invalid",
        null,
    )
    private val prefixes = listOf(
        "Super", "Legend of", "Final", "Mega", "Chrono", "Dragon",
        "Star", "Castlevania:", "Metroid", "Sonic", "Pokemon", "Kirby",
        "007", "z-Type", "Ōkami", "alpha",
    )

    private fun buildSyntheticLibrary(count: Int): List<RomEntry> = List(count) { i ->
        val plat = platforms[i % platforms.size]
        val title = "${prefixes[(i * 7) % prefixes.size]} Quest ${(count - i) % 997} #$i"
        RomEntry(
            id = "$plat:roms/$plat/game_$i.rom",
            name = title,
            platformId = plat,
            uri = "content://roms/$plat/game_$i.rom",
            path = "/storage/emulated/0/ROMs/$plat/game_$i.rom",
            visibleInUi = (i % 50) != 0,
            genre = genres[i % genres.size],
            developer = developers[(i * 3) % developers.size],
            year = years[(i * 5) % years.size],
            description = if (i % 4 == 0) "Classic adventure title number $i" else null,
        )
    }

    // Straightforward unoptimized reference implementations used strictly to
    // verify bit-for-bit order and count parity against the optimized paths.
    private fun referenceBrowseRoms(
        roms: List<RomEntry>,
        query: LibraryBrowse.BrowseQuery,
        lastLaunchedMs: Map<String, Long>,
        favorites: Set<String>,
        collections: Map<String, List<String>>,
        playtimeMs: Map<String, Long>,
        hiddenRomIds: Set<String>,
        nowMs: Long,
        launchablePlatformIds: Set<String>?,
    ): List<RomEntry> {
        val listed = HiddenRoms.listed(roms, hiddenRomIds)
        val base = when (query.mode) {
            LibraryBrowse.Mode.ALL, LibraryBrowse.Mode.GAMES -> listed
            LibraryBrowse.Mode.ALPHA -> listed.withIndex().sortedWith(
                compareBy<IndexedValue<RomEntry>> { it.value.name.lowercase() }.thenBy { it.index },
            ).map { it.value }
            LibraryBrowse.Mode.UNPLAYED -> listed
                .filter { (lastLaunchedMs[SlotKey.rom(it.id)] ?: 0L) <= 0L }
                .withIndex()
                .sortedWith(
                    compareBy<IndexedValue<RomEntry>> { it.value.name.lowercase() }.thenBy { it.index },
                ).map { it.value }
            LibraryBrowse.Mode.RECENT -> {
                val byKey = listed.associateBy { SlotKey.rom(it.id) }
                val keys = lastLaunchedMs.keys.filter { it in byKey }
                keys.withIndex().sortedWith(
                    compareByDescending<IndexedValue<String>> { lastLaunchedMs[it.value] ?: Long.MIN_VALUE }
                        .thenBy { it.index },
                ).mapNotNull { byKey[it.value] }
            }
            LibraryBrowse.Mode.PLAYED_TODAY -> refWindow(listed, lastLaunchedMs, nowMs, LibraryBrowse.DAY_WINDOW_MS)
            LibraryBrowse.Mode.PLAYED_THIS_WEEK -> refWindow(listed, lastLaunchedMs, nowMs, LibraryBrowse.WEEK_WINDOW_MS)
            LibraryBrowse.Mode.PLAYED_THIS_MONTH -> refWindow(listed, lastLaunchedMs, nowMs, LibraryBrowse.MONTH_WINDOW_MS)
            LibraryBrowse.Mode.MOST_PLAYED -> {
                val byKey = listed.associateBy { SlotKey.rom(it.id) }
                val keys = playtimeMs.filter { (k, v) -> v > 0L && k in byKey }.keys.toList()
                keys.withIndex().sortedWith(
                    compareByDescending<IndexedValue<String>> { playtimeMs[it.value] ?: Long.MIN_VALUE }
                        .thenBy { it.index },
                ).mapNotNull { byKey[it.value] }
            }
            LibraryBrowse.Mode.RECENTLY_INSTALLED -> emptyList()
            LibraryBrowse.Mode.FAVORITES -> {
                val favIds = favorites.mapNotNull { if (SlotKey.isRom(it)) SlotKey.romId(it) else null }.toSet()
                listed.filter { it.id in favIds }
            }
            LibraryBrowse.Mode.COLLECTION -> {
                val name = query.collectionName?.trim().orEmpty()
                val byId = listed.associateBy { it.id }
                collections[name].orEmpty().mapNotNull { SlotKey.romId(it)?.let { id -> byId[id] } }
            }
        }
        val yearRegex = Regex("""(\d{4})""")
        fun refYearDecade(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val y = yearRegex.find(raw.trim())?.groupValues?.get(1)?.toIntOrNull() ?: return null
            return if (y in 1970..2099) "${(y / 10) * 10}s" else null
        }
        val needle = query.text.trim().lowercase()
        val genreFilter = query.genre?.trim().orEmpty()
        val devFilter = query.developer?.trim().orEmpty()
        val decadeFilter = query.yearDecade?.trim().orEmpty()
        val filtered = base.filter { rom ->
            (query.platformId == null || rom.platformId == query.platformId) &&
                (genreFilter.isEmpty() || rom.genre.orEmpty().split(',', '/', '|', ';')
                    .map { it.trim() }.filter { it.isNotEmpty() }
                    .any { it.equals(genreFilter, ignoreCase = true) }) &&
                (devFilter.isEmpty() || rom.developer?.trim().orEmpty()
                    .let { it.isNotEmpty() && it.equals(devFilter, ignoreCase = true) }) &&
                (decadeFilter.isEmpty() || refYearDecade(rom.year)
                    ?.equals(decadeFilter, ignoreCase = true) == true) &&
                (launchablePlatformIds == null || rom.platformId in launchablePlatformIds) &&
                (needle.isEmpty() || LibraryBrowse.romMatchesSearch(rom, needle))
        }
        if (!LibraryBrowse.allowsCustomSort(query.mode) || query.sort == LibraryBrowse.Sort.DEFAULT || filtered.size <= 1) {
            return filtered
        }
        return when (query.sort) {
            LibraryBrowse.Sort.DEFAULT -> filtered
            LibraryBrowse.Sort.NAME -> filtered.withIndex().sortedWith(
                compareBy<IndexedValue<RomEntry>> { it.value.name.lowercase() }.thenBy { it.index },
            ).map { it.value }
            LibraryBrowse.Sort.LAST_PLAYED -> filtered.withIndex().sortedWith(
                compareByDescending<IndexedValue<RomEntry>> {
                    lastLaunchedMs[SlotKey.rom(it.value.id)] ?: Long.MIN_VALUE
                }.thenBy { it.index },
            ).map { it.value }
            LibraryBrowse.Sort.MOST_PLAYED -> filtered.withIndex().sortedWith(
                compareByDescending<IndexedValue<RomEntry>> {
                    playtimeMs[SlotKey.rom(it.value.id)] ?: Long.MIN_VALUE
                }.thenBy { it.index },
            ).map { it.value }
            LibraryBrowse.Sort.PLATFORM -> filtered.withIndex().sortedWith(
                compareBy<IndexedValue<RomEntry>> { it.value.platformId.trim().lowercase() }
                    .thenBy { it.value.name.lowercase() }
                    .thenBy { it.index },
            ).map { it.value }
        }
    }

    private fun refWindow(
        listed: List<RomEntry>,
        lastLaunchedMs: Map<String, Long>,
        nowMs: Long,
        windowMs: Long,
    ): List<RomEntry> {
        if (nowMs <= 0L) return emptyList()
        val since = nowMs - windowMs
        val byKey = listed.associateBy { SlotKey.rom(it.id) }
        val inWindow = byKey.keys.filter { k ->
            val t = lastLaunchedMs[k]
            t != null && t > 0L && t >= since
        }
        return inWindow.withIndex().sortedWith(
            compareByDescending<IndexedValue<String>> { lastLaunchedMs[it.value] ?: Long.MIN_VALUE }
                .thenBy { it.index },
        ).mapNotNull { byKey[it.value] }
    }

    @Test
    fun `exact order and count parity across all browse modes sorts filters snapshot and PickerItems`() {
        val roms = buildSyntheticLibrary(2_000)
        val romById = roms.associateBy { it.id }
        val hidden = roms.filterIndexed { i, _ -> i % 43 == 0 }.map { it.id }.toSet()
        val now = 2_000_000_000_000L
        val lastLaunched = linkedMapOf<String, Long>().apply {
            for (k in 0 until 40) {
                val rom = roms[k * 37]
                val age = when (k % 4) {
                    0 -> 1_000L * (k + 1) // today
                    1 -> LibraryBrowse.DAY_WINDOW_MS + 60_000L * (k + 1) // this week
                    2 -> LibraryBrowse.WEEK_WINDOW_MS + 60_000L * (k + 1) // this month
                    else -> LibraryBrowse.MONTH_WINDOW_MS + 60_000L * (k + 1) // older
                }
                put(SlotKey.rom(rom.id), now - age)
            }
            put(SlotKey.rom(roms[1999].id), 0L)
            put("com.example.game", now - 500L)
        }
        val playtime = linkedMapOf<String, Long>().apply {
            for (k in 0 until 35) {
                put(SlotKey.rom(roms[k * 41].id), (k + 1) * 12_345L)
            }
            put(SlotKey.rom(roms[5].id), 0L)
        }
        val favorites = (0 until 50).map { SlotKey.rom(roms[it * 29].id) }.toSet() + "com.example.game"
        val collections = mapOf(
            "Top Picks" to (0 until 30).map { SlotKey.rom(roms[it * 53].id) } +
                listOf("com.example.game", SlotKey.rom("missing:none")),
        )
        val launchable = setOf("snes", "gba", "3ds", "switch", "ps1")

        val queries = buildList {
            for (mode in LibraryBrowse.Mode.entries) {
                add(
                    LibraryBrowse.BrowseQuery(
                        mode = mode,
                        collectionName = if (mode == LibraryBrowse.Mode.COLLECTION) "Top Picks" else null,
                    ),
                )
            }
            for (sort in LibraryBrowse.Sort.entries) {
                add(LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.ALL, sort = sort))
                add(LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.ALPHA, sort = sort))
                add(LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.UNPLAYED, sort = sort))
            }
            add(LibraryBrowse.BrowseQuery(platformId = "snes", genre = "Action", developer = "Nintendo"))
            add(LibraryBrowse.BrowseQuery(yearDecade = "1990s", text = "quest"))
            add(LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.FAVORITES, text = "adventure", sort = LibraryBrowse.Sort.PLATFORM))
        }

        for (q in queries) {
            for (gate in listOf(null, launchable)) {
                val expected = referenceBrowseRoms(
                    roms, q, lastLaunched, favorites, collections, playtime, hidden, now, gate,
                )
                val actualScan = LibraryBrowse.browseRoms(
                    roms, q, lastLaunched, favorites, collections, playtime, hidden, now, gate,
                )
                val actualIndexed = LibraryBrowse.browseRoms(
                    roms, q, lastLaunched, favorites, collections, playtime, hidden, now, gate, romById,
                )
                assertEquals("scan mismatch q=$q gate=$gate", expected, actualScan)
                assertEquals("indexed mismatch q=$q gate=$gate", expected, actualIndexed)
            }
        }

        // playedInWindowCount parity across all 3 windows.
        assertEquals(
            LibraryBrowse.filterPlayedInWindow(lastLaunched.keys.toList(), lastLaunched, now, LibraryBrowse.DAY_WINDOW_MS).size,
            LibraryBrowse.playedInWindowCount(lastLaunched, now, LibraryBrowse.DAY_WINDOW_MS),
        )
        assertEquals(
            LibraryBrowse.filterPlayedInWindow(lastLaunched.keys.toList(), lastLaunched, now, LibraryBrowse.WEEK_WINDOW_MS).size,
            LibraryBrowse.playedInWindowCount(lastLaunched, now, LibraryBrowse.WEEK_WINDOW_MS),
        )
        assertEquals(
            LibraryBrowse.filterPlayedInWindow(lastLaunched.keys.toList(), lastLaunched, now, LibraryBrowse.MONTH_WINDOW_MS).size,
            LibraryBrowse.playedInWindowCount(lastLaunched, now, LibraryBrowse.MONTH_WINDOW_MS),
        )

        // browseChipSnapshot parity both unrestricted and launchableOnly-gated.
        for (gate in listOf(null, launchable)) {
            val snap = LibraryBrowse.browseChipSnapshot(
                roms = roms,
                lastLaunchedMs = lastLaunched,
                playtimeMs = playtime,
                hiddenRomIds = hidden,
                nowMs = now,
                launchablePlatformIds = gate,
            )
            assertEquals(LibraryBrowse.recentCount(lastLaunched), snap.recent)
            assertEquals(LibraryBrowse.playedInWindowCount(lastLaunched, now, LibraryBrowse.DAY_WINDOW_MS), snap.today)
            assertEquals(LibraryBrowse.playedInWindowCount(lastLaunched, now, LibraryBrowse.WEEK_WINDOW_MS), snap.week)
            assertEquals(LibraryBrowse.playedInWindowCount(lastLaunched, now, LibraryBrowse.MONTH_WINDOW_MS), snap.month)
            assertEquals(LibraryBrowse.topPlayedCount(playtime), snap.top)
            assertEquals(LibraryBrowse.listedRomCount(roms, hidden), snap.listedRoms)
            assertEquals(LibraryBrowse.unplayedRomCount(roms, lastLaunched, hidden), snap.unplayed)
            assertEquals(LibraryBrowse.presentGenreCounts(roms, hidden), snap.genres)
            assertEquals(LibraryBrowse.presentDeveloperCounts(roms, hidden), snap.developers)
            assertEquals(LibraryBrowse.presentYearDecadeCounts(roms, hidden), snap.years)
            val expectedPlatforms = if (gate == null) {
                LibraryBrowse.presentPlatformCounts(roms, hidden)
            } else {
                LibraryBrowse.presentPlatformCounts(
                    LibraryBrowse.filterByLaunchablePlatforms(roms, gate),
                    hidden,
                )
            }
            assertEquals(expectedPlatforms, snap.platforms)
        }

        // PickerItems.sortedRoms & build exact parity against reference comparator.
        val refSorted = HiddenRoms.listed(roms, hidden).sortedWith(
            compareBy(
                { Platforms.byId(it.platformId)?.displayName ?: it.platformId },
                { it.name.lowercase() },
            ),
        )
        val fastSorted = PickerItems.sortedRoms(roms, hidden)
        assertEquals(refSorted, fastSorted)

        val apps = listOf(
            AppEntry("com.retroarch", "RetroArch", isGame = false),
            AppEntry("org.ppsspp.ppsspp", "PPSSPP", isGame = true),
        )
        for (q in listOf("", "super", "super nintendo", "quest 10", "retro")) {
            val builtFull = PickerItems.build(apps, roms, q, hiddenRomIds = hidden, preSortedRoms = fastSorted)
            val expectedRoms = if (q.isEmpty()) {
                refSorted
            } else {
                refSorted.filter { PickerItems.romMatches(it, q) }
            }
            assertEquals(
                "PickerItems ROM match parity for q='$q'",
                expectedRoms,
                builtFull.filterIsInstance<PickerItem.Rom>().map { it.entry },
            )
        }
    }

    @Test
    fun `20k ROM scale benchmarks meet interactive latency budgets`() {
        val roms = buildSyntheticLibrary(20_000)
        val romById = roms.associateBy { it.id }
        val hidden = roms.filterIndexed { i, _ -> i % 200 == 0 }.map { it.id }.toSet()
        val now = 2_000_000_000_000L
        val lastLaunched = linkedMapOf<String, Long>().apply {
            for (k in 0 until 40) {
                put(SlotKey.rom(roms[k * 397 + 1].id), now - (k + 1) * 3_600_000L)
            }
        }
        val playtime = linkedMapOf<String, Long>().apply {
            for (k in 0 until 30) {
                put(SlotKey.rom(roms[k * 499 + 1].id), (k + 1) * 60_000L)
            }
        }
        val collections = mapOf(
            "Favorites" to (0 until 25).map { SlotKey.rom(roms[it * 593 + 1].id) },
        )
        val launchable = setOf("snes", "gba", "3ds", "switch", "ps1", "psp")
        val apps = List(80) { i -> AppEntry("com.app.pkg$i", "App Title $i", isGame = i % 3 == 0) }

        val qAlpha = LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.ALPHA)
        val qRecent = LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.RECENT)
        val qWeek = LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.PLAYED_THIS_WEEK)
        val qTop = LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.MOST_PLAYED)
        val qCol = LibraryBrowse.BrowseQuery(mode = LibraryBrowse.Mode.COLLECTION, collectionName = "Favorites")

        // Warm up JIT cleanly before timing.
        repeat(3) {
            LibraryBrowse.browseRoms(roms, qAlpha, lastLaunched, hiddenRomIds = hidden)
            LibraryBrowse.browseRoms(roms, qRecent, lastLaunched, hiddenRomIds = hidden, romById = romById)
            LibraryBrowse.browseChipSnapshot(roms, lastLaunched, playtime, hidden, now, launchable)
            PickerItems.sortedRoms(roms, hidden)
        }

        fun measureMs(runs: Int = 5, block: () -> Any): Double {
            var bestNs = Long.MAX_VALUE
            repeat(runs) {
                val t0 = System.nanoTime()
                val res = block()
                val dt = System.nanoTime() - t0
                assertTrue(res.hashCode() != Int.MIN_VALUE || true)
                if (dt < bestNs) bestNs = dt
            }
            return bestNs / 1_000_000.0
        }

        val alphaMs = measureMs {
            val out = LibraryBrowse.browseRoms(roms, qAlpha, lastLaunched, hiddenRomIds = hidden)
            assertTrue(out.size > 19_000)
            out
        }
        val recentMs = measureMs(runs = 20) {
            val out = LibraryBrowse.browseRoms(roms, qRecent, lastLaunched, hiddenRomIds = hidden, romById = romById)
            assertTrue(out.size in 35..40)
            out
        }
        val weekMs = measureMs(runs = 20) {
            val out = LibraryBrowse.browseRoms(roms, qWeek, lastLaunched, hiddenRomIds = hidden, nowMs = now, romById = romById)
            assertTrue(out.isNotEmpty())
            out
        }
        val topMs = measureMs(runs = 20) {
            val out = LibraryBrowse.browseRoms(roms, qTop, playtimeMs = playtime, hiddenRomIds = hidden, romById = romById)
            assertTrue(out.isNotEmpty())
            out
        }
        val colMs = measureMs(runs = 20) {
            val out = LibraryBrowse.browseRoms(roms, qCol, collections = collections, hiddenRomIds = hidden, romById = romById)
            assertTrue(out.isNotEmpty())
            out
        }
        val snapshotMs = measureMs {
            val snap = LibraryBrowse.browseChipSnapshot(roms, lastLaunched, playtime, hidden, now, launchable)
            assertTrue(snap.listedRoms > 19_000)
            snap
        }
        var sortedForPicker: List<RomEntry> = emptyList()
        val pickerSortMs = measureMs {
            sortedForPicker = PickerItems.sortedRoms(roms, hidden)
            assertTrue(sortedForPicker.size > 19_000)
            sortedForPicker
        }
        val pickerSearchMs = measureMs {
            val first = PickerItems.build(apps, roms, "chrono", hidden, preSortedRoms = sortedForPicker)
            val narrowed = PickerItems.build(
                apps, roms, "chrono quest", hidden,
                preSortedRoms = sortedForPicker,
                previousQuery = "chrono",
                previousItems = first,
            )
            assertTrue(narrowed.isNotEmpty())
            narrowed
        }

        println(
            "LibraryBrowseScaleTest (20,000 ROMs): " +
                "ALPHA=${"%.2f".format(alphaMs)}ms, " +
                "RECENT(romById)=${"%.3f".format(recentMs)}ms, " +
                "WEEK(romById)=${"%.3f".format(weekMs)}ms, " +
                "TOP(romById)=${"%.3f".format(topMs)}ms, " +
                "COLLECTION(romById)=${"%.3f".format(colMs)}ms, " +
                "browseChipSnapshot=${"%.2f".format(snapshotMs)}ms, " +
                "PickerItems.sortedRoms=${"%.2f".format(pickerSortMs)}ms, " +
                "PickerItems.search+narrow=${"%.2f".format(pickerSearchMs)}ms",
        )

        assertTrue("browseRoms(ALPHA) 20k too slow: ${"%.2f".format(alphaMs)}ms", alphaMs < 60.0)
        assertTrue("browseRoms(RECENT) 20k with romById too slow: ${"%.3f".format(recentMs)}ms", recentMs < 5.0)
        assertTrue("browseRoms(WEEK) 20k with romById too slow: ${"%.3f".format(weekMs)}ms", weekMs < 5.0)
        assertTrue("browseRoms(TOP) 20k with romById too slow: ${"%.3f".format(topMs)}ms", topMs < 5.0)
        assertTrue("browseRoms(COLLECTION) 20k with romById too slow: ${"%.3f".format(colMs)}ms", colMs < 5.0)
        assertTrue("browseChipSnapshot 20k too slow: ${"%.2f".format(snapshotMs)}ms", snapshotMs < 35.0)
        assertTrue("PickerItems.sortedRoms 20k too slow: ${"%.2f".format(pickerSortMs)}ms", pickerSortMs < 50.0)
        assertTrue("PickerItems search+narrow 20k too slow: ${"%.2f".format(pickerSearchMs)}ms", pickerSearchMs < 25.0)
    }
}
