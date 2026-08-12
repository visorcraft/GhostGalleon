package com.visorcraft.ghostgalleon.art

/**
 * Negative cache for SteamGridDB: skip titles that already failed a search
 * until the query changes or the TTL elapses.
 * Pure selection; persistence is the caller's job.
 */
object SgdbMissCache {

    const val DEFAULT_TTL_MS = 7L * 24 * 60 * 60 * 1000

    data class Miss(
        val romId: String,
        val query: String,
        val atMs: Long,
    )

    fun shouldSkip(
        miss: Miss?,
        currentQuery: String,
        nowMs: Long,
        ttlMs: Long = DEFAULT_TTL_MS,
    ): Boolean {
        if (miss == null) return false
        if (miss.query != currentQuery) return false
        if (ttlMs <= 0L) return false
        return nowMs - miss.atMs < ttlMs
    }

    fun record(
        existing: Map<String, Miss>,
        romId: String,
        query: String,
        nowMs: Long,
    ): Map<String, Miss> {
        val q = query.trim()
        if (romId.isBlank() || q.isEmpty()) return existing
        return existing + (romId to Miss(romId, q, nowMs))
    }

    fun prune(existing: Map<String, Miss>, nowMs: Long, ttlMs: Long = DEFAULT_TTL_MS): Map<String, Miss> =
        existing.filterValues { nowMs - it.atMs < ttlMs }
}
