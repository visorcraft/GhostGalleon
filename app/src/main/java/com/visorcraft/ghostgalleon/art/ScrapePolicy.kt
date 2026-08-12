package com.visorcraft.ghostgalleon.art

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.UiText
import com.visorcraft.ghostgalleon.i18n.text

/**
 * Pure “may the SteamGridDB scrape run now?” policy.
 * Host-tested; Android only supplies [NetworkSnapshot] / [BatterySnapshot].
 */
object ScrapePolicy {

    data class NetworkSnapshot(
        val connected: Boolean,
        /** True when the active network is Wi‑Fi (or Ethernet treated as unmetered). */
        val onWifi: Boolean,
        val metered: Boolean,
    )

    data class BatterySnapshot(
        /** 0..100, or null if unknown. */
        val percent: Int?,
        val charging: Boolean,
    )

    data class Preferences(
        /** When true, refuse scrape on cellular / metered links. */
        val wifiOnly: Boolean = true,
        /**
         * Pause when battery is at or below this percent and not charging.
         * 0 disables the battery gate.
         */
        val pauseBelowPercent: Int = 15,
    )

    enum class BlockReason {
        NO_NETWORK,
        WIFI_ONLY,
        LOW_BATTERY,
    }

    sealed class Decision {
        data object Allow : Decision()
        data class Block(val reason: BlockReason) : Decision()
    }

    fun mayRun(
        network: NetworkSnapshot,
        battery: BatterySnapshot,
        prefs: Preferences = Preferences(),
    ): Decision {
        if (!network.connected) return Decision.Block(BlockReason.NO_NETWORK)
        if (prefs.wifiOnly && (network.metered || !network.onWifi)) {
            return Decision.Block(BlockReason.WIFI_ONLY)
        }
        val floor = prefs.pauseBelowPercent
        if (floor > 0) {
            val pct = battery.percent
            if (pct != null && pct in 0..100 && pct <= floor && !battery.charging) {
                return Decision.Block(BlockReason.LOW_BATTERY)
            }
        }
        return Decision.Allow
    }

    fun blockMessage(reason: BlockReason): UiText = when (reason) {
        BlockReason.NO_NETWORK -> text(R.string.scrape_blocked_no_network)
        BlockReason.WIFI_ONLY -> text(R.string.scrape_blocked_wifi_only)
        BlockReason.LOW_BATTERY -> text(R.string.scrape_blocked_low_battery)
    }
}
