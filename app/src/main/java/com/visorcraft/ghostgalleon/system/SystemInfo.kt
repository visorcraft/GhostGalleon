package com.visorcraft.ghostgalleon.system

import java.util.Locale
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.joinText
import com.visorcraft.ghostgalleon.i18n.quantityText
import com.visorcraft.ghostgalleon.i18n.text

/** Android-independent readings; presentation is emitted as [UiText]. */
data class SystemReadings(
    val manufacturer: String = "",
    val model: String = "",
    val device: String = "",
    val hardware: String = "",
    val androidRelease: String = "",
    val sdkInt: Int = 0,
    val cpuCoreCount: Int = 0,
    val cpuAbi: String = "",
    val cpuMaxMhz: Int? = null,
    val ramTotalBytes: Long = 0L,
    val ramAvailBytes: Long = 0L,
    val internalTotalBytes: Long = 0L,
    val internalFreeBytes: Long = 0L,
    val secondaryName: String? = null,
    val secondaryIsSd: Boolean = true,
    val secondaryTotalBytes: Long? = null,
    val secondaryFreeBytes: Long? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val powerSource: String? = null,
    val powerMicroWatts: Long? = null,
)

object SystemInfoFormat {

    fun formatBytes(bytes: Long): UiText {
        if (bytes < 0L) return text(R.string.label_unavailable)
        if (bytes < 1024L) return text(R.string.system_bytes, bytes)
        val units = intArrayOf(
            R.string.system_kilobytes,
            R.string.system_megabytes,
            R.string.system_gigabytes,
            R.string.system_terabytes,
        )
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return text(units[unit.coerceAtLeast(0)], value)
    }

    fun formatWatts(microWatts: Long?): UiText {
        if (microWatts == null || microWatts < 0L) return text(R.string.label_unavailable)
        return text(R.string.system_watts, microWatts / 1_000_000.0)
    }

    fun formatRam(total: Long, avail: Long): UiText {
        if (total <= 0L) return text(R.string.label_unavailable)
        val used = (total - avail).coerceAtLeast(0L)
        return text(R.string.system_used_total, formatBytes(used), formatBytes(total))
    }

    fun formatStorage(free: Long, total: Long): UiText {
        if (total <= 0L) return text(R.string.label_unavailable)
        return text(R.string.system_free_total, formatBytes(free), formatBytes(total))
    }

    fun formatBattery(percent: Int?, charging: Boolean?, source: String?): UiText {
        if (percent == null || percent !in 0..100) return text(R.string.label_unavailable)
        val charge = when (charging) {
            true -> text(R.string.system_charging)
            false -> text(R.string.system_discharging)
            null -> null
        }
        val power = source
            ?.takeIf { it.isNotBlank() && it != "UNKNOWN" }
            ?.let {
                when (it.uppercase(Locale.ROOT)) {
                    "BATTERY" -> text(R.string.system_power_battery)
                    "AC" -> text(R.string.system_power_ac)
                    "USB" -> text(R.string.system_power_usb)
                    "WIRELESS" -> text(R.string.system_power_wireless)
                    else -> dynamicText(it)
                }
            }
        val tail = listOfNotNull(charge, power)
        return if (tail.isEmpty()) text(R.string.system_battery_percent, percent)
        else text(R.string.system_battery_detail, percent, joinText(tail, ", "))
    }

    fun hardwareLine(r: SystemReadings): UiText {
        val parts = listOf(r.manufacturer, r.model)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return when {
            parts.isNotEmpty() -> dynamicText(parts.joinToString(" "))
            r.device.isNotBlank() -> dynamicText(r.device)
            r.hardware.isNotBlank() -> dynamicText(r.hardware)
            else -> text(R.string.label_unavailable)
        }
    }

    private fun cpuLine(r: SystemReadings): UiText {
        val parts = mutableListOf<UiText>()
        if (r.cpuCoreCount > 0) {
            parts += quantityText(
                R.plurals.system_cpu_cores,
                r.cpuCoreCount,
                r.cpuCoreCount,
            )
        }
        if (r.cpuAbi.isNotBlank()) parts += dynamicText(r.cpuAbi)
        r.cpuMaxMhz?.takeIf { it > 0 }?.let {
            parts += text(R.string.system_cpu_up_to, it)
        }
        return if (parts.isEmpty()) text(R.string.label_unavailable) else joinText(parts, " · ")
    }

    fun rows(r: SystemReadings): List<Pair<UiText, UiText>> {
        val out = mutableListOf<Pair<UiText, UiText>>()
        out += text(R.string.system_hardware) to hardwareLine(r)
        if (r.device.isNotBlank() && r.device != r.model) {
            out += text(R.string.system_device) to dynamicText(r.device)
        }
        if (r.hardware.isNotBlank()) {
            out += text(R.string.system_soc_board) to dynamicText(r.hardware)
        }
        val android = when {
            r.androidRelease.isNotBlank() && r.sdkInt > 0 ->
                text(R.string.system_android_api, r.androidRelease, r.sdkInt)
            r.androidRelease.isNotBlank() -> dynamicText(r.androidRelease)
            r.sdkInt > 0 -> text(R.string.system_api_level, r.sdkInt)
            else -> text(R.string.label_unavailable)
        }
        out += text(R.string.system_android) to android
        out += text(R.string.system_cpu) to cpuLine(r)
        out += text(R.string.system_ram) to formatRam(r.ramTotalBytes, r.ramAvailBytes)
        out += text(R.string.system_internal_storage) to
            formatStorage(r.internalFreeBytes, r.internalTotalBytes)
        val secondaryLabel = when {
            r.secondaryName.isNullOrBlank() -> text(R.string.system_microsd)
            r.secondaryIsSd -> text(R.string.system_microsd_named, r.secondaryName)
            else -> text(R.string.system_storage_named, r.secondaryName)
        }
        if (r.secondaryTotalBytes != null && r.secondaryTotalBytes > 0L) {
            out += secondaryLabel to formatStorage(
                r.secondaryFreeBytes ?: 0L,
                r.secondaryTotalBytes,
            )
        } else {
            out += secondaryLabel to text(R.string.system_not_present)
        }
        out += text(R.string.system_battery) to
            formatBattery(r.batteryPercent, r.charging, r.powerSource)
        out += text(R.string.system_power_draw) to formatWatts(r.powerMicroWatts)
        return out
    }

    fun powerMicroWatts(currentMicroamps: Long?, voltageMillivolts: Int?): Long? {
        if (currentMicroamps == null || voltageMillivolts == null) return null
        if (voltageMillivolts <= 0) return null
        val microamps = kotlin.math.abs(currentMicroamps)
        if (microamps <= 0L) return null
        return microamps * voltageMillivolts / 1000L
    }
}
