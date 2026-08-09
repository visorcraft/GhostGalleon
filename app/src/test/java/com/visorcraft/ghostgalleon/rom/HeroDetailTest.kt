package com.visorcraft.ghostgalleon.rom

import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.i18n.dynamicText
import com.visorcraft.ghostgalleon.i18n.joinText
import com.visorcraft.ghostgalleon.i18n.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroDetailTest {

    @Test
    fun `descriptionText trims and blanks to null`() {
        assertEquals("Hello", HeroDetail.descriptionText("  Hello  "))
        assertNull(HeroDetail.descriptionText("   "))
        assertNull(HeroDetail.descriptionText(null))
    }

    @Test
    fun `playerLine reports installed preferred player`() {
        val platform = Platforms.SNES
        val pref = platform.players.first().id
        val line = HeroDetail.playerLine(platform, pref) { true }
        assertEquals(
            text(R.string.format_player, platform.players.first().displayName),
            line,
        )
    }

    @Test
    fun `playerLine not installed when none match`() {
        val platform = Platforms.SNES
        val line = HeroDetail.playerLine(platform, platform.players.first().id) { false }
        assertEquals(
            text(
                R.string.format_player_not_installed,
                platform.players.first().displayName,
            ),
            line,
        )
    }

    @Test
    fun `screenshotUri reads rom field`() {
        val rom = RomEntry(
            id = "snes:x.smc",
            name = "x",
            platformId = "snes",
            uri = "content://r",
            path = null,
            screenshotUri = "content://shot",
        )
        assertEquals("content://shot", HeroDetail.screenshotUri(rom))
        assertNull(HeroDetail.screenshotUri(rom.copy(screenshotUri = null)))
    }

    @Test
    fun `metadataLine joins year genre developer rating`() {
        val rom = RomEntry(
            id = "snes:x.smc",
            name = "x",
            platformId = "snes",
            uri = "content://r",
            path = null,
            year = "1995",
            genre = "RPG",
            developer = "Square",
            rating = "4.5",
        )
        assertEquals("1995 · RPG · Square · ★ 4.5", HeroDetail.metadataLine(rom))
        assertNull(HeroDetail.metadataLine(rom.copy(year = null, genre = null, developer = null, rating = null)))
    }

    @Test
    fun `videoUri reads rom field`() {
        val rom = RomEntry(
            id = "snes:x.smc",
            name = "x",
            platformId = "snes",
            uri = "content://r",
            path = null,
            videoUri = "content://vid/x.mp4",
        )
        assertEquals("content://vid/x.mp4", HeroDetail.videoUri(rom))
        assertNull(HeroDetail.videoUri(rom.copy(videoUri = "  ")))
    }

    @Test
    fun `compactSubline joins platform play and player`() {
        assertEquals(
            joinText(
                listOf(
                    dynamicText("Nintendo DS"),
                    text(R.string.stats_never_played),
                    dynamicText("melonDualDS"),
                ),
                " · ",
            ),
            HeroDetail.compactSubline(
                "Nintendo DS",
                text(R.string.stats_never_played),
                "melonDualDS",
            ),
        )
        assertEquals(
            joinText(listOf(dynamicText("SNES"), dynamicText("melonDS")), " · "),
            HeroDetail.compactSubline("SNES", null, "melonDS"),
        )
        assertEquals(joinText(emptyList(), " · "), HeroDetail.compactSubline(null, null, null))
    }

    @Test
    fun `playerShortName strips Player prefix`() {
        val platform = Platforms.SNES
        val pref = platform.players.first().id
        val short = HeroDetail.playerShortName(platform, pref) { true }
        assertEquals(platform.players.first().displayName, short)
    }
}
