package com.visorcraft.ghostgalleon.library

import org.json.JSONArray
import org.json.JSONObject

data class RaCheevo(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val unlocked: Boolean,
    val badgeName: String?,
)

data class RaTheaterSnap(
    val progress: RaProgress,
    val nextLocked: RaCheevo?,
    val lastUnlock: RaCheevo?,
    val unlockedIds: Set<Int>,
    val items: List<RaCheevo> = emptyList(),
)

/**
 * Pure parse of RetroAchievements theater snaps. Never throws;
 * malformed or blank JSON is an empty snap.
 */
object RaTheater {

    fun parse(json: String?): RaTheaterSnap {
        if (json.isNullOrBlank()) return emptySnap()
        return try {
            val progress = RetroAchievements.parseProgress(json)
            val items = parseAchievements(JSONObject(json))
            val unlockedIds = LinkedHashSet<Int>()
            var lastUnlock: RaCheevo? = null
            for (item in items) {
                if (!item.unlocked) continue
                unlockedIds.add(item.id)
                lastUnlock = item
            }
            RaTheaterSnap(
                progress = progress,
                nextLocked = nextLocked(items),
                lastUnlock = lastUnlock,
                unlockedIds = unlockedIds,
                items = items,
            )
        } catch (_: Exception) {
            emptySnap()
        }
    }

    fun nextLocked(items: List<RaCheevo>): RaCheevo? =
        items.firstOrNull { !it.unlocked }

    fun newlyUnlocked(prev: Set<Int>, next: Set<Int>): List<Int> =
        (next - prev).toList()

    fun pollDue(lastMs: Long, nowMs: Long, intervalMs: Long): Boolean =
        lastMs <= 0L || nowMs - lastMs >= intervalMs.coerceAtLeast(30_000L)

    private fun emptySnap(): RaTheaterSnap =
        RaTheaterSnap(
            progress = RaProgress(),
            nextLocked = null,
            lastUnlock = null,
            unlockedIds = emptySet(),
            items = emptyList(),
        )

    private fun parseAchievements(root: JSONObject): List<RaCheevo> {
        val raw = root.opt("Achievements") ?: return emptyList()
        val out = ArrayList<RaCheevo>()
        when (raw) {
            is JSONArray -> {
                for (i in 0 until raw.length()) {
                    val o = raw.optJSONObject(i) ?: continue
                    parseCheevo(o)?.let(out::add)
                }
            }
            is JSONObject -> {
                val keys = raw.keys()
                while (keys.hasNext()) {
                    val o = raw.optJSONObject(keys.next()) ?: continue
                    parseCheevo(o)?.let(out::add)
                }
            }
        }
        return out
    }

    private fun parseCheevo(o: JSONObject): RaCheevo? {
        val id = o.optInt("ID", 0)
        if (id <= 0) return null
        return RaCheevo(
            id = id,
            title = o.optString("Title", ""),
            description = o.optString("Description", ""),
            points = o.optInt("Points", 0),
            unlocked = hasDate(o, "DateEarned") || hasDate(o, "DateEarnedHardcore"),
            badgeName = stringOrNull(o, "BadgeName"),
        )
    }

    /** org.json optString turns JSON null into the literal "null". */
    private fun hasDate(o: JSONObject, key: String): Boolean {
        if (!o.has(key) || o.isNull(key)) return false
        return o.optString(key, "").trim().isNotEmpty()
    }

    private fun stringOrNull(o: JSONObject, key: String): String? {
        if (!o.has(key) || o.isNull(key)) return null
        return o.optString(key, "").trim().ifEmpty { null }
    }
}
