package space.liushenme.markdownreader.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import space.liushenme.markdownreader.data.webdav.WebDavFile

class GitProjectSyncPolicyTest {

    @Test
    fun tombstoneBlocksImport_whenDeleteIsNewer() {
        assertTrue(GitProjectSyncPolicy.tombstoneBlocksImport(addTimeMillis = 100, deletedAt = 200))
    }

    @Test
    fun tombstoneBlocksImport_whenImportIsNotOlder() {
        assertFalse(GitProjectSyncPolicy.tombstoneBlocksImport(addTimeMillis = 200, deletedAt = 100))
        assertFalse(GitProjectSyncPolicy.tombstoneBlocksImport(addTimeMillis = 200, deletedAt = 200))
    }

    @Test
    fun selectLatestBackup_prefersNewestModifiedTime() {
        val older = file("backup2026-09-01-a.zip", lastModify = 10)
        val newer = file("backup2026-09-02-b.zip", lastModify = 20)
        val latestAlias = file(GitProjectSyncPolicy.LATEST_BACKUP_NAME, lastModify = 15)
        assertEquals(
            newer,
            GitProjectSyncPolicy.selectLatestBackup(listOf(older, latestAlias, newer)),
        )
    }

    @Test
    fun selectLatestBackup_withoutTimestamps_prefersBackupZipThenName() {
        val dated = file("backup2026-09-01.zip", lastModify = 0)
        val alias = file(GitProjectSyncPolicy.LATEST_BACKUP_NAME, lastModify = 0)
        assertEquals(
            alias,
            GitProjectSyncPolicy.selectLatestBackup(listOf(dated, alias)),
        )
        assertEquals(
            file("backup2026-09-25-b.zip", 0),
            GitProjectSyncPolicy.selectLatestBackup(
                listOf(
                    file("backup2026-09-01-a.zip", 0),
                    file("backup2026-09-25-b.zip", 0),
                ),
            ),
        )
    }

    @Test
    fun selectLatestBackup_ignoresDirectoriesAndEmpty() {
        val dir = WebDavFile(
            path = "/backup",
            displayName = "backup",
            size = 0,
            contentType = "httpd/unix-directory",
            resourceType = "<collection/>",
            lastModify = 99,
        )
        assertNull(GitProjectSyncPolicy.selectLatestBackup(listOf(dir)))
        assertNull(GitProjectSyncPolicy.selectLatestBackup(emptyList()))
    }

    @Test
    fun isNewerBackup_sameFileOnlyWhenModifiedLater() {
        val base = file("backup.zip", 10)
        assertFalse(GitProjectSyncPolicy.isNewerBackup(base, file("backup.zip", 10)))
        assertTrue(GitProjectSyncPolicy.isNewerBackup(base, file("backup.zip", 11)))
    }

    @Test
    fun isNewerBackup_differentFileUsesModifiedTime() {
        val base = file("backup2026-09-01-a.zip", 10)
        assertTrue(
            GitProjectSyncPolicy.isNewerBackup(base, file("backup2026-09-25-b.zip", 30)),
        )
        assertFalse(
            GitProjectSyncPolicy.isNewerBackup(base, file("backup2026-09-25-b.zip", 5)),
        )
        assertTrue(
            GitProjectSyncPolicy.isNewerBackup(file("old.zip", 0), file("backup.zip", 5)),
        )
    }

    private fun file(name: String, lastModify: Long) = WebDavFile(
        path = "/$name",
        displayName = name,
        size = 1,
        contentType = "",
        resourceType = "",
        lastModify = lastModify,
    )
}
