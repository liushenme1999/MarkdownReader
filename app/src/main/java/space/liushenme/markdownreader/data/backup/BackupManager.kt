package space.liushenme.markdownreader.data.backup

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import space.liushenme.markdownreader.BuildConfig
import space.liushenme.markdownreader.data.local.BookContentHasher
import space.liushenme.markdownreader.data.local.dao.BookDao
import space.liushenme.markdownreader.data.local.dao.BookmarkDao
import space.liushenme.markdownreader.data.local.dao.GitProjectDao
import space.liushenme.markdownreader.data.local.dao.HighlightDao
import space.liushenme.markdownreader.data.local.dao.ReadingProgressDao
import space.liushenme.markdownreader.data.local.dao.ShelfGroupDao
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import space.liushenme.markdownreader.data.local.entity.GitProjectEntity
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.data.local.entity.ReadingProgressEntity
import space.liushenme.markdownreader.data.local.entity.ShelfGroupEntity
import space.liushenme.markdownreader.data.preferences.readerPreferencesDataStore
import space.liushenme.markdownreader.data.repository.ReaderSettingsRepository
import space.liushenme.markdownreader.data.repository.WebDavConfig
import space.liushenme.markdownreader.data.repository.WebDavConfigRepository
import space.liushenme.markdownreader.data.webdav.Authorization
import space.liushenme.markdownreader.data.webdav.WebDav
import space.liushenme.markdownreader.data.webdav.WebDavException
import space.liushenme.markdownreader.data.webdav.WebDavFile
import space.liushenme.markdownreader.git.GitDocumentOpener
import space.liushenme.markdownreader.git.GitHubRepoUrlParser
import space.liushenme.markdownreader.git.GitProjectCloner
import space.liushenme.markdownreader.git.GitProjectStorage
import space.liushenme.markdownreader.git.GitRecentOpenedPaths
import space.liushenme.markdownreader.importing.ParsedBookStorage

data class RemoteBackupInfo(
    val displayName: String,
    val lastModify: Long,
)

data class ProgressSyncResult(
    /** 从云端写入本机的书籍数 */
    val pulledCount: Int,
    /** 是否因本机进度更新而上传了备份 */
    val pushed: Boolean,
)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webDavConfigRepository: WebDavConfigRepository,
    private val bookContentSync: BookContentSync,
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao,
    private val highlightDao: HighlightDao,
    private val readingProgressDao: ReadingProgressDao,
    private val shelfGroupDao: ShelfGroupDao,
    private val gitProjectDao: GitProjectDao,
    private val gitDocumentOpener: GitDocumentOpener,
) {
    private val mutex = Mutex()
    private val dataStore = readerPreferencesDataStore(context)

    private val workDir: File
        get() = File(context.cacheDir, "backup_work").also { it.mkdirs() }

    private val zipFile: File
        get() = File(context.cacheDir, "tmp_backup.zip")

    suspend fun backup(): Result<String> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                backupUnlocked(includeMissingContents = true)
            }.onFailure {
                Log.e(TAG, "备份失败", it)
                cleanupTemp()
            }
        }
    }

    /**
     * 书架下拉刷新：拉取云端最新备份中的阅读进度，与本机取较新一侧；
     * 若本机有更靠前进度则再上传元数据备份，把云端更新为最新。
     */
    suspend fun syncReadingProgress(): Result<ProgressSyncResult> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val config = webDavConfigRepository.current()
                if (!config.isConfigured) {
                    error("请先配置 WebDAV 账号与密码")
                }
                val auth = ensureAuthorized(config)
                val rootUrl = webDavConfigRepository.rootUrl(config)
                val latest = latestBackupFile(rootUrl, auth)
                    ?: error("云端暂无备份，请先在任意设备备份一次")

                if (zipFile.exists()) zipFile.delete()
                WebDav(rootUrl + latest.displayName, auth).downloadTo(zipFile.absolutePath, true)
                val unpackDir = File(workDir, "sync_unpack").also {
                    it.deleteRecursively()
                    it.mkdirs()
                }
                BackupZip.unzipTo(zipFile, unpackDir)

                val remoteBooks = readJsonList(File(unpackDir, "books.json"), BookEntity::class.java)
                val localBooks = bookDao.getAllBooksList()
                var pulledCount = 0
                var needPush = false

                val gitMetaPulled = mergeGitProjectsFromBackup(
                    unpackDir = unpackDir,
                    cloneMissing = false,
                )
                if (gitMetaPulled > 0) pulledCount += gitMetaPulled
                if (localGitProjectsNeedPush(unpackDir)) needPush = true

                for (remote in remoteBooks) {
                    val local = findLocalBookForRemote(remote, localBooks) ?: continue
                    when {
                        shouldPreferRemoteProgress(remote, local) -> {
                            if (!isSameReadingProgress(remote, local)) {
                                bookDao.updateBook(
                                    local.copy(
                                        currentPosition = remote.currentPosition,
                                        progressPreviewText = remote.progressPreviewText,
                                        readingProgress = remote.readingProgress,
                                        lastReadTime = newerDate(
                                            remote.lastReadTime,
                                            local.lastReadTime,
                                        ),
                                        totalChars = remote.totalChars.takeIf { it > 0 }
                                            ?: local.totalChars,
                                        gitProjectId = local.gitProjectId
                                            ?: resolveGitProjectIdForBook(remote),
                                        gitRelativePath = remote.gitRelativePath
                                            ?: local.gitRelativePath,
                                    ),
                                )
                                pulledCount++
                            }
                        }
                        !isSameReadingProgress(remote, local) -> {
                            needPush = true
                        }
                    }
                }

                rematchGitBooks()

                // 本机有云端没有的书且已有阅读进度时，也需要推送
                if (!needPush) {
                    val remoteHashes = remoteBooks.mapNotNull { remoteStableHash(it) }.toSet()
                    val remoteIds = remoteBooks.map { it.id }.toSet()
                    needPush = localBooks.any { local ->
                        val hash = localStableHash(local)
                        val onRemote = (hash != null && hash in remoteHashes) ||
                            local.id in remoteIds
                        !onRemote && (
                            local.currentPosition > 0 ||
                                local.readingProgress > 0f ||
                                local.lastReadTime != null
                            )
                    }
                }

                if (needPush) {
                    // 进度同步推送只更新元数据，避免下拉刷新时卡在正文上传
                    backupUnlocked(includeMissingContents = false)
                } else {
                    cleanupTemp()
                }

                ProgressSyncResult(pulledCount = pulledCount, pushed = needPush)
            }.onFailure {
                Log.e(TAG, "同步阅读进度失败", it)
                cleanupTemp()
            }
        }
    }

    /** 调用方须已持有 [mutex] */
    private suspend fun backupUnlocked(includeMissingContents: Boolean): String {
        val config = webDavConfigRepository.current()
        if (!config.isConfigured) {
            error("请先配置 WebDAV 账号与密码")
        }
        val auth = ensureAuthorized(config)
        val rootUrl = webDavConfigRepository.rootUrl(config)
        WebDav(rootUrl, auth).makeAsDir()

        val packDir = File(workDir, "pack").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        writeBackupContents(packDir)

        val zipName = remoteZipFileName(config)
        BackupZip.zipDirectory(packDir, zipFile)

        WebDav(rootUrl + zipName, auth).upload(zipFile)

        if (config.onlyLatestBackup && zipName != LATEST_BACKUP_NAME) {
            runCatching {
                WebDav(rootUrl + LATEST_BACKUP_NAME, auth).upload(zipFile)
            }
        }

        if (includeMissingContents) {
            bookContentSync.uploadMissingBookContents(
                bookDao.getAllBooksList().map { it.id },
            )
        }

        webDavConfigRepository.setLastBackupTime(System.currentTimeMillis())
        cleanupTemp()
        return zipName
    }

    /**
     * 退出 App 时自动备份：已配置、距上次 ≥1 天、远程尚无当日同名文件。
     */
    suspend fun autoBackup() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val config = webDavConfigRepository.current()
                    if (!config.isConfigured) return@runCatching
                    val last = config.lastBackupTime
                    if (last + TimeUnit.DAYS.toMillis(1) > System.currentTimeMillis()) {
                        return@runCatching
                    }
                    val auth = ensureAuthorized(config)
                    val rootUrl = webDavConfigRepository.rootUrl(config)
                    WebDav(rootUrl, auth).makeAsDir()
                    val zipName = remoteZipFileName(config)
                    if (WebDav(rootUrl + zipName, auth).exists()) {
                        webDavConfigRepository.setLastBackupTime(System.currentTimeMillis())
                        return@runCatching
                    }

                    val packDir = File(workDir, "pack").also {
                        it.deleteRecursively()
                        it.mkdirs()
                    }
                    writeBackupContents(packDir)
                    BackupZip.zipDirectory(packDir, zipFile)
                    WebDav(rootUrl + zipName, auth).upload(zipFile)
                    if (config.onlyLatestBackup && zipName != LATEST_BACKUP_NAME) {
                        runCatching {
                            WebDav(rootUrl + LATEST_BACKUP_NAME, auth).upload(zipFile)
                        }
                    }
                    bookContentSync.uploadMissingBookContents(
                        bookDao.getAllBooksList().map { it.id },
                    )
                    webDavConfigRepository.setLastBackupTime(System.currentTimeMillis())
                    cleanupTemp()
                }.onFailure {
                    Log.w(TAG, "自动备份失败: ${it.localizedMessage}", it)
                    cleanupTemp()
                }
            }
        }
    }

    /**
     * 启动时检测：远程最新备份是否比本地 lastBackup 更新超过 1 分钟。
     */
    suspend fun findNewerRemoteBackup(): RemoteBackupInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val config = webDavConfigRepository.current()
            if (!config.isConfigured || !config.autoCheckNewBackup) return@runCatching null
            val auth = ensureAuthorized(config)
            val rootUrl = webDavConfigRepository.rootUrl(config)
            val latest = latestBackupFile(rootUrl, auth) ?: return@runCatching null
            if (latest.lastModify - config.lastBackupTime > TimeUnit.MINUTES.toMillis(1)) {
                RemoteBackupInfo(latest.displayName, latest.lastModify)
            } else {
                null
            }
        }.onFailure {
            Log.w(TAG, "检测远程新备份失败: ${it.localizedMessage}", it)
        }.getOrNull()
    }

    suspend fun listRemoteBackups(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val config = webDavConfigRepository.current()
            if (!config.isConfigured) error("请先配置 WebDAV 账号与密码")
            val auth = ensureAuthorized(config)
            val rootUrl = webDavConfigRepository.rootUrl(config)
            WebDav(rootUrl, auth).listFiles()
                .filter { !it.isDir && it.displayName.startsWith("backup") }
                .sortedByDescending { it.displayName }
                .map { it.displayName }
        }
    }

    suspend fun restore(remoteFileName: String): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val config = webDavConfigRepository.current()
                if (!config.isConfigured) error("请先配置 WebDAV 账号与密码")
                val auth = ensureAuthorized(config)
                val rootUrl = webDavConfigRepository.rootUrl(config)

                if (zipFile.exists()) zipFile.delete()
                WebDav(rootUrl + remoteFileName, auth).downloadTo(zipFile.absolutePath, true)

                val unpackDir = File(workDir, "unpack").also {
                    it.deleteRecursively()
                    it.mkdirs()
                }
                BackupZip.unzipTo(zipFile, unpackDir)
                applyMergeRestore(unpackDir)
                webDavConfigRepository.setLastBackupTime(System.currentTimeMillis())
                cleanupTemp()
            }.onFailure {
                Log.e(TAG, "恢复失败", it)
                cleanupTemp()
            }
        }
    }

    private suspend fun latestBackupFile(rootUrl: String, auth: Authorization): WebDavFile? {
        return WebDav(rootUrl, auth).listFiles()
            .filter { !it.isDir && it.displayName.startsWith("backup") }
            .maxByOrNull { it.lastModify }
    }

    private suspend fun ensureAuthorized(config: WebDavConfig): Authorization {
        val auth = Authorization(config.account, config.password)
        val rootUrl = webDavConfigRepository.rootUrl(config)
        if (!WebDav(rootUrl, auth).check()) {
            throw WebDavException("WebDAV 授权失败，请检查账号密码")
        }
        return auth
    }

    private fun remoteZipFileName(config: WebDavConfig): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
        val device = config.deviceName.trim()
        val name = if (device.isNotEmpty()) {
            "backup$date-$device.zip"
        } else {
            "backup$date.zip"
        }
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    /** 日常备份：仅元数据/进度/书签等，不含 parsed_books 正文 */
    private suspend fun writeBackupContents(packDir: File) {
        val manifest = mapOf(
            "formatVersion" to FORMAT_VERSION,
            "appVersionName" to BuildConfig.VERSION_NAME,
            "appVersionCode" to BuildConfig.VERSION_CODE,
            "createdAt" to System.currentTimeMillis(),
            "includesBookContent" to false,
        )
        File(packDir, "manifest.json").writeText(BackupGson.gson.toJson(manifest))

        File(packDir, "books.json").writeText(
            BackupGson.gson.toJson(bookDao.getAllBooksList()),
        )
        File(packDir, "git_projects.json").writeText(
            BackupGson.gson.toJson(
                gitProjectDao.getAllProjectsList().map { GitProjectBackup.fromEntity(it) },
            ),
        )
        File(packDir, "bookmarks.json").writeText(
            BackupGson.gson.toJson(bookmarkDao.getAllBookmarksList()),
        )
        File(packDir, "highlights.json").writeText(
            BackupGson.gson.toJson(highlightDao.getAllHighlightsList()),
        )
        File(packDir, "reading_progress.json").writeText(
            BackupGson.gson.toJson(readingProgressDao.getAllList()),
        )
        File(packDir, "shelf_groups.json").writeText(
            BackupGson.gson.toJson(shelfGroupDao.getAll()),
        )

        exportPreferences(File(packDir, "preferences.json"))

        val avatar = File(context.filesDir, AVATAR_FILE_NAME)
        if (avatar.isFile) {
            avatar.copyTo(File(packDir, AVATAR_FILE_NAME), overwrite = true)
        }
    }

    private suspend fun exportPreferences(outFile: File) {
        val snapshot = dataStore.data.first()
        val map = linkedMapOf<String, Any?>()
        for ((key, value) in snapshot.asMap()) {
            val name = key.name
            if (name in WebDavConfigRepository.WEBDAV_PREF_KEYS) continue
            if (name in ReaderSettingsRepository.DEVICE_LOCAL_PREF_KEYS) continue
            when (value) {
                is String, is Int, is Long, is Float, is Double, is Boolean -> map[name] = value
                is Set<*> -> map[name] = value.map { it.toString() }
                else -> map[name] = value.toString()
            }
        }
        outFile.writeText(BackupGson.gson.toJson(map))
    }

    /** 合并更新恢复：不整库清空；正文按需从 books/{contentHash}.zip 拉取 */
    private suspend fun applyMergeRestore(unpackDir: File) {
        val books = readJsonList(File(unpackDir, "books.json"), BookEntity::class.java)
        val bookmarks = readJsonList(File(unpackDir, "bookmarks.json"), BookmarkEntity::class.java)
        val highlights = readJsonList(File(unpackDir, "highlights.json"), HighlightEntity::class.java)
        val progress = readJsonList(
            File(unpackDir, "reading_progress.json"),
            ReadingProgressEntity::class.java,
        )
        val groups = readJsonList(File(unpackDir, "shelf_groups.json"), ShelfGroupEntity::class.java)

        // 先合并 Git 项目（缺失则浅克隆），再合并书籍以便 rematch gitProjectId
        mergeGitProjectsFromBackup(unpackDir = unpackDir, cloneMissing = true)

        // 兼容旧备份：若包内仍带 parsed_books，先落到临时 remoteId 目录，merge 时再迁到本地 id
        val packedParsed = File(unpackDir, PARSED_BOOKS_DIR)
        if (packedParsed.isDirectory) {
            val localRoot = File(context.filesDir, PARSED_BOOKS_DIR)
            packedParsed.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    val dest = File(localRoot, child.name)
                    if (!File(dest, ParsedBookStorage.BODY_FILE).isFile) {
                        dest.parentFile?.mkdirs()
                        child.copyRecursively(dest, overwrite = true)
                    }
                }
            }
        }

        val idMap = LinkedHashMap<Long, Long>()
        for (remote in books) {
            idMap[remote.id] = mergeBook(remote)
        }
        rematchGitBooks()
        refreshGitDocumentBundles()

        val remappedBookmarks = bookmarks.mapNotNull { bm ->
            val localBookId = idMap[bm.bookId] ?: return@mapNotNull null
            bm.copy(bookId = localBookId)
        }
        val remappedHighlights = highlights.mapNotNull { hl ->
            val localBookId = idMap[hl.bookId] ?: return@mapNotNull null
            hl.copy(bookId = localBookId)
        }
        val remappedProgress = progress.mapNotNull { row ->
            val localBookId = idMap[row.bookId] ?: return@mapNotNull null
            row.copy(id = 0, bookId = localBookId)
        }

        if (remappedBookmarks.isNotEmpty()) bookmarkDao.insertBookmarks(remappedBookmarks)
        if (remappedHighlights.isNotEmpty()) highlightDao.insertHighlights(remappedHighlights)
        for (row in remappedProgress) {
            readingProgressDao.upsertAddProgress(
                row.bookId,
                row.date,
                row.readChars,
                row.readTimeMinutes,
            )
        }
        if (groups.isNotEmpty()) shelfGroupDao.insertAll(groups)

        val avatarSrc = File(unpackDir, AVATAR_FILE_NAME)
        val avatarDest = File(context.filesDir, AVATAR_FILE_NAME)
        if (avatarSrc.isFile) {
            avatarSrc.copyTo(avatarDest, overwrite = true)
        }

        importPreferences(File(unpackDir, "preferences.json"), avatarDest)
    }

    /** @return 本机 books.id */
    private suspend fun mergeBook(remote: BookEntity): Long {
        val resolvedHash = resolveRemoteContentHash(remote)
        var local = if (resolvedHash.isNotBlank()) {
            bookDao.getBookByContentHash(resolvedHash)
        } else {
            null
        }
        // 旧备份无真实哈希时回退按远程 id 匹配
        if (local == null &&
            (remote.contentHash.isBlank() || BookContentHasher.isLegacy(remote.contentHash))
        ) {
            local = bookDao.getBookById(remote.id)
        }

        val resolvedGitProjectId = resolveGitProjectIdForBook(remote)
            ?: local?.gitProjectId
        val resolvedGitPath = remote.gitRelativePath ?: local?.gitRelativePath
        val resolvedFilePath = if (resolvedGitProjectId != null && !resolvedGitPath.isNullOrBlank()) {
            GitDocumentOpener.gitFilePath(resolvedGitProjectId, resolvedGitPath)
        } else {
            remote.filePath
        }

        if (local == null) {
            val newId = bookDao.insertBook(
                remote.copy(
                    id = 0,
                    contentHash = resolvedHash.ifBlank { BookContentHasher.legacyHash(remote.id) },
                    filePath = resolvedFilePath,
                    coverImagePath = null,
                    parsedBundlePath = null,
                    gitProjectId = resolvedGitProjectId,
                    gitRelativePath = resolvedGitPath,
                ),
            )
            adoptPackedBundle(remote.id, newId)
            if (resolvedGitProjectId == null) {
                bookContentSync.ensureLocalBookContent(newId)
            }
            val inserted = bookDao.getBookById(newId) ?: return newId
            val withHash = inserted.copy(
                contentHash = bookContentSync.upgradeContentHashIfNeeded(inserted),
            )
            bookDao.updateBook(remapBookPaths(withHash))
            return newId
        }

        adoptPackedBundle(remote.id, local.id)
        if (resolvedGitProjectId == null) {
            bookContentSync.ensureLocalBookContent(local.id)
        }

        // 以阅读位置/进度为主：本机若只是最近打开过但进度更旧，仍应采用云端更靠前的进度
        val preferRemoteProgress = shouldPreferRemoteProgress(remote, local)
        val hash = bookContentSync.upgradeContentHashIfNeeded(
            local.copy(contentHash = resolvedHash.ifBlank { local.contentHash }),
        )
        val base = remapBookPaths(local.copy(contentHash = hash))

        val merged = base.copy(
            title = remote.title,
            author = remote.author,
            importFormat = remote.importFormat,
            coverColor = remote.coverColor,
            contentHash = hash,
            totalChars = remote.totalChars.takeIf { it > 0 } ?: local.totalChars,
            currentPosition = if (preferRemoteProgress) remote.currentPosition else local.currentPosition,
            progressPreviewText = if (preferRemoteProgress) {
                remote.progressPreviewText
            } else {
                local.progressPreviewText
            },
            readingProgress = if (preferRemoteProgress) remote.readingProgress else local.readingProgress,
            lastReadTime = if (preferRemoteProgress) {
                newerDate(remote.lastReadTime, local.lastReadTime)
            } else {
                newerDate(local.lastReadTime, remote.lastReadTime)
            },
            addTime = local.addTime,
            isFavorite = remote.isFavorite || local.isFavorite,
            shelfGroup = remote.shelfGroup.ifBlank { local.shelfGroup },
            isPinned = remote.isPinned || local.isPinned,
            pinOrder = maxOf(remote.pinOrder, local.pinOrder),
            filePath = if (resolvedGitProjectId != null && !resolvedGitPath.isNullOrBlank()) {
                GitDocumentOpener.gitFilePath(resolvedGitProjectId, resolvedGitPath)
            } else {
                base.filePath
            },
            coverImagePath = base.coverImagePath
                ?: local.coverImagePath?.takeUnless { isForeignAppPrivatePath(it) },
            parsedBundlePath = base.parsedBundlePath,
            gitProjectId = resolvedGitProjectId,
            gitRelativePath = resolvedGitPath,
        )
        bookDao.updateBook(merged)
        if (resolvedGitProjectId == null && !localCanonicalBundleHasBody(local.id)) {
            bookContentSync.ensureLocalBookContent(local.id)
            bookDao.updateBook(remapBookPaths(merged))
        }
        return local.id
    }

    /**
     * 恢复合并时选择进度来源：先比字符位置与进度比例，再比 lastReadTime。
     * 避免「本机刚打开过书、时间更新但进度更旧」盖住云端进度。
     */
    private fun shouldPreferRemoteProgress(remote: BookEntity, local: BookEntity): Boolean {
        val remotePos = remote.currentPosition.coerceAtLeast(0)
        val localPos = local.currentPosition.coerceAtLeast(0)
        if (remotePos != localPos) return remotePos > localPos

        val remoteProg = remote.readingProgress
        val localProg = local.readingProgress
        if (kotlin.math.abs(remoteProg - localProg) > 0.0001f) {
            return remoteProg > localProg
        }

        val remoteTime = remote.lastReadTime?.time ?: 0L
        val localTime = local.lastReadTime?.time ?: 0L
        return remoteTime >= localTime
    }

    private fun isSameReadingProgress(a: BookEntity, b: BookEntity): Boolean {
        if (a.currentPosition.coerceAtLeast(0) != b.currentPosition.coerceAtLeast(0)) return false
        if (kotlin.math.abs(a.readingProgress - b.readingProgress) > 0.0001f) return false
        return (a.progressPreviewText.trim()) == (b.progressPreviewText.trim())
    }

    private fun findLocalBookForRemote(
        remote: BookEntity,
        locals: List<BookEntity>,
    ): BookEntity? {
        val remoteHash = remoteStableHash(remote)
        if (remoteHash != null) {
            locals.firstOrNull { localStableHash(it) == remoteHash }?.let { return it }
        }
        if (remote.contentHash.isBlank() || BookContentHasher.isLegacy(remote.contentHash)) {
            locals.firstOrNull { it.id == remote.id }?.let { return it }
        }
        return null
    }

    private fun remoteStableHash(remote: BookEntity): String? {
        if (remote.contentHash.isNotBlank() && !BookContentHasher.isLegacy(remote.contentHash)) {
            return remote.contentHash
        }
        return null
    }

    private fun localStableHash(local: BookEntity): String? {
        if (local.contentHash.isNotBlank() && !BookContentHasher.isLegacy(local.contentHash)) {
            return local.contentHash
        }
        return null
    }

    private fun newerDate(a: Date?, b: Date?): Date? {
        val at = a?.time ?: 0L
        val bt = b?.time ?: 0L
        return when {
            at >= bt && a != null -> a
            b != null -> b
            else -> a
        }
    }

    private fun resolveRemoteContentHash(remote: BookEntity): String {
        if (remote.contentHash.isNotBlank() && !BookContentHasher.isLegacy(remote.contentHash)) {
            return remote.contentHash
        }
        val packed = File(context.filesDir, "$PARSED_BOOKS_DIR/${remote.id}")
        if (File(packed, ParsedBookStorage.BODY_FILE).isFile) {
            return BookContentHasher.hashFromBundleOrFallback(
                bundleDir = packed,
                importFormat = remote.importFormat,
                title = remote.title,
                filePath = remote.filePath,
            )
        }
        if (remote.contentHash.isNotBlank()) return remote.contentHash
        return BookContentHasher.hashEmptyFallback(
            remote.importFormat,
            remote.title,
            remote.filePath,
        )
    }

    /** 旧备份 parsed_books/{remoteId} → 本机 parsed_books/{localId} */
    private fun adoptPackedBundle(remoteId: Long, localId: Long) {
        if (remoteId == localId) return
        val src = File(context.filesDir, "$PARSED_BOOKS_DIR/$remoteId")
        val dst = File(context.filesDir, "$PARSED_BOOKS_DIR/$localId")
        if (File(dst, ParsedBookStorage.BODY_FILE).isFile) return
        if (!File(src, ParsedBookStorage.BODY_FILE).isFile) return
        dst.parentFile?.mkdirs()
        if (dst.exists()) dst.deleteRecursively()
        src.copyRecursively(dst, overwrite = true)
    }

    private fun localCanonicalBundleHasBody(bookId: Long): Boolean {
        val bundleDir = File(context.filesDir, "$PARSED_BOOKS_DIR/$bookId")
        return File(bundleDir, ParsedBookStorage.BODY_FILE).isFile
    }

    /**
     * 将备份中的路径改写为当前设备 canonical 路径。
     * 绝不保留其他设备/旧安装的 filesDir 绝对路径。
     */
    private fun remapBookPaths(book: BookEntity): BookEntity {
        val bundleDir = File(context.filesDir, "$PARSED_BOOKS_DIR/${book.id}")
        val bodyFile = File(bundleDir, ParsedBookStorage.BODY_FILE)
        val localBundlePath = bundleDir.absolutePath

        val newCover = when {
            File(bundleDir, "cover.jpg").isFile -> File(bundleDir, "cover.jpg").absolutePath
            File(bundleDir, "cover.png").isFile -> File(bundleDir, "cover.png").absolutePath
            else -> null
        }

        // http(s) 可跨设备复用；content:// 与他机私有路径均不可用
        val reusableRemotePath =
            book.filePath.takeIf {
                it.startsWith("http://", ignoreCase = true) ||
                    it.startsWith("https://", ignoreCase = true)
            }

        val newFilePath = when {
            bodyFile.isFile -> bodyFile.absolutePath
            reusableRemotePath != null -> reusableRemotePath
            else -> bodyFile.absolutePath
        }

        return book.copy(
            filePath = newFilePath,
            coverImagePath = newCover,
            parsedBundlePath = localBundlePath,
        )
    }

    private fun isForeignAppPrivatePath(path: String): Boolean {
        if (path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true) ||
            path.startsWith("content://", ignoreCase = true)
        ) {
            return false
        }
        val filesDir = context.filesDir.absolutePath
        return path.startsWith("/") && !path.startsWith(filesDir)
    }

    private suspend fun importPreferences(prefsFile: File, avatarDest: File) {
        if (!prefsFile.isFile) return
        val type = TypeToken.getParameterized(
            Map::class.java,
            String::class.java,
            Any::class.java,
        ).type
        val map: Map<String, Any> = BackupGson.gson.fromJson(prefsFile.readText(), type) ?: return

        val intKeys = setOf(
            "reader_font_size",
            "reader_reader_padding_dp",
            "reader_last_highlight_color",
        )
        val floatKeys = setOf("reader_line_spacing_multiplier")

        dataStore.edit { prefs ->
            for ((name, value) in map) {
                if (name in WebDavConfigRepository.WEBDAV_PREF_KEYS) continue
                if (name in ReaderSettingsRepository.DEVICE_LOCAL_PREF_KEYS) continue
                when (value) {
                    is Boolean -> prefs[booleanPreferencesKey(name)] = value
                    is String -> prefs[stringPreferencesKey(name)] = value
                    is Number -> when {
                        name in intKeys -> prefs[intPreferencesKey(name)] = value.toInt()
                        name in floatKeys -> prefs[floatPreferencesKey(name)] = value.toFloat()
                        value is Int -> prefs[intPreferencesKey(name)] = value
                        value is Long -> prefs[longPreferencesKey(name)] = value
                        value is Float -> prefs[floatPreferencesKey(name)] = value
                        value is Double -> {
                            val asLong = value.toLong()
                            if (value == asLong.toDouble()) {
                                prefs[longPreferencesKey(name)] = asLong
                            } else {
                                prefs[doublePreferencesKey(name)] = value
                            }
                        }
                        else -> prefs[doublePreferencesKey(name)] = value.toDouble()
                    }
                    is List<*> -> {
                        prefs[stringSetPreferencesKey(name)] =
                            value.map { it.toString() }.toSet()
                    }
                }
            }
            if (avatarDest.isFile) {
                prefs[stringPreferencesKey("profile_avatar_path")] = avatarDest.absolutePath
            }
        }
    }

    private fun <T> readJsonList(file: File, elementClass: Class<T>): List<T> {
        if (!file.isFile) return emptyList()
        val type = TypeToken.getParameterized(List::class.java, elementClass).type
        return BackupGson.gson.fromJson(file.readText(), type) ?: emptyList()
    }

    /**
     * @return 从云端写入本机的 Git 项目元数据条数（lastOpened / 收藏置顶等）
     */
    private suspend fun mergeGitProjectsFromBackup(
        unpackDir: File,
        cloneMissing: Boolean,
    ): Int {
        val remotes = readJsonList(File(unpackDir, "git_projects.json"), GitProjectBackup::class.java)
        if (remotes.isEmpty()) return 0
        var pulled = 0
        for (remote in remotes) {
            if (remote.remoteUrl.isBlank()) continue
            val changed = mergeGitProject(remote, cloneMissing = cloneMissing)
            if (changed) pulled++
        }
        return pulled
    }

    private suspend fun mergeGitProject(
        remote: GitProjectBackup,
        cloneMissing: Boolean,
    ): Boolean {
        val local = gitProjectDao.getByRemoteUrl(remote.remoteUrl)
        if (local == null) {
            if (!cloneMissing) return false
            return runCatching {
                cloneGitProjectFromBackup(remote)
                true
            }.onFailure {
                Log.w(TAG, "恢复克隆 Git 项目失败 ${remote.remoteUrl}: ${it.localizedMessage}", it)
            }.getOrDefault(false)
        }

        val preferRemoteOpened = shouldPreferRemoteLastOpened(remote, local)
        val remoteRecent = GitRecentOpenedPaths.resolveList(
            remote.recentOpenedPathsJson,
            remote.lastOpenedRelativePath,
        )
        val localRecent = GitRecentOpenedPaths.resolveList(
            local.recentOpenedPathsJson,
            local.lastOpenedRelativePath,
        )
        val mergedRecent = if (preferRemoteOpened) {
            GitRecentOpenedPaths.mergePreferFirst(remoteRecent, localRecent)
        } else {
            GitRecentOpenedPaths.mergePreferFirst(localRecent, remoteRecent)
        }
        val merged = local.copy(
            title = remote.title.ifBlank { local.title },
            defaultBranch = remote.defaultBranch.ifBlank { local.defaultBranch },
            isPinned = remote.isPinned || local.isPinned,
            pinOrder = maxOf(remote.pinOrder, local.pinOrder),
            isFavorite = remote.isFavorite || local.isFavorite,
            shelfGroup = remote.shelfGroup.ifBlank { local.shelfGroup },
            lastOpenedRelativePath = if (preferRemoteOpened) {
                remote.lastOpenedRelativePath
            } else {
                local.lastOpenedRelativePath
            },
            lastOpenedAt = if (preferRemoteOpened) {
                newerDate(remote.lastOpenedAt, local.lastOpenedAt)
            } else {
                newerDate(local.lastOpenedAt, remote.lastOpenedAt)
            },
            recentOpenedPathsJson = GitRecentOpenedPaths.encode(mergedRecent),
        )
        val changed = merged != local
        if (changed) gitProjectDao.update(merged)
        return changed && preferRemoteOpened &&
            remote.lastOpenedRelativePath != local.lastOpenedRelativePath
    }

    private suspend fun cloneGitProjectFromBackup(remote: GitProjectBackup) {
        val parsed = GitHubRepoUrlParser.parse(remote.remoteUrl)
        val resolvedTitle = parsed?.repo
            ?: remote.title.ifBlank { parsed?.displayName.orEmpty() }
        val placeholder = GitProjectEntity(
            title = resolvedTitle,
            remoteUrl = remote.remoteUrl,
            defaultBranch = remote.defaultBranch,
            localPath = "",
            addTime = remote.addTime,
            isPinned = remote.isPinned,
            pinOrder = remote.pinOrder,
            isFavorite = remote.isFavorite,
            shelfGroup = remote.shelfGroup,
            lastOpenedRelativePath = remote.lastOpenedRelativePath,
            lastOpenedAt = remote.lastOpenedAt,
            recentOpenedPathsJson = GitRecentOpenedPaths.encode(
                GitRecentOpenedPaths.resolveList(
                    remote.recentOpenedPathsJson,
                    remote.lastOpenedRelativePath,
                ),
            ),
        )
        val projectId = gitProjectDao.insert(placeholder)
        val dest = GitProjectStorage.projectDir(context, projectId)
        try {
            val branch = remote.defaultBranch.trim().takeIf { it.isNotEmpty() }
            val result = GitProjectCloner.clone(
                cloneUrl = remote.remoteUrl.trimEnd('/') + ".git",
                destination = dest,
                branch = branch,
            )
            gitProjectDao.update(
                placeholder.copy(
                    id = projectId,
                    localPath = dest.absolutePath,
                    defaultBranch = result.branch.ifBlank { remote.defaultBranch },
                    lastCommitSha = result.commitSha.ifBlank { remote.lastCommitSha },
                    lastPulledAt = Date(),
                ),
            )
        } catch (e: Exception) {
            runCatching {
                gitProjectDao.getById(projectId)?.let { row ->
                    GitProjectStorage.deleteProjectDir(dest.absolutePath)
                    gitProjectDao.delete(row)
                }
            }
            throw e
        }
    }

    private fun shouldPreferRemoteLastOpened(
        remote: GitProjectBackup,
        local: GitProjectEntity,
    ): Boolean {
        val remoteAt = remote.lastOpenedAt?.time ?: 0L
        val localAt = local.lastOpenedAt?.time ?: 0L
        if (remoteAt != localAt) return remoteAt > localAt
        val remotePath = remote.lastOpenedRelativePath.orEmpty()
        val localPath = local.lastOpenedRelativePath.orEmpty()
        return remotePath.isNotBlank() && remotePath != localPath && localPath.isBlank()
    }

    private suspend fun localGitProjectsNeedPush(unpackDir: File): Boolean {
        val remotes = readJsonList(File(unpackDir, "git_projects.json"), GitProjectBackup::class.java)
        val remoteByUrl = remotes.associateBy { it.remoteUrl }
        for (local in gitProjectDao.getAllProjectsList()) {
            val remote = remoteByUrl[local.remoteUrl]
            if (remote == null) {
                if (!local.lastOpenedRelativePath.isNullOrBlank() || local.isFavorite || local.isPinned) {
                    return true
                }
                continue
            }
            val remotePreferred = shouldPreferRemoteLastOpened(remote, local)
            if (!remotePreferred) {
                val localAt = local.lastOpenedAt?.time ?: 0L
                val remoteAt = remote.lastOpenedAt?.time ?: 0L
                val pathDiffer =
                    local.lastOpenedRelativePath.orEmpty() != remote.lastOpenedRelativePath.orEmpty()
                if ((!local.lastOpenedRelativePath.isNullOrBlank() && pathDiffer) || localAt > remoteAt) {
                    return true
                }
            }
            if ((local.isFavorite && !remote.isFavorite) || (local.isPinned && !remote.isPinned)) {
                return true
            }
        }
        return false
    }

    private suspend fun resolveGitProjectIdForBook(remote: BookEntity): Long? {
        val path = remote.gitRelativePath ?: return null
        if (path.isBlank() || remote.contentHash.isBlank()) return null
        return gitProjectDao.getAllProjectsList().firstOrNull { project ->
            BookContentHasher.hashForGitDocument(project.remoteUrl, path) == remote.contentHash
        }?.id
    }

    private suspend fun rematchGitBooks() {
        val projects = gitProjectDao.getAllProjectsList()
        if (projects.isEmpty()) return
        val books = bookDao.getAllBooksList().filter { !it.gitRelativePath.isNullOrBlank() }
        for (book in books) {
            val path = book.gitRelativePath ?: continue
            val match = projects.firstOrNull { project ->
                BookContentHasher.hashForGitDocument(project.remoteUrl, path) == book.contentHash ||
                    book.gitProjectId == project.id
            } ?: projects.firstOrNull { project ->
                book.contentHash == BookContentHasher.hashForGitDocument(project.remoteUrl, path)
            }
            val project = match ?: continue
            val expectedPath = GitDocumentOpener.gitFilePath(project.id, path)
            if (book.gitProjectId == project.id && book.filePath == expectedPath) continue
            bookDao.updateBook(
                book.copy(
                    gitProjectId = project.id,
                    gitRelativePath = path,
                    filePath = expectedPath,
                    author = project.title,
                ),
            )
        }
    }

    private suspend fun refreshGitDocumentBundles() {
        val projects = gitProjectDao.getAllProjectsList()
        for (project in projects) {
            if (project.localPath.isBlank()) continue
            runCatching {
                gitDocumentOpener.refreshOpenedDocuments(context, project)
            }.onFailure {
                Log.w(TAG, "刷新 Git 文档失败 project=${project.id}: ${it.localizedMessage}", it)
            }
        }
    }

    private fun cleanupTemp() {
        runCatching { workDir.deleteRecursively() }
        runCatching { zipFile.delete() }
    }

    companion object {
        private const val TAG = "BackupManager"
        /** v3：备份包不含书籍正文；正文走 books/{contentHash}.zip；合并键为 contentHash */
        private const val FORMAT_VERSION = 3
        private const val LATEST_BACKUP_NAME = "backup.zip"
        private const val PARSED_BOOKS_DIR = "parsed_books"
        private const val AVATAR_FILE_NAME = "profile_avatar.jpg"
    }
}
