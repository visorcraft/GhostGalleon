package com.visorcraft.ghostgalleon.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Exhaustive pure JVM tests for [CustomSoundPackMatcher].
 */
class CustomSoundPackMatcherTest {

    @Test
    fun `every one of the 24 SoundCues matches canonical name and all declared friendly aliases case-insensitively`() {
        assertEquals("all 24 cues must have alias definitions", SoundCue.entries.size, CustomSoundPackMatcher.CUE_ALIASES.size)

        // Ensure zero alias collisions across cues or ambient stems
        val seenStems = mutableMapOf<String, String>()
        for ((cue, aliases) in CustomSoundPackMatcher.CUE_ALIASES) {
            for (alias in aliases) {
                val prev = seenStems.put(alias, cue.name)
                assertNull("duplicate alias '$alias' shared by $prev and ${cue.name}", prev)
            }
        }
        for (amb in CustomSoundPackMatcher.AMBIENT_ALIASES) {
            assertNull("ambient alias '$amb' collides with cue ${seenStems[amb]}", seenStems[amb])
        }

        // Verify canonical name and friendly aliases across all supported extensions and mixed casing
        for (cue in SoundCue.entries) {
            val canonical = cue.name.lowercase(Locale.ROOT)
            assertEquals(cue, CustomSoundPackMatcher.matchCue("$canonical.wav"))
            assertEquals(cue, CustomSoundPackMatcher.matchCue("${canonical.uppercase(Locale.ROOT)}.OGG"))
            assertEquals(cue, CustomSoundPackMatcher.matchCue("${canonical.replace('_', '-')}.mp3"))
            assertEquals(cue, CustomSoundPackMatcher.matchCue("${canonical.replace('_', ' ')}.flac"))
            assertEquals(cue, CustomSoundPackMatcher.matchCue("$canonical.m4a"))

            val aliases = CustomSoundPackMatcher.CUE_ALIASES.getValue(cue)
            assertTrue("$cue must have at least one alias", aliases.isNotEmpty())
            for (alias in aliases) {
                assertEquals("alias '$alias' failed for $cue", cue, CustomSoundPackMatcher.matchCue("$alias.wav"))
                assertEquals(
                    "uppercase alias '$alias' failed for $cue",
                    cue,
                    CustomSoundPackMatcher.matchCue("${alias.uppercase(Locale.ROOT)}.ogg"),
                )
            }
        }

        // Spot-check key user-friendly aliases explicitly
        assertEquals(SoundCue.NAV_TICK, CustomSoundPackMatcher.matchCue("nav.wav"))
        assertEquals(SoundCue.NAV_TICK, CustomSoundPackMatcher.matchCue("tick.ogg"))
        assertEquals(SoundCue.NAV_TICK, CustomSoundPackMatcher.matchCue("cursor.mp3"))
        assertEquals(SoundCue.NAV_EDGE_BUMP, CustomSoundPackMatcher.matchCue("bump.wav"))
        assertEquals(SoundCue.NAV_PAGE_TURN, CustomSoundPackMatcher.matchCue("page.wav"))
        assertEquals(SoundCue.DOCK_ENTER, CustomSoundPackMatcher.matchCue("dock.wav"))
        assertEquals(SoundCue.CONFIRM, CustomSoundPackMatcher.matchCue("select.wav"))
        assertEquals(SoundCue.CONFIRM, CustomSoundPackMatcher.matchCue("ok.ogg"))
        assertEquals(SoundCue.LAUNCH_GAME, CustomSoundPackMatcher.matchCue("launch.wav"))
        assertEquals(SoundCue.LAUNCH_GAME, CustomSoundPackMatcher.matchCue("start.wav"))
        assertEquals(SoundCue.BACK, CustomSoundPackMatcher.matchCue("cancel.wav"))
        assertEquals(SoundCue.SWAP_DISPLAY, CustomSoundPackMatcher.matchCue("swap.wav"))
        assertEquals(SoundCue.MODE_TOGGLE, CustomSoundPackMatcher.matchCue("mode.wav"))
        assertEquals(SoundCue.MODAL_OPEN, CustomSoundPackMatcher.matchCue("open.wav"))
        assertEquals(SoundCue.MODAL_CLOSE, CustomSoundPackMatcher.matchCue("close.wav"))
        assertEquals(SoundCue.FAVORITE_ON, CustomSoundPackMatcher.matchCue("fav.wav"))
        assertEquals(SoundCue.FAVORITE_OFF, CustomSoundPackMatcher.matchCue("unfav.wav"))
        assertEquals(SoundCue.MOVE_PICKUP, CustomSoundPackMatcher.matchCue("pickup.wav"))
        assertEquals(SoundCue.MOVE_DROP, CustomSoundPackMatcher.matchCue("drop.wav"))
        assertEquals(SoundCue.DESTRUCTIVE, CustomSoundPackMatcher.matchCue("delete.wav"))
        assertEquals(SoundCue.FILTER_SELECT, CustomSoundPackMatcher.matchCue("filter.wav"))
        assertEquals(SoundCue.ERROR_DENY, CustomSoundPackMatcher.matchCue("deny.wav"))
        assertEquals(SoundCue.BOOT_CHIME, CustomSoundPackMatcher.matchCue("boot.wav"))
        assertEquals(SoundCue.RA_ACHIEVEMENT, CustomSoundPackMatcher.matchCue("achievement.wav"))
        assertEquals(SoundCue.RA_ACHIEVEMENT, CustomSoundPackMatcher.matchCue("ra.ogg"))
        assertEquals(SoundCue.SETUP_COMPLETE, CustomSoundPackMatcher.matchCue("setup.wav"))

        assertTrue(CustomSoundPackMatcher.matchesAmbient("ambient.ogg"))
        assertTrue(CustomSoundPackMatcher.matchesAmbient("BGM.FLAC"))
        assertFalse(CustomSoundPackMatcher.matchesAmbient("._ambient.wav"))
    }

    @Test
    fun `hidden dotfiles, path traversal, non-audio extensions, and empty or oversize files are rejected`() {
        val rejectedNames = listOf(
            "",
            "   ",
            ".DS_Store",
            "._nav.wav",
            ".confirm.ogg",
            "../nav.wav",
            "sub/nav.wav",
            "sub\\nav.wav",
            "nav.txt",
            "confirm.png",
            "boot.zip",
            "nav",
            "nav.",
            "unknown_sound.wav",
        )
        for (bad in rejectedNames) {
            assertNull("expected rejection for '$bad'", CustomSoundPackMatcher.matchCue(bad))
        }

        val candidates = listOf(
            CustomSoundCandidate("._nav.wav", "content://1", 1024),
            CustomSoundCandidate(".DS_Store", "content://2", 512),
            CustomSoundCandidate("readme.txt", "content://3", 256),
            CustomSoundCandidate("random_clip.wav", "content://4", 1024),
            CustomSoundCandidate("confirm.wav", "content://5", CustomSoundPackMatcher.MAX_SFX_BYTES + 1),
            CustomSoundCandidate("ambient.ogg", "content://6", CustomSoundPackMatcher.MAX_AMBIENT_BYTES + 1),
            CustomSoundCandidate("back.wav", "content://7", 0L),
            CustomSoundCandidate("subfolder", "content://8", 0L, isDirectory = true),
            CustomSoundCandidate("nav.wav", "content://9", CustomSoundPackMatcher.MAX_SFX_BYTES), // exact max allowed!
            CustomSoundCandidate("bgm.ogg", "content://10", CustomSoundPackMatcher.MAX_AMBIENT_BYTES), // exact max allowed!
        )

        val res = CustomSoundPackMatcher.scanCandidates(candidates)
        assertEquals(1, res.totalMatched)
        assertEquals(1, res.cueEntries.size)
        assertEquals("content://9", res.cueEntries[SoundCue.NAV_TICK]?.uriString)
        assertEquals("content://10", res.ambientEntry?.uriString)
        assertEquals(2, res.skippedOversize)
        assertEquals(5, res.skippedUnsupported) // ._nav, .DS_Store, readme, random_clip, 0-byte back.wav (dir ignored)
    }

    @Test
    fun `format precedence selects wav over ogg over flac over mp3 over m4a regardless of listing order`() {
        // Reverse precedence order in input to verify deterministic sorting
        val candidates = listOf(
            CustomSoundCandidate("confirm.m4a", "content://m4a", 4096),
            CustomSoundCandidate("confirm.mp3", "content://mp3", 4096),
            CustomSoundCandidate("confirm.flac", "content://flac", 4096),
            CustomSoundCandidate("confirm.ogg", "content://ogg", 4096),
            CustomSoundCandidate("confirm.wav", "content://wav", 4096),
        )
        assertEquals("content://wav", CustomSoundPackMatcher.scanCandidates(candidates).cueEntries[SoundCue.CONFIRM]?.uriString)

        val noWav = candidates.filterNot { it.displayName.endsWith(".wav") }
        assertEquals("content://ogg", CustomSoundPackMatcher.scanCandidates(noWav).cueEntries[SoundCue.CONFIRM]?.uriString)

        val noOgg = noWav.filterNot { it.displayName.endsWith(".ogg") }
        assertEquals("content://flac", CustomSoundPackMatcher.scanCandidates(noOgg).cueEntries[SoundCue.CONFIRM]?.uriString)

        val noFlac = noOgg.filterNot { it.displayName.endsWith(".flac") }
        assertEquals("content://mp3", CustomSoundPackMatcher.scanCandidates(noFlac).cueEntries[SoundCue.CONFIRM]?.uriString)

        val onlyM4a = noFlac.filterNot { it.displayName.endsWith(".mp3") }
        assertEquals("content://m4a", CustomSoundPackMatcher.scanCandidates(onlyM4a).cueEntries[SoundCue.CONFIRM]?.uriString)

        // When extensions tie, canonical cue name wins over shorthand alias regardless of order
        val aliasVsCanonical = listOf(
            CustomSoundCandidate("tick.wav", "content://alias", 2048),
            CustomSoundCandidate("nav_tick.wav", "content://canonical", 2048),
        )
        assertEquals(
            "content://canonical",
            CustomSoundPackMatcher.scanCandidates(aliasVsCanonical).cueEntries[SoundCue.NAV_TICK]?.uriString,
        )
        assertEquals(
            "content://canonical",
            CustomSoundPackMatcher.scanCandidates(aliasVsCanonical.reversed()).cueEntries[SoundCue.NAV_TICK]?.uriString,
        )
    }

    @Test
    fun `resolveWithFallback and cache naming preserve per-cue fallback and path-safe deterministic keys`() {
        val custom = mapOf(
            SoundCue.CONFIRM to "/cache/custom/confirm.wav",
            SoundCue.BOOT_CHIME to "/cache/custom/boot.ogg",
        )
        val procedural = SoundCue.entries.associateWith { cue -> "/cache/proc/${cue.name.lowercase(Locale.ROOT)}.wav" }
        val merged = CustomSoundPackMatcher.resolveWithFallback(custom, procedural)

        assertEquals(SoundCue.entries.size, merged.size)
        assertEquals("/cache/custom/confirm.wav", merged[SoundCue.CONFIRM])
        assertEquals("/cache/custom/boot.ogg", merged[SoundCue.BOOT_CHIME])
        assertEquals("/cache/proc/nav_tick.wav", merged[SoundCue.NAV_TICK])
        assertEquals("/cache/proc/ra_achievement.wav", merged[SoundCue.RA_ACHIEVEMENT])

        val dirA = CustomSoundPackMatcher.cacheFolderName("content://com.android.externalstorage/tree/primary:Sounds/PackA")
        val dirA2 = CustomSoundPackMatcher.cacheFolderName("content://com.android.externalstorage/tree/primary:Sounds/PackA")
        val dirB = CustomSoundPackMatcher.cacheFolderName("content://com.android.externalstorage/tree/primary:Sounds/PackB")
        assertEquals(dirA, dirA2)
        assertNotEquals(dirA, dirB)
        assertTrue(dirA.startsWith("custom_"))
        assertFalse(dirA.contains('/'))
        assertFalse(dirA.contains(".."))

        val entry = CustomSoundEntry("nav.WAV", "content://1", "wav", 12345L, 9999L)
        val cachedName = CustomSoundPackMatcher.cachedCueFileName(SoundCue.NAV_TICK, entry)
        assertEquals("nav_tick_12345_9999.wav", cachedName)
        assertNotNull(CustomSoundPackMatcher.cachedAmbientFileName(entry))

        // Empty candidate list returns cleanly empty result
        val empty = CustomSoundPackMatcher.scanCandidates(emptyList())
        assertEquals(0, empty.totalMatched)
        assertTrue(empty.cueEntries.isEmpty())
        assertNull(empty.ambientEntry)
        assertEquals(0, empty.skippedOversize)
        assertEquals(0, empty.skippedUnsupported)
    }
}
