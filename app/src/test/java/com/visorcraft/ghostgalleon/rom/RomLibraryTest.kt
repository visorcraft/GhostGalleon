package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RomLibraryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun entry(
        id: String = "snes:snes/smw.smc",
        name: String = "smw",
        platformId: String = "snes",
        uri: String = "content://doc/1",
        path: String? = "/storage/7F7E-2949/roms/snes/smw.smc",
        artUri: String? = null,
    ) = RomEntry(id, name, platformId, uri, path, artUri)

    @Test
    fun `missing file loads as empty`() {
        assertEquals(emptyList<RomEntry>(),
            RomLibrary(tmp.root.resolve("nope/rom_library.json")).load())
    }

    @Test
    fun `corrupt file loads as empty`() {
        val f = tmp.newFile("rom_library.json")
        f.writeText("{ definitely not a json array")
        assertEquals(emptyList<RomEntry>(), RomLibrary(f).load())
    }

    @Test
    fun `save then load round-trips entries`() {
        val f = tmp.root.resolve("lib/rom_library.json")
        val entries = listOf(
            entry(),
            entry(id = "3ds:3ds/bravely.3ds", name = "bravely", platformId = "3ds",
                uri = "content://doc/2", path = null),
            entry(id = "switch:switch/mk8.nsp", name = "mk8", platformId = "switch",
                uri = "content://doc/3", path = "/storage/7F7E-2949/roms/switch/mk8.nsp"),
        )
        RomLibrary(f).save(entries)
        assertEquals(entries, RomLibrary(f).load())
    }

    @Test
    fun `description and screenshotUri round-trip`() {
        val f = tmp.root.resolve("lib-meta/rom_library.json")
        val e = entry().copy(
            description = "A classic platformer.",
            screenshotUri = "content://doc/shot.png",
            artUri = "content://doc/art.png",
        )
        RomLibrary(f).save(listOf(e))
        val loaded = RomLibrary(f).load().single()
        assertEquals("A classic platformer.", loaded.description)
        assertEquals("content://doc/shot.png", loaded.screenshotUri)
        assertEquals("content://doc/art.png", loaded.artUri)
    }

    @Test
    fun `null path round-trips as null`() {
        val f = tmp.root.resolve("lib2/rom_library.json")
        RomLibrary(f).save(listOf(entry(path = null)))
        assertEquals(null, RomLibrary(f).load()[0].path)
    }

    @Test
    fun `artUri round-trips`() {
        val f = tmp.root.resolve("lib-art/rom_library.json")
        RomLibrary(f).save(listOf(
            entry(artUri = "content://doc/art/smw.png"),
            entry(id = "snes:snes/ct.smc", name = "ct", uri = "content://doc/2"),
        ))
        val loaded = RomLibrary(f).load()
        assertEquals("content://doc/art/smw.png", loaded[0].artUri)
        assertEquals(null, loaded[1].artUri)
    }

    @Test
    fun `old library json without artUri still loads`() {
        val f = tmp.newFile("old_rom_library.json")
        f.writeText(
            """[{"id":"snes:snes/smw.smc","name":"smw","platformId":"snes",""" +
                """"uri":"content://doc/1","path":null}]""",
        )
        val loaded = RomLibrary(f).load()
        assertEquals(1, loaded.size)
        assertEquals(null, loaded[0].artUri)
    }

    @Test
    fun `save is atomic and creates parent directories`() {
        val f = tmp.root.resolve("deep/nested/rom_library.json")
        RomLibrary(f).save(listOf(entry()))
        assertTrue(f.exists())
        assertFalse(File(f.path + ".tmp").exists())
    }

    @Test
    fun `saving an empty library round-trips`() {
        val f = tmp.root.resolve("lib3/rom_library.json")
        RomLibrary(f).save(emptyList())
        assertEquals(emptyList<RomEntry>(), RomLibrary(f).load())
    }

    // ---- Rescan guard: unreadable trees must never wipe the library ----

    private class FakeTree(private val files: List<DocFile>) : DocumentTree {
        override fun walk(): List<DocFile> = files
    }

    private fun treeUri(docId: String) =
        "content://com.android.externalstorage.documents/tree/" + docId.replace(":", "%3A")

    // SAF child document URIs embed the tree URI, so stored entries can be
    // attributed to their tree by prefix.
    private fun childUri(treeDocId: String, childDocId: String) =
        treeUri(treeDocId) + "/document/" +
            childDocId.replace(":", "%3A").replace("/", "%2F")

    private val cardTree = treeUri("7F7E-2949:roms")
    private val internalTree = treeUri("primary:Emulation/ROMs")

    @Test
    fun `all trees unreadable aborts without scanning`() {
        val result = RomLibrary.rescanBlocking(
            treeUris = listOf(cardTree),
            prior = listOf(entry(uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"))),
            isReadable = { false },
            treeFor = { error("unreadable trees must never be scanned") },
        )
        assertEquals(RomLibrary.RescanResult.Unreadable, result)
    }

    @Test
    fun `unreadable tree retains its prior entries while readable trees rescan`() {
        val retained = entry(
            id = "gba:gba/fe.gba", name = "fe", platformId = "gba",
            uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/gba/fe.gba"),
        )
        val stale = entry( // internal-tree entry the rescan no longer finds
            id = "snes:snes/old.smc", name = "old",
            uri = childUri("primary:Emulation/ROMs", "primary:Emulation/ROMs/snes/old.smc"),
        )
        val freshTree = FakeTree(listOf(
            DocFile("smw.smc",
                childUri("primary:Emulation/ROMs", "primary:Emulation/ROMs/snes/smw.smc"),
                "snes/smw.smc"),
        ))
        val result = RomLibrary.rescanBlocking(
            treeUris = listOf(cardTree, internalTree),
            prior = listOf(retained, stale),
            isReadable = { it == internalTree },
            treeFor = { uri ->
                assertEquals(internalTree, uri)
                freshTree to "ROMs"
            },
        )
        val success = result as RomLibrary.RescanResult.Success
        // gba (retained from the unreadable card) sorts before snes (fresh).
        assertEquals(listOf("gba:gba/fe.gba", "snes:snes/smw.smc"),
            success.entries.map { it.id })
        // The stale internal entry is gone: its tree was readable and rescan
        // no longer reports it.
        assertFalse(success.entries.any { it.id == "snes:snes/old.smc" })
    }

    @Test
    fun `all trees readable drops prior entries entirely`() {
        val result = RomLibrary.rescanBlocking(
            treeUris = listOf(cardTree),
            prior = listOf(entry(uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"))),
            isReadable = { true },
            treeFor = { FakeTree(emptyList<DocFile>()) to "roms" },
        )
        val success = result as RomLibrary.RescanResult.Success
        assertTrue(success.entries.isEmpty())
        assertEquals(1, success.scannedTrees)
        assertEquals(1, success.totalTrees)
    }

    @Test
    fun `no granted trees succeeds with an empty library`() {
        val result = RomLibrary.rescanBlocking(
            treeUris = emptyList(),
            prior = listOf(entry()),
            isReadable = { error("no trees to check") },
            treeFor = { error("no trees to scan") },
        )
        val success = result as RomLibrary.RescanResult.Success
        assertTrue(success.entries.isEmpty())
        assertEquals(0, success.totalTrees)
    }

    @Test
    fun `retained entries that collide with fresh ids are not duplicated`() {
        // Entry ids are platform:relativePath — tree-independent — so a file
        // visible from two grants collides. The fresh scan wins, once.
        val old = entry(
            uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"),
        )
        val result = RomLibrary.rescanBlocking(
            treeUris = listOf(cardTree, internalTree),
            prior = listOf(old),
            isReadable = { it == internalTree },
            treeFor = {
                FakeTree(listOf(DocFile("smw.smc",
                    childUri("primary:Emulation/ROMs", "primary:Emulation/ROMs/snes/smw.smc"),
                    "snes/smw.smc"))) to "ROMs"
            },
        )
        val success = result as RomLibrary.RescanResult.Success
        assertEquals(listOf("snes:snes/smw.smc"), success.entries.map { it.id })
        assertTrue(success.entries[0].uri.startsWith(internalTree))
    }

    // ---- Incremental fingerprint skip ----

    @Test
    fun `clean tree is skipped and prior entries retained when not forced`() {
        val priorEntry = entry(
            uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"),
        )
        val files = listOf(
            DocFile(
                "smw.smc",
                childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"),
                "snes/smw.smc",
            ),
        )
        val fp = TreeFingerprint.ofCombined(files)
        var treeForCalls = 0
        val (result, newFp) = RomLibrary.rescanBlockingWithFingerprints(
            treeUris = listOf(cardTree),
            prior = listOf(priorEntry),
            isReadable = { true },
            treeFor = {
                treeForCalls++
                FakeTree(files) to "roms"
            },
            priorFingerprints = mapOf(cardTree to fp),
            force = false,
            fingerprintOf = { TreeFingerprint.ofCombined(it) },
        )
        val success = result as RomLibrary.RescanResult.Success
        assertEquals(1, success.skippedCleanTrees)
        assertEquals(listOf("snes:snes/smw.smc"), success.entries.map { it.id })
        assertEquals(fp, newFp[cardTree])
        // treeFor still called once to read the listing for fingerprint.
        assertEquals(1, treeForCalls)
    }

    @Test
    fun `skipWalk avoids treeFor and keeps prior entries`() {
        val priorEntry = entry(
            uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"),
        )
        var treeForCalls = 0
        val (result, _) = RomLibrary.rescanBlockingWithFingerprints(
            treeUris = listOf(cardTree),
            prior = listOf(priorEntry),
            isReadable = { true },
            treeFor = {
                treeForCalls++
                error("walk should be skipped")
            },
            priorFingerprints = mapOf(cardTree to "keep"),
            force = false,
            skipWalk = { it == cardTree },
        )
        val success = result as RomLibrary.RescanResult.Success
        assertEquals(1, success.skippedCleanTrees)
        assertEquals(0, treeForCalls)
        assertEquals(listOf("snes:snes/smw.smc"), success.entries.map { it.id })
    }

    @Test
    fun `dirty tree is rescanned even when prior fingerprint exists`() {
        val priorEntry = entry(
            id = "snes:snes/old.smc",
            name = "old",
            uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/old.smc"),
        )
        val files = listOf(
            DocFile(
                "smw.smc",
                childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"),
                "snes/smw.smc",
            ),
        )
        val result = RomLibrary.rescanBlocking(
            treeUris = listOf(cardTree),
            prior = listOf(priorEntry),
            isReadable = { true },
            treeFor = { FakeTree(files) to "roms" },
            priorFingerprints = mapOf(cardTree to "stale-fingerprint"),
            force = false,
        )
        val success = result as RomLibrary.RescanResult.Success
        assertEquals(0, success.skippedCleanTrees)
        assertEquals(listOf("snes:snes/smw.smc"), success.entries.map { it.id })
        assertFalse(success.entries.any { it.id == "snes:snes/old.smc" })
    }

    @Test
    fun `force rescan rescans clean trees`() {
        val files = listOf(
            DocFile(
                "smw.smc",
                childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"),
                "snes/smw.smc",
            ),
        )
        val fp = TreeFingerprint.ofCombined(files)
        val result = RomLibrary.rescanBlocking(
            treeUris = listOf(cardTree),
            prior = listOf(entry(uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"))),
            isReadable = { true },
            treeFor = { FakeTree(files) to "roms" },
            priorFingerprints = mapOf(cardTree to fp),
            force = true,
            fingerprintOf = { TreeFingerprint.ofCombined(it) },
        )
        val success = result as RomLibrary.RescanResult.Success
        assertEquals(0, success.skippedCleanTrees)
        assertEquals(listOf("snes:snes/smw.smc"), success.entries.map { it.id })
    }

    @Test
    fun `all unreadable still Unreadable with fingerprints preserved`() {
        val priorFp = mapOf(cardTree to "abc")
        val (result, fp) = RomLibrary.rescanBlockingWithFingerprints(
            treeUris = listOf(cardTree),
            prior = listOf(entry(uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"))),
            isReadable = { false },
            treeFor = { error("must not scan") },
            priorFingerprints = priorFp,
            force = false,
        )
        assertEquals(RomLibrary.RescanResult.Unreadable, result)
        assertEquals(priorFp, fp)
    }

    @Test
    fun `quickMeta pure match skips treeFor walk`() {
        val priorEntry = entry(
            uri = childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"),
        )
        val files = listOf(
            DocFile(
                "smw.smc",
                childUri("7F7E-2949:roms", "7F7E-2949:roms/snes/smw.smc"),
                "snes/smw.smc",
            ),
        )
        val meta = TreeFingerprint.ofFilesMeta(files)
        var treeForCalls = 0
        val (result, newFp) = RomLibrary.rescanBlockingWithFingerprints(
            treeUris = listOf(cardTree),
            prior = listOf(priorEntry),
            isReadable = { true },
            treeFor = {
                treeForCalls++
                error("clean meta must not walk")
            },
            priorFingerprints = mapOf(cardTree to meta),
            force = false,
            quickMeta = { meta },
        )
        val success = result as RomLibrary.RescanResult.Success
        assertEquals(1, success.skippedCleanTrees)
        assertEquals(0, treeForCalls)
        assertEquals(meta, newFp[cardTree])
        assertEquals(listOf("snes:snes/smw.smc"), success.entries.map { it.id })
    }

    @Test
    fun `streaming codec omits null optional keys handles escapes and stays legacy-compatible`() {
        val f = tmp.root.resolve("streaming/rom_library.json")
        val tricky = listOf(
            RomEntry(
                id = "snes:snes/Quote \"Slash\" \\ Back\\nLine.smc",
                name = "Quote \"Slash\" \\ Back\nLine\t— Ōkami® 🎮",
                platformId = "snes",
                uri = "content://com.android.externalstorage.documents/tree/7F7E%3Aroms/document/snes%2Fa.smc",
                path = "/storage/7F7E-2949/roms/snes/a.smc",
                artUri = null,
                visibleInUi = true,
                description = "Line 1\r\nLine 2\t\"quoted\" \\u0020 \u0007bell",
                screenshotUri = null,
                logoUri = null,
                videoUri = null,
                year = "1994",
                genre = "Action / RPG",
                developer = "Square",
                rating = "9.8",
            ),
            RomEntry(
                id = "switch:switch/Hades II [0100A00019DE0000][v0].nsp",
                name = "Hades II [0100A00019DE0000][v0]",
                platformId = "switch",
                uri = "content://doc/base",
                path = null,
                visibleInUi = true,
            ),
            RomEntry(
                id = "switch:switch/Hades II [0100A00019DE0800][v196608].nsp",
                name = "Hades II [0100A00019DE0800][v196608]",
                platformId = "switch",
                uri = "content://doc/upd",
                path = null,
                visibleInUi = false,
            ),
        )
        val lib = RomLibrary(f)
        lib.save(tricky)
        val raw = f.readText()
        // Null optional fields and default visibleInUi=true must be omitted.
        assertFalse(raw.contains(":null"))
        assertFalse(raw.contains("\"visibleInUi\":true"))
        assertTrue(raw.contains("\"visibleInUi\":false"))
        // Streaming read round-trips identically (including control chars, unicode, emoji, quotes).
        assertEquals(tricky, lib.load())
        // Also parseable by org.json DOM parseEntries (used by SettingsBundle).
        assertEquals(tricky, RomLibrary.parseEntries(org.json.JSONArray(raw)))
        assertEquals(tricky, RomLibrary.parseEntries(RomLibrary.entriesToJson(tricky)))

        // Legacy DOM-written file with explicit nulls, escaped slashes (\/), \uXXXX escapes,
        // pretty-print whitespace, and unknown extra keys still loads bit-for-bit.
        val legacyFile = tmp.newFile("legacy_rom_library.json")
        legacyFile.writeText(
            """
            [
              {
                "id": "snes:snes\/zelda.smc",
                "name": "Zelda \u2014 Link\u0027s Awakening",
                "platformId": "snes",
                "uri": "content:\/\/doc\/1",
                "path": null,
                "artUri": null,
                "visibleInUi": true,
                "description": null,
                "screenshotUri": null,
                "logoUri": null,
                "videoUri": null,
                "year": "1993",
                "genre": null,
                "developer": "Nintendo",
                "rating": null,
                "unknownNested": {"a": [1, 2, true, false, null], "b": -3.14e2}
              }
            ]
            """.trimIndent(),
        )
        val legacyLoaded = RomLibrary(legacyFile).load()
        assertEquals(1, legacyLoaded.size)
        assertEquals("snes:snes/zelda.smc", legacyLoaded[0].id)
        assertEquals("Zelda — Link's Awakening", legacyLoaded[0].name)
        assertEquals("content://doc/1", legacyLoaded[0].uri)
        assertEquals(null, legacyLoaded[0].path)
        assertEquals("1993", legacyLoaded[0].year)
        assertEquals("Nintendo", legacyLoaded[0].developer)
        assertTrue(legacyLoaded[0].visibleInUi)

        // Truncated / trailing-garbage files fail soft to emptyList.
        val truncated = tmp.newFile("truncated.json")
        truncated.writeText("""[{"id":"a","name":"b","platformId":"snes","uri":"u"""")
        assertEquals(emptyList<RomEntry>(), RomLibrary(truncated).load())

        val trailing = tmp.newFile("trailing.json")
        trailing.writeText("""[] garbage""")
        assertEquals(emptyList<RomEntry>(), RomLibrary(trailing).load())
    }

    @Test
    fun `10000 ROM streaming round-trip shrinks file size by at least 25 percent and loads under 80ms`() {
        val platforms = listOf("snes", "nes", "gba", "nds", "3ds", "ps1", "psp", "n64", "genesis", "gc")
        val entries = ArrayList<RomEntry>(10_000)
        // Include 60 Switch entries (20 games × base + update + DLC) + 9,940 retro ROMs.
        for (g in 0 until 20) {
            val hex = String.format("%012X", 0x010015100000L + g)
            entries.add(
                RomEntry(
                    id = "switch:switch/Game $g [${hex}000][v0].nsp",
                    name = "Game $g [${hex}000][v0]",
                    platformId = "switch",
                    uri = "content://doc/switch/g${g}_base",
                    path = "/storage/7F7E-2949/roms/switch/Game $g [${hex}000][v0].nsp",
                    visibleInUi = true,
                ),
            )
            entries.add(
                RomEntry(
                    id = "switch:switch/Game $g [${hex}800][v65536].nsp",
                    name = "Game $g [${hex}800][v65536]",
                    platformId = "switch",
                    uri = "content://doc/switch/g${g}_upd",
                    path = "/storage/7F7E-2949/roms/switch/Game $g [${hex}800][v65536].nsp",
                    visibleInUi = false,
                ),
            )
            entries.add(
                RomEntry(
                    id = "switch:switch/Game $g [DLC][${hex}001][v0].nsp",
                    name = "Game $g [DLC][${hex}001][v0]",
                    platformId = "switch",
                    uri = "content://doc/switch/g${g}_dlc",
                    path = "/storage/7F7E-2949/roms/switch/Game $g [DLC][${hex}001][v0].nsp",
                    visibleInUi = false,
                ),
            )
        }
        for (i in 0 until 9_940) {
            val plat = platforms[i % platforms.size]
            entries.add(
                RomEntry(
                    id = "$plat:$plat/Title_$i.rom",
                    name = "Title $i",
                    platformId = plat,
                    uri = "content://com.android.externalstorage.documents/tree/7F7E%3Aroms/document/$plat%2FTitle_$i.rom",
                    path = "/storage/7F7E-2949/roms/$plat/Title_$i.rom",
                    artUri = if (i % 3 == 0) "content://doc/art/$i.png" else null,
                    year = if (i % 2 == 0) "${1985 + (i % 35)}" else null,
                    genre = if (i % 2 == 0) "Action / Adventure" else null,
                ),
            )
        }

        // Legacy DOM size with all 10 explicit null keys per entry.
        val legacyFile = tmp.newFile("legacy_10k.json")
        legacyFile.bufferedWriter().use { w ->
            w.write("[")
            for (i in entries.indices) {
                if (i > 0) w.write(",")
                val e = entries[i]
                w.write(
                    org.json.JSONObject()
                        .put("id", e.id)
                        .put("name", e.name)
                        .put("platformId", e.platformId)
                        .put("uri", e.uri)
                        .put("path", e.path ?: org.json.JSONObject.NULL)
                        .put("artUri", e.artUri ?: org.json.JSONObject.NULL)
                        .put("visibleInUi", e.visibleInUi)
                        .put("description", e.description ?: org.json.JSONObject.NULL)
                        .put("screenshotUri", e.screenshotUri ?: org.json.JSONObject.NULL)
                        .put("logoUri", e.logoUri ?: org.json.JSONObject.NULL)
                        .put("videoUri", e.videoUri ?: org.json.JSONObject.NULL)
                        .put("year", e.year ?: org.json.JSONObject.NULL)
                        .put("genre", e.genre ?: org.json.JSONObject.NULL)
                        .put("developer", e.developer ?: org.json.JSONObject.NULL)
                        .put("rating", e.rating ?: org.json.JSONObject.NULL)
                        .toString(),
                )
            }
            w.write("]")
        }

        val streamFile = tmp.root.resolve("stream_10k/rom_library.json")
        val lib = RomLibrary(streamFile)

        // Warm up JIT + verify round-trip exactness.
        lib.save(entries)
        val firstLoaded = lib.load()
        assertEquals(entries, firstLoaded)

        // File size savings check (>= 25% smaller than legacy null-bloated format).
        val legacyBytes = legacyFile.length()
        val streamBytes = streamFile.length()
        val reductionPct = (1.0 - streamBytes.toDouble() / legacyBytes.toDouble()) * 100.0
        assertTrue(
            "expected >= 25% smaller file (legacy=${legacyBytes}B, stream=${streamBytes}B, saved=${"%.1f".format(reductionPct)}%)",
            reductionPct >= 25.0,
        )

        // Legacy file also loads identically via streaming reader!
        assertEquals(entries, RomLibrary(legacyFile).load())

        // Benchmark steady-state save + load of 10,000 ROMs (including Switch dedupe).
        var bestSaveNs = Long.MAX_VALUE
        var bestLoadNs = Long.MAX_VALUE
        repeat(5) {
            val t0 = System.nanoTime()
            lib.save(entries)
            val saveDt = System.nanoTime() - t0
            if (saveDt < bestSaveNs) bestSaveNs = saveDt

            val t1 = System.nanoTime()
            val loaded = lib.load()
            val loadDt = System.nanoTime() - t1
            assertEquals(10_000, loaded.size)
            if (loadDt < bestLoadNs) bestLoadNs = loadDt
        }
        val saveMs = bestSaveNs / 1_000_000.0
        val loadMs = bestLoadNs / 1_000_000.0
        println(
            "RomLibraryTest (10,000 ROMs): legacy=${legacyBytes / 1024}KiB -> stream=${streamBytes / 1024}KiB " +
                "(-${"%.1f".format(reductionPct)}%), save=${"%.2f".format(saveMs)}ms, load+dedupe=${"%.2f".format(loadMs)}ms",
        )
        assertTrue("10k RomLibrary.load() too slow: ${"%.2f".format(loadMs)}ms", loadMs < 80.0)
    }
}
