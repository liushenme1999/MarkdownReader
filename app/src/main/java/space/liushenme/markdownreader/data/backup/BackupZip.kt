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
        val destRoot = destDir.canonicalFile
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                // 防 zip-slip：备份包/第三方来源的 zip 可能带 `../` 或绝对路径条目，
                // 规范化后必须仍落在目标目录内，否则直接跳过（不能写到文件系统别处）。
                val outFile = File(destRoot, entry.name).canonicalFile
                val insideDest = outFile != destRoot &&
                    outFile.path.startsWith(destRoot.path + File.separator)
                if (insideDest) {
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { output -> zis.copyTo(output) }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
