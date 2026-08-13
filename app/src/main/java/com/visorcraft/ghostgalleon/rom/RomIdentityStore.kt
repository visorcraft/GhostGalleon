package com.visorcraft.ghostgalleon.rom

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistent ROM identity sidecar: JSON array in filesDir/rom_identity.json,
 * written atomically (tmp + rename) like [RomLibrary] / SettingsStore.
 * Missing or corrupt file → empty map. Does not rewrite [RomEntry.id].
 */
class RomIdentityStore(private val file: File) {

    fun load(): Map<String, RomIdentity> {
        if (!file.exists()) return emptyMap()
        return try {
            parse(JSONArray(file.readText()))
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun save(identities: Map<String, RomIdentity>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(toJson(identities).toString())
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    companion object {
        internal fun toJson(identities: Map<String, RomIdentity>): JSONArray {
            val arr = JSONArray()
            identities.values.forEach { id ->
                arr.put(
                    JSONObject()
                        .put("romId", id.romId)
                        .put("algo", id.algo)
                        .put("hash", id.hash ?: JSONObject.NULL)
                        .put("headerTitle", id.headerTitle ?: JSONObject.NULL)
                        .put("groupId", id.groupId ?: JSONObject.NULL)
                        .put("discIndex", id.discIndex ?: JSONObject.NULL)
                        .put("ready", id.ready),
                )
            }
            return arr
        }

        internal fun parse(arr: JSONArray): Map<String, RomIdentity> {
            val out = LinkedHashMap<String, RomIdentity>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val romId = o.optString("romId", "")
                if (romId.isEmpty()) continue
                val identity = RomIdentity(
                    romId = romId,
                    algo = o.optString("algo", RomIdentities.ALGO_SHA1_PAYLOAD),
                    hash = o.optNullableString("hash"),
                    headerTitle = o.optNullableString("headerTitle"),
                    groupId = o.optNullableString("groupId"),
                    discIndex = if (!o.has("discIndex") || o.isNull("discIndex")) {
                        null
                    } else {
                        o.optInt("discIndex")
                    },
                    ready = o.optBoolean("ready", false),
                )
                out[romId] = identity
            }
            return out
        }

        private fun JSONObject.optNullableString(key: String): String? =
            if (!has(key) || isNull(key)) null else getString(key)
    }
}
