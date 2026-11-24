package com.visorcraft.ghostgalleon.audio

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
