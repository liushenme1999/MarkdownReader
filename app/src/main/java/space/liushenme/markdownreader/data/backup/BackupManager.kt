package space.liushenme.markdownreader.data.backup

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
import space.liushenme.markdownreader.data.local.dao.DeletedBookDao
import space.liushenme.markdownreader.data.local.dao.DeletedGitProjectDao
import space.liushenme.markdownreader.data.local.dao.GitProjectDao
import space.liushenme.markdownreader.data.local.dao.HighlightDao
import space.liushenme.markdownreader.data.local.dao.ReadingProgressDao
import space.liushenme.markdownreader.data.local.dao.ShelfGroupDao
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import space.liushenme.markdownreader.data.local.entity.DeletedBookEntity
import space.liushenme.markdownreader.data.local.entity.DeletedGitProjectEntity
import space.liushenme.markdownreader.data.local.entity.GitProjectEntity
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.data.local.entity.ReadingProgressEntity
import space.liushenme.markdownreader.data.local.entity.ShelfGroupEntity
import space.liushenme.markdownreader.data.preferences.PreferenceNumbers
import space.liushenme.markdownreader.data.preferences.readerPreferencesDataStore
import space.liushenme.markdownreader.data.repository.ReaderSettingsRepository
import space.liushenme.markdownreader.data.repository.WebDavConfig
import space.liushenme.markdownreader.data.repository.WebDavConfigRepository
import space.liushenme.markdownreader.data.webdav.Authorization
import space.liushenme.markdownreader.data.webdav.WebDav
import space.liushenme.markdownreader.data.webdav.WebDavException
import space.liushenme.markdownreader.data.webdav.WebDavFile
import space.liushenme.markdownreader.git.GitCloneException
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

private data class AbsorbedGitSnapshot(
    val pulled: Int,
    val needsPush: Boolean,
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
    private val deletedBookDao: DeletedBookDao,
    private val deletedGitProjectDao: DeletedGitProjectDao,
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

    /** 未配置 WebDAV 时直接跳过，避免删除 Git 项目后弹出配置错误。 */
    suspend fun backupIfConfigured(): Result<String?> {
        val config = webDavConfigRepository.current()
        if (!config.isConfigured) return Result.success(null)
        return backup()
    }

    /**
     * 书架下拉刷新：拉取云端最新备份中的阅读进度，与本机取较新一侧；
     * 若本机有更靠前进度则再上传元数据备份，把云端更新为最新。
     */
    suspend fun syncReadingProgress(): Result<ProgressSyncResult> {
        val result = mutex.withLock {
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
                var pulledCount = 0
                var needPush = false

                // 先应用删除墓碑，避免对端仍保留的书/项目被进度同步推回云端
                pulledCount += mergeAndApplyBookTombstones(unpackDir)
                pulledCount += mergeAndApplyGitProjectTombstones(unpackDir)
                if (localTombstonesNeedPush(unpackDir)) needPush = true

                // 跨设备：云端有、本机无的 Git 项目只补书架元数据，点进项目再克隆
                val gitMetaPulled = mergeGitProjectsFromBackup(
                    unpackDir = unpackDir,
                    cloneMissing = false,
                )
                if (gitMetaPulled > 0) pulledCount += gitMetaPulled
                if (localGitProjectsNeedPush(unpackDir)) needPush = true

                val localBooks = bookDao.getAllBooksList()
                val deletedHashes = deletedBookDao.getAllHashes().toSet()
                val deletedProjectUrls = deletedGitProjectDao.getAllRemoteUrls()
                val liveProjectUrls = gitProjectDao.getAllProjectsList().map { it.remoteUrl }
                for (remote in remoteBooks) {
                    val remoteHash = remoteStableHash(remote) ?: remote.contentHash.takeIf { it.isNotBlank() }
                    if (remoteHash != null && remoteHash in deletedHashes) {
                        // 云端元数据仍含已删书：需推送以更新 books.json
                        needPush = true
                        continue
                    }
                    if (!GitLinkedBookRestore.shouldRestore(
                            gitRelativePath = remote.gitRelativePath,
                            contentHash = remoteHash ?: remote.contentHash,
                            deletedBookHashes = deletedHashes,
                            deletedProjectUrls = deletedProjectUrls,
                            liveProjectUrls = liveProjectUrls,
                        )
                    ) {
                        needPush = true
                        continue
                    }
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

                // 本机有云端没有的书且已有阅读进度时，也需要推送（已墓碑删除的不会再推回）
                if (!needPush) {
                    val remoteHashes = remoteBooks.mapNotNull { remoteStableHash(it) }.toSet()
                    val remoteIds = remoteBooks.map { it.id }.toSet()
                    needPush = localBooks.any { local ->
                        val hash = localStableHash(local)
                        if (hash != null && hash in deletedHashes) return@any false
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
                    var baseFile = latest
                    var refreshes = 0
                    while (refreshes < GitProjectSyncPolicy.MAX_PRE_PUSH_REMERGES) {
                        val newest = latestBackupFile(rootUrl, auth)
                        if (newest == null || !GitProjectSyncPolicy.isNewerBackup(baseFile, newest)) {
                            break
                        }
                        refreshes++
                        downloadBackup(rootUrl, auth, newest, unpackDir)
                        baseFile = newest
                        val absorbed = absorbGitSnapshot(unpackDir)
                        pulledCount += absorbed.pulled
                        if (absorbed.needsPush) needPush = true
                    }
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
        return result
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
        return GitProjectSyncPolicy.selectLatestBackup(WebDav(rootUrl, auth).listFiles())
    }

    private suspend fun downloadBackup(
        rootUrl: String,
        auth: Authorization,
        file: WebDavFile,
        unpackDir: File,
    ) {
        if (zipFile.exists()) zipFile.delete()
        WebDav(rootUrl + file.displayName, auth).downloadTo(zipFile.absolutePath, true)
        unpackDir.deleteRecursively()
        unpackDir.mkdirs()
        BackupZip.unzipTo(zipFile, unpackDir)
    }

    /**
     * 把备份里的删除墓碑和 Git 项目元数据并进本机。
     * 下拉刷新不克隆仓库，只补书架记录。
     */
    private suspend fun absorbGitSnapshot(unpackDir: File): AbsorbedGitSnapshot {
        var pulled = mergeAndApplyBookTombstones(unpackDir)
        pulled += mergeAndApplyGitProjectTombstones(unpackDir)
        var needsPush = localTombstonesNeedPush(unpackDir)
        val gitMeta = mergeGitProjectsFromBackup(unpackDir, cloneMissing = false)
        pulled += gitMeta
        if (localGitProjectsNeedPush(unpackDir)) needsPush = true
        return AbsorbedGitSnapshot(pulled, needsPush)
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
        File(packDir, "deleted_books.json").writeText(
            BackupGson.gson.toJson(
                deletedBookDao.getAll().map { DeletedBookBackup.fromEntity(it) },
            ),
        )
        File(packDir, "deleted_git_projects.json").writeText(
            BackupGson.gson.toJson(
                deletedGitProjectDao.getAll().map { DeletedGitProjectBackup.fromEntity(it) },
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

        // 先应用删除墓碑，再合并，避免把已删条目又合并回来
        mergeAndApplyBookTombstones(unpackDir)
        mergeAndApplyGitProjectTombstones(unpackDir)

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

        val deletedHashes = deletedBookDao.getAllHashes().toSet()
        val deletedProjectUrls = deletedGitProjectDao.getAllRemoteUrls()
        val liveProjectUrls = gitProjectDao.getAllProjectsList().map { it.remoteUrl }
        val idMap = LinkedHashMap<Long, Long>()
        val webDavBookIds = LinkedHashSet<Long>()
        for (remote in books) {
            val remoteHash = remoteStableHash(remote)
                ?: remote.contentHash.takeIf { it.isNotBlank() }
            if (remoteHash != null && remoteHash in deletedHashes) continue
            if (!GitLinkedBookRestore.shouldRestore(
                    gitRelativePath = remote.gitRelativePath,
                    contentHash = remoteHash ?: remote.contentHash,
                    deletedBookHashes = deletedHashes,
                    deletedProjectUrls = deletedProjectUrls,
                    liveProjectUrls = liveProjectUrls,
                )
            ) {
                continue
            }
            val localId = mergeBook(remote)
            if (localId > 0L) {
                idMap[remote.id] = localId
                val local = bookDao.getBookById(localId)
                if (local?.gitProjectId == null) {
                    webDavBookIds.add(localId)
                }
            }
        }
        fetchRestoredBookContents(idMap.filterValues { it in webDavBookIds })
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
        val deletedHashes = deletedBookDao.getAllHashes().toSet()
        val deletedProjectUrls = deletedGitProjectDao.getAllRemoteUrls()
        val liveProjectUrls = gitProjectDao.getAllProjectsList().map { it.remoteUrl }
        if (!GitLinkedBookRestore.shouldRestore(
                gitRelativePath = remote.gitRelativePath,
                contentHash = resolvedHash.ifBlank { remote.contentHash },
                deletedBookHashes = deletedHashes,
                deletedProjectUrls = deletedProjectUrls,
                liveProjectUrls = liveProjectUrls,
            )
        ) {
            return 0L
        }
        if (resolvedHash.isNotBlank()) {
            val tomb = deletedBookDao.getByHash(resolvedHash)
            if (tomb != null) {
                val existing = bookDao.getBookByContentHash(resolvedHash)
                if (existing != null && wasReaddedAfterTombstone(existing.addTime, tomb.deletedAt)) {
                    deletedBookDao.deleteByHash(resolvedHash)
                } else {
                    existing?.let { stale ->
                        ParsedBookStorage.deleteBundleDir(stale.parsedBundlePath)
                        stale.coverImagePath?.let { path -> runCatching { File(path).delete() } }
                        bookDao.deleteBook(stale)
                    }
                    return 0L
                }
            }
        }
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
            val inserted = bookDao.getBookById(newId) ?: return newId
            val withHash = inserted.copy(
                contentHash = bookContentSync.upgradeContentHashIfNeeded(inserted),
            )
            bookDao.updateBook(remapBookPaths(withHash))
            return newId
        }

        adoptPackedBundle(remote.id, local.id)

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
        return local.id
    }

    private suspend fun fetchRestoredBookContents(remoteToLocal: Map<Long, Long>) {
        if (remoteToLocal.isEmpty()) return
        bookContentSync.ensureLocalBookContents(remoteToLocal)
        for (bookId in remoteToLocal.values) {
            val book = bookDao.getBookById(bookId) ?: continue
            val hashed = book.copy(
                contentHash = bookContentSync.upgradeContentHashIfNeeded(book),
            )
            bookDao.updateBook(remapBookPaths(hashed))
        }
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
        if (!File(src, ParsedBookStorage.BODY_FILE).isFile) return
        if (!ParsedBookStorage.shouldReplaceBundle(dst, src)) return
        dst.parentFile?.mkdirs()
        if (dst.exists()) dst.deleteRecursively()
        src.copyRecursively(dst, overwrite = true)
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

        dataStore.edit { prefs ->
            for ((name, value) in map) {
                if (name in WebDavConfigRepository.WEBDAV_PREF_KEYS) continue
                if (name in ReaderSettingsRepository.DEVICE_LOCAL_PREF_KEYS) continue
                when (value) {
                    is Boolean -> prefs[booleanPreferencesKey(name)] = value
                    is String -> prefs[stringPreferencesKey(name)] = value
                    is Number -> PreferenceNumbers.writeNumber(prefs, name, value)
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
     * 合并云端删除墓碑，并删除本机仍存在的对应书籍。
     * @return 本机实际删除的书籍数
     */
    private suspend fun mergeAndApplyBookTombstones(unpackDir: File): Int {
        val remotes = readJsonList(File(unpackDir, "deleted_books.json"), DeletedBookBackup::class.java)
        for (remote in remotes) {
            if (remote.contentHash.isBlank()) continue
            val existing = deletedBookDao.getByHash(remote.contentHash)
            if (existing == null || remote.deletedAt >= existing.deletedAt) {
                deletedBookDao.upsert(remote.toEntity())
            }
        }
        val hashes = deletedBookDao.getAllHashes().toSet()
        if (hashes.isEmpty()) return 0
        var removed = 0
        for (local in bookDao.getAllBooksList()) {
            val hash = localStableHash(local)
                ?: local.contentHash.takeIf { it.isNotBlank() }
                ?: continue
            if (hash !in hashes) continue
            val tomb = deletedBookDao.getByHash(hash)
            if (tomb != null && wasReaddedAfterTombstone(local.addTime, tomb.deletedAt)) {
                deletedBookDao.deleteByHash(hash)
                continue
            }
            ParsedBookStorage.deleteBundleDir(local.parsedBundlePath)
            local.coverImagePath?.let { path -> runCatching { File(path).delete() } }
            bookDao.deleteBook(local)
            runCatching {
                bookContentSync.deleteRemoteBookContent(hash, fallbackNumericId = local.id)
            }
            removed++
        }
        return removed
    }

    /**
     * 合并云端 Git 项目删除墓碑，并删除本机对应项目。
     * @return 本机实际删除的项目数
     */
    private suspend fun mergeAndApplyGitProjectTombstones(unpackDir: File): Int {
        val remotes = readJsonList(
            File(unpackDir, "deleted_git_projects.json"),
            DeletedGitProjectBackup::class.java,
        )
        for (remote in remotes) {
            if (remote.remoteUrl.isBlank()) continue
            val canonical = GitHubRepoUrlParser.canonicalBrowseUrl(remote.remoteUrl)
                ?: remote.remoteUrl.trim()
            val existing = findDeletedGitProject(canonical)
            if (existing == null || remote.deletedAt >= existing.deletedAt) {
                deletedGitProjectDao.upsert(
                    DeletedGitProjectEntity(remoteUrl = canonical, deletedAt = remote.deletedAt),
                )
            }
        }
        val tombstones = deletedGitProjectDao.getAll()
        if (tombstones.isEmpty()) return 0
        var removed = 0
        for (local in gitProjectDao.getAllProjectsList()) {
            val tomb = tombstones.firstOrNull {
                GitHubRepoUrlParser.sameRepo(it.remoteUrl, local.remoteUrl)
            } ?: continue
            if (wasReaddedAfterTombstone(local.addTime, tomb.deletedAt)) {
                revokeGitProjectTombstones(local.remoteUrl)
                continue
            }
            removeLocalGitProject(local)
            removed++
        }
        return removed
    }

    private suspend fun removeLocalGitProject(project: GitProjectEntity) {
        val now = System.currentTimeMillis()
        val books = bookDao.getBooksByGitProjectId(project.id)
        for (book in books) {
            val hashes = linkedSetOf<String>()
            if (book.contentHash.isNotBlank()) hashes += book.contentHash
            val relative = book.gitRelativePath?.trim().orEmpty()
            if (relative.isNotEmpty() && project.remoteUrl.isNotBlank()) {
                hashes += BookContentHasher.hashForGitDocument(project.remoteUrl, relative)
            }
            if (hashes.isEmpty()) {
                hashes += BookContentHasher.hashEmptyFallback(
                    book.importFormat,
                    book.title,
                    book.filePath,
                )
            }
            for (hash in hashes) {
                deletedBookDao.upsert(DeletedBookEntity(contentHash = hash, deletedAt = now))
            }
            ParsedBookStorage.deleteBundleDir(book.parsedBundlePath)
            book.coverImagePath?.let { path -> runCatching { File(path).delete() } }
        }
        if (books.isNotEmpty()) {
            bookDao.deleteBooksByIds(books.map { it.id })
        }
        GitProjectStorage.deleteProjectDir(project.localPath)
        gitProjectDao.delete(project)
    }

    /** 本机有云端备份尚未包含的墓碑，或云端仍残留已删条目时需要推送。 */
    private suspend fun localTombstonesNeedPush(unpackDir: File): Boolean {
        val remoteBookTombs = readJsonList(
            File(unpackDir, "deleted_books.json"),
            DeletedBookBackup::class.java,
        )
        val remoteBooks = remoteBookTombs.map { it.contentHash }.toSet()
        if (deletedBookDao.getAll().any { it.contentHash !in remoteBooks }) return true
        // 本机已重新导入、墓碑已撤销，但云端 deleted_books 仍在，需要推送去掉
        if (remoteBookTombs.any { remote ->
                remote.contentHash.isNotBlank() &&
                    deletedBookDao.getByHash(remote.contentHash) == null &&
                    bookDao.getBookByContentHash(remote.contentHash) != null
            }
        ) {
            return true
        }

        val remoteProjectTombs = readJsonList(
            File(unpackDir, "deleted_git_projects.json"),
            DeletedGitProjectBackup::class.java,
        )
        val remoteProjects = remoteProjectTombs.map { it.remoteUrl }.toSet()
        if (deletedGitProjectDao.getAll().any { localTomb ->
                remoteProjects.none { GitHubRepoUrlParser.sameRepo(it, localTomb.remoteUrl) }
            }
        ) {
            return true
        }
        if (remoteProjectTombs.any { remote ->
                remote.remoteUrl.isNotBlank() &&
                    findDeletedGitProject(remote.remoteUrl) == null &&
                    gitProjectDao.getAllProjectsList().any {
                        GitHubRepoUrlParser.sameRepo(it.remoteUrl, remote.remoteUrl)
                    }
            }
        ) {
            return true
        }

        val deletedHashes = deletedBookDao.getAllHashes().toSet()
        if (deletedHashes.isNotEmpty()) {
            val books = readJsonList(File(unpackDir, "books.json"), BookEntity::class.java)
            if (books.any { book ->
                    val hash = remoteStableHash(book) ?: book.contentHash.takeIf { it.isNotBlank() }
                    hash != null && hash in deletedHashes
                }
            ) {
                return true
            }
        }
        val deletedUrls = deletedGitProjectDao.getAllRemoteUrls().toSet()
        if (deletedUrls.isNotEmpty()) {
            val projects = readJsonList(
                File(unpackDir, "git_projects.json"),
                GitProjectBackup::class.java,
            )
            if (projects.any { project ->
                    deletedUrls.any { GitHubRepoUrlParser.sameRepo(it, project.remoteUrl) }
                }
            ) {
                return true
            }
        }
        return false
    }

    /**
     * @return 从云端写入本机的条数：新建克隆，或更新了最近打开/收藏置顶等元数据
     */
    private suspend fun mergeGitProjectsFromBackup(
        unpackDir: File,
        cloneMissing: Boolean,
    ): Int {
        val remotes = readJsonList(File(unpackDir, "git_projects.json"), GitProjectBackup::class.java)
        if (remotes.isEmpty()) return 0
        val tombstones = deletedGitProjectDao.getAll()
        val pending = remotes.filter { remote ->
            if (remote.remoteUrl.isBlank()) return@filter false
            val tomb = tombstones.firstOrNull {
                GitHubRepoUrlParser.sameRepo(it.remoteUrl, remote.remoteUrl)
            } ?: return@filter true
            !GitProjectSyncPolicy.tombstoneBlocksImport(remote.addTime.time, tomb.deletedAt)
        }
        val outcomes = mapLimitedParallel(pending, GIT_RESTORE_CLONE_PARALLELISM) { remote ->
            mergeGitProject(remote, cloneMissing = cloneMissing)
        }
        return outcomes.count { it }
    }

    private suspend fun mergeGitProject(
        remote: GitProjectBackup,
        cloneMissing: Boolean,
    ): Boolean {
        val canonicalUrl = GitHubRepoUrlParser.canonicalBrowseUrl(remote.remoteUrl)
            ?: remote.remoteUrl
        val tomb = findDeletedGitProject(canonicalUrl)
        if (tomb != null &&
            GitProjectSyncPolicy.tombstoneBlocksImport(remote.addTime.time, tomb.deletedAt)
        ) {
            return false
        }
        if (tomb != null) {
            revokeGitProjectTombstones(canonicalUrl)
        }
        var local = gitProjectDao.getByRemoteUrl(canonicalUrl)
            ?: gitProjectDao.getByRemoteUrl(remote.remoteUrl)
            ?: gitProjectDao.getAllProjectsList().firstOrNull {
                GitHubRepoUrlParser.sameRepo(it.remoteUrl, remote.remoteUrl)
            }
        if (local == null) {
            return runCatching {
                if (cloneMissing) {
                    cloneGitProjectFromBackup(remote)
                } else {
                    insertGitProjectMetadataFromBackup(remote)
                }
                true
            }.onFailure {
                Log.w(TAG, "恢复克隆 Git 项目失败 ${remote.remoteUrl}: ${it.localizedMessage}", it)
            }.getOrDefault(false)
        }

        if (cloneMissing && !GitProjectStorage.hasValidRepo(local.localPath)) {
            runCatching {
                recloneExistingGitProject(local, remote)
            }.onFailure {
                Log.w(TAG, "恢复补克隆 Git 项目失败 ${remote.remoteUrl}: ${it.localizedMessage}", it)
            }
            local = gitProjectDao.getById(local.id) ?: local
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

    private suspend fun insertGitProjectMetadataFromBackup(remote: GitProjectBackup): GitProjectEntity {
        val parsed = GitHubRepoUrlParser.parse(remote.remoteUrl)
        val canonical = parsed?.httpsBrowseUrl
            ?: GitHubRepoUrlParser.canonicalBrowseUrl(remote.remoteUrl)
            ?: remote.remoteUrl
        val resolvedTitle = parsed?.repo?.ifBlank { remote.title } ?: remote.title
        val placeholder = GitProjectEntity(
            title = resolvedTitle,
            remoteUrl = canonical,
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
        return placeholder.copy(id = projectId)
    }

    private suspend fun cloneGitProjectFromBackup(remote: GitProjectBackup) {
        val local = insertGitProjectMetadataFromBackup(remote)
        val projectId = local.id
        try {
            recloneExistingGitProject(
                local = local,
                remote = remote,
            )
        } catch (e: Exception) {
            // 克隆失败仍保留元数据（远程地址、阅读记录），之后可在项目页重新拉取
            runCatching {
                GitProjectStorage.deleteProjectDir(
                    GitProjectStorage.projectDir(context, projectId).absolutePath,
                )
            }
            throw e
        }
    }

    private suspend fun recloneExistingGitProject(
        local: GitProjectEntity,
        remote: GitProjectBackup,
    ) {
        val parsed = GitHubRepoUrlParser.parse(local.remoteUrl.ifBlank { remote.remoteUrl })
            ?: throw GitCloneException("无法识别的 GitHub 仓库地址")
        val dest = GitProjectStorage.projectDir(context, local.id)
        val branch = remote.defaultBranch.trim().ifEmpty { local.defaultBranch }
            .takeIf { it.isNotEmpty() }
        val result = GitProjectCloner.clone(
            cloneUrl = parsed.cloneUrl,
            destination = dest,
            branch = branch,
        )
        gitProjectDao.update(
            local.copy(
                title = parsed.repo.ifBlank { local.title },
                remoteUrl = parsed.httpsBrowseUrl,
                localPath = dest.absolutePath,
                defaultBranch = result.branch.ifBlank { remote.defaultBranch.ifBlank { local.defaultBranch } },
                lastCommitSha = result.commitSha.ifBlank { remote.lastCommitSha.ifBlank { local.lastCommitSha } },
                lastPulledAt = Date(),
                hasRemoteUpdate = false,
            ),
        )
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
        val remoteByUrl = remotes.associateBy {
            GitHubRepoUrlParser.canonicalBrowseUrl(it.remoteUrl) ?: it.remoteUrl
        }
        val deletedUrls = deletedGitProjectDao.getAllRemoteUrls().toSet()
        for (local in gitProjectDao.getAllProjectsList()) {
            if (deletedUrls.any { GitHubRepoUrlParser.sameRepo(it, local.remoteUrl) }) continue
            val localKey = GitHubRepoUrlParser.canonicalBrowseUrl(local.remoteUrl) ?: local.remoteUrl
            val remote = remoteByUrl[localKey]
                ?: remotes.firstOrNull { GitHubRepoUrlParser.sameRepo(it.remoteUrl, local.remoteUrl) }
            if (remote == null) {
                // 本机已导入、云端尚无：需推送，否则另一台设备同步时看不到该项目
                return true
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
            BookContentHasher.matchesGitDocument(remote.contentHash, project.remoteUrl, path)
        }?.id
    }

    private suspend fun rematchGitBooks() {
        val projects = gitProjectDao.getAllProjectsList()
        if (projects.isEmpty()) return
        val books = bookDao.getAllBooksList().filter { !it.gitRelativePath.isNullOrBlank() }
        for (book in books) {
            val path = book.gitRelativePath ?: continue
            val match = projects.firstOrNull { project ->
                BookContentHasher.matchesGitDocument(book.contentHash, project.remoteUrl, path) ||
                    book.gitProjectId == project.id
            } ?: projects.firstOrNull { project ->
                BookContentHasher.matchesGitDocument(book.contentHash, project.remoteUrl, path)
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

    private fun wasReaddedAfterTombstone(addedAt: Date?, deletedAt: Long): Boolean {
        val added = addedAt?.time ?: return false
        return !GitProjectSyncPolicy.tombstoneBlocksImport(added, deletedAt)
    }

    private suspend fun revokeGitProjectTombstones(remoteUrl: String) {
        for (url in GitHubRepoUrlParser.lookupUrls(remoteUrl)) {
            deletedGitProjectDao.deleteByRemoteUrl(url)
        }
        deletedGitProjectDao.getAll()
            .filter { GitHubRepoUrlParser.sameRepo(it.remoteUrl, remoteUrl) }
            .forEach { deletedGitProjectDao.deleteByRemoteUrl(it.remoteUrl) }
    }

    private suspend fun findDeletedGitProject(remoteUrl: String): DeletedGitProjectEntity? {
        for (url in GitHubRepoUrlParser.lookupUrls(remoteUrl)) {
            deletedGitProjectDao.getByRemoteUrl(url)?.let { return it }
        }
        return deletedGitProjectDao.getAll().firstOrNull {
            GitHubRepoUrlParser.sameRepo(it.remoteUrl, remoteUrl)
        }
    }

    private fun cleanupTemp() {
        runCatching { workDir.deleteRecursively() }
        runCatching { zipFile.delete() }
    }

    companion object {
        private const val TAG = "BackupManager"
        /** v4：增加 deleted_books / deleted_git_projects 墓碑，跨设备同步删除 */
        private const val FORMAT_VERSION = 4
        private const val LATEST_BACKUP_NAME = "backup.zip"
        private const val PARSED_BOOKS_DIR = "parsed_books"
        private const val AVATAR_FILE_NAME = "profile_avatar.jpg"
    }
}
