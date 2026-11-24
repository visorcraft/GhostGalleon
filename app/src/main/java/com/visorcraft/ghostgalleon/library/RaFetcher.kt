package com.visorcraft.ghostgalleon.library

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Thin RetroAchievements HTTP fetch. Failures return null / empty (never throw).
 * Host tests use [fetchUrl] seam with canned responses.
 *
 * Title resolve uses the official game list for a console id (`i=`), then
 * client-side title match, caching the parsed per-console game index by
 * `(apiKeyHash, consoleId)` so browsing multiple ROMs on the same console
 * downloads & parses the console catalog once instead of per ROM.
 * (`f=` is "games with achievements only", not a title filter — do not encode titles into `f`.)
 */
object RaFetcher {

    private const val MAX_CACHED_CONSOLES: Int = 32
    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    private data class ConsoleCacheKey(
        val apiKeyHash: String,
        val consoleId: Int,
    )

    private class ConsoleGameIndex(
        val exactByLowerTitle: HashMap<String, Int>,
        val entries: List<Pair<String, Int>>,
    ) {
        fun match(titleHint: String): Int? {
            val needle = titleHint.trim().lowercase(Locale.ROOT)
            if (needle.isEmpty()) return null
            exactByLowerTitle[needle]?.let { return it }
            for (i in entries.indices) {
                val pair = entries[i]
                if (pair.first.contains(needle)) return pair.second
            }
            return null
        }
    }

    private val consoleCacheLock = Any()
    private val consoleIndexCache = ConcurrentHashMap<ConsoleCacheKey, ConsoleGameIndex>()

    /** Clears all cached RetroAchievements console game-list indexes. */
    fun clearCache() {
        synchronized(consoleCacheLock) {
            consoleIndexCache.clear()
        }
    }

    internal fun cacheSize(): Int = consoleIndexCache.size

    /**
     * Progress for a known game id.
     * GET API_GetGameInfoAndUserProgress.php?z=user&y=key&g=gameId
     */
    fun progressUrl(
        username: String,
        apiKey: String,
        gameId: Int,
    ): String {
        val u = URLEncoder.encode(username.trim(), "UTF-8")
        val k = URLEncoder.encode(apiKey.trim(), "UTF-8")
        return "https://retroachievements.org/API/API_GetGameInfoAndUserProgress.php?z=$u&y=$k&g=$gameId"
    }

    /**
     * Official game list for a console. [consoleId] is the RA system id
     * (e.g. 3 = SNES). `f=1` limits to games that have achievements.
     */
    fun gameListUrl(apiKey: String, consoleId: Int): String {
        val k = URLEncoder.encode(apiKey.trim(), "UTF-8")
        return "https://retroachievements.org/API/API_GetGameList.php?y=$k&i=$consoleId&f=1"
    }

    /**
     * Map Ghost Galleon [platformId] to a RetroAchievements console id.
     * Null when the platform has no reliable mapping.
     */
    fun consoleIdForPlatform(platformId: String?): Int? = when (platformId?.lowercase()) {
        "nes" -> 7
        "snes" -> 3
        "n64" -> 2
        "gb" -> 4
        "gbc" -> 6
        "gba" -> 5
        "nds" -> 18
        "genesis", "megadrive" -> 1
        "ps1" -> 12
        "psp" -> 41
        "pcengine", "tg16" -> 8
        "dreamcast" -> 40
        "arcade" -> 27
        "saturn" -> 39
        "3ds" -> 62
        "gamecube", "gc" -> 16
        "wii" -> 19
        "wiiu" -> 20
        "ps2" -> 21
        "switch" -> 53
        "psvita", "vita" -> 46
        else -> null
    }

    /**
     * Raw [progressUrl] body, or null on blank credentials / resolve / HTTP
     * miss. [fetchUrl] injects HTTP for host tests. When [gameId] is null,
     * resolves via console game list + title match using [platformId] /
     * [titleHint].
     */
    fun fetchProgressJson(
        username: String,
        apiKey: String,
        gameId: Int?,
        titleHint: String?,
        platformId: String? = null,
        fetchUrl: (String) -> String? = ::httpGet,
    ): String? {
        if (username.isBlank() || apiKey.isBlank()) return null
        val id = gameId
            ?: resolveGameId(apiKey, titleHint, platformId, fetchUrl)
            ?: return null
        return fetchUrl(progressUrl(username, apiKey, id))
    }

    /**
     * Fetch and parse progress. [fetchUrl] injects HTTP for host tests.
     * When [gameId] is null, resolves via console game list + title match
     * using [platformId] / [titleHint].
     */
    fun fetchProgress(
        username: String,
        apiKey: String,
        gameId: Int?,
        titleHint: String?,
        platformId: String? = null,
        fetchUrl: (String) -> String? = ::httpGet,
    ): RaProgress {
        val body = fetchProgressJson(
            username, apiKey, gameId, titleHint, platformId, fetchUrl,
        ) ?: return RaProgress()
        return RetroAchievements.parseProgress(body)
    }

    /**
     * Pick the first game whose Title contains [titleHint] (case-insensitive).
     * Prefer exact (ignore case) matches over substring.
     */
    fun parseGameIdMatchingTitle(json: String?, titleHint: String?): Int? {
        if (json.isNullOrBlank() || titleHint.isNullOrBlank()) return null
        val needle = titleHint.trim().lowercase()
        if (needle.isEmpty()) return null
        return try {
            val arr = org.json.JSONArray(json.trim().let {
                if (it.startsWith("[")) it else "[]"
            })
            var substringId: Int? = null
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val title = o.optString("Title", o.optString("title", "")).trim()
                if (title.isEmpty()) continue
                val id = when {
                    o.has("ID") -> o.optInt("ID")
                    o.has("id") -> o.optInt("id")
                    else -> 0
                }.takeIf { it > 0 } ?: continue
                val lower = title.lowercase()
                if (lower == needle) return id
                if (substringId == null && lower.contains(needle)) substringId = id
            }
            substringId
        } catch (_: Exception) {
            null
        }
    }

    /** @deprecated Prefer [parseGameIdMatchingTitle]; kept for older tests. */
    fun parseFirstGameId(json: String?): Int? {
        if (json.isNullOrBlank()) return null
        return try {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                val arr = org.json.JSONArray(trimmed)
                if (arr.length() == 0) return null
                val o = arr.optJSONObject(0) ?: return null
                when {
                    o.has("ID") -> o.optInt("ID").takeIf { it > 0 }
                    o.has("id") -> o.optInt("id").takeIf { it > 0 }
                    else -> null
                }
            } else {
                val o = org.json.JSONObject(trimmed)
                o.optInt("ID", o.optInt("id", 0)).takeIf { it > 0 }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveGameId(
        apiKey: String,
        titleHint: String?,
        platformId: String?,
        fetchUrl: (String) -> String?,
    ): Int? {
        val title = titleHint?.trim().orEmpty()
        if (title.isEmpty()) return null
        val consoleId = consoleIdForPlatform(platformId) ?: return null
        val key = ConsoleCacheKey(sha256Hex(apiKey.trim()), consoleId)
        val cached = consoleIndexCache[key]
        if (cached != null) return cached.match(title)

        val body = fetchUrl(gameListUrl(apiKey, consoleId)) ?: return null
        val parsed = parseConsoleGameIndex(body) ?: return null
        synchronized(consoleCacheLock) {
            if (consoleIndexCache.size >= MAX_CACHED_CONSOLES) {
                consoleIndexCache.clear()
            }
            consoleIndexCache[key] = parsed
        }
        return parsed.match(title)
    }

    private fun parseConsoleGameIndex(json: String?): ConsoleGameIndex? {
        if (json.isNullOrBlank()) return null
        val trimmed = json.trim()
        if (!trimmed.startsWith("[")) return null
        return try {
            val arr = org.json.JSONArray(trimmed)
            val count = arr.length()
            val exact = HashMap<String, Int>((count * 4) / 3 + 16)
            val list = ArrayList<Pair<String, Int>>(count)
            for (i in 0 until count) {
                val o = arr.optJSONObject(i) ?: continue
                val title = o.optString("Title", o.optString("title", "")).trim()
                if (title.isEmpty()) continue
                val id = when {
                    o.has("ID") -> o.optInt("ID")
                    o.has("id") -> o.optInt("id")
                    else -> 0
                }.takeIf { it > 0 } ?: continue
                val lower = title.lowercase(Locale.ROOT)
                exact.putIfAbsent(lower, id)
                list.add(lower to id)
            }
            ConsoleGameIndex(exactByLowerTitle = exact, entries = list)
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        val out = CharArray(digest.size * 2)
        var i = 0
        for (b in digest) {
            val v = b.toInt() and 0xFF
            out[i++] = HEX_CHARS[v ushr 4]
            out[i++] = HEX_CHARS[v and 0x0F]
        }
        return String(out)
    }

    private fun httpGet(url: String): String? {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
            try {
                if (conn.responseCode !in 200..299) return null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }
}
