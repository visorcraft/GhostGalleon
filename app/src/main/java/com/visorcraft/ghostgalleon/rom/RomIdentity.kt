package com.visorcraft.ghostgalleon.rom

import java.security.MessageDigest

data class RomIdentity(
    val romId: String,
    val algo: String,
    val hash: String?,
    val headerTitle: String?,
    val groupId: String?,
    val discIndex: Int?,
    val ready: Boolean,
)

object RomIdentities {
    const val ALGO_SHA1_PAYLOAD = "sha1-payload"
    const val ALGO_SHA256_SAMPLE = "sha256-sample"
    const val ALGO_SFO_TITLE = "sfo-title"
    const val ALGO_DAT_CRC = "dat-crc"
    const val SMALL_MAX_BYTES = 32L * 1024 * 1024

    private val HEX = "0123456789abcdef".toCharArray()

    fun stripInes(payload: ByteArray): ByteArray {
        if (payload.size > 16 &&
            payload[0] == 'N'.code.toByte() &&
            payload[1] == 'E'.code.toByte() &&
            payload[2] == 'S'.code.toByte() &&
            payload[3] == 0x1A.toByte()
        ) {
            return payload.copyOfRange(16, payload.size)
        }
        return payload
    }

    fun sha1Hex(bytes: ByteArray): String =
        hex(MessageDigest.getInstance("SHA-1").digest(bytes))

    fun sampleSha256(size: Long, head: ByteArray, mid: ByteArray, tail: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(le64(size))
        md.update(head)
        md.update(mid)
        md.update(tail)
        return hex(md.digest())
    }

    fun chooseAlgo(size: Long, platformId: String): String = when {
        platformId == "psvita" || platformId == "vita" -> ALGO_SFO_TITLE
        platformId == "arcade" -> ALGO_DAT_CRC
        size > SMALL_MAX_BYTES -> ALGO_SHA256_SAMPLE
        else -> ALGO_SHA1_PAYLOAD
    }

    /** Skip sidecar write + deck notify when nothing new was hashed and the id set is unchanged. */
    fun sidecarQuiet(
        priorKeys: Set<String>,
        nextKeys: Set<String>,
        newlyComputed: Int,
    ): Boolean = newlyComputed == 0 && priorKeys == nextKeys

    private fun le64(value: Long): ByteArray {
        val out = ByteArray(8)
        var v = value
        for (i in 0 until 8) {
            out[i] = (v and 0xFF).toByte()
            v = v ushr 8
        }
        return out
    }

    private fun hex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        var i = 0
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out[i++] = HEX[v ushr 4]
            out[i++] = HEX[v and 0x0F]
        }
        return String(out)
    }
}
