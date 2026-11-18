package space.liushenme.markdownreader.data.backup

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupZipTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = File.createTempFile("backup-zip", "").let {
            it.delete()
            it.mkdirs()
            it
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun zipDirectoryThenUnzip_roundTripsNestedFiles() {
        val source = File(root, "source").apply { mkdirs() }
        File(source, "books.json").writeText("""{"books":[1,2,3]}""")
        File(source, "covers").mkdirs()
        File(source, "covers/1.png").writeBytes(byteArrayOf(1, 2, 3, 4))
        File(source, "covers/2.png").writeBytes(byteArrayOf(5, 6))

        val zip = File(root, "backup.zip")
        BackupZip.zipDirectory(source, zip)
        assertTrue(zip.isFile && zip.length() > 0)

        val dest = File(root, "restored")
        BackupZip.unzipTo(zip, dest)

        assertEquals("""{"books":[1,2,3]}""", File(dest, "books.json").readText())
        assertTrue(File(dest, "covers/1.png").readBytes().contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertTrue(File(dest, "covers/2.png").readBytes().contentEquals(byteArrayOf(5, 6)))
    }

    @Test
    fun unzipTo_rejectsEntriesEscapingDestination() {
        val zip = File(root, "evil.zip")
        ZipOutputStream(zip.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("../escaped.txt"))
            zos.write("evil".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("safe.txt"))
            zos.write("ok".toByteArray())
            zos.closeEntry()
        }

        val dest = File(root, "restored")
        BackupZip.unzipTo(zip, dest)

        assertFalse("zip-slip 条目不能写到目标目录之外", File(root, "escaped.txt").exists())
        assertTrue(File(dest, "safe.txt").isFile)
    }

    @Test
    fun unzipTo_rejectsAbsolutePathEntries() {
        val zip = File(root, "abs.zip")
        val absoluteEntry = File(root, "outside.txt").absolutePath
        ZipOutputStream(zip.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry(absoluteEntry))
            zos.write("evil".toByteArray())
            zos.closeEntry()
        }

        val dest = File(root, "restored")
        BackupZip.unzipTo(zip, dest)

        assertFalse("绝对路径条目不能落到目标目录之外", File(absoluteEntry).exists())
    }

    @Test
    fun zipDirectory_overwritesExistingArchive() {
        val source = File(root, "source").apply { mkdirs() }
        File(source, "a.txt").writeText("first")
        val zip = File(root, "backup.zip")
        File(zip.path).writeText("stale content")

        BackupZip.zipDirectory(source, zip)

        val dest = File(root, "restored")
        BackupZip.unzipTo(zip, dest)
        assertEquals("first", File(dest, "a.txt").readText())
    }
}
