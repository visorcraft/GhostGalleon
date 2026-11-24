package com.visorcraft.ghostgalleon.art

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArtCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `key is stable, id-dependent, and filesystem-safe`() {
        val id = "snes:snes/Super Mario Kart (USA).smc"
        assertEquals(ArtCache.keyFor(id), ArtCache.keyFor(id))
        assertNotEquals(ArtCache.keyFor(id), ArtCache.keyFor("snes:snes/other.smc"))
        // SHA-256 hex: safe as a filename for any id (ids contain ':'/'/').
        assertTrue(ArtCache.keyFor(id).matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `disk round-trip in a temp dir`() {
        val cache = ArtCache(tmp.root.resolve("art"))
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        cache.writeDiskBytes("snes:snes/smw.smc", bytes)
        assertTrue(cache.diskHas("snes:snes/smw.smc"))
        assertArrayEquals(bytes, cache.readDiskBytes("snes:snes/smw.smc"))
        // No tmp file left behind.
        assertTrue(tmp.root.resolve("art").listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `miss returns null`() {
        val cache = ArtCache(tmp.root.resolve("art"))
        assertFalse(cache.diskHas("snes:snes/nope.smc"))
        assertNull(cache.readDiskBytes("snes:snes/nope.smc"))
    }

    @Test
    fun `invalidate removes disk bytes and source stamp`() {
        val artDir = tmp.root.resolve("art-inv")
        val cache = ArtCache(artDir)
        val id = "snes:snes/smw.smc"
        cache.writeDiskBytes(id, byteArrayOf(9, 9, 9))
        // Simulate a URI-stamped entry left by a prior load.
        File(artDir, ArtCache.keyFor(id) + ".src").writeText("content://old-art")
        assertTrue(cache.diskHas(id))
        assertTrue(File(artDir, ArtCache.keyFor(id) + ".src").isFile)
        cache.invalidate(id)
        assertFalse(cache.diskHas(id))
        assertNull(cache.readDiskBytes(id))
        assertFalse(File(artDir, ArtCache.keyFor(id) + ".src").exists())
        assertFalse(File(artDir, ArtCache.keyFor(id) + ".hero.png").exists())
    }

    @Test
    fun `artGeneration bumps on write and invalidate so hero skip can refresh`() {
        val cache = ArtCache(tmp.root.resolve("art-gen"))
        val id = "snes:snes/smw.smc"
        assertEquals(0, cache.artGeneration(id))
        cache.writeDiskBytes(id, byteArrayOf(1, 2, 3))
        assertEquals(1, cache.artGeneration(id))
        cache.writeDiskBytes(id, byteArrayOf(4, 5), ArtCache.ArtKind.HERO)
        assertEquals(2, cache.artGeneration(id))
        cache.invalidate(id)
        assertEquals(3, cache.artGeneration(id))
        assertEquals(0, cache.artGeneration("snes:other.smc"))
    }

    @Test
    fun `sourceStampMatches rejects mismatched override sources`() {
        // No expected URI (scrape path): any stamp is acceptable.
        assertTrue(ArtCache.sourceStampMatches(null, null))
        assertTrue(ArtCache.sourceStampMatches("content://x", null))
        // Expected URI (override / local art): stamp must match exactly.
        assertFalse(ArtCache.sourceStampMatches(null, "content://override"))
        assertFalse(ArtCache.sourceStampMatches("content://old", "content://override"))
        assertTrue(ArtCache.sourceStampMatches("content://override", "content://override"))
    }

    @Test
    fun `memKeyFor changes when source URI changes so override cannot hit old mem`() {
        val id = "snes:snes/smw.smc"
        val plain = ArtCache.memKeyFor(id, ArtCache.ArtKind.GRID, null)
        val a = ArtCache.memKeyFor(id, ArtCache.ArtKind.GRID, "content://a")
        val b = ArtCache.memKeyFor(id, ArtCache.ArtKind.GRID, "content://b")
        assertEquals(id, plain)
        assertNotEquals(plain, a)
        assertNotEquals(a, b)
        assertTrue(a.startsWith("$id|src:"))
    }

    @Test
    fun `effectiveArtUri plus memKeyFor models SET_ART cache isolation`() {
        // Shipped load path: effectiveArtUri(override) → memKeyFor(source).
        // After SET_ART the source URI changes, so the mem key must change
        // and cannot hit the prior scanner-art entry.
        val entry = com.visorcraft.ghostgalleon.rom.RomEntry(
            id = "snes:game.smc", name = "game", platformId = "snes",
            uri = "content://rom", path = "/r", artUri = "content://scanner",
        )
        val before = ArtOverride.effectiveArtUri(entry, emptyMap())
        val after = ArtOverride.effectiveArtUri(
            entry, mapOf(entry.id to "content://picked"),
        )
        assertEquals("content://scanner", before)
        assertEquals("content://picked", after)
        assertNotEquals(
            ArtCache.memKeyFor(entry.id, ArtCache.ArtKind.GRID, before),
            ArtCache.memKeyFor(entry.id, ArtCache.ArtKind.GRID, after),
        )
    }

    @Test
    fun `sample size keeps both dimensions at or above target`() {
        assertEquals(1, ArtCache.sampleSizeFor(1000, 750, 512))
        assertEquals(2, ArtCache.sampleSizeFor(2048, 1536, 512))
        assertEquals(4, ArtCache.sampleSizeFor(4000, 3000, 512))
        // One dimension below target blocks further sampling (no upscale,
        // no drop below target); already-small images stay at 1.
        assertEquals(1, ArtCache.sampleSizeFor(4000, 600, 512))
        assertEquals(1, ArtCache.sampleSizeFor(512, 512, 512))
        assertEquals(1, ArtCache.sampleSizeFor(300, 200, 512))
    }

    @Test
    fun `lru touch gap is one minute so flings do not thrash mtime`() {
        // Contract: disk hits only rewrite lastModified after this gap.
        assertEquals(60_000L, ArtCache.LRU_TOUCH_MIN_GAP_MS)
    }

    @Test
    fun `hero sample size tracks width alone`() {
        // Heroes are wide and short; the scrape path passes the width as
        // both dimensions so height never blocks sampling.
        assertEquals(1, ArtCache.sampleSizeFor(1920, 1920, 1600))
        assertEquals(2, ArtCache.sampleSizeFor(3840, 3840, 1600))
        // A 3840x620 hero samples to 1920x310 under the width-only policy,
        // where the both-dims policy would have left it full-size.
        assertEquals(1, ArtCache.sampleSizeFor(3840, 620, 1600))
    }

    // Eviction: oldest-lastModified files go first, deleting only as many
    // as needed to get back under the cap.

    private fun sizedFile(name: String, bytes: Int, lastModified: Long): File =
        tmp.root.resolve(name).apply {
            writeBytes(ByteArray(bytes))
            setLastModified(lastModified)
        }

    @Test
    fun `eviction deletes oldest first and stops at the cap`() {
        val old = sizedFile("old.png", 60, 1_000)
        val mid = sizedFile("mid.png", 60, 2_000)
        val new = sizedFile("new.png", 60, 3_000)
        // 180 total, cap 120: only the oldest goes.
        assertEquals(
            listOf(old),
            ArtCache.evictionCandidates(listOf(new, mid, old), 120))
        // Cap 60: oldest two go, the newest alone fills the cap.
        assertEquals(
            listOf(old, mid),
            ArtCache.evictionCandidates(listOf(new, mid, old), 60))
    }

    @Test
    fun `eviction is a no-op at or under the cap`() {
        val a = sizedFile("a.png", 40, 1_000)
        val b = sizedFile("b.png", 40, 2_000)
        assertTrue(ArtCache.evictionCandidates(listOf(a, b), 80).isEmpty())
        assertTrue(ArtCache.evictionCandidates(listOf(a, b), 1_000).isEmpty())
        assertTrue(ArtCache.evictionCandidates(emptyList(), 0).isEmpty())
    }

    @Test
    fun `eviction selection on a real cache dir picks the stale file`() {
        val cache = ArtCache(tmp.root.resolve("art"))
        cache.writeDiskBytes("snes:snes/a.smc", ByteArray(10))
        cache.writeDiskBytes("snes:snes/b.smc", ByteArray(10))
        val artDir = tmp.root.resolve("art")
        // Pin timestamps explicitly (write-time lastModified has only ~1 s
        // resolution, too coarse to order two writes in one test).
        File(artDir, ArtCache.keyFor("snes:snes/a.smc") + ".png").setLastModified(2_000)
        File(artDir, ArtCache.keyFor("snes:snes/b.smc") + ".png").setLastModified(1_000)
        val evict = ArtCache.evictionCandidates(artDir.listFiles()!!.toList(), 15)
        assertEquals(
            listOf(ArtCache.keyFor("snes:snes/b.smc") + ".png"),
            evict.map { it.name })
    }

    @Test
    fun `disk cache cap is 256 MiB`() {
        assertEquals(256L * 1024 * 1024, ArtCache.DISK_CACHE_CAP_BYTES)
    }

    @Test
    fun `webp disk cache starts at API 30`() {
        assertEquals(30, ArtCache.WEBP_MIN_SDK)
        assertFalse(ArtCache.usesWebpDiskCache(29))
        assertTrue(ArtCache.usesWebpDiskCache(30))
        assertTrue(ArtCache.usesWebpDiskCache(34))
        assertEquals(
            android.graphics.Bitmap.CompressFormat.JPEG,
            ArtCache.cacheCompressFormat(29),
        )
        assertEquals(
            android.graphics.Bitmap.CompressFormat.WEBP_LOSSY,
            ArtCache.cacheCompressFormat(30),
        )
    }

    @Test
    fun `pixelByteCount matches RGB_565 vs ARGB packing`() {
        assertEquals(0, ArtCache.pixelByteCount(0, 10, true))
        assertEquals(512 * 512 * 2, ArtCache.pixelByteCount(512, 512, rgb565 = true))
        assertEquals(1600 * 400 * 4, ArtCache.pixelByteCount(1600, 400, rgb565 = false))
    }

    @Test
    fun `canReuseInBitmap rejects displayed recycled undersized or wrong config`() {
        assertTrue(
            ArtCache.canReuseInBitmap(
                candidateBytes = 100,
                candidateConfigName = "RGB_565",
                candidateRecycled = false,
                displayCount = 0,
                neededBytes = 80,
                neededConfigName = "RGB_565",
            ),
        )
        assertFalse(
            ArtCache.canReuseInBitmap(
                candidateBytes = 100,
                candidateConfigName = "RGB_565",
                candidateRecycled = false,
                displayCount = 1,
                neededBytes = 80,
                neededConfigName = "RGB_565",
            ),
        )
        assertFalse(
            ArtCache.canReuseInBitmap(
                candidateBytes = 100,
                candidateConfigName = "RGB_565",
                candidateRecycled = true,
                displayCount = 0,
                neededBytes = 80,
                neededConfigName = "RGB_565",
            ),
        )
        assertFalse(
            ArtCache.canReuseInBitmap(
                candidateBytes = 50,
                candidateConfigName = "RGB_565",
                candidateRecycled = false,
                displayCount = 0,
                neededBytes = 80,
                neededConfigName = "RGB_565",
            ),
        )
        assertFalse(
            ArtCache.canReuseInBitmap(
                candidateBytes = 100,
                candidateConfigName = "ARGB_8888",
                candidateRecycled = false,
                displayCount = 0,
                neededBytes = 80,
                neededConfigName = "RGB_565",
            ),
        )
    }

    @Test
    fun `computeMemoryCacheBytes scales dynamically between 16 MiB and 48 MiB`() {
        assertEquals(ArtCache.MIN_MEMORY_BYTES, ArtCache.computeMemoryCacheBytes(64L * 1024 * 1024))
        assertEquals(ArtCache.MIN_MEMORY_BYTES, ArtCache.computeMemoryCacheBytes(128L * 1024 * 1024))
        assertEquals(32 * 1024 * 1024, ArtCache.computeMemoryCacheBytes(256L * 1024 * 1024))
        assertEquals(ArtCache.MAX_MEMORY_BYTES, ArtCache.computeMemoryCacheBytes(512L * 1024 * 1024))
        assertEquals(ArtCache.MAX_MEMORY_BYTES, ArtCache.computeMemoryCacheBytes(2048L * 1024 * 1024))
    }

    @Test
    fun `memKeyFor skips SHA-256 and keyCache stays strictly bounded`() {
        ArtCache.clearKeyCacheForTest()
        val mk = ArtCache.memKeyFor("snes:a.smc", ArtCache.ArtKind.GRID, "content://provider/doc/123")
        assertEquals("snes:a.smc|src:content://provider/doc/123", mk)
        // memKeyFor must NOT touch keyCache / SHA-256 on the main thread!
        assertEquals(0, ArtCache.keyCacheSize())

        for (i in 0 until ArtCache.KEY_CACHE_MAX_ENTRIES + 500) {
            ArtCache.keyFor("snes:rom_$i.smc")
            assertTrue(
                "keyCache exceeded cap: ${ArtCache.keyCacheSize()}",
                ArtCache.keyCacheSize() <= ArtCache.KEY_CACHE_MAX_ENTRIES,
            )
        }
    }

    @Test
    fun `disk presence index short-circuits misses and stays consistent across write evict invalidate clear`() {
        val artDir = tmp.root.resolve("art-idx")
        val cache = ArtCache(artDir)
        val idA = "snes:snes/a.smc"
        val idB = "snes:snes/b.smc"
        val idMissing = "snes:snes/missing.smc"

        // Pre-populate one file before seeding (simulates cold start with prior disk cache).
        artDir.mkdirs()
        File(artDir, cache.fileNameFor(idA, ArtCache.ArtKind.GRID)).writeBytes(ByteArray(100))
        File(artDir, cache.fileNameFor(idA, ArtCache.ArtKind.GRID)).setLastModified(1_000L)

        assertFalse(cache.isDiskIndexReady())
        assertFalse(cache.isKnownDiskMiss(idMissing)) // never false-negatives before seed!

        cache.ensureDiskIndexSeeded()
        assertTrue(cache.isDiskIndexReady())
        assertTrue(cache.diskHas(idA))
        assertFalse(cache.isKnownDiskMiss(idA))
        assertTrue(cache.isKnownDiskMiss(idMissing))
        // Empty HERO/LOGO kind sets short-circuit in O(1) without touching SHA-256/keyCache.
        ArtCache.clearKeyCacheForTest()
        assertTrue(cache.isKnownDiskMiss(idA, ArtCache.ArtKind.HERO))
        assertTrue(cache.isKnownDiskMiss(idMissing, ArtCache.ArtKind.LOGO))
        assertEquals(0, ArtCache.keyCacheSize())
        assertNull(cache.readDiskBytes(idMissing))

        // Write updates index immediately for GRID, HERO, LOGO.
        cache.writeDiskBytes(idB, ByteArray(100), ArtCache.ArtKind.GRID)
        cache.writeDiskBytes(idB, ByteArray(80), ArtCache.ArtKind.HERO)
        cache.writeDiskBytes(idB, ByteArray(60), ArtCache.ArtKind.LOGO)
        assertFalse(cache.isKnownDiskMiss(idB, ArtCache.ArtKind.GRID))
        assertFalse(cache.isKnownDiskMiss(idB, ArtCache.ArtKind.HERO))
        assertFalse(cache.isKnownDiskMiss(idB, ArtCache.ArtKind.LOGO))
        assertTrue(cache.diskHas(idB, ArtCache.ArtKind.HERO))
        assertTrue(cache.diskHas(idB, ArtCache.ArtKind.LOGO))

        // Eviction removes evicted entries from index (idA is oldest with mtime 1_000L).
        File(artDir, cache.fileNameFor(idB, ArtCache.ArtKind.GRID)).setLastModified(5_000L)
        File(artDir, cache.fileNameFor(idB, ArtCache.ArtKind.HERO)).setLastModified(6_000L)
        File(artDir, cache.fileNameFor(idB, ArtCache.ArtKind.LOGO)).setLastModified(7_000L)
        cache.evictIfOverCap(capBytes = 250L) // total 340 -> evicts idA (100B), leaves 240B
        assertFalse(cache.diskHas(idA))
        assertTrue(cache.isKnownDiskMiss(idA))
        assertTrue(cache.diskHas(idB, ArtCache.ArtKind.GRID))

        // Invalidate removes all kinds for idB from disk + index.
        cache.invalidate(idB)
        assertTrue(cache.isKnownDiskMiss(idB, ArtCache.ArtKind.GRID))
        assertTrue(cache.isKnownDiskMiss(idB, ArtCache.ArtKind.HERO))
        assertTrue(cache.isKnownDiskMiss(idB, ArtCache.ArtKind.LOGO))

        // Clear removes everything and marks index empty.
        cache.writeDiskBytes(idA, ByteArray(50))
        assertTrue(cache.diskHas(idA))
        cache.clear()
        assertFalse(cache.diskHas(idA))
        assertTrue(cache.isKnownDiskMiss(idA))

        // External write to artDir (e.g. ArtArchive.unzip) + invalidateDiskIndex() re-seeds cleanly.
        val externalId = "snes:snes/restored.smc"
        File(artDir, cache.fileNameFor(externalId, ArtCache.ArtKind.GRID)).writeBytes(ByteArray(64))
        assertTrue(cache.isKnownDiskMiss(externalId)) // stale before invalidation
        cache.invalidateDiskIndex()
        assertFalse(cache.isDiskIndexReady())
        assertFalse(cache.isKnownDiskMiss(externalId))
        assertTrue(cache.diskHas(externalId))
        cache.ensureDiskIndexSeeded()
        assertTrue(cache.isDiskIndexReady())
        assertTrue(cache.diskHas(externalId))
        assertFalse(cache.isKnownDiskMiss(externalId))
    }

    @Test
    fun `concurrent GRID HERO LOGO writes for same romId have zero tmp races`() {
        val artDir = tmp.root.resolve("art-concurrent")
        val cache = ArtCache(artDir)
        val romId = "snes:snes/Chrono Trigger.smc"
        val threads = 6
        val rounds = 40
        val start = java.util.concurrent.CountDownLatch(1)
        val done = java.util.concurrent.CountDownLatch(threads)
        val errors = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val kinds = arrayOf(ArtCache.ArtKind.GRID, ArtCache.ArtKind.HERO, ArtCache.ArtKind.LOGO)

        repeat(threads) { t ->
            val kind = kinds[t % kinds.size]
            val payload = ByteArray(128) { (kind.ordinal + 1).toByte() }
            Thread {
                try {
                    start.await()
                    repeat(rounds) {
                        cache.writeDiskBytes(romId, payload, kind)
                    }
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    done.countDown()
                }
            }.start()
        }
        start.countDown()
        assertTrue("concurrent writers timed out", done.await(30, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue("concurrent write errors: $errors", errors.isEmpty())
        assertTrue(artDir.listFiles()!!.none { it.name.endsWith(".tmp") })
        for (kind in kinds) {
            val expected = ByteArray(128) { (kind.ordinal + 1).toByte() }
            assertArrayEquals(expected, cache.readDiskBytes(romId, kind))
        }
    }

    @Test
    fun `evictionCandidates 5000-file single-stat benchmark selects oldest accurately`() {
        val dir = tmp.newFolder("evict-5k")
        val count = 5_000
        val files = ArrayList<File>(count)
        for (i in 0 until count) {
            // Scramble mtime order so TimSort exercises full O(N log N) comparisons.
            val f = File(dir, "f_$i.png")
            f.writeBytes(ByteArray(100))
            val mtime = ((i * 7919) % count + 1) * 1_000L
            f.setLastModified(mtime)
            files.add(f)
        }
        val capBytes = 100L * 2_500L // evict oldest half (2,500 files)

        val t0 = System.nanoTime()
        val evict = ArtCache.evictionCandidates(files, capBytes)
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
        println("ArtCacheTest (5,000-file single-stat evictionCandidates): ${"%.2f".format(elapsedMs)}ms (evicted=${evict.size})")

        assertEquals(2_500, evict.size)
        // Verify monotonically non-decreasing mtime across evicted candidates.
        var prevMtime = Long.MIN_VALUE
        for (f in evict) {
            val m = f.lastModified()
            assertTrue("expected sorted oldest-first mtime ($m >= $prevMtime)", m >= prevMtime)
            prevMtime = m
        }
        assertTrue("5k evictionCandidates too slow: ${"%.2f".format(elapsedMs)}ms", elapsedMs < 500.0)
    }
}
