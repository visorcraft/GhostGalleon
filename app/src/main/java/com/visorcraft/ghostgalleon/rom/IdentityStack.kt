package com.visorcraft.ghostgalleon.rom

/** Fold ROM ids that share a non-null groupId into one primary id. */
object IdentityStack {

    /**
     * Returns primary ids for [ids] in first-seen order.
     * Ungrouped (null groupId) keep their place. Grouped members collapse
     * to the member with max [lastLaunchedMs], else the earliest in [ids].
     */
    fun primaryIds(
        ids: List<String>,
        groupId: (String) -> String?,
        lastLaunchedMs: Map<String, Long>,
    ): List<String> {
        val membersByGroup = LinkedHashMap<String, MutableList<String>>()
        for (id in ids) {
            val g = groupId(id)?.takeIf { it.isNotBlank() } ?: continue
            membersByGroup.getOrPut(g) { mutableListOf() }.add(id)
        }
        val primaryOf = HashMap<String, String>(membersByGroup.size)
        for ((g, members) in membersByGroup) {
            primaryOf[g] = members.withIndex().minWith(
                compareByDescending<IndexedValue<String>> { lastLaunchedMs[it.value] ?: 0L }
                    .thenBy { it.index },
            ).value
        }
        val seenGroups = HashSet<String>(membersByGroup.size)
        val out = ArrayList<String>(ids.size)
        for (id in ids) {
            val g = groupId(id)?.takeIf { it.isNotBlank() }
            if (g == null) {
                out += id
                continue
            }
            if (!seenGroups.add(g)) continue
            out += primaryOf.getValue(g)
        }
        return out
    }

    /** First 8 + last 8 when longer than 16; otherwise the full hash. */
    fun shortHash(hash: String): String {
        if (hash.length <= 16) return hash
        return hash.take(8) + "…" + hash.takeLast(8)
    }
}
