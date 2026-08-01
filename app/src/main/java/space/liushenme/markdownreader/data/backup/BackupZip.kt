package space.liushenme.markdownreader.data.backup

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupZip {
    fun zipDirectory(sourceDir: File, zipFile: File) {
        zipFile.parentFile?.mkdirs()
        if (zipFile.exists()) zipFile.delete()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isDirectory) return@forEach
                val relative = file.relativeTo(sourceDir).path.replace('\\', '/')
                zos.putNextEntry(ZipEntry(relative))
                FileInputStream(file).use { input -> input.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    fun unzipTo(zipFile: File, destDir: File) {
        if (destDir.exists()) destDir.deleteRecursively()
        destDir.mkdirs()
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output -> zis.copyTo(output) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
