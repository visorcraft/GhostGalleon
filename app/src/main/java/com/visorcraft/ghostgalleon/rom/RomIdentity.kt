package com.visorcraft.ghostgalleon.rom

import java.io.InputStream
import java.security.MessageDigest

data class RomIdentity(
    val romId: String,
    val algo: String,
    val hash: String?,
    val headerTitle: String?,
    val groupId: String?,
    val discIndex: Int?,
    val ready: Boolean,
    val sourceUri: String? = null,
)

object RomIdentities {
    const val ALGO_SHA1_PAYLOAD = "sha1-payload"
    const val ALGO_SHA256_SAMPLE = "sha256-sample"
    const val ALGO_SFO_TITLE = "sfo-title"
    const val ALGO_DAT_CRC = "dat-crc"
    const val SMALL_MAX_BYTES = 32L * 1024 * 1024
    const val STREAM_BUFFER_BYTES = 64 * 1024
    const val CHECKPOINT_INTERVAL = 100

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

    /**
     * Streams [stream] through SHA-1 using a bounded [bufferSize] (default 64 KiB),
     * stripping the 16-byte iNES header (`NES\u001a` + 12 bytes) iff total payload length
     * exceeds 16 bytes — bit-exact with `sha1Hex(stripInes(stream.readBytes()))` while
     * holding 64 KiB peak RAM instead of up to 64 MiB.
     */
    fun sha1Payload(
        stream: InputStream,
        bufferSize: Int = STREAM_BUFFER_BYTES,
    ): String {
        val md = MessageDigest.getInstance("SHA-1")
        // Read up to 17 bytes first because stripInes requires size > 16 (a header-only
        // 16-byte file is NOT stripped). Handles arbitrary partial stream reads cleanly.
        val probe = ByteArray(17)
        var probeLen = 0
        while (probeLen < probe.size) {
            val r = stream.read(probe, probeLen, probe.size - probeLen)
            if (r < 0) break
            probeLen += r
        }
        if (probeLen == 0) return hex(md.digest())
        val isInes = probeLen == 17 &&
            probe[0] == 'N'.code.toByte() &&
            probe[1] == 'E'.code.toByte() &&
            probe[2] == 'S'.code.toByte() &&
            probe[3] == 0x1A.toByte()
        if (isInes) {
            md.update(probe, 16, 1)
        } else {
            md.update(probe, 0, probeLen)
        }
        if (probeLen == probe.size) {
            val buf = ByteArray(bufferSize.coerceAtLeast(32))
            while (true) {
                val r = stream.read(buf)
                if (r < 0) break
                if (r > 0) md.update(buf, 0, r)
            }
        }
        return hex(md.digest())
    }

    /**
     * Skips [bytesToSkip] bytes from [stream], falling back to a 64 KiB chunked discard
     * loop whenever `InputStream.skip()` returns `<= 0` (e.g., unskippable SAF streams)
     * instead of reading byte-by-byte. Returns the number of bytes actually skipped.
     */
    fun skipFully(
        stream: InputStream,
        bytesToSkip: Long,
        discardBuffer: ByteArray = ByteArray(STREAM_BUFFER_BYTES),
    ): Long {
        if (bytesToSkip <= 0L) return 0L
        var left = bytesToSkip
        val buf = if (discardBuffer.isNotEmpty()) discardBuffer else ByteArray(STREAM_BUFFER_BYTES)
        while (left > 0L) {
            val skipped = stream.skip(left)
            if (skipped > 0L) {
                left -= skipped
                continue
            }
            val want = minOf(left, buf.size.toLong()).toInt()
            val r = stream.read(buf, 0, want)
            if (r < 0) break
            left -= r.toLong()
        }
        return bytesToSkip - left
    }

    /**
     * True when a persisted [prior] identity row can be reused without re-hashing.
     *
     * - Ready rows (`ready == true`) are reused unless [entryUri] changed from a recorded `sourceUri`.
     * - Failed rows (`ready == false`) are NOT retried on cold boots (`retryFailed == false`)
     *   until an explicit rescan (`retryFailed == true`) or URI change occurs.
     */
    fun shouldReusePrior(
        prior: RomIdentity?,
        entryUri: String,
        retryFailed: Boolean,
    ): Boolean {
        if (prior == null) return false
        val uriChanged = prior.sourceUri != null && prior.sourceUri != entryUri
        if (uriChanged) return false
        if (prior.ready) return true
        return !retryFailed
    }

    /** True when [sinceLastSave] newly hashed ROMs have accumulated enough progress to checkpoint. */
    fun shouldCheckpoint(
        sinceLastSave: Int,
        interval: Int = CHECKPOINT_INTERVAL,
    ): Boolean = interval > 0 && sinceLastSave >= interval

    /** True iff [entries] contains at least one arcade ROM requiring the bundled arcade title TSV. */
    fun hasArcadeEntries(entries: List<RomEntry>): Boolean {
        for (i in entries.indices) {
            if (entries[i].platformId == "arcade") return true
        }
        return false
    }

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
