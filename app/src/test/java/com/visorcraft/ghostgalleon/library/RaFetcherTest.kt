package com.visorcraft.ghostgalleon.library

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RaFetcherTest {

    @Before
    fun setUp() {
        RaFetcher.clearCache()
    }

    @After
    fun tearDown() {
        RaFetcher.clearCache()
    }

    @Test
    fun `progressUrl encodes credentials and game id`() {
        val url = RaFetcher.progressUrl("user name", "key+1", 1234)
        assertTrue(url.contains("g=1234"))
        assertTrue(url.contains("z="))
        assertTrue(url.contains("y="))
        assertTrue(url.startsWith("https://retroachievements.org/API/"))
    }

    @Test
    fun `gameListUrl uses console id i and f=1 achievements flag`() {
        val url = RaFetcher.gameListUrl("key", 3)
        assertTrue(url.contains("i=3"))
        assertTrue(url.contains("f=1"))
        assertFalse("title must not be stuffed into f=", url.contains("f=Super"))
        assertTrue(url.contains("API_GetGameList.php"))
    }

    @Test
    fun `consoleIdForPlatform maps common systems`() {
        assertEquals(3, RaFetcher.consoleIdForPlatform("snes"))
        assertEquals(5, RaFetcher.consoleIdForPlatform("gba"))
        assertEquals(62, RaFetcher.consoleIdForPlatform("3ds"))
        assertEquals(16, RaFetcher.consoleIdForPlatform("gamecube"))
        assertEquals(21, RaFetcher.consoleIdForPlatform("ps2"))
        assertEquals(19, RaFetcher.consoleIdForPlatform("wii"))
        assertEquals(20, RaFetcher.consoleIdForPlatform("wiiu"))
        assertEquals(53, RaFetcher.consoleIdForPlatform("switch"))
        assertEquals(46, RaFetcher.consoleIdForPlatform("psvita"))
        assertNull(RaFetcher.consoleIdForPlatform("windows"))
        assertNull(RaFetcher.consoleIdForPlatform("unknown-plat"))
    }

    @Test
    fun `parseGameIdMatchingTitle prefers exact then substring`() {
        val json = """
            [
              {"ID":1,"Title":"Super Demo World"},
              {"ID":2,"Title":"Demo"},
              {"ID":3,"Title":"Other"}
            ]
        """.trimIndent()
        assertEquals(2, RaFetcher.parseGameIdMatchingTitle(json, "Demo"))
        assertEquals(1, RaFetcher.parseGameIdMatchingTitle(json, "Super Demo"))
        assertNull(RaFetcher.parseGameIdMatchingTitle(json, "Missing"))
        assertNull(RaFetcher.parseGameIdMatchingTitle(null, "Demo"))
    }

    @Test
    fun `parseFirstGameId from array and object`() {
        assertEquals(42, RaFetcher.parseFirstGameId("""[{"ID":42,"Title":"T"}]"""))
        assertEquals(7, RaFetcher.parseFirstGameId("""{"id":7}"""))
        assertTrue(RaFetcher.parseFirstGameId(null) == null)
        assertTrue(RaFetcher.parseFirstGameId("[]") == null)
        assertTrue(RaFetcher.parseFirstGameId("not-json") == null)
    }

    @Test
    fun `fetchProgressJson returns raw body`() {
        val body = """{"ID":99,"Title":"Demo","Achievements":{}}"""
        val json = RaFetcher.fetchProgressJson(
            username = "u",
            apiKey = "k",
            gameId = 99,
            titleHint = null,
            fetchUrl = { body },
        )
        assertEquals(body, json)
        assertNull(
            RaFetcher.fetchProgressJson("", "k", 1, null) { error("no net") },
        )
    }

    @Test
    fun `fetchProgress uses inject seam and parseProgress`() {
        val body = """
            {"ID":99,"Title":"Demo","NumAwardedToUser":3,"NumAchievements":10}
        """.trimIndent()
        val progress = RaFetcher.fetchProgress(
            username = "u",
            apiKey = "k",
            gameId = 99,
            titleHint = null,
            fetchUrl = { body },
        )
        assertEquals(99, progress.gameId)
        assertEquals(3, progress.numAwarded)
        assertEquals(10, progress.numPossible)
        assertFalse(progress.isEmpty)
    }

    @Test
    fun `fetchProgress blank credentials returns empty`() {
        val p = RaFetcher.fetchProgress("", "k", 1, null) { error("no net") }
        assertTrue(p.isEmpty)
    }

    @Test
    fun `fetchProgress resolves via console game list then progress`() {
        val progress = RaFetcher.fetchProgress(
            username = "u",
            apiKey = "k",
            gameId = null,
            titleHint = "Super Demo",
            platformId = "snes",
            fetchUrl = { url ->
                when {
                    url.contains("API_GetGameList") && url.contains("i=3") ->
                        """[{"ID":55,"Title":"Super Demo"},{"ID":1,"Title":"Other"}]"""
                    url.contains("g=55") ->
                        """{"ID":55,"Title":"Super Demo","NumAwardedToUser":1,"NumAchievements":5}"""
                    else -> null
                }
            },
        )
        assertEquals(55, progress.gameId)
        assertEquals(1, progress.numAwarded)
    }

    @Test
    fun `fetchProgress without platform mapping cannot title-resolve`() {
        val p = RaFetcher.fetchProgress(
            username = "u",
            apiKey = "k",
            gameId = null,
            titleHint = "Anything",
            platformId = "not-a-platform",
            fetchUrl = { error("should not hit network") },
        )
        assertTrue(p.isEmpty)
    }

    @Test
    fun `console game list cache invokes fetchUrl once across 50 ROMs on same console and isolates keys and consoles`() {
        val catalogJson = buildString {
            append('[')
            for (i in 1..50) {
                if (i > 1) append(',')
                append("""{"ID":${1000 + i},"Title":"SNES Game $i Deluxe"}""")
            }
            append(']')
        }
        var gameListFetches = 0
        var progressFetches = 0

        val fetcher: (String) -> String? = { url ->
            when {
                url.contains("API_GetGameList") -> {
                    gameListFetches++
                    catalogJson
                }
                url.contains("API_GetGameInfoAndUserProgress") -> {
                    progressFetches++
                    val gid = url.substringAfter("g=").substringBefore('&').toInt()
                    """{"ID":$gid,"Title":"Game $gid","NumAwardedToUser":2,"NumAchievements":10}"""
                }
                else -> null
            }
        }

        // Resolve 50 ROMs on SNES (exact + substring matches) -> console list fetched ONLY 1x!
        for (i in 1..50) {
            val hint = if (i % 5 == 0) "SNES Game $i" else "SNES Game $i Deluxe"
            val progress = RaFetcher.fetchProgress(
                username = "player",
                apiKey = "api-key-A",
                gameId = null,
                titleHint = hint,
                platformId = "snes",
                fetchUrl = fetcher,
            )
            assertEquals(1000 + i, progress.gameId)
            assertEquals(2, progress.numAwarded)
        }
        assertEquals("expected exactly 1 game-list fetch across 50 SNES ROMs", 1, gameListFetches)
        assertEquals(50, progressFetches)
        assertEquals(1, RaFetcher.cacheSize())

        // Different API key on same console -> isolated cache miss (+1 fetch)
        RaFetcher.fetchProgress("player", "api-key-B", null, "SNES Game 1 Deluxe", "snes", fetcher)
        assertEquals(2, gameListFetches)
        assertEquals(2, RaFetcher.cacheSize())

        // Different console on same API key -> isolated cache miss (+1 fetch)
        RaFetcher.fetchProgress("player", "api-key-A", null, "SNES Game 1 Deluxe", "gba", fetcher)
        assertEquals(3, gameListFetches)
        assertEquals(3, RaFetcher.cacheSize())

        // clearCache resets cache cleanly so next resolve fetches again
        RaFetcher.clearCache()
        assertEquals(0, RaFetcher.cacheSize())
        RaFetcher.fetchProgress("player", "api-key-A", null, "SNES Game 1 Deluxe", "snes", fetcher)
        assertEquals(4, gameListFetches)

        // Transient null / malformed network failure must NOT poison the cache
        RaFetcher.clearCache()
        var transientCalls = 0
        val flaky = RaFetcher.fetchProgress("player", "api-key-C", null, "SNES Game 1 Deluxe", "snes") { url ->
            if (url.contains("API_GetGameList")) {
                transientCalls++
                if (transientCalls == 1) null else catalogJson
            } else {
                """{"ID":1001,"Title":"SNES Game 1 Deluxe","NumAwardedToUser":1,"NumAchievements":5}"""
            }
        }
        assertTrue(flaky.isEmpty)
        assertEquals(0, RaFetcher.cacheSize())
        val recovered = RaFetcher.fetchProgress("player", "api-key-C", null, "SNES Game 1 Deluxe", "snes") { url ->
            if (url.contains("API_GetGameList")) {
                transientCalls++
                catalogJson
            } else {
                """{"ID":1001,"Title":"SNES Game 1 Deluxe","NumAwardedToUser":1,"NumAchievements":5}"""
            }
        }
        assertEquals(1001, recovered.gameId)
        assertEquals(2, transientCalls)
    }
}
