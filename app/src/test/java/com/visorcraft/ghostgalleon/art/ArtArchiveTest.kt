package com.visorcraft.ghostgalleon.art

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.ByteArrayOutputStream

class ArtArchiveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `zip then unzip round-trips files and skips tmp`() {
        val src = tmp.newFolder("art")
        File(src, "a.png").writeBytes(byteArrayOf(1, 2, 3))
        File(src, "b.hero.png").writeBytes(byteArrayOf(9))
        File(src, "skip.tmp").writeBytes(byteArrayOf(7))
        val zip = ByteArrayOutputStream()
        assertEquals(2, ArtArchive.zip(src, zip))
        val dest = tmp.newFolder("out")
        assertEquals(2, ArtArchive.unzip(ByteArrayInputStream(zip.toByteArray()), dest))
        assertArrayEquals(byteArrayOf(1, 2, 3), File(dest, "a.png").readBytes())
        assertArrayEquals(byteArrayOf(9), File(dest, "b.hero.png").readBytes())
        assertFalse(File(dest, "skip.tmp").exists())
    }

    @Test
    fun `unzip rejects parent-path entries`() {
        val dest = tmp.newFolder("safe")
        val zip = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zip).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("../escape.png"))
            z.write(byteArrayOf(1))
            z.closeEntry()
        }
        assertEquals(0, ArtArchive.unzip(ByteArrayInputStream(zip.toByteArray()), dest))
        assertTrue(dest.listFiles()?.isEmpty() != false)
    }
}
