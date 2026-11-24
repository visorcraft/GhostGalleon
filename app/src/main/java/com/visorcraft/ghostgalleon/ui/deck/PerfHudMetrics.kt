package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.system.SystemReadings
import com.visorcraft.ghostgalleon.ui.UiDimens

/**
 * Pure telemetry meter & semantic severity policy for `PERF_HUD` metric cards.
 * Maps [SystemReadings] rows emitted by [com.visorcraft.ghostgalleon.system.SystemInfoFormat]
 * into normalized 0..1 bar fractions and WCAG-AA-verified green/amber/red status
 * colors. Host-tested.
 */
object PerfHudMetrics {

    enum class Severity {
        GOOD,
        WARN,
        CRITICAL,
    }

    data class MeterSpec(
        val fraction: Float,
        val severity: Severity,
    )

    /** High-contrast emerald green for nominal/charging telemetry on dark cards. */
    const val COLOR_GOOD: Int = 0xFF69F0AE.toInt()

    /** High-contrast warm amber for elevated load / low-battery telemetry. */
    const val COLOR_WARN: Int = 0xFFFFB74D.toInt()

    /** Lifted coral red meeting WCAG AA >= 4.5:1 across all built-in dark themes. */
    const val COLOR_CRITICAL: Int = 0xFFFF6E6E.toInt()

    const val RAM_WARN_FRACTION: Float = 0.75f
    const val RAM_CRITICAL_FRACTION: Float = 0.90f

    const val STORAGE_WARN_FRACTION: Float = 0.80f
    const val STORAGE_CRITICAL_FRACTION: Float = 0.92f

    const val BATTERY_WARN_PERCENT: Int = 20
    const val BATTERY_CRITICAL_PERCENT: Int = 10

    /** Handheld sustained power-draw meter full-scale ceiling in watts. */
    const val POWER_MAX_WATTS: Double = 15.0
    const val POWER_WARN_WATTS: Double = 6.0
    const val POWER_CRITICAL_WATTS: Double = 10.0

    /** Height (dp) of each PERF_HUD card's inline meter bar. */
    const val METER_HEIGHT_DP: Int = 4

    /** Diameter (dp) of the subtle semantic status dot in metered card headers. */
    const val STATUS_DOT_DP: Int = 6

    /** Resolve an optional visual meter for a [SystemInfoFormat.rows] entry by its label resource id. */
    fun meterForRow(label: UiText, readings: SystemReadings): MeterSpec? {
        val resId = (label as? UiText.Resource)?.id ?: return null
        return when (resId) {
            R.string.system_ram -> ramMeter(readings.ramTotalBytes, readings.ramAvailBytes)
            R.string.system_internal_storage ->
                storageMeter(readings.internalFreeBytes, readings.internalTotalBytes)
            R.string.system_microsd,
            R.string.system_microsd_named,
            R.string.system_storage_named,
            -> storageMeter(readings.secondaryFreeBytes, readings.secondaryTotalBytes)
            R.string.system_battery -> batteryMeter(readings.batteryPercent, readings.charging)
            R.string.system_power_draw -> powerMeter(readings.powerMicroWatts)
            else -> null
        }
    }

    fun ramMeter(totalBytes: Long, availBytes: Long): MeterSpec? {
        if (totalBytes <= 0L) return null
        val used = (totalBytes - availBytes).coerceAtLeast(0L)
        val fraction = (used.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        val severity = when {
            fraction >= RAM_CRITICAL_FRACTION -> Severity.CRITICAL
            fraction >= RAM_WARN_FRACTION -> Severity.WARN
            else -> Severity.GOOD
        }
        return MeterSpec(fraction, severity)
    }

    fun storageMeter(freeBytes: Long?, totalBytes: Long?): MeterSpec? {
        if (totalBytes == null || totalBytes <= 0L) return null
        val free = (freeBytes ?: 0L).coerceAtLeast(0L)
        val used = (totalBytes - free).coerceAtLeast(0L)
        val fraction = (used.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        val severity = when {
            fraction >= STORAGE_CRITICAL_FRACTION -> Severity.CRITICAL
            fraction >= STORAGE_WARN_FRACTION -> Severity.WARN
            else -> Severity.GOOD
        }
        return MeterSpec(fraction, severity)
    }

    fun batteryMeter(percent: Int?, charging: Boolean?): MeterSpec? {
        if (percent == null || percent !in 0..100) return null
        val fraction = (percent / 100f).coerceIn(0f, 1f)
        val severity = when {
            charging == true -> Severity.GOOD
            percent <= BATTERY_CRITICAL_PERCENT -> Severity.CRITICAL
            percent <= BATTERY_WARN_PERCENT -> Severity.WARN
            else -> Severity.GOOD
        }
        return MeterSpec(fraction, severity)
    }

    fun powerMeter(microWatts: Long?): MeterSpec? {
        if (microWatts == null || microWatts <= 0L) return null
        val watts = microWatts / 1_000_000.0
        val fraction = (watts / POWER_MAX_WATTS).toFloat().coerceIn(0f, 1f)
        val severity = when {
            watts >= POWER_CRITICAL_WATTS -> Severity.CRITICAL
            watts >= POWER_WARN_WATTS -> Severity.WARN
            else -> Severity.GOOD
        }
        return MeterSpec(fraction, severity)
    }

    /** Meter fill and header status-dot color for [severity]. */
    fun statusColor(severity: Severity): Int = when (severity) {
        Severity.GOOD -> COLOR_GOOD
        Severity.WARN -> COLOR_WARN
        Severity.CRITICAL -> COLOR_CRITICAL
    }

    /** Value text color: crisp primary white while nominal; amber/red on warn/critical. */
    fun valueTextColor(severity: Severity?): Int = when (severity) {
        Severity.WARN -> COLOR_WARN
        Severity.CRITICAL -> COLOR_CRITICAL
        Severity.GOOD, null -> UiDimens.ALPHA_PRIMARY
    }
}
