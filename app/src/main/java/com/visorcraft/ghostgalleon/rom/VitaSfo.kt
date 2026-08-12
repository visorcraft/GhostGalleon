package com.visorcraft.ghostgalleon.rom

/**
 * PlayStation Vita / PSP `param.sfo` reader. Pure; host-tested.
 */
object VitaSfo {

    data class Info(val titleId: String?, val title: String?)

    fun parse(bytes: ByteArray): Info? {
        if (bytes.size < 20) return null
        if (bytes[0] != 0.toByte() || bytes[1] != 'P'.code.toByte() ||
            bytes[2] != 'S'.code.toByte() || bytes[3] != 'F'.code.toByte()
        ) {
            return null
        }
        val keyOff = u32(bytes, 8)
        val dataOff = u32(bytes, 12)
        val count = u32(bytes, 16)
        if (count <= 0 || count > 256) return null
        var titleId: String? = null
        var title: String? = null
        var i = 0
        while (i < count) {
            val base = 20 + i * 16
            if (base + 16 > bytes.size) break
            val kRel = u16(bytes, base)
            val fmt = u16(bytes, base + 2)
            val len = u32(bytes, base + 4)
            val dRel = u32(bytes, base + 12)
            val keyStart = keyOff + kRel
            val dataStart = dataOff + dRel
            if (keyStart !in bytes.indices || dataStart !in bytes.indices) {
                i++
                continue
            }
            val key = cString(bytes, keyStart) ?: break
            if (isStringFmt(fmt) && len > 0 && dataStart + len <= bytes.size) {
                val raw = bytes.copyOfRange(dataStart, dataStart + len)
                val value = String(raw, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
                when (key) {
                    "TITLE_ID" -> titleId = value.ifBlank { null }?.uppercase()
                    "TITLE" -> title = value.ifBlank { null }
                }
            }
            i++
        }
        if (titleId == null && title == null) return null
        return Info(titleId, title)
    }

    private fun isStringFmt(fmt: Int): Boolean =
        fmt == 0x0004 || fmt == 0x0204 || fmt == 0x0404

    private fun u16(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun u32(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)

    private fun cString(b: ByteArray, start: Int): String? {
        var end = start
        while (end < b.size && b[end] != 0.toByte()) end++
        if (end == start) return ""
        return String(b, start, end - start, Charsets.UTF_8)
    }
}
