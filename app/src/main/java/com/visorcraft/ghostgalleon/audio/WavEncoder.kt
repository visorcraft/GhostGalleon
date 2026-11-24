package com.visorcraft.ghostgalleon.audio

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Pure Kotlin RIFF/WAVE PCM-16 encoder and header inspector.
 *
 * Writes standard 44-byte little-endian PCM WAV payloads consumable directly by
 * Android `SoundPool`, `MediaPlayer`, or desktop audio tools.
 */
object WavEncoder {

    const val HEADER_BYTES: Int = 44
    const val AUDIO_FORMAT_PCM: Int = 1
    const val BITS_PER_SAMPLE: Int = 16
    private const val IO_CHUNK_BYTES: Int = 16 * 1024

    /** Parsed metadata from a 44-byte RIFF/WAVE PCM header. */
    data class WavInfo(
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val byteRate: Int,
        val blockAlign: Int,
        val bitsPerSample: Int,
        val dataBytes: Int,
    ) {
        val sampleCount: Int
            get() = if (bitsPerSample > 0) dataBytes / (bitsPerSample / 8) else 0
    }

    /**
     * Encodes 16-bit signed linear PCM [samples] into a RIFF/WAVE byte array.
     * For stereo ([channels] == 2), [samples] must hold interleaved `[L0, R0, ...]` frames.
     */
    fun encodePcm16(
        samples: ShortArray,
        sampleRate: Int = AudioDsp.SAMPLE_RATE,
        channels: Int = 1,
    ): ByteArray {
        require(sampleRate > 0) { "sampleRate must be positive, got $sampleRate" }
        require(channels in 1..2) { "channels must be 1 (mono) or 2 (stereo), got $channels" }
        if (channels == 2) {
            require(samples.size % 2 == 0) { "stereo sample array must have even length, got ${samples.size}" }
        }

        val bytesPerSample = BITS_PER_SAMPLE / 8
        val dataBytes = samples.size * bytesPerSample
        val chunkSize = 36 + dataBytes
        val byteRate = sampleRate * channels * bytesPerSample
        val blockAlign = channels * bytesPerSample

        val out = ByteArray(HEADER_BYTES + dataBytes)

        // RIFF chunk descriptor
        writeAscii(out, 0, "RIFF")
        writeInt32Le(out, 4, chunkSize)
        writeAscii(out, 8, "WAVE")

        // fmt sub-chunk
        writeAscii(out, 12, "fmt ")
        writeInt32Le(out, 16, 16) // PCM fmt chunk size
        writeInt16Le(out, 20, AUDIO_FORMAT_PCM)
        writeInt16Le(out, 22, channels)
        writeInt32Le(out, 24, sampleRate)
        writeInt32Le(out, 28, byteRate)
        writeInt16Le(out, 32, blockAlign)
        writeInt16Le(out, 34, BITS_PER_SAMPLE)

        // data sub-chunk
        writeAscii(out, 36, "data")
        writeInt32Le(out, 40, dataBytes)

        var offset = HEADER_BYTES
        for (sample in samples) {
            val v = sample.toInt()
            out[offset] = (v and 0xFF).toByte()
            out[offset + 1] = ((v ushr 8) and 0xFF).toByte()
            offset += 2
        }
        return out
    }

    /** Parses a standard 44-byte RIFF/WAVE header, returning null on invalid input. */
    fun inspectHeader(wav: ByteArray): WavInfo? {
        if (wav.size < HEADER_BYTES) return null
        if (!matchesAscii(wav, 0, "RIFF")) return null
        if (!matchesAscii(wav, 8, "WAVE")) return null
        if (!matchesAscii(wav, 12, "fmt ")) return null
        if (!matchesAscii(wav, 36, "data")) return null
        return WavInfo(
            audioFormat = readInt16Le(wav, 20),
            channels = readInt16Le(wav, 22),
            sampleRate = readInt32Le(wav, 24),
            byteRate = readInt32Le(wav, 28),
            blockAlign = readInt16Le(wav, 32),
            bitsPerSample = readInt16Le(wav, 34),
            dataBytes = readInt32Le(wav, 40),
        )
    }

    /** Decodes 16-bit PCM samples from a standard 44-byte RIFF/WAVE buffer, or null if invalid. */
    fun decodePcm16(wav: ByteArray): ShortArray? {
        val info = inspectHeader(wav) ?: return null
        if (info.audioFormat != AUDIO_FORMAT_PCM || info.bitsPerSample != BITS_PER_SAMPLE) return null
        if (info.dataBytes < 0 || info.dataBytes > wav.size - HEADER_BYTES) return null
        val count = info.dataBytes / 2
        return ShortArray(count) { i ->
            val off = HEADER_BYTES + i * 2
            val lo = wav[off].toInt() and 0xFF
            val hi = wav[off + 1].toInt() and 0xFF
            ((hi shl 8) or lo).toShort()
        }
    }

    /**
     * Streams 16-bit signed linear PCM [samples] directly to [file] using a small 16 KiB
     * chunk buffer (avoiding a full-file transient `ByteArray` copy for multi-megabyte loops).
     */
    fun writePcm16ToFile(
        file: File,
        samples: ShortArray,
        sampleRate: Int = AudioDsp.SAMPLE_RATE,
        channels: Int = 1,
    ) {
        require(sampleRate > 0) { "sampleRate must be positive, got $sampleRate" }
        require(channels in 1..2) { "channels must be 1 (mono) or 2 (stereo), got $channels" }
        if (channels == 2) {
            require(samples.size % 2 == 0) { "stereo sample array must have even length, got ${samples.size}" }
        }
        val bytesPerSample = BITS_PER_SAMPLE / 8
        val dataBytes = samples.size * bytesPerSample
        val header = ByteArray(HEADER_BYTES)
        writeHeader(header, sampleRate, channels, dataBytes)

        val buf = ByteArray(IO_CHUNK_BYTES)
        FileOutputStream(file).use { out ->
            out.write(header)
            var idx = 0
            val total = samples.size
            while (idx < total) {
                var bytePos = 0
                while (idx < total && bytePos + 2 <= buf.size) {
                    val v = samples[idx++].toInt()
                    buf[bytePos] = (v and 0xFF).toByte()
                    buf[bytePos + 1] = ((v ushr 8) and 0xFF).toByte()
                    bytePos += 2
                }
                out.write(buf, 0, bytePos)
            }
            out.flush()
        }
    }

    /**
     * Validates a cached WAV [file] against exact PCM-16 format, [expectedSampleRate],
     * [expectedChannels], and [expectedSampleCount], then streams the payload directly into
     * a [ShortArray] with a 16 KiB read buffer. Returns `null` on missing, corrupt,
     * truncated, or mismatched files.
     */
    fun readPcm16FromFile(
        file: File,
        expectedSampleRate: Int,
        expectedChannels: Int,
        expectedSampleCount: Int,
    ): ShortArray? {
        if (expectedSampleRate <= 0 || expectedChannels !in 1..2 || expectedSampleCount <= 0) return null
        if (expectedChannels == 2 && expectedSampleCount % 2 != 0) return null
        if (!file.isFile) return null
        val expectedDataBytes = expectedSampleCount.toLong() * 2L
        if (expectedDataBytes > Int.MAX_VALUE.toLong()) return null
        val expectedFileBytes = HEADER_BYTES.toLong() + expectedDataBytes
        if (file.length() != expectedFileBytes) return null

        FileInputStream(file).use { input ->
            val header = ByteArray(HEADER_BYTES)
            if (!readFully(input, header, HEADER_BYTES)) return null
            val info = inspectHeader(header) ?: return null
            if (info.audioFormat != AUDIO_FORMAT_PCM ||
                info.bitsPerSample != BITS_PER_SAMPLE ||
                info.channels != expectedChannels ||
                info.sampleRate != expectedSampleRate ||
                info.dataBytes != expectedDataBytes.toInt()
            ) {
                return null
            }

            val out = ShortArray(expectedSampleCount)
            val buf = ByteArray(IO_CHUNK_BYTES)
            var sampleIdx = 0
            var remainingBytes = info.dataBytes
            var carry = 0
            while (remainingBytes > 0) {
                val want = minOf(buf.size - carry, remainingBytes)
                val n = input.read(buf, carry, want)
                if (n <= 0) return null
                remainingBytes -= n
                val avail = carry + n
                val usableBytes = avail and 1.inv()
                var off = 0
                while (off < usableBytes) {
                    val lo = buf[off].toInt() and 0xFF
                    val hi = buf[off + 1].toInt() and 0xFF
                    out[sampleIdx++] = ((hi shl 8) or lo).toShort()
                    off += 2
                }
                if (avail != usableBytes) {
                    buf[0] = buf[usableBytes]
                    carry = 1
                } else {
                    carry = 0
                }
            }
            if (carry != 0 || sampleIdx != expectedSampleCount) return null
            return out
        }
    }

    private fun writeHeader(dst: ByteArray, sampleRate: Int, channels: Int, dataBytes: Int) {
        val bytesPerSample = BITS_PER_SAMPLE / 8
        val chunkSize = 36 + dataBytes
        val byteRate = sampleRate * channels * bytesPerSample
        val blockAlign = channels * bytesPerSample
        writeAscii(dst, 0, "RIFF")
        writeInt32Le(dst, 4, chunkSize)
        writeAscii(dst, 8, "WAVE")
        writeAscii(dst, 12, "fmt ")
        writeInt32Le(dst, 16, 16)
        writeInt16Le(dst, 20, AUDIO_FORMAT_PCM)
        writeInt16Le(dst, 22, channels)
        writeInt32Le(dst, 24, sampleRate)
        writeInt32Le(dst, 28, byteRate)
        writeInt16Le(dst, 32, blockAlign)
        writeInt16Le(dst, 34, BITS_PER_SAMPLE)
        writeAscii(dst, 36, "data")
        writeInt32Le(dst, 40, dataBytes)
    }

    private fun readFully(input: FileInputStream, dst: ByteArray, length: Int): Boolean {
        var offset = 0
        while (offset < length) {
            val n = input.read(dst, offset, length - offset)
            if (n <= 0) return false
            offset += n
        }
        return true
    }

    private fun writeAscii(dst: ByteArray, offset: Int, text: String) {
        for (i in text.indices) {
            dst[offset + i] = text[i].code.toByte()
        }
    }

    private fun matchesAscii(src: ByteArray, offset: Int, expected: String): Boolean {
        if (offset + expected.length > src.size) return false
        for (i in expected.indices) {
            if (src[offset + i].toInt().toChar() != expected[i]) return false
        }
        return true
    }

    private fun writeInt16Le(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = (value and 0xFF).toByte()
        dst[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun writeInt32Le(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = (value and 0xFF).toByte()
        dst[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        dst[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        dst[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun readInt16Le(src: ByteArray, offset: Int): Int {
        val b0 = src[offset].toInt() and 0xFF
        val b1 = src[offset + 1].toInt() and 0xFF
        return b0 or (b1 shl 8)
    }

    private fun readInt32Le(src: ByteArray, offset: Int): Int {
        val b0 = src[offset].toInt() and 0xFF
        val b1 = src[offset + 1].toInt() and 0xFF
        val b2 = src[offset + 2].toInt() and 0xFF
        val b3 = src[offset + 3].toInt() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}
