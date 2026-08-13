package com.visorcraft.ghostgalleon.rom

import org.json.JSONArray
import org.json.JSONObject

data class LensBlock(
    val address: Int,
    val length: Int,
    val format: String,
    val labels: List<String>,
)

data class LensSpec(
    val id: String,
    val title: String,
    val platformId: String?,
    val hashes: Set<String>,
    val romIds: Set<String>,
    val intervalMs: Long,
    val blocks: List<LensBlock>,
)

object LensCatalog {
    const val MAX_BYTES = 256

    fun parse(json: String): List<LensSpec> {
        return try {
            val trimmed = json.trim()
            if (trimmed.isEmpty()) return emptyList()
            when {
                trimmed.startsWith("[") -> parseArray(JSONArray(trimmed))
                else -> {
                    val root = JSONObject(trimmed)
                    val arr = root.optJSONArray("lenses")
                    if (arr != null) {
                        parseArray(arr)
                    } else {
                        parseLens(root)?.let { listOf(it) } ?: emptyList()
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun match(
        lenses: List<LensSpec>,
        romId: String?,
        hash: String?,
        platformId: String?,
    ): LensSpec? {
        return lenses.firstOrNull { spec ->
            if (spec.platformId != null && spec.platformId != platformId) return@firstOrNull false
            val hashHit = hash != null && hash in spec.hashes
            val romHit = romId != null && romId in spec.romIds
            hashHit || romHit
        }
    }

    fun totalBytes(spec: LensSpec): Int = spec.blocks.sumOf { it.length }

    fun acceptable(spec: LensSpec): Boolean {
        val total = totalBytes(spec)
        return total in 1..MAX_BYTES && spec.blocks.all { it.length > 0 }
    }

    private fun parseArray(arr: JSONArray): List<LensSpec> {
        val out = ArrayList<LensSpec>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            parseLens(o)?.let { out.add(it) }
        }
        return out
    }

    private fun parseLens(o: JSONObject): LensSpec? {
        val id = o.optString("id", "").trim()
        if (id.isEmpty()) return null
        val match = o.optJSONObject("match")
        return LensSpec(
            id = id,
            title = o.optString("title", id).ifBlank { id },
            platformId = stringOrNull(match, "platformId") ?: stringOrNull(o, "platformId"),
            hashes = stringSet(match, "hash", "hashes"),
            romIds = stringSet(match, "romId", "romIds"),
            intervalMs = o.optLong("intervalMs", 200L),
            blocks = parseBlocks(o.optJSONArray("blocks")),
        )
    }

    private fun parseBlocks(arr: JSONArray?): List<LensBlock> {
        if (arr == null) return emptyList()
        val out = ArrayList<LensBlock>(arr.length())
        for (i in 0 until arr.length()) {
            val b = arr.optJSONObject(i) ?: continue
            val address = parseAddress(b.opt("address")) ?: continue
            val labelsArr = b.optJSONArray("labels")
            val labels = if (labelsArr == null) {
                emptyList()
            } else {
                (0 until labelsArr.length()).mapNotNull { j ->
                    if (labelsArr.isNull(j)) null
                    else labelsArr.optString(j, "").trim().takeIf { it.isNotEmpty() }
                }
            }
            out.add(
                LensBlock(
                    address = address,
                    length = b.optInt("length", 0),
                    format = b.optString("format", "bytes").ifBlank { "bytes" },
                    labels = labels,
                ),
            )
        }
        return out
    }

    private fun stringOrNull(o: JSONObject?, key: String): String? {
        if (o == null || !o.has(key) || o.isNull(key)) return null
        return o.optString(key, "").trim().ifBlank { null }
    }

    private fun stringSet(o: JSONObject?, vararg keys: String): Set<String> {
        if (o == null) return emptySet()
        for (key in keys) {
            if (!o.has(key) || o.isNull(key)) continue
            val arr = o.optJSONArray(key)
            if (arr != null) {
                return (0 until arr.length()).mapNotNull { i ->
                    if (arr.isNull(i)) null
                    else arr.optString(i, "").trim().takeIf { it.isNotEmpty() }
                }.toSet()
            }
            val single = o.optString(key, "").trim()
            if (single.isNotEmpty()) return setOf(single)
        }
        return emptySet()
    }

    private fun parseAddress(raw: Any?): Int? {
        return when (raw) {
            null, JSONObject.NULL -> null
            is Number -> raw.toInt()
            is String -> {
                val t = raw.trim()
                if (t.isEmpty()) null
                else if (t.startsWith("0x") || t.startsWith("0X")) t.substring(2).toIntOrNull(16)
                else t.toIntOrNull() ?: t.toIntOrNull(16)
            }
            else -> null
        }
    }
}
