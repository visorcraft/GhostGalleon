package com.visorcraft.ghostgalleon.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pure JVM tests for [AmbientLoopCache] disk caching, RIFF/WAVE header & byte-length
 * validation, corrupt/truncated/version-mismatch fallback, and stale-file pruning.
 */
class AmbientLoopCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `loadOrSynthesize round-trips bit-exact PCM and skips re-synthesis on warm hit`() {
        val dir = tmp.newFolder("gg_sfx")
        val synthCount = AtomicInteger(0)

        val tCold0 = System.nanoTime()
        val cold = AmbientLoopCache.loadOrSynthesize(
            cacheDir = dir,
            pack = SoundPackId.NEON,
            durationSec = AmbientSynthesizer.DEFAULT_DURATION_SEC,
            sampleRate = AmbientSynthesizer.SAMPLE_RATE,
            onSynthesized = { synthCount.incrementAndGet() },
        )
        val coldMs = (System.nanoTime() - tCold0) / 1_000_000.0

        assertEquals(1, synthCount.get())
        val expectedFile = File(dir, "ambient_v${AmbientLoopCache.CACHE_VERSION}_neon.wav")
        assertTrue("expected cached WAV at ${expectedFile.name}", expectedFile.isFile)
        assertTrue("no leftover .tmp files allowed", dir.listFiles()!!.none { it.name.endsWith(".tmp") })

        val tWarm0 = System.nanoTime()
        val warm = AmbientLoopCache.loadOrSynthesize(
            cacheDir = dir,
            pack = SoundPackId.NEON,
            durationSec = AmbientSynthesizer.DEFAULT_DURATION_SEC,
            sampleRate = AmbientSynthesizer.SAMPLE_RATE,
            onSynthesized = { synthCount.incrementAndGet() },
        )
        val warmMs = (System.nanoTime() - tWarm0) / 1_000_000.0
        println("AmbientLoopCacheTest (NEON 6s stereo): cold=${"%.2f".format(coldMs)}ms, warm=${"%.2f".format(warmMs)}ms")

        assertEquals("warm load must NOT re-synthesize", 1, synthCount.get())
        assertArrayEquals("warm cached PCM must match synthesized PCM bit-for-bit", cold, warm)
        assertTrue("warm load too slow: ${"%.2f".format(warmMs)}ms", warmMs < 100.0)

        // AUTO resolves to GHOST cache file; OFF returns empty without writing anything.
        val autoLoop = AmbientLoopCache.loadOrSynthesize(dir, SoundPackId.AUTO, durationSec = 2.0)
        assertTrue(File(dir, "ambient_v${AmbientLoopCache.CACHE_VERSION}_ghost.wav").isFile)
        val ghostLoop = AmbientLoopCache.loadOrSynthesize(dir, SoundPackId.GHOST, durationSec = 2.0)
        assertArrayEquals(autoLoop, ghostLoop)
        assertEquals(0, AmbientLoopCache.loadOrSynthesize(dir, SoundPackId.OFF).size)
    }

    @Test
    fun `corrupt, truncated, oversized, and parameter-mismatched cache files fall back safely and self-heal`() {
        val dir = tmp.newFolder("gg_sfx_corrupt")
        val synthCount = AtomicInteger(0)
        val pack = SoundPackId.GHOST
        val durationSec = 2.0
        val sampleRate = AmbientSynthesizer.SAMPLE_RATE
        val target = File(dir, AmbientLoopCache.cacheFileName(pack))

        val golden = AmbientLoopCache.loadOrSynthesize(
            cacheDir = dir,
            pack = pack,
            durationSec = durationSec,
            sampleRate = sampleRate,
            onSynthesized = { synthCount.incrementAndGet() },
        )
        assertEquals(1, synthCount.get())

        // 1. Corrupt RIFF header magic
        val corruptBytes = target.readBytes()
        corruptBytes[0] = 'X'.code.toByte()
        target.writeBytes(corruptBytes)
        val recoveredCorrupt = AmbientLoopCache.loadOrSynthesize(
            cacheDir = dir,
            pack = pack,
            durationSec = durationSec,
            sampleRate = sampleRate,
            onSynthesized = { synthCount.incrementAndGet() },
        )
        assertEquals("corrupt header must trigger re-synthesis", 2, synthCount.get())
        assertArrayEquals(golden, recoveredCorrupt)
        // Repaired file should now hit cleanly
        AmbientLoopCache.loadOrSynthesize(
            cacheDir = dir,
            pack = pack,
            durationSec = durationSec,
            sampleRate = sampleRate,
            onSynthesized = { synthCount.incrementAndGet() },
        )
        assertEquals("self-healed cache must hit on next read", 2, synthCount.get())

        // 2. Truncated file (missing trailing bytes)
        val validBytes = target.readBytes()
        target.writeBytes(validBytes.copyOf(validBytes.size - 16))
        val recoveredTruncated = AmbientLoopCache.loadOrSynthesize(
            cacheDir = dir,
            pack = pack,
            durationSec = durationSec,
            sampleRate = sampleRate,
            onSynthesized = { synthCount.incrementAndGet() },
        )
        assertEquals("truncated file must trigger re-synthesis", 3, synthCount.get())
        assertArrayEquals(golden, recoveredTruncated)

        // 3. Oversized file with trailing junk bytes
        target.writeBytes(validBytes + byteArrayOf(1, 2, 3, 4))
        val recoveredOversized = AmbientLoopCache.loadOrSynthesize(
            cacheDir = dir,
            pack = pack,
            durationSec = durationSec,
            sampleRate = sampleRate,
            onSynthesized = { synthCount.incrementAndGet() },
        )
        assertEquals("oversized file must trigger re-synthesis", 4, synthCount.get())
        assertArrayEquals(golden, recoveredOversized)

        // 4. Wrong sample rate in WAV header (22050 Hz when 44100 Hz requested)
        val wrongRateWav = WavEncoder.encodePcm16(golden, sampleRate = 22050, channels = 2)
        target.writeBytes(wrongRateWav)
        val recoveredRate = AmbientLoopCache.loadOrSynthesize(
            cacheDir = dir,
            pack = pack,
            durationSec = durationSec,
            sampleRate = sampleRate,
            onSynthesized = { synthCount.incrementAndGet() },
        )
        assertEquals("wrong sample rate must trigger re-synthesis", 5, synthCount.get())
        assertArrayEquals(golden, recoveredRate)

        // 5. Frame count mismatch (cached 2.0s file when 2.5s requested)
        val longerLoop = AmbientLoopCache.loadOrSynthesize(
            cacheDir = dir,
            pack = pack,
            durationSec = 2.5,
            sampleRate = sampleRate,
            onSynthesized = { synthCount.incrementAndGet() },
        )
        assertEquals("frame count mismatch must trigger re-synthesis", 6, synthCount.get())
        assertEquals((2.5 * sampleRate).toInt() * 2, longerLoop.size)

        // 6. Version mismatch uses distinct cache file
        AmbientLoopCache.loadOrSynthesize(
            cacheDir = dir,
            pack = pack,
            durationSec = durationSec,
            sampleRate = sampleRate,
            version = AmbientLoopCache.CACHE_VERSION + 1,
            onSynthesized = { synthCount.incrementAndGet() },
        )
        assertEquals("bumped version must miss old version cache", 7, synthCount.get())
        assertTrue(File(dir, "ambient_v${AmbientLoopCache.CACHE_VERSION + 1}_ghost.wav").isFile)
    }

    @Test
    fun `pruneStaleFiles deletes old ambient versions and tmp files while preserving current ambient and SFX files`() {
        val dir = tmp.newFolder("gg_sfx_prune")
        val keepCurrent = File(dir, "ambient_v2_ghost.wav").apply { writeBytes(byteArrayOf(1)) }
        val keepSfx = File(dir, "ghost_v1_nav_tick.wav").apply { writeBytes(byteArrayOf(2)) }
        val staleOldVer = File(dir, "ambient_v1_ghost.wav").apply { writeBytes(byteArrayOf(3)) }
        val staleTmp = File(dir, "ambient_v2_neon.wav.1.tmp").apply { writeBytes(byteArrayOf(4)) }
        val staleUnknownPack = File(dir, "ambient_v2_unknown.wav").apply { writeBytes(byteArrayOf(5)) }

        assertTrue(AmbientLoopCache.isAmbientCacheArtifact(keepCurrent.name))
        assertTrue(AmbientLoopCache.isAmbientCacheArtifact(staleTmp.name))
        assertFalse(AmbientLoopCache.isAmbientCacheArtifact(keepSfx.name))
        assertTrue(AmbientLoopCache.isCurrentCacheFileName(keepCurrent.name, currentVersion = 2))
        assertFalse(AmbientLoopCache.isCurrentCacheFileName(staleOldVer.name, currentVersion = 2))
        assertFalse(AmbientLoopCache.isCurrentCacheFileName(staleUnknownPack.name, currentVersion = 2))

        AmbientLoopCache.pruneStaleFiles(dir, currentVersion = 2)

        assertTrue("current ambient cache must survive prune", keepCurrent.isFile)
        assertTrue("SFX cache files must never be touched by ambient prune", keepSfx.isFile)
        assertFalse("old ambient version must be pruned", staleOldVer.exists())
        assertFalse("orphaned ambient tmp must be pruned", staleTmp.exists())
        assertFalse("unknown pack ambient file must be pruned", staleUnknownPack.exists())
    }
}
