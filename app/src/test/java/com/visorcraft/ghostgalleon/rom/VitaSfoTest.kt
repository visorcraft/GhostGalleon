package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VitaSfoTest {

    @Test
    fun `parse reads TITLE_ID and TITLE`() {
        val bytes = packSfo(
            listOf(
                "TITLE_ID" to "PCSE00001",
                "TITLE" to "Uncharted Golden Abyss",
            ),
        )
        val info = VitaSfo.parse(bytes)!!
        assertEquals("PCSE00001", info.titleId)
        assertEquals("Uncharted Golden Abyss", info.title)
    }

    @Test
    fun `garbage is null`() {
        assertNull(VitaSfo.parse(ByteArray(8)))
        assertNull(VitaSfo.parse("not an sfo".toByteArray()))
    }

    @Test
    fun `vpk zip yields param sfo`() {
        val sfo = packSfo(listOf("TITLE_ID" to "PCSG12345", "TITLE" to "Game"))
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { zos ->
            zos.putNextEntry(ZipEntry("sce_sys/param.sfo"))
            zos.write(sfo)
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("eboot.bin"))
            zos.write(byteArrayOf(1, 2, 3))
            zos.closeEntry()
        }
        val extracted = VitaVpk.paramSfo(ByteArrayInputStream(zip.toByteArray()))!!
        assertEquals("PCSG12345", VitaSfo.parse(extracted)!!.titleId)
    }

    private fun packSfo(fields: List<Pair<String, String>>): ByteArray {
        val keys = fields.map { (k, _) -> (k + '\u0000').toByteArray(Charsets.UTF_8) }
        val values = fields.map { (_, v) -> (v + '\u0000').toByteArray(Charsets.UTF_8) }
        val keyTable = keys.fold(ByteArray(0)) { acc, b -> acc + b }
        val dataTable = values.fold(ByteArray(0)) { acc, b -> acc + b }
        val keyOff = 20 + 16 * fields.size
        val dataOff = keyOff + keyTable.size
        val out = ByteArray(dataOff + dataTable.size)
        out[1] = 'P'.code.toByte()
        out[2] = 'S'.code.toByte()
        out[3] = 'F'.code.toByte()
        put32(out, 4, 0x00000101)
        put32(out, 8, keyOff)
        put32(out, 12, dataOff)
        put32(out, 16, fields.size)
        var kRel = 0
        var dRel = 0
        fields.indices.forEach { i ->
            val base = 20 + i * 16
            put16(out, base, kRel)
            put16(out, base + 2, 0x0204)
            put32(out, base + 4, values[i].size)
            put32(out, base + 8, values[i].size)
            put32(out, base + 12, dRel)
            kRel += keys[i].size
            dRel += values[i].size
        }
        System.arraycopy(keyTable, 0, out, keyOff, keyTable.size)
        System.arraycopy(dataTable, 0, out, dataOff, dataTable.size)
        return out
    }

    private fun put16(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun put32(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v ushr 8) and 0xFF).toByte()
        b[off + 2] = ((v ushr 16) and 0xFF).toByte()
        b[off + 3] = ((v ushr 24) and 0xFF).toByte()
    }
}
