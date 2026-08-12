package com.visorcraft.ghostgalleon.art

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Zip / unzip the private art disk cache. Filenames stay as on disk
 * (hashed ids). Host-tested on a temp dir.
 */
object ArtArchive {

    /** Write every regular file under [dir] into [out]. Returns file count. */
    fun zip(dir: File, out: OutputStream): Int {
        if (!dir.isDirectory) return 0
        var count = 0
        ZipOutputStream(out).use { zip ->
            dir.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { file ->
                val rel = file.relativeTo(dir).invariantSeparatorsPath
                if (rel.startsWith("..") || rel.contains("/../")) return@forEach
                zip.putNextEntry(ZipEntry(rel))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                count++
            }
        }
        return count
    }

    /** Extract [input] into [dir]. Skips `..` entries. Returns extracted count. */
    fun unzip(input: InputStream, dir: File): Int {
        dir.mkdirs()
        var count = 0
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.trimStart('/').replace('\\', '/')
                if (name.isNotEmpty() && !name.contains("..") && !entry.isDirectory) {
                    val target = File(dir, name)
                    if (target.canonicalPath.startsWith(dir.canonicalPath)) {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                        count++
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return count
    }
}
