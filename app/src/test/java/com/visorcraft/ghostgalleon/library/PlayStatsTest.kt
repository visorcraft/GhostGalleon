package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.joinText
import com.visorcraft.ghostgalleon.i18n.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayStatsTest {

    @Test
    fun `sessionDurationMs clamps negative to zero`() {
        assertEquals(0L, SessionMath.sessionDurationMs(100, 50))
        assertEquals(50L, SessionMath.sessionDurationMs(100, 150))
    }

    @Test
    fun `recordLaunch stamps lastLaunchedMs`() {
        val next = SessionMath.recordLaunch(PlayStats.EMPTY, "rom:snes:x", 1_000L)
        assertEquals(1_000L, next.lastLaunchedMs["rom:snes:x"])
    }

    @Test
    fun `recordReturn accrues playtime`() {
        val afterLaunch = SessionMath.recordLaunch(PlayStats.EMPTY, "k", 1_000L)
        val after = SessionMath.recordReturn(afterLaunch, "k", 1_000L, 1_000L + 120_000L)
        assertEquals(120_000L, after.totalPlaytimeMs["k"])
        val twice = SessionMath.recordReturn(after, "k", 2_000L, 2_000L + 60_000L)
        assertEquals(180_000L, twice.totalPlaytimeMs["k"])
    }

    @Test
    fun `formatPlaytime humanizes`() {
        assertEquals(text(R.string.time_zero_minutes), SessionMath.formatPlaytime(0))
        assertEquals(text(R.string.time_minutes, 5L), SessionMath.formatPlaytime(5 * 60_000L))
        assertEquals(text(R.string.time_hours, 1L), SessionMath.formatPlaytime(60 * 60_000L))
        assertEquals(
            text(R.string.time_hours_minutes, 1L, 5L),
            SessionMath.formatPlaytime(65 * 60_000L),
        )
    }

    @Test
    fun `formatLastPlayed returns null when unknown`() {
        assertNull(SessionMath.formatLastPlayed(null, 1_000L))
        assertEquals(
            text(R.string.time_just_now),
            SessionMath.formatLastPlayed(1_000L, 1_000L + 10_000L),
        )
        assertEquals(
            text(R.string.time_minutes_ago, 2L),
            SessionMath.formatLastPlayed(1_000L, 1_000L + 2 * 60_000L),
        )
    }

    @Test
    fun `cardMetaLine joins last played and playtime`() {
        val now = 10_000L + 2 * 60_000L
        val ago = text(R.string.time_minutes_ago, 2L)
        val duration = text(R.string.time_minutes, 12L)
        assertEquals(
            joinText(listOf(ago, duration), " · "),
            SessionMath.cardMetaLine(10_000L, 12 * 60_000L, now),
        )
        assertEquals(
            joinText(listOf(ago, text(R.string.stats_played_prefix, duration)), " · "),
            SessionMath.cardMetaLine(10_000L, 12 * 60_000L, now, playedPrefix = true),
        )
        assertEquals(
            joinText(listOf(text(R.string.stats_never_played)), " · "),
            SessionMath.cardMetaLine(null, 0L, now),
        )
        assertEquals(
            joinText(listOf(text(R.string.time_minutes, 5L)), " · "),
            SessionMath.cardMetaLine(null, 5 * 60_000L, now),
        )
    }

    @Test
    fun `cardMetaLine appends favorite and dock tags`() {
        val now = 10_000L + 2 * 60_000L
        val ago = text(R.string.time_minutes_ago, 2L)
        val never = text(R.string.stats_never_played)
        val favorite = text(R.string.glyph_favorite)
        val dock = text(R.string.label_dock)
        assertEquals(
            joinText(listOf(ago, favorite, dock), " · "),
            SessionMath.cardMetaLine(
                10_000L, 0L, now, favorite = true, inDock = true,
            ),
        )
        assertEquals(
            joinText(listOf(never, favorite), " · "),
            SessionMath.cardMetaLine(null, 0L, now, favorite = true),
        )
        assertEquals(
            joinText(listOf(never, dock), " · "),
            SessionMath.cardMetaLine(null, 0L, now, inDock = true),
        )
    }

    @Test
    fun `stampLastPlayed records last launch without playtime`() {
        val stamped = SessionMath.stampLastPlayed(PlayStats.EMPTY, "rom:x", 1_000L)
        assertEquals(1_000L, stamped.lastLaunchedMs["rom:x"])
        assertTrue(stamped.totalPlaytimeMs.isEmpty())
        assertEquals(PlayStats.EMPTY, SessionMath.stampLastPlayed(PlayStats.EMPTY, "  ", 1L))
        assertEquals(PlayStats.EMPTY, SessionMath.stampLastPlayed(PlayStats.EMPTY, "k", 0L))
    }

    @Test
    fun `bulkStampLastPlayed only stamps unplayed keys`() {
        val stats = PlayStats(lastLaunchedMs = mapOf("a" to 50L))
        assertEquals(2, SessionMath.unplayedCountInSelection(stats.lastLaunchedMs, listOf("a", "b", "c")))
        val (next, n) = SessionMath.bulkStampLastPlayed(stats, listOf("a", "b", "c"), 100L)
        assertEquals(2, n)
        assertEquals(50L, next.lastLaunchedMs["a"])
        assertEquals(100L, next.lastLaunchedMs["b"])
        assertEquals(100L, next.lastLaunchedMs["c"])
        val (noop, n0) = SessionMath.bulkStampLastPlayed(next, listOf("a", "b"), 200L)
        assertEquals(0, n0)
        assertEquals(next, noop)
    }

    @Test
    fun `hasStats and clearStats drop launch and playtime only`() {
        val stats = PlayStats(
            lastLaunchedMs = mapOf("a" to 10L, "b" to 20L),
            totalPlaytimeMs = mapOf("a" to 5_000L),
        )
        assertTrue(SessionMath.hasStats(stats, "a"))
        assertTrue(SessionMath.hasStats(stats, "b"))
        assertFalse(SessionMath.hasStats(stats, "missing"))
        assertFalse(SessionMath.hasStats(stats, "  "))
        val cleared = SessionMath.clearStats(stats, "a")
        assertFalse(SessionMath.hasStats(cleared, "a"))
        assertTrue(SessionMath.hasStats(cleared, "b"))
        assertEquals(mapOf("b" to 20L), cleared.lastLaunchedMs)
        assertTrue(cleared.totalPlaytimeMs.isEmpty())
        // No-op when already empty
        assertEquals(cleared, SessionMath.clearStats(cleared, "a"))
    }

    @Test
    fun `bulkClearStats clears multiple keys`() {
        val stats = PlayStats(
            lastLaunchedMs = mapOf("a" to 10L, "b" to 20L, "c" to 30L),
            totalPlaytimeMs = mapOf("a" to 100L, "c" to 200L),
        )
        assertEquals(2, SessionMath.statsCountInSelection(stats, listOf("a", "x", "c")))
        val (next, n) = SessionMath.bulkClearStats(stats, listOf("a", "c", "missing"))
        assertEquals(2, n)
        assertFalse(SessionMath.hasStats(next, "a"))
        assertFalse(SessionMath.hasStats(next, "c"))
        assertTrue(SessionMath.hasStats(next, "b"))
        assertEquals(mapOf("b" to 20L), next.lastLaunchedMs)
    }
}
