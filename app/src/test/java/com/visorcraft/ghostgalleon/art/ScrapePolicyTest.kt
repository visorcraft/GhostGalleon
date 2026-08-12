package com.visorcraft.ghostgalleon.art

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.resourceIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrapePolicyTest {

    private val wifi = ScrapePolicy.NetworkSnapshot(
        connected = true,
        onWifi = true,
        metered = false,
    )
    private val cellular = ScrapePolicy.NetworkSnapshot(
        connected = true,
        onWifi = false,
        metered = true,
    )
    private val offline = ScrapePolicy.NetworkSnapshot(
        connected = false,
        onWifi = false,
        metered = true,
    )
    private val charged = ScrapePolicy.BatterySnapshot(percent = 80, charging = false)
    private val low = ScrapePolicy.BatterySnapshot(percent = 10, charging = false)
    private val lowCharging = ScrapePolicy.BatterySnapshot(percent = 10, charging = true)

    @Test
    fun `allows scrape on wifi with healthy battery`() {
        assertEquals(
            ScrapePolicy.Decision.Allow,
            ScrapePolicy.mayRun(wifi, charged),
        )
    }

    @Test
    fun `blocks offline`() {
        val d = ScrapePolicy.mayRun(offline, charged)
        assertTrue(d is ScrapePolicy.Decision.Block)
        assertEquals(
            ScrapePolicy.BlockReason.NO_NETWORK,
            (d as ScrapePolicy.Decision.Block).reason,
        )
    }

    @Test
    fun `wifiOnly blocks metered cellular`() {
        val d = ScrapePolicy.mayRun(
            cellular,
            charged,
            ScrapePolicy.Preferences(wifiOnly = true),
        )
        assertEquals(
            ScrapePolicy.BlockReason.WIFI_ONLY,
            (d as ScrapePolicy.Decision.Block).reason,
        )
        assertEquals(
            ScrapePolicy.Decision.Allow,
            ScrapePolicy.mayRun(
                cellular,
                charged,
                ScrapePolicy.Preferences(wifiOnly = false),
            ),
        )
    }

    @Test
    fun `low battery blocks when not charging`() {
        val d = ScrapePolicy.mayRun(
            wifi,
            low,
            ScrapePolicy.Preferences(pauseBelowPercent = 15),
        )
        assertEquals(
            ScrapePolicy.BlockReason.LOW_BATTERY,
            (d as ScrapePolicy.Decision.Block).reason,
        )
        assertEquals(
            ScrapePolicy.Decision.Allow,
            ScrapePolicy.mayRun(wifi, lowCharging),
        )
        assertEquals(
            ScrapePolicy.Decision.Allow,
            ScrapePolicy.mayRun(
                wifi,
                low,
                ScrapePolicy.Preferences(pauseBelowPercent = 0),
            ),
        )
    }

    @Test
    fun `block messages use distinct string resources`() {
        val ids = ScrapePolicy.BlockReason.entries.map {
            ScrapePolicy.blockMessage(it).resourceIds().single()
        }.toSet()
        assertEquals(3, ids.size)
        assertTrue(R.string.scrape_blocked_no_network in ids)
    }
}
