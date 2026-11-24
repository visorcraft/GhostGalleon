package com.visorcraft.ghostgalleon.ui.deck

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanionA11yTest {

    @Test
    fun `metricCardDescription joins label and value for single-swipe TalkBack announcements`() {
        assertEquals("RAM: 6.4 GB / 12.0 GB", CompanionA11y.metricCardDescription("RAM", "6.4 GB / 12.0 GB"))
        assertEquals("Akku: 82%", CompanionA11y.metricCardDescription(" Akku ", " 82% "))
        assertEquals("RAM", CompanionA11y.metricCardDescription("RAM", "  "))
        assertEquals("82%", CompanionA11y.metricCardDescription("", "82%"))
    }

    @Test
    fun `plainHintDescription normalizes double spacing around middots across locales`() {
        assertEquals(
            "A · Launch · Y / long-press · Remove · B · Close",
            CompanionA11y.plainHintDescription("A · Launch  ·  Y / long-press · Remove  ·  B · Close"),
        )
        assertEquals(
            "A เปิด · Y/กดค้าง ลบ · B ปิด",
            CompanionA11y.plainHintDescription("  A เปิด ·  Y/กดค้าง ลบ · B ปิด  "),
        )
    }

    @Test
    fun `heroSummary combines title, subtitle, and optional RA line without duplicating labels`() {
        assertEquals(
            "Chrono Trigger, Super Nintendo, RA 14 / 20 (70%)",
            CompanionA11y.heroSummary("Chrono Trigger", "Super Nintendo", "RA 14 / 20 (70%)"),
        )
        assertEquals(
            "Settings",
            CompanionA11y.heroSummary("Settings", "Settings", null),
        )
        assertEquals(
            "Super Metroid, Super Nintendo",
            CompanionA11y.heroSummary("Super Metroid", "Super Nintendo", "   "),
        )
    }
}
