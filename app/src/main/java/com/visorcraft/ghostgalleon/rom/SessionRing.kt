package com.visorcraft.ghostgalleon.rom

data class SessionRingEntry(
    val key: String,
    val playerId: String?,
    val packageName: String,
    val policy: SessionPolicy,
    val launchedAtMs: Long,
    val title: String,
)

object SessionRing {
    const val CAP = 8

    fun push(ring: List<SessionRingEntry>, entry: SessionRingEntry): List<SessionRingEntry> =
        (listOf(entry) + ring.filterNot { it.key == entry.key }).take(CAP)

    fun remove(ring: List<SessionRingEntry>, key: String): List<SessionRingEntry> =
        ring.filterNot { it.key == key }
}
