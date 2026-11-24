package com.visorcraft.ghostgalleon.audio

import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * Pure JVM disk cache for synthesized stereo ambient loops stored under
 * `cacheDir/gg_sfx/ambient_v<ver>_<pack>.wav`.
 *
 * Validates RIFF/WAVE header metadata (PCM-16, stereo, exact sample rate, exact
 * frame count / byte length) before reading so truncated, corrupt, or parameter-mismatched
 * cache files safely fall back to fresh procedural synthesis and atomic rewrite.
 */
object AmbientLoopCache {

    /** Bump whenever [AmbientSynthesizer] DSP output changes materially. */
    const val CACHE_VERSION: Int = 1

    const val FILE_PREFIX: String = "ambient_"

    private val CONCRETE_PACK_IDS: Set<String> = SoundPackId.CONCRETE_PACKS.map { it.id }.toSet()
    private val tmpCounter = AtomicLong(0L)

    /** Resolves [pack] (`AUTO -> GHOST`) to its versioned cache filename `ambient_v<ver>_<pack>.wav`. */
    fun cacheFileName(
        pack: SoundPackId,
        version: Int = CACHE_VERSION,
    ): String {
        val resolved = if (pack == SoundPackId.AUTO) SoundPackId.GHOST else pack
        require(resolved in SoundPackId.CONCRETE_PACKS) {
            "Ambient cache requires a playable pack, got $pack"
        }
        require(version >= 1) { "Cache version must be >= 1, got $version" }
        return "ambient_v${version}_${resolved.id}.wav"
    }

    /** True iff [fileName] belongs to the ambient cache namespace (`ambient_*`). */
    fun isAmbientCacheArtifact(fileName: String): Boolean = fileName.startsWith(FILE_PREFIX)

    /** True iff [fileName] is a current-version ambient loop cache WAV for a known concrete pack. */
    fun isCurrentCacheFileName(
        fileName: String,
        currentVersion: Int = CACHE_VERSION,
    ): Boolean {
        if (currentVersion < 1) return false
        val prefix = "ambient_v${currentVersion}_"
        if (!fileName.startsWith(prefix) || !fileName.endsWith(".wav")) return false
        val packId = fileName.substring(prefix.length, fileName.length - 4)
        return packId in CONCRETE_PACK_IDS
    }

    /** Removes obsolete ambient cache versions and orphaned `ambient_*.tmp` files in [cacheDir]. */
    fun pruneStaleFiles(
        cacheDir: File,
        currentVersion: Int = CACHE_VERSION,
    ) {
        if (!cacheDir.isDirectory) return
        val children = cacheDir.listFiles() ?: return
        for (child in children) {
            if (!child.isFile) continue
            val name = child.name
            if (isAmbientCacheArtifact(name) && !isCurrentCacheFileName(name, currentVersion)) {
                runCatching { child.delete() }
            }
        }
    }

    /**
     * Loads the cached stereo ambient PCM-16 loop for [pack] from [cacheDir] if present
     * and header/length valid; otherwise synthesizes fresh via [AmbientSynthesizer.synthesizeLoop]
     * and persists atomically (`<name>.<id>.tmp` -> rename).
     *
     * Never throws on I/O or disk-full failures — always returns valid synthesized PCM.
     */
    fun loadOrSynthesize(
        cacheDir: File,
        pack: SoundPackId,
        durationSec: Double = AmbientSynthesizer.DEFAULT_DURATION_SEC,
        sampleRate: Int = AmbientSynthesizer.SAMPLE_RATE,
        version: Int = CACHE_VERSION,
        onSynthesized: (() -> Unit)? = null,
    ): ShortArray {
        if (pack == SoundPackId.OFF) return ShortArray(0)
        val resolved = if (pack == SoundPackId.AUTO) SoundPackId.GHOST else pack
        val expectedFrames = (durationSec * sampleRate).roundToInt().coerceAtLeast(1024)
        val expectedSamples = expectedFrames * AmbientSynthesizer.CHANNELS
        val target = File(cacheDir, cacheFileName(resolved, version))

        val cached = runCatching {
            WavEncoder.readPcm16FromFile(
                file = target,
                expectedSampleRate = sampleRate,
                expectedChannels = AmbientSynthesizer.CHANNELS,
                expectedSampleCount = expectedSamples,
            )
        }.getOrNull()
        if (cached != null) {
            return cached
        }

        // Cache miss, corrupt/truncated file, or sample-rate/frame-count mismatch: synthesize fresh.
        onSynthesized?.invoke()
        val synthesized = AmbientSynthesizer.synthesizeLoop(
            pack = resolved,
            durationSec = durationSec,
            sampleRate = sampleRate,
        )
        if (synthesized.size == expectedSamples) {
            writeAtomically(cacheDir, target, synthesized, sampleRate)
        }
        return synthesized
    }

    private fun writeAtomically(
        cacheDir: File,
        target: File,
        samples: ShortArray,
        sampleRate: Int,
    ) {
        if (!cacheDir.exists() && !cacheDir.mkdirs() && !cacheDir.isDirectory) return
        val tmp = File(cacheDir, "${target.name}.${tmpCounter.incrementAndGet()}.tmp")
        runCatching {
            WavEncoder.writePcm16ToFile(
                file = tmp,
                samples = samples,
                sampleRate = sampleRate,
                channels = AmbientSynthesizer.CHANNELS,
            )
            if (!tmp.renameTo(target)) {
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
            }
        }.onFailure {
            runCatching { tmp.delete() }
        }
    }
}
