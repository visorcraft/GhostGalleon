package com.visorcraft.ghostgalleon.library

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.joinText
import com.visorcraft.ghostgalleon.i18n.text

/**
 * Per-slot-key launch / playtime stats. Keys are the same as grid/dock
 * slot keys (package name or "rom:<id>"). Pure; host-tested.
 */
data class PlayStats(
    val lastLaunchedMs: Map<String, Long> = emptyMap(),
    val totalPlaytimeMs: Map<String, Long> = emptyMap(),
) {
    companion object {
        val EMPTY = PlayStats()
    }
}

/** Session accrual: launch stamps last-played; return accrues duration. */
object SessionMath {

    /** Duration of a session, clamped to non-negative. */
    fun sessionDurationMs(launchAtMs: Long, returnAtMs: Long): Long =
        (returnAtMs - launchAtMs).coerceAtLeast(0L)

    fun recordLaunch(stats: PlayStats, key: String, nowMs: Long): PlayStats =
        stats.copy(lastLaunchedMs = stats.lastLaunchedMs + (key to nowMs))

    fun recordReturn(
        stats: PlayStats,
        key: String,
        launchAtMs: Long,
        returnAtMs: Long,
    ): PlayStats {
        val delta = sessionDurationMs(launchAtMs, returnAtMs)
        if (delta == 0L) return stats
        val prev = stats.totalPlaytimeMs[key] ?: 0L
        return stats.copy(
            totalPlaytimeMs = stats.totalPlaytimeMs + (key to (prev + delta)),
        )
    }

    /**
     * True when [key] has a positive last-launch stamp and/or positive
     * accumulated playtime (menu can offer "Clear play stats").
     */
    fun hasStats(stats: PlayStats, key: String): Boolean {
        val k = key.trim()
        if (k.isEmpty()) return false
        return (stats.lastLaunchedMs[k] ?: 0L) > 0L ||
            (stats.totalPlaytimeMs[k] ?: 0L) > 0L
    }

    /**
     * Stamp [key] as last-played at [nowMs] without accruing playtime.
     * Blank key or non-positive [nowMs] → unchanged. Used by "Mark as played".
     */
    fun stampLastPlayed(stats: PlayStats, key: String, nowMs: Long): PlayStats {
        val k = key.trim()
        if (k.isEmpty() || nowMs <= 0L) return stats
        return stats.copy(lastLaunchedMs = stats.lastLaunchedMs + (k to nowMs))
    }

    /**
     * How many of [keys] have never been launched (Mark as played bulk label).
     */
    fun unplayedCountInSelection(
        lastLaunchedMs: Map<String, Long>,
        keys: Collection<String>,
    ): Int = keys.count { k ->
        val t = k.trim()
        t.isNotEmpty() && (lastLaunchedMs[t] ?: 0L) <= 0L
    }

    /**
     * Stamp every unplayed key in [keys] as last-played at [nowMs].
     * Returns updated stats and how many keys were newly stamped.
     */
    fun bulkStampLastPlayed(
        stats: PlayStats,
        keys: Collection<String>,
        nowMs: Long,
    ): Pair<PlayStats, Int> {
        if (nowMs <= 0L || keys.isEmpty()) return stats to 0
        var next = stats
        var stamped = 0
        keys.forEach { key ->
            val k = key.trim()
            if (k.isEmpty()) return@forEach
            if ((next.lastLaunchedMs[k] ?: 0L) > 0L) return@forEach
            next = stampLastPlayed(next, k, nowMs)
            stamped++
        }
        return next to stamped
    }

    /**
     * Drop last-launch + playtime for [key]. Missing key is a no-op.
     * Does not touch favorites, collections, or dock/grid pins.
     */
    fun clearStats(stats: PlayStats, key: String): PlayStats {
        val k = key.trim()
        if (k.isEmpty()) return stats
        if (!hasStats(stats, k)) return stats
        return stats.copy(
            lastLaunchedMs = stats.lastLaunchedMs - k,
            totalPlaytimeMs = stats.totalPlaytimeMs - k,
        )
    }

    /** How many of [keys] currently have play stats (bulk clear label). */
    fun statsCountInSelection(stats: PlayStats, keys: Collection<String>): Int =
        keys.count { hasStats(stats, it) }

    /**
     * Clear last-launch + playtime for every key in [keys]. Returns updated
     * stats and how many keys actually had stats removed.
     */
    fun bulkClearStats(stats: PlayStats, keys: Collection<String>): Pair<PlayStats, Int> {
        var next = stats
        var cleared = 0
        keys.forEach { key ->
            if (hasStats(next, key)) {
                next = clearStats(next, key)
                cleared++
            }
        }
        return next to cleared
    }

    /** Locale-ready playtime for hero labels (for example 12m or 1h 5m). */
    fun formatPlaytime(ms: Long): UiText {
        if (ms <= 0L) return text(R.string.time_zero_minutes)
        val totalMin = ms / 60_000L
        if (totalMin < 60L) return text(R.string.time_minutes, totalMin)
        val hours = totalMin / 60L
        val minutes = totalMin % 60L
        return if (minutes == 0L) text(R.string.time_hours, hours)
        else text(R.string.time_hours_minutes, hours, minutes)
    }

    /** Relative last-played text from [nowMs] and [lastMs], or null. */
    fun formatLastPlayed(lastMs: Long?, nowMs: Long): UiText? {
        if (lastMs == null || lastMs <= 0L) return null
        val minutes = (nowMs - lastMs).coerceAtLeast(0L) / 60_000L
        return when {
            minutes < 1L -> text(R.string.time_just_now)
            minutes < 60L -> text(R.string.time_minutes_ago, minutes)
            minutes < 60L * 24L -> text(R.string.time_hours_ago, minutes / 60L)
            else -> text(R.string.time_days_ago, minutes / (60L * 24L))
        }
    }

    /** Compact translated card/hero subtitle. */
    fun cardMetaLine(
        lastMs: Long?,
        playtimeMs: Long,
        nowMs: Long,
        playedPrefix: Boolean = false,
        favorite: Boolean = false,
        inDock: Boolean = false,
    ): UiText {
        val parts = mutableListOf<UiText>()
        formatLastPlayed(lastMs, nowMs)?.let(parts::add)
        if (playtimeMs > 0L) {
            val duration = formatPlaytime(playtimeMs)
            parts += if (playedPrefix) text(R.string.stats_played_prefix, duration) else duration
        }
        if (parts.isEmpty()) parts += text(R.string.stats_never_played)
        if (favorite) parts += text(R.string.glyph_favorite)
        if (inDock) parts += text(R.string.label_dock)
        return joinText(parts, " · ")
    }
}
