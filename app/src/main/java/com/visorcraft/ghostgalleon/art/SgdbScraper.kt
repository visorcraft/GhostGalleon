package com.visorcraft.ghostgalleon.art

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.visorcraft.ghostgalleon.rom.RomEntry
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * SteamGridDB gap-filler. For every library entry with no
 * local artUri and no cached art: search SGDB by the cleaned ROM name,
 * take the first result, download its first grid and hero image into
 * ArtCache (grid = tile art, hero keyed separately for the hero panel).
 * Entries whose grid slot is already cached but whose HERO slot is empty
 * are still scraped — a hero-only backfill that skips the grid download.
 *
 * Work is **heroes-first** ([SgdbQueue.prioritize]) and may use up to
 * [SgdbQueue.MAX_WORKERS] parallel workers, each still polite (~200ms
 * between HTTP calls). Cancel is cooperative between ROMs/requests.
 *
 * Downloaded CDN bytes are downscaled and re-encoded as PNG at the cache
 * target ([ArtCache.downscaledPngBytes]: grid ~512px max dimension, hero
 * ~1600px wide) BEFORE the disk write. Undecodable bytes count as a failure
 * and are never cached. Host-tested via [SgdbTransport] / `shrink` seams.
 */
object Sgdb {
    const val BASE = "https://www.steamgriddb.com/api/v2"

    private val BRACKET = Regex("""\[[^\]]*\]""")
    private val PAREN = Regex("""\([^)]*\)""")

    /**
     * Search-name normalization: strip region/tag parentheticals and
     * square-bracket tags ("(USA)", version/title-id tags), collapse
     * whitespace. "Super Mario World (USA) (!)" → "Super Mario World".
     */
    fun normalizeName(stem: String): String {
        var s = BRACKET.replace(stem, " ")
        s = PAREN.replace(s, " ")
        return s.trim().split(Regex("""\s+""")).joinToString(" ")
    }

    fun searchUrl(name: String): String =
        "$BASE/search/autocomplete/" +
            URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    fun gridsUrl(gameId: Long): String = "$BASE/grids/game/$gameId"

    fun heroesUrl(gameId: Long): String = "$BASE/heroes/game/$gameId"

    fun logosUrl(gameId: Long): String = "$BASE/logos/game/$gameId"

    /** Up to [limit] image URLs from a grids/heroes/logos response. */
    fun parseImageUrls(json: String, limit: Int = 6): List<String> = runCatching {
        val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
        buildList {
            for (i in 0 until minOf(data.length(), limit.coerceAtLeast(0))) {
                val url = data.getJSONObject(i).optString("url")
                if (url.isNotBlank()) add(url)
            }
        }
    }.getOrDefault(emptyList())

    /** First game id of a search/autocomplete response, else null. */
    fun parseSearchFirstId(json: String): Long? = runCatching {
        val data = JSONObject(json).optJSONArray("data")
        if (data == null || data.length() == 0) null else data.getJSONObject(0).getLong("id")
    }.getOrNull()

    /** First image URL of a grids/heroes response, else null. */
    fun parseFirstImageUrl(json: String): String? = runCatching {
        val data = JSONObject(json).optJSONArray("data")
        if (data == null || data.length() == 0) null else data.getJSONObject(0).getString("url")
    }.getOrNull()
}

/** HTTP seam so host tests can drive the scraper with canned responses. */
interface SgdbTransport {
    /** Authenticated API GET; body on HTTP 200, null on any failure. */
    fun get(url: String, apiKey: String): String?

    /** Plain GET for image bytes (SGDB CDN needs no auth). */
    fun download(url: String): ByteArray?
}

/**
 * Run/cancel surface of one batch scrape, so the app-scoped [ScrapeJob]
 * can be host-tested with a fake runner. Implemented by [SgdbScraper].
 */
interface ScrapeRunner {
    val isRunning: Boolean

    /** Cooperative cancel: takes effect between requests/ROMs. */
    fun cancel()

    /**
     * Start the batch job; false when a job is already running. Progress
     * (done, total) and the final [SgdbScraper.Summary] are delivered on
     * the main thread.
     */
    fun start(
        apiKey: String,
        entries: List<RomEntry>,
        onProgress: (done: Int, total: Int) -> Unit,
        onDone: (SgdbScraper.Summary) -> Unit,
    ): Boolean
}

/** HttpURLConnection transport; logs and swallows every failure. */
class HttpSgdbTransport : SgdbTransport {

    override fun get(url: String, apiKey: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            Log.w(TAG, "GET $url -> HTTP $code")
            return null
        }
        conn.inputStream.bufferedReader().use { it.readText() }
    }.onFailure { Log.w(TAG, "GET $url failed", it) }.getOrNull()

    override fun download(url: String): ByteArray? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            Log.w(TAG, "GET $url -> HTTP $code")
            return null
        }
        conn.inputStream.use { it.readBytes() }
    }.onFailure { Log.w(TAG, "GET $url failed", it) }.getOrNull()

    private companion object {
        const val TAG = "SgdbScraper"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
    }
}

class SgdbScraper(
    private val cache: ArtCache,
    private val transport: SgdbTransport,
    private val delayMs: Long = DELAY_MS,
    // Downscale+re-encode seam: BitmapFactory is Android-only, so host
    // tests inject an identity/failing fake. Default downscales to the
    // ArtCache target for the kind before the bytes reach disk.
    private val shrink: (bytes: ByteArray, kind: ArtCache.ArtKind) -> ByteArray? =
        { bytes, kind -> ArtCache.downscaledPngBytes(bytes, kind) },
    private val skipMiss: (RomEntry) -> Boolean = { false },
    private val onMiss: (romId: String, query: String) -> Unit = { _, _ -> },
) : ScrapeRunner {

    data class Summary(
        val downloaded: Int,
        val skipped: Int,
        val failed: Int,
        val cancelled: Boolean,
    )

    @Volatile
    private var cancelled = false
    private val running = AtomicBoolean(false)

    override val isRunning: Boolean get() = running.get()

    /** Cooperative cancel: takes effect between requests/ROMs. */
    override fun cancel() {
        cancelled = true
    }

    /**
     * Start the batch job; false when a job is already running. Progress
     * (done, total) and the final [Summary] are delivered on the main
     * thread.
     */
    override fun start(
        apiKey: String,
        entries: List<RomEntry>,
        onProgress: (done: Int, total: Int) -> Unit,
        onDone: (Summary) -> Unit,
    ): Boolean {
        if (!running.compareAndSet(false, true)) return false
        cancelled = false
        val handler = Handler(Looper.getMainLooper())
        EXECUTOR.execute {
            val summary = runBlocking(
                apiKey,
                entries,
                sleep = { Thread.sleep(it) },
                onProgress = { done, total -> handler.post { onProgress(done, total) } },
                parallelWorkers = SgdbQueue.MAX_WORKERS,
            )
            running.set(false)
            handler.post { onDone(summary) }
        }
        return true
    }

    /**
     * The whole job, synchronously, over injected seams — host-tested.
     * Heroes-first ordering via [SgdbQueue]; optional bounded parallelism
     * ([parallelWorkers], default [SgdbQueue.MAX_WORKERS]).
     */
    internal fun runBlocking(
        apiKey: String,
        entries: List<RomEntry>,
        sleep: (Long) -> Unit,
        onProgress: (done: Int, total: Int) -> Unit,
        /** Production uses [SgdbQueue.MAX_WORKERS]; host tests default to 1. */
        parallelWorkers: Int = 1,
    ): Summary {
        val queue = SgdbQueue.prioritize(
            entries,
            hasGrid = { id -> cache.diskHas(id) },
            hasHero = { id -> cache.diskHas(id, ArtCache.ArtKind.HERO) },
            skipMiss = skipMiss,
        )
        val skipped = entries.size - queue.size
        if (queue.isEmpty()) {
            onProgress(0, 0)
            return Summary(0, skipped, 0, cancelled = false)
        }
        val workers = SgdbQueue.workerCount(queue.size, parallelWorkers.coerceAtLeast(1))
        val downloaded = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val doneCount = AtomicInteger(0)
        val total = queue.size
        if (workers <= 1) {
            for (need in queue) {
                if (cancelled) {
                    return Summary(downloaded.get(), skipped, failed.get(), cancelled = true)
                }
                if (scrapeOne(apiKey, need, sleep)) downloaded.incrementAndGet()
                else failed.incrementAndGet()
                onProgress(doneCount.incrementAndGet(), total)
            }
            return Summary(downloaded.get(), skipped, failed.get(), cancelled = false)
        }
        // Bounded parallel: each worker pulls the next Need under a lock.
        val lock = Any()
        var nextIndex = 0
        fun takeNext(): SgdbQueue.Need? = synchronized(lock) {
            if (nextIndex >= queue.size) null
            else queue[nextIndex++]
        }
        val pool = Executors.newFixedThreadPool(workers)
        val latch = java.util.concurrent.CountDownLatch(workers)
        repeat(workers) {
            pool.execute {
                try {
                    while (!cancelled) {
                        val need = takeNext() ?: break
                        if (scrapeOne(apiKey, need, sleep)) downloaded.incrementAndGet()
                        else failed.incrementAndGet()
                        onProgress(doneCount.incrementAndGet(), total)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
        pool.shutdownNow()
        return Summary(
            downloaded.get(),
            skipped,
            failed.get(),
            cancelled = cancelled,
        )
    }

    /** One ROM need: search → grid (if needed) + hero (if needed). Never throws. */
    private fun scrapeOne(
        apiKey: String,
        need: SgdbQueue.Need,
        sleep: (Long) -> Unit,
    ): Boolean {
        val rom = need.entry
        return runCatching {
            val query = Sgdb.normalizeName(rom.name)
            if (query.isEmpty()) return false
            val searchJson = request(sleep) { transport.get(Sgdb.searchUrl(query), apiKey) }
            if (searchJson == null) {
                onMiss(rom.id, query)
                return false
            }
            if (cancelled) return false
            val gameId = Sgdb.parseSearchFirstId(searchJson)
            if (gameId == null) {
                onMiss(rom.id, query)
                return false
            }

            var stored = false
            if (need.needGrid) {
                val gridUrl = request(sleep) { transport.get(Sgdb.gridsUrl(gameId), apiKey) }
                    ?.let(Sgdb::parseFirstImageUrl)
                if (cancelled) return false
                if (gridUrl != null) {
                    request(sleep) { transport.download(gridUrl) }?.let { raw ->
                        shrink(raw, ArtCache.ArtKind.GRID)?.let { png ->
                            cache.writeDiskBytes(rom.id, png)
                            stored = true
                        }
                    }
                }
            } else {
                // Hero-only backfill still counts as success if hero lands.
                stored = cache.diskHas(rom.id) || rom.artUri != null
            }
            if (cancelled) return false

            if (need.needHero) {
                val heroUrl = request(sleep) { transport.get(Sgdb.heroesUrl(gameId), apiKey) }
                    ?.let(Sgdb::parseFirstImageUrl)
                if (cancelled) return false
                if (heroUrl != null) {
                    request(sleep) { transport.download(heroUrl) }?.let { raw ->
                        shrink(raw, ArtCache.ArtKind.HERO)?.let { png ->
                            cache.writeDiskBytes(rom.id, png, ArtCache.ArtKind.HERO)
                            stored = true
                        }
                    }
                }
            }
            if (cancelled) return false
            if (!cache.diskHas(rom.id, ArtCache.ArtKind.LOGO) && rom.logoUri == null) {
                val logoUrl = request(sleep) { transport.get(Sgdb.logosUrl(gameId), apiKey) }
                    ?.let(Sgdb::parseFirstImageUrl)
                if (logoUrl != null) {
                    request(sleep) { transport.download(logoUrl) }?.let { raw ->
                        shrink(raw, ArtCache.ArtKind.GRID)?.let { png ->
                            cache.writeDiskBytes(rom.id, png, ArtCache.ArtKind.LOGO)
                            stored = true
                        }
                    }
                }
            }
            stored
        }.getOrDefault(false)
    }

    /** One HTTP call followed by the polite delay. */
    private inline fun <T> request(sleep: (Long) -> Unit, call: () -> T): T {
        val result = call()
        sleep(delayMs)
        return result
    }

    companion object {
        const val DELAY_MS = 200L

        // Coordinator thread only — workers for parallel scrape are ephemeral
        // pools inside runBlocking (never RomLibrary's SCAN_EXECUTOR).
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
