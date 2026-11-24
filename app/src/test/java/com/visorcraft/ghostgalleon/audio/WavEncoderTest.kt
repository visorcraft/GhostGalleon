package com.visorcraft.ghostgalleon.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WavEncoderTest {

    @Test
    fun `encodePcm16 writes spec-compliant mono RIFF WAVE header and payload`() {
        val samples = shortArrayOf(0, 1234, -1234, 32767, -32768, 42)
        val wav = WavEncoder.encodePcm16(samples, sampleRate = 44100, channels = 1)

        assertEquals(WavEncoder.HEADER_BYTES + samples.size * 2, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))

        // ChunkSize at bytes 4..7 == 36 + dataBytes
        val expectedChunkSize = 36 + samples.size * 2
        assertEquals(expectedChunkSize, readLe32(wav, 4))
        assertEquals(16, readLe32(wav, 16)) // PCM subchunk1 size

        val info = WavEncoder.inspectHeader(wav)
        assertNotNull(info)
        assertEquals(1, info!!.audioFormat)
        assertEquals(1, info.channels)
        assertEquals(44100, info.sampleRate)
        assertEquals(88200, info.byteRate)
        assertEquals(2, info.blockAlign)
        assertEquals(16, info.bitsPerSample)
        assertEquals(samples.size * 2, info.dataBytes)
        assertEquals(samples.size, info.sampleCount)

        val decoded = WavEncoder.decodePcm16(wav)
        assertNotNull(decoded)
        assertArrayEquals(samples, decoded)
    }

    @Test
    fun `encodePcm16 round-trips stereo interleaved PCM and synthesized cues`() {
        val stereo = PcmSynth.synthesizeStereo(SoundCue.CONFIRM, SoundPackId.GHOST, pan = 0.25f)
        val wav = WavEncoder.encodePcm16(stereo, sampleRate = 44100, channels = 2)

        val info = WavEncoder.inspectHeader(wav)
        assertNotNull(info)
        assertEquals(2, info!!.channels)
        assertEquals(44100 * 4, info.byteRate)
        assertEquals(4, info.blockAlign)
        assertEquals(stereo.size, info.sampleCount)
        assertArrayEquals(stereo, WavEncoder.decodePcm16(wav))
    }

    @Test
    fun `empty sample array encodes valid 44-byte WAV header`() {
        val wav = WavEncoder.encodePcm16(ShortArray(0), sampleRate = 44100, channels = 1)
        assertEquals(44, wav.size)
        assertEquals(36, readLe32(wav, 4))
        assertEquals(0, readLe32(wav, 40))
        assertArrayEquals(ShortArray(0), WavEncoder.decodePcm16(wav))
    }

    @Test
    fun `invalid arguments and malformed headers are rejected cleanly`() {
        assertThrows(IllegalArgumentException::class.java) {
            WavEncoder.encodePcm16(shortArrayOf(0), sampleRate = 0, channels = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WavEncoder.encodePcm16(shortArrayOf(0), sampleRate = 44100, channels = 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WavEncoder.encodePcm16(shortArrayOf(1, 2, 3), sampleRate = 44100, channels = 2)
        }

        assertNull(WavEncoder.inspectHeader(ByteArray(10)))
        assertNull(WavEncoder.inspectHeader(ByteArray(44)))
        assertNull(WavEncoder.decodePcm16(ByteArray(44)))
    }

    @Test
    fun `decodePcm16 rejects corrupt headers with overflow-inducing dataBytes cleanly`() {
        val base = WavEncoder.encodePcm16(shortArrayOf(10, 20), sampleRate = 44100, channels = 1)
        val corruptSizes = listOf(
            Int.MAX_VALUE,
            Int.MAX_VALUE - 10,
            Int.MAX_VALUE - WavEncoder.HEADER_BYTES + 1,
            base.size - WavEncoder.HEADER_BYTES + 2,
            -2,
            Int.MIN_VALUE,
        )
        for (badDataBytes in corruptSizes) {
            val corrupt = base.copyOf()
            writeLe32(corrupt, 40, badDataBytes)
            assertNull("expected null for corrupt dataBytes=$badDataBytes", WavEncoder.decodePcm16(corrupt))
        }
    }

    private fun writeLe32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun readLe32(bytes: ByteArray, offset: Int): Int {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        val b3 = bytes[offset + 3].toInt() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}
