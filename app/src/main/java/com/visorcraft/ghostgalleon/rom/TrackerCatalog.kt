package com.visorcraft.ghostgalleon.rom

enum class TrackerKind { BITS, GRID, METER, LINE }

data class TrackerWidget(
    val kind: TrackerKind,
    val blockIndex: Int,
    val cols: Int,
    val labels: List<String>,
)

object TrackerCatalog {
    fun parseKind(raw: String?): TrackerKind = when (raw?.trim()?.lowercase()) {
        "bits" -> TrackerKind.BITS
        "grid" -> TrackerKind.GRID
        "meter" -> TrackerKind.METER
        else -> TrackerKind.LINE
    }

    fun acceptable(spec: LensSpec): Boolean {
        if (!LensCatalog.acceptable(spec)) return false
        if (spec.widgets.isEmpty()) return true
        return spec.widgets.all { w ->
            w.blockIndex in spec.blocks.indices &&
                (w.kind != TrackerKind.BITS ||
                    w.labels.size <= spec.blocks[w.blockIndex].length * 8)
        }
    }

    fun bitOn(bytes: ByteArray, index: Int): Boolean {
        val byteIndex = index / 8
        if (index < 0 || byteIndex >= bytes.size) return false
        val bit = index % 8
        return ((bytes[byteIndex].toInt() ushr bit) and 1) == 1
    }

    fun meterValue(bytes: ByteArray): Int =
        if (bytes.isEmpty()) 0 else bytes[0].toInt() and 0xFF
}
