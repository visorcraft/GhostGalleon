package com.visorcraft.ghostgalleon.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.visorcraft.ghostgalleon.rom.RomEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Arrays
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * ROM artwork cache: a disk cache under `filesDir/art/` keyed by RomEntry
 * id (SHA-256 hashed filename, since ids contain ':'/'/'), backed by an
 * in-memory LRU. Disk usage is capped at [DISK_CACHE_CAP_BYTES]: writes
 * evict least-recently-used files (lastModified, bumped on read hits —
 * throttled so flings do not rewrite mtime on every hit).
 * All I/O and bitmap decode/encode runs on a bounded background pool;
 * results are delivered on the main thread. Concurrent loads for the same
 * memory key coalesce into one decode.
 *
 * The disk path is pure JVM (File + bytes) so host tests can drive it with
 * a temp dir; android.util.LruCache is an Android stub in host tests, so
 * the memory cache is created lazily and only touched on the async path.
 */
class ArtCache(private val dir: File) {

    /** Cache slot: GRID = tile art (also what [load] serves), HERO = wide
     *  hero-panel art keyed separately (scraped alongside grids). */
    enum class ArtKind { GRID, HERO, LOGO }

    private val memory by lazy {
        val capBytes = computeMemoryCacheBytes(Runtime.getRuntime().maxMemory())
        object : LruCache<String, Bitmap>(capBytes) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount

            override fun entryRemoved(
                evicted: Boolean,
                key: String,
                oldValue: Bitmap,
                newValue: Bitmap?,
            ) {
                // Capacity evictions (and explicit drops) feed the inBitmap
                // pool when no ImageView still displays the pixels.
                if (evicted || newValue == null) offerReusable(oldValue)
            }
        }
    }

    // Per-kind in-memory index of cached image filenames in [dir], seeded lazily
    // off-UI via dir.list() so guaranteed disk misses short-circuit in O(1) RAM
    // without waking DECODE_EXECUTOR or issuing stat() syscalls (and empty
    // HERO/LOGO sets skip SHA-256 hashing completely).
    private val diskIndexLock = Any()
    private val gridDiskIndex: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val heroDiskIndex: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val logoDiskIndex: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile
    private var diskIndexReady: Boolean = false
    private val diskIndexSeedScheduled = AtomicBoolean(false)

    private val evictLock = Any()
    private val tmpCounter = AtomicLong(0L)

    private fun indexFor(kind: ArtKind): MutableSet<String> = when (kind) {
        ArtKind.GRID -> gridDiskIndex
        ArtKind.HERO -> heroDiskIndex
        ArtKind.LOGO -> logoDiskIndex
    }

    private fun addFileNameToIndexLocked(name: String) {
        when {
            name.endsWith(".hero.png") -> heroDiskIndex.add(name)
            name.endsWith(".logo.png") -> logoDiskIndex.add(name)
            name.endsWith(".png") -> gridDiskIndex.add(name)
        }
    }

    private fun removeFileNameFromIndexLocked(name: String) {
        when {
            name.endsWith(".hero.png") -> heroDiskIndex.remove(name)
            name.endsWith(".logo.png") -> logoDiskIndex.remove(name)
            name.endsWith(".png") -> gridDiskIndex.remove(name)
        }
    }

    internal fun fileNameFor(romId: String, kind: ArtKind = ArtKind.GRID): String {
        val suffix = when (kind) {
            ArtKind.HERO -> ".hero.png"
            ArtKind.LOGO -> ".logo.png"
            ArtKind.GRID -> ".png"
        }
        return keyFor(romId) + suffix
    }

    private fun fileFor(romId: String, kind: ArtKind = ArtKind.GRID): File =
        File(dir, fileNameFor(romId, kind))

    internal fun tempFileFor(romId: String, kind: ArtKind): File {
        val kindTag = when (kind) {
            ArtKind.GRID -> "grid"
            ArtKind.HERO -> "hero"
            ArtKind.LOGO -> "logo"
        }
        return File(dir, "${keyFor(romId)}.$kindTag.${tmpCounter.incrementAndGet()}.tmp")
    }

    /** Seed the disk-presence index once (blocking; call off UI thread). */
    internal fun ensureDiskIndexSeeded() {
        if (diskIndexReady) return
        synchronized(diskIndexLock) {
            if (diskIndexReady) return
            val names = dir.list()
            if (names != null) {
                for (name in names) {
                    addFileNameToIndexLocked(name)
                }
            }
            diskIndexReady = true
        }
    }

    private fun scheduleDiskIndexSeed() {
        if (diskIndexReady) return
        if (diskIndexSeedScheduled.compareAndSet(false, true)) {
            DECODE_EXECUTOR.execute { ensureDiskIndexSeeded() }
        }
    }

    internal fun isDiskIndexReady(): Boolean = diskIndexReady

    /** True when the disk index is seeded and proves [romId]/[kind] is absent on disk. */
    internal fun isKnownDiskMiss(romId: String, kind: ArtKind = ArtKind.GRID): Boolean {
        if (!diskIndexReady) return false
        val set = indexFor(kind)
        if (set.isEmpty()) return true
        return !set.contains(fileNameFor(romId, kind))
    }

    private fun recordDiskPresent(kind: ArtKind, fileName: String) {
        synchronized(diskIndexLock) {
            indexFor(kind).add(fileName)
        }
    }

    private fun recordDiskAbsent(kind: ArtKind, fileName: String) {
        synchronized(diskIndexLock) {
            if (!File(dir, fileName).isFile) {
                indexFor(kind).remove(fileName)
            }
        }
    }

    /**
     * Reset the in-memory disk-presence index and size estimate so external
     * writers to [dir] (e.g. backup/restore `ArtArchive.unzip`) are picked up
     * cleanly on the next lazy re-seed.
     */
    fun invalidateDiskIndex() {
        synchronized(diskIndexLock) {
            gridDiskIndex.clear()
            heroDiskIndex.clear()
            logoDiskIndex.clear()
            diskIndexReady = false
            diskIndexSeedScheduled.set(false)
        }
        approxDiskBytes.set(-1L)
    }

    /** Sidecar recording which source URI produced the GRID disk bytes.
     *  When [load] supplies a different effective URI (user override vs
     *  scanner art), a stamp mismatch forces a re-decode so stale cache
     *  never wins over [ArtOverride]. */
    private fun sourceStampFile(romId: String, kind: ArtKind = ArtKind.GRID): File {
        val suffix = when (kind) {
            ArtKind.HERO -> ".hero.src"
            ArtKind.LOGO -> ".logo.src"
            ArtKind.GRID -> ".src"
        }
        return File(dir, keyFor(romId) + suffix)
    }

    /** True when a downscaled copy is already on disk. */
    fun diskHas(romId: String, kind: ArtKind = ArtKind.GRID): Boolean {
        if (diskIndexReady) {
            val set = indexFor(kind)
            if (set.isEmpty()) return false
            return set.contains(fileNameFor(romId, kind))
        }
        return fileFor(romId, kind).isFile
    }

    /** Raw cached bytes for [romId], or null on a miss. A hit may bump the
     *  file's lastModified (throttled) so disk eviction stays LRU without
     *  rewriting mtime on every carousel fling hit. */
    fun readDiskBytes(romId: String, kind: ArtKind = ArtKind.GRID): ByteArray? {
        if (isKnownDiskMiss(romId, kind)) return null
        val file = fileFor(romId, kind)
        if (!file.isFile) {
            recordDiskAbsent(kind, file.name)
            return null
        }
        touchLruIfStale(file)
        return file.readBytes()
    }

    /** Bump mtime at most every [LRU_TOUCH_MIN_GAP_MS] to cut fs metadata
     *  thrash when the same tiles are re-read during flings. */
    private fun touchLruIfStale(file: File, nowMs: Long = System.currentTimeMillis()) {
        val age = nowMs - file.lastModified()
        if (age >= LRU_TOUCH_MIN_GAP_MS) {
            file.setLastModified(nowMs)
        }
    }

    /**
     * Drop memory + disk GRID/HERO/LOGO slots for [romId] (and their source
     * stamps). Call after SET_ART / clear override so the next load
     * re-decodes the new effective URI instead of serving stale bytes.
     */
    fun invalidate(romId: String) {
        // Mem keys may be plain romId, romId.hero, or romId|src:… / .hero|src:…
        // Snapshot keys then remove — LruCache has no prefix API.
        runCatching {
            val doomed = memory.snapshot().keys.filter {
                it == romId ||
                    it == "$romId.hero" ||
                    it.startsWith("$romId|") ||
                    it.startsWith("$romId.hero|") ||
                    it == "$romId.logo" ||
                    it.startsWith("$romId.logo|")
            }
            doomed.forEach { memory.remove(it) }
        }
        val gridFile = fileFor(romId, ArtKind.GRID)
        val heroFile = fileFor(romId, ArtKind.HERO)
        val logoFile = fileFor(romId, ArtKind.LOGO)
        val gridStamp = sourceStampFile(romId, ArtKind.GRID)
        val heroStamp = sourceStampFile(romId, ArtKind.HERO)
        val logoStamp = sourceStampFile(romId, ArtKind.LOGO)
        gridFile.delete()
        heroFile.delete()
        logoFile.delete()
        gridStamp.delete()
        heroStamp.delete()
        logoStamp.delete()
        synchronized(diskIndexLock) {
            gridDiskIndex.remove(gridFile.name)
            heroDiskIndex.remove(heroFile.name)
            logoDiskIndex.remove(logoFile.name)
        }
        bumpArtGeneration(romId)
    }

    /** Delete all cached disk & memory art entries cleanly and reset disk index. */
    fun clear() {
        runCatching { memory.evictAll() }
        synchronized(evictLock) {
            val files = dir.listFiles()
            if (files != null) {
                for (f in files) f.delete()
            }
            approxDiskBytes.set(0L)
            synchronized(diskIndexLock) {
                gridDiskIndex.clear()
                heroDiskIndex.clear()
                logoDiskIndex.clear()
                diskIndexReady = true
            }
        }
    }

    /** Atomic write (kind-scoped unique tmp + rename), like RomLibrary/SettingsStore.
     *  Triggers disk-cap eviction afterwards so scrapes cannot grow the cache
     *  without bound. Clears any prior source stamp so a later URI-backed
     *  load cannot treat scrape bytes as override art. */
    fun writeDiskBytes(romId: String, bytes: ByteArray, kind: ArtKind = ArtKind.GRID) {
        dir.mkdirs()
        val tmp = tempFileFor(romId, kind)
        val target = fileFor(romId, kind)
        try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
        val stamp = sourceStampFile(romId, kind)
        stamp.delete()
        recordDiskPresent(kind, target.name)
        bumpArtGeneration(romId)
        // Scrape / non-URI writers own the bytes without a URI stamp.
        runCatching { memory.remove(romId) }
        if (kind == ArtKind.HERO) runCatching { memory.remove(romId + ".hero") }
        if (kind == ArtKind.LOGO) runCatching { memory.remove(romId + ".logo") }
        noteDiskWrite(bytes.size)
        evictIfOverCap()
    }

    // Deletes the least-recently-used cache files (lastModified order) until
    // the directory is back under DISK_CACHE_CAP_BYTES. The just-written
    // file has the newest timestamp, so a single oversized write survives.
    /** Approximate on-disk size; -1 means unknown (force a scan). */
    private val approxDiskBytes = AtomicLong(-1L)

    internal fun evictIfOverCap(capBytes: Long = DISK_CACHE_CAP_BYTES) {
        // Skip a full directory listing while we still believe we are under cap.
        val approx = approxDiskBytes.get()
        if (approx in 0L until capBytes) return
        synchronized(evictLock) {
            val recheck = approxDiskBytes.get()
            if (recheck in 0L until capBytes) return
            val files = dir.listFiles() ?: return
            val stats = ArrayList<FileStat>(files.size)
            var total = 0L
            for (file in files) {
                if (!file.isFile || file.name.endsWith(".tmp")) continue
                val len = file.length()
                val mtime = file.lastModified()
                stats.add(FileStat(file, mtime, len))
                total += len
            }
            if (total <= capBytes) {
                approxDiskBytes.set(total)
                return
            }
            val arr = stats.toTypedArray()
            Arrays.sort(arr) { a, b -> a.lastModified.compareTo(b.lastModified) }
            val deletedNames = ArrayList<String>()
            for (entry in arr) {
                if (total <= capBytes) break
                if (entry.file.delete()) {
                    deletedNames.add(entry.file.name)
                }
                total -= entry.length
            }
            if (deletedNames.isNotEmpty()) {
                synchronized(diskIndexLock) {
                    for (name in deletedNames) {
                        // Guard against a concurrent write recreating [name]
                        // between delete() above and acquiring diskIndexLock.
                        if (!File(dir, name).isFile) {
                            removeFileNameFromIndexLocked(name)
                        }
                    }
                }
            }
            approxDiskBytes.set(total.coerceAtLeast(0L))
        }
    }

    private fun noteDiskWrite(addedBytes: Int) {
        while (true) {
            val cur = approxDiskBytes.get()
            if (cur < 0L) return
            if (approxDiskBytes.compareAndSet(cur, cur + addedBytes.toLong())) return
        }
    }

    /**
     * Load art for [rom]: memory cache → disk cache → (GRID only) the
     * entry's local `artUri` (decoded downscaled to roughly [maxDimension]
     * px and written into the disk cache). HERO art has no local source, so
     * a HERO disk miss resolves to null. [onResult] always runs on the main
     * thread, synchronously on a memory hit, and receives null when no art
     * exists or the source cannot be read. Stale-result guarding is the
     * caller's job (ArtTile re-checks the overlay's tag before applying) —
     * and [isStillValid] re-runs on the decode thread BEFORE any I/O or
     * decode, so work queued behind a long carousel fling is dropped cheap
     * instead of decoding hundreds of stale cards first.
     */
    fun load(
        context: Context,
        rom: RomEntry,
        maxDimension: Int,
        kind: ArtKind = ArtKind.GRID,
        isStillValid: () -> Boolean = { true },
        // Optional artOverrides[rom.id] URI wins over rom.artUri for GRID.
        artOverrides: Map<String, String> = emptyMap(),
        onResult: (Bitmap?) -> Unit,
    ) {
        // HERO art has no local source, so a HERO disk miss resolves to null.
        // GRID: user override (Settings.artOverrides) then scanner artUri.
        val localUri = if (kind == ArtKind.GRID) {
            ArtOverride.effectiveArtUri(rom, artOverrides)
        } else {
            null
        }
        // Memory key includes the effective source so a SET_ART override
        // cannot hit a prior mem entry keyed only by rom.id.
        val memKey = memKeyFor(rom.id, kind, localUri)
        enqueue(
            context, memKey, rom.id, kind,
            uriString = localUri,
            maxDimension, isStillValid, onResult,
        )
    }

    /**
     * Same memory → disk → decode-downscaled pipeline as [load], but keyed by
     * an arbitrary string with an explicit source URI — used for per-app
     * custom icons ("app:<package>" keys), which are not ROM entries. The
     * decode reuses [decodeDownscaled] (bounds-then-sample via
     * [sampleSizeFor]) and the PNG disk cache.
     */
    fun loadUri(
        context: Context,
        key: String,
        uriString: String,
        maxDimension: Int,
        isStillValid: () -> Boolean = { true },
        onResult: (Bitmap?) -> Unit,
    ) = enqueue(context, key, key, ArtKind.GRID, uriString, maxDimension, isStillValid, onResult)

    /**
     * Warm memory/disk for [rom] without a UI callback. Used to prefetch
     * carousel neighbors during idle so flings hit cache more often.
     * Still honors [isStillValid] so work drops if the user scrolled away.
     */
    fun prefetch(
        context: Context,
        rom: RomEntry,
        maxDimension: Int,
        artOverrides: Map<String, String> = emptyMap(),
        isStillValid: () -> Boolean = { true },
    ) {
        val localUri = ArtOverride.effectiveArtUri(rom, artOverrides)
        runCatching {
            if (memory.get(memKeyFor(rom.id, ArtKind.GRID, localUri)) != null) return
        }
        load(
            context, rom, maxDimension,
            kind = ArtKind.GRID,
            isStillValid = isStillValid,
            artOverrides = artOverrides,
            onResult = { /* warm only */ },
        )
    }

    private fun enqueue(
        context: Context,
        memKey: String,
        diskKey: String,
        kind: ArtKind,
        uriString: String?,
        maxDimension: Int,
        isStillValid: () -> Boolean,
        onResult: (Bitmap?) -> Unit,
    ) {
        memory.get(memKey)?.let { onResult(it); return }
        if (!diskIndexReady) {
            scheduleDiskIndexSeed()
        } else if (uriString == null && isKnownDiskMiss(diskKey, kind)) {
            // O(1) RAM negative-miss fast path: no source URI and disk index
            // proves no cached art exists, so skip waking DECODE_EXECUTOR.
            onResult(null)
            return
        }
        // Coalesce concurrent loads (bind + ±2 prefetch for the same tile).
        var startWork = false
        val waiters = inFlight.compute(memKey) { _, existing ->
            if (existing == null) {
                startWork = true
                CopyOnWriteArrayList<(Bitmap?) -> Unit>().also { it.add(onResult) }
            } else {
                existing.add(onResult)
                existing
            }
        }!!
        if (!startWork) return

        val appContext = context.applicationContext
        DECODE_EXECUTOR.execute {
            // Sole waiter already scrolled away: skip disk decode too.
            // Coalesced bind+prefetch (waiters > 1) still warms memory.
            val onlyStale = waiters.size == 1 && !isStillValid()
            val diskHit = if (onlyStale || isKnownDiskMiss(diskKey, kind)) {
                null
            } else {
                decodeCached(diskKey, kind, expectedSourceUri = uriString)
            }
            val bitmap = diskHit ?: uriString?.let { uri ->
                if (onlyStale) return@let null
                decodeDownscaled(appContext, uri, maxDimension)?.also { bmp ->
                    // Skip disk write when the view already moved on — still
                    // put memory so a near-term rebind is free.
                    if (isStillValid()) {
                        runCatching {
                            val out = ByteArrayOutputStream()
                            bmp.compress(
                                cacheCompressFormat(Build.VERSION.SDK_INT),
                                CACHE_COMPRESS_QUALITY,
                                out,
                            )
                            writeDiskBytesFromUri(diskKey, out.toByteArray(), kind, uri)
                        }
                    }
                }
            }
            if (bitmap != null) memory.put(memKey, bitmap)
            val cbs = inFlight.remove(memKey) ?: emptyList()
            MAIN_HANDLER.post {
                cbs.forEach { it(bitmap) }
            }
        }
    }

    /** Disk hit only when bytes exist AND (no expected URI, or stamp matches). */
    private fun decodeCached(
        romId: String,
        kind: ArtKind = ArtKind.GRID,
        expectedSourceUri: String? = null,
    ): Bitmap? {
        if (isKnownDiskMiss(romId, kind)) return null
        val file = fileFor(romId, kind)
        if (!file.isFile) {
            recordDiskAbsent(kind, file.name)
            return null
        }
        // Stamp first: a SET_ART mismatch must not pay for a full read.
        // Scraped / no-URI loads have no expected source — skip the sidecar.
        if (expectedSourceUri != null) {
            val stampText = sourceStampFile(romId, kind).takeIf { it.isFile }?.readText()
            if (!sourceStampMatches(stampText, expectedSourceUri)) return null
        }
        touchLruIfStale(file)
        return decodeFile(file, sample = 1, rgb565 = kind == ArtKind.GRID)
    }

    /** Persist URI-sourced art and stamp the source so a later different
     *  override/scraper cannot silently reuse these bytes. */
    private fun writeDiskBytesFromUri(
        romId: String,
        bytes: ByteArray,
        kind: ArtKind,
        sourceUri: String,
    ) {
        dir.mkdirs()
        val tmp = tempFileFor(romId, kind)
        val target = fileFor(romId, kind)
        try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
        val stamp = sourceStampFile(romId, kind)
        stamp.writeText(sourceUri)
        recordDiskPresent(kind, target.name)
        noteDiskWrite(bytes.size)
        runCatching { memory.remove(romId) }
        if (kind == ArtKind.HERO) runCatching { memory.remove(romId + ".hero") }
        if (kind == ArtKind.LOGO) runCatching { memory.remove(romId + ".logo") }
        // Also drop any source-suffixed mem keys for this rom (best-effort).
        runCatching { memory.remove(memKeyFor(romId, kind, sourceUri)) }
        evictIfOverCap()
    }

    /** In-flight decode waiters keyed by memory key (coalesce bind+prefetch). */
    private val inFlight =
        ConcurrentHashMap<String, CopyOnWriteArrayList<(Bitmap?) -> Unit>>()

    /** Bumped on scrape write / SET_ART invalidate so same-ROM hero skips refresh. */
    private val artGenerations = ConcurrentHashMap<String, Int>()

    /** Write generation for [romId]; 0 when this process has not written it. */
    fun artGeneration(romId: String): Int = artGenerations[romId] ?: 0

    private fun bumpArtGeneration(romId: String) {
        artGenerations.merge(romId, 1, Int::plus)
    }

    private class FileStat(
        val file: File,
        val lastModified: Long,
        val length: Long,
    )

    companion object {
        internal const val MIN_MEMORY_BYTES = 16 * 1024 * 1024
        internal const val MAX_MEMORY_BYTES = 48 * 1024 * 1024

        /** Dynamic in-memory LRU budget: 1/8 of max heap, clamped to 16..48 MiB. Pure. */
        internal fun computeMemoryCacheBytes(maxHeapBytes: Long): Int =
            (maxHeapBytes / 8L)
                .coerceIn(MIN_MEMORY_BYTES.toLong(), MAX_MEMORY_BYTES.toLong())
                .toInt()

        /** Min age before a disk hit rewrites lastModified (ms). */
        internal const val LRU_TOUCH_MIN_GAP_MS = 60_000L

        /**
         * Disk encode: WEBP_LOSSY on API 30+ (smaller, faster than JPEG at
         * the same quality); JPEG below that. BitmapFactory sniffs either
         * inside the existing `.png` filenames.
         */
        internal const val WEBP_MIN_SDK = 30
        private const val CACHE_COMPRESS_QUALITY = 85
        private const val REUSABLE_CAP = 12

        private val reusable = ArrayDeque<Bitmap>()
        private val displayCounts = ConcurrentHashMap<Bitmap, Int>()

        /** True when new disk writes should be WEBP_LOSSY. Pure. */
        internal fun usesWebpDiskCache(sdkInt: Int): Boolean = sdkInt >= WEBP_MIN_SDK

        internal fun cacheCompressFormat(sdkInt: Int): Bitmap.CompressFormat =
            if (usesWebpDiskCache(sdkInt)) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.JPEG
            }

        /** Packed size for an [inBitmap] candidate. Pure. */
        internal fun pixelByteCount(width: Int, height: Int, rgb565: Boolean): Int {
            if (width <= 0 || height <= 0) return 0
            return width * height * if (rgb565) 2 else 4
        }

        /**
         * Whether a pooled bitmap may back a decode. Displayed bitmaps are
         * rejected so inBitmap cannot overwrite pixels an ImageView still
         * shows. Pure — pinned by host tests.
         */
        internal fun canReuseInBitmap(
            candidateBytes: Int,
            candidateConfigName: String?,
            candidateRecycled: Boolean,
            displayCount: Int,
            neededBytes: Int,
            neededConfigName: String,
        ): Boolean {
            if (candidateRecycled || displayCount > 0) return false
            if (candidateConfigName != neededConfigName) return false
            if (neededBytes <= 0 || candidateBytes < neededBytes) return false
            return true
        }

        /** ImageView is showing [bitmap] — do not reuse it as inBitmap. */
        fun acquireDisplay(bitmap: Bitmap) {
            displayCounts.merge(bitmap, 1, Int::plus)
        }

        /** ImageView dropped [bitmap] (rebind / clear). */
        fun releaseDisplay(bitmap: Bitmap) {
            displayCounts.compute(bitmap) { _, n ->
                val next = (n ?: 1) - 1
                if (next <= 0) null else next
            }
        }

        /** Drop the display ref on [image]'s current [BitmapDrawable], if any. */
        fun dropDisplayed(image: android.widget.ImageView) {
            val bmp = (image.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                ?: return
            releaseDisplay(bmp)
        }

        /** Point [image] at [bitmap] and mark it displayed. */
        fun showDisplayed(image: android.widget.ImageView, bitmap: Bitmap) {
            dropDisplayed(image)
            acquireDisplay(bitmap)
            image.setImageBitmap(bitmap)
        }

        internal fun offerReusable(bitmap: Bitmap) {
            if (!bitmap.isMutable || bitmap.isRecycled) return
            if ((displayCounts[bitmap] ?: 0) > 0) return
            synchronized(reusable) {
                if (reusable.size >= REUSABLE_CAP) return
                reusable.addLast(bitmap)
            }
        }

        internal fun takeReusable(neededBytes: Int, config: Bitmap.Config): Bitmap? {
            val want = config.name
            synchronized(reusable) {
                val it = reusable.iterator()
                while (it.hasNext()) {
                    val candidate = it.next()
                    if (candidate.isRecycled) {
                        it.remove()
                        continue
                    }
                    val ok = canReuseInBitmap(
                        candidateBytes = candidate.byteCount,
                        candidateConfigName = candidate.config.name,
                        candidateRecycled = false,
                        displayCount = displayCounts[candidate] ?: 0,
                        neededBytes = neededBytes,
                        neededConfigName = want,
                    )
                    if (!ok) {
                        if (candidate.isRecycled || (displayCounts[candidate] ?: 0) > 0) {
                            it.remove()
                        }
                        continue
                    }
                    it.remove()
                    return candidate
                }
            }
            return null
        }

        private fun decodeBytes(bytes: ByteArray, sample: Int, rgb565: Boolean): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val step = sample.coerceAtLeast(1)
            val opts = decodeOptions(bounds.outWidth, bounds.outHeight, step, rgb565)
            val hit = runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            }.getOrNull()
            if (hit != null) return hit
            opts.inBitmap = null
            return runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            }.getOrNull()
        }

        /** Disk-cache decode without a heap copy of the encoded bytes. */
        private fun decodeFile(file: File, sample: Int, rgb565: Boolean): Bitmap? {
            val path = file.path
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val step = sample.coerceAtLeast(1)
            val opts = decodeOptions(bounds.outWidth, bounds.outHeight, step, rgb565)
            val hit = runCatching { BitmapFactory.decodeFile(path, opts) }.getOrNull()
            if (hit != null) return hit
            opts.inBitmap = null
            return runCatching { BitmapFactory.decodeFile(path, opts) }.getOrNull()
        }

        private fun decodeOptions(
            srcW: Int,
            srcH: Int,
            sample: Int,
            rgb565: Boolean,
        ): BitmapFactory.Options {
            val w = (srcW / sample).coerceAtLeast(1)
            val h = (srcH / sample).coerceAtLeast(1)
            val config = if (rgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            return BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = config
                inMutable = true
                inBitmap = takeReusable(pixelByteCount(w, h, rgb565), config)
            }
        }

        // Lazy: host unit tests load ArtCache without a Looper.
        private val MAIN_HANDLER by lazy { Handler(Looper.getMainLooper()) }

        /** Disk cache cap; writes evict least-recently-used files
         *  (lastModified) once the directory exceeds this. */
        const val DISK_CACHE_CAP_BYTES = 256L * 1024 * 1024

        /**
         * Memory key for a load: plain rom id (or `.hero`/`.logo`) when there
         * is no local source URI; otherwise rom id + raw source URI so SET_ART
         * cannot hit a prior entry for a different source — with zero SHA-256
         * hashing on the UI thread and zero [keyCache] pollution.
         */
        internal fun memKeyFor(romId: String, kind: ArtKind, sourceUri: String?): String {
            val base = when (kind) {
                ArtKind.HERO -> "$romId.hero"
                ArtKind.LOGO -> "$romId.logo"
                ArtKind.GRID -> romId
            }
            if (sourceUri.isNullOrBlank()) return base
            return "$base|src:$sourceUri"
        }

        /**
         * Whether a disk entry with [stampText] may satisfy a load that wants
         * [expectedSourceUri]. Null expected = any stamp ok (scrape / no-URI
         * path). Non-null expected requires an exact stamp match so a prior
         * cache from a different override/scanner URI cannot win.
         */
        internal fun sourceStampMatches(stampText: String?, expectedSourceUri: String?): Boolean {
            if (expectedSourceUri == null) return true
            return stampText != null && stampText == expectedSourceUri
        }

        /**
         * Pure eviction selection: snapshots `(file, lastModified, length)`
         * ONCE per file before sorting so TimSort never issues O(N log N)
         * `stat()` syscalls or races against concurrent `touchLruIfStale()`.
         */
        internal fun evictionCandidates(files: List<File>, capBytes: Long): List<File> {
            if (files.isEmpty()) return emptyList()
            val stats = ArrayList<FileStat>(files.size)
            var total = 0L
            for (file in files) {
                if (!file.isFile) continue
                val len = file.length()
                val mtime = file.lastModified()
                stats.add(FileStat(file, mtime, len))
                total += len
            }
            if (total <= capBytes || stats.isEmpty()) return emptyList()
            val arr = stats.toTypedArray()
            Arrays.sort(arr) { a, b -> a.lastModified.compareTo(b.lastModified) }
            val evict = ArrayList<File>()
            for (entry in arr) {
                if (total <= capBytes) break
                evict.add(entry.file)
                total -= entry.length
            }
            return evict
        }

        /** Scrape cache targets: grid tiles ~512px max dimension, heroes
         *  ~1600px wide. Scraped art is downscaled to these before it ever
         *  touches the disk cache, so [decodeCached] never sees multi-MB
         *  CDN originals. */
        const val GRID_SCRAPE_TARGET_PX = 512
        const val HERO_SCRAPE_TARGET_WIDTH_PX = 1600

        // Bounded decode pool: carousel flings queue many tiles; 1 thread
        // serializes multi-second backlogs. isStillValid still drops stale
        // work before I/O. Cap=3: dual-display + prefetch without flooding
        // GC on Sugar. Separate from RomLibrary's SCAN_EXECUTOR.
        private val DECODE_EXECUTOR = Executors.newFixedThreadPool(3)

        private val KEY_HEX = "0123456789abcdef".toCharArray()
        internal const val KEY_CACHE_MAX_ENTRIES: Int = 4096
        private val keyCache = ConcurrentHashMap<String, String>(256)

        internal fun keyCacheSize(): Int = keyCache.size

        internal fun clearKeyCacheForTest() {
            keyCache.clear()
        }

        /**
         * Stable cache key for a RomEntry id: SHA-256 hex (ids contain
         * ':'/'/', unusable in a filename). Pure — pinned by host tests.
         * Results are memoized in a strictly bounded cache so 20k ROM cards
         * cannot grow [keyCache] unboundedly.
         */
        fun keyFor(romId: String): String {
            keyCache[romId]?.let { return it }
            val hashed = sha256Hex(romId)
            if (keyCache.size >= KEY_CACHE_MAX_ENTRIES) {
                keyCache.clear()
            }
            keyCache[romId] = hashed
            return hashed
        }

        private val SHA256_LOCAL = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }

        internal fun sha256Hex(value: String): String {
            val md = SHA256_LOCAL.get()!!
            md.reset()
            val digest = md.digest(value.toByteArray(Charsets.UTF_8))
            val out = CharArray(digest.size * 2)
            var i = 0
            for (b in digest) {
                val v = b.toInt() and 0xFF
                out[i++] = KEY_HEX[v ushr 4]
                out[i++] = KEY_HEX[v and 0x0F]
            }
            return String(out)
        }

        /**
         * inSampleSize for a bounds-then-sample decode: the largest power of
         * two that keeps BOTH dimensions at or above [maxDimension] after
         * sampling (never upscales, never drops below target). Pure — pinned
         * by host tests.
         */
        internal fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
            var sample = 1
            while (width / (sample * 2) >= maxDimension &&
                height / (sample * 2) >= maxDimension
            ) {
                sample *= 2
            }
            return sample
        }

        /**
         * Bounds-then-sample downscale of encoded image [bytes], re-encoded
         * as WEBP_LOSSY (API 30+) or JPEG at the cache target for [kind]
         * (grid: ~[GRID_SCRAPE_TARGET_PX] px max dimension; hero:
         * ~[HERO_SCRAPE_TARGET_WIDTH_PX] px wide — heroes are wide and
         * short, so the sample tracks width alone).
         * Null when [bytes] is not a decodable image. Android-only
         * (BitmapFactory); host tests inject a fake through the caller's
         * seam ([SgdbScraper]'s `shrink`).
         */
        internal fun downscaledPngBytes(bytes: ByteArray, kind: ArtKind): ByteArray? =
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                val sample = if (kind == ArtKind.HERO) {
                    // Width-only sampling: pass the width as both dimensions.
                    sampleSizeFor(
                        bounds.outWidth,
                        bounds.outWidth,
                        HERO_SCRAPE_TARGET_WIDTH_PX,
                    )
                } else {
                    sampleSizeFor(bounds.outWidth, bounds.outHeight, GRID_SCRAPE_TARGET_PX)
                }
                val rgb565 = kind == ArtKind.GRID
                val bmp = decodeBytes(bytes, sample, rgb565) ?: return null
                val out = ByteArrayOutputStream()
                bmp.compress(
                    cacheCompressFormat(Build.VERSION.SDK_INT),
                    CACHE_COMPRESS_QUALITY,
                    out,
                )
                // Scrape shrink never displays this bitmap — return the
                // allocation to the inBitmap pool.
                offerReusable(bmp)
                out.toByteArray()
            }.getOrNull()

        /**
         * Bounds-then-sample decode down to roughly [maxDimension] px; null
         * on any failure (revoked grant, deleted file, non-image). Runs on
         * DECODE_EXECUTOR, never the UI thread.
         */
        internal fun decodeDownscaled(
            context: Context,
            uriString: String,
            maxDimension: Int,
        ): Bitmap? = runCatching {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
            val opts = decodeOptions(bounds.outWidth, bounds.outHeight, sample, rgb565 = true)
            val hit = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            if (hit != null) return@runCatching hit
            opts.inBitmap = null
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull()
    }
}
