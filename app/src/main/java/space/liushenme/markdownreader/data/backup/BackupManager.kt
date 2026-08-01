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
import space.liushenme.markdownreader.data.local.dao.BookDao
import space.liushenme.markdownreader.data.local.dao.BookmarkDao
import space.liushenme.markdownreader.data.local.dao.HighlightDao
import space.liushenme.markdownreader.data.local.dao.ReadingProgressDao
import space.liushenme.markdownreader.data.local.dao.ShelfGroupDao
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.data.local.entity.ReadingProgressEntity
import space.liushenme.markdownreader.data.local.entity.ShelfGroupEntity
import space.liushenme.markdownreader.data.preferences.readerPreferencesDataStore
import space.liushenme.markdownreader.data.repository.WebDavConfig
import space.liushenme.markdownreader.data.repository.WebDavConfigRepository
import space.liushenme.markdownreader.data.webdav.Authorization
import space.liushenme.markdownreader.data.webdav.WebDav
import space.liushenme.markdownreader.data.webdav.WebDavException

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webDavConfigRepository: WebDavConfigRepository,
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao,
    private val highlightDao: HighlightDao,
    private val readingProgressDao: ReadingProgressDao,
    private val shelfGroupDao: ShelfGroupDao,
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

                webDavConfigRepository.setLastBackupTime(System.currentTimeMillis())
                cleanupTemp()
                zipName
            }.onFailure {
                Log.e(TAG, "备份失败", it)
                cleanupTemp()
            }
        }
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
                    webDavConfigRepository.setLastBackupTime(System.currentTimeMillis())
                    cleanupTemp()
                }.onFailure {
                    Log.w(TAG, "自动备份失败: ${it.localizedMessage}", it)
                    cleanupTemp()
                }
            }
        }
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
                applyRestore(unpackDir)
                webDavConfigRepository.setLastBackupTime(System.currentTimeMillis())
                cleanupTemp()
            }.onFailure {
                Log.e(TAG, "恢复失败", it)
                cleanupTemp()
            }
        }
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

    private suspend fun writeBackupContents(packDir: File) {
        val manifest = mapOf(
            "formatVersion" to FORMAT_VERSION,
            "appVersionName" to BuildConfig.VERSION_NAME,
            "appVersionCode" to BuildConfig.VERSION_CODE,
            "createdAt" to System.currentTimeMillis(),
        )
        File(packDir, "manifest.json").writeText(BackupGson.gson.toJson(manifest))

        File(packDir, "books.json").writeText(
            BackupGson.gson.toJson(bookDao.getAllBooksList()),
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

        val parsedBooks = File(context.filesDir, PARSED_BOOKS_DIR)
        if (parsedBooks.isDirectory) {
            parsedBooks.copyRecursively(
                File(packDir, PARSED_BOOKS_DIR),
                overwrite = true,
            )
        }
    }

    private suspend fun exportPreferences(outFile: File) {
        val snapshot = dataStore.data.first()
        val map = linkedMapOf<String, Any?>()
        for ((key, value) in snapshot.asMap()) {
            val name = key.name
            if (name in WebDavConfigRepository.WEBDAV_PREF_KEYS) continue
            when (value) {
                is String, is Int, is Long, is Float, is Double, is Boolean -> map[name] = value
                is Set<*> -> map[name] = value.map { it.toString() }
                else -> map[name] = value.toString()
            }
        }
        outFile.writeText(BackupGson.gson.toJson(map))
    }

    private suspend fun applyRestore(unpackDir: File) {
        val books: List<BookEntity> = readJsonList(File(unpackDir, "books.json"))
        val bookmarks: List<BookmarkEntity> = readJsonList(File(unpackDir, "bookmarks.json"))
        val highlights: List<HighlightEntity> = readJsonList(File(unpackDir, "highlights.json"))
        val progress: List<ReadingProgressEntity> =
            readJsonList(File(unpackDir, "reading_progress.json"))
        val groups: List<ShelfGroupEntity> = readJsonList(File(unpackDir, "shelf_groups.json"))

        bookmarkDao.deleteAll()
        highlightDao.deleteAll()
        readingProgressDao.deleteAll()
        bookDao.deleteAll()
        shelfGroupDao.deleteAll()

        val localParsedRoot = File(context.filesDir, PARSED_BOOKS_DIR)
        localParsedRoot.deleteRecursively()
        val packedParsed = File(unpackDir, PARSED_BOOKS_DIR)
        if (packedParsed.isDirectory) {
            packedParsed.copyRecursively(localParsedRoot, overwrite = true)
        }

        val remappedBooks = books.map { remapBookPaths(it) }
        if (remappedBooks.isNotEmpty()) {
            bookDao.insertBooks(remappedBooks)
        }
        if (bookmarks.isNotEmpty()) bookmarkDao.insertBookmarks(bookmarks)
        if (highlights.isNotEmpty()) highlightDao.insertHighlights(highlights)
        if (progress.isNotEmpty()) readingProgressDao.insertAll(progress)
        if (groups.isNotEmpty()) shelfGroupDao.insertAll(groups)

        val avatarSrc = File(unpackDir, AVATAR_FILE_NAME)
        val avatarDest = File(context.filesDir, AVATAR_FILE_NAME)
        if (avatarSrc.isFile) {
            avatarSrc.copyTo(avatarDest, overwrite = true)
        }

        importPreferences(File(unpackDir, "preferences.json"), avatarDest)
    }

    private fun remapBookPaths(book: BookEntity): BookEntity {
        val filesDir = context.filesDir.absolutePath
        val bundleDir = File(context.filesDir, "$PARSED_BOOKS_DIR/${book.id}")
        val newBundle = if (bundleDir.isDirectory) bundleDir.absolutePath else book.parsedBundlePath

        val newCover = when {
            book.coverImagePath.isNullOrBlank() -> book.coverImagePath
            File(bundleDir, "cover.jpg").isFile -> File(bundleDir, "cover.jpg").absolutePath
            File(bundleDir, "cover.png").isFile -> File(bundleDir, "cover.png").absolutePath
            book.coverImagePath!!.contains("/parsed_books/") -> {
                val name = File(book.coverImagePath!!).name
                val candidate = File(bundleDir, name)
                if (candidate.isFile) candidate.absolutePath else book.coverImagePath
            }
            else -> book.coverImagePath
        }

        val newFilePath = when {
            book.filePath.contains("/parsed_books/") || book.filePath.startsWith(filesDir) -> {
                val body = File(bundleDir, "body.txt")
                if (body.isFile) body.absolutePath else book.filePath
            }
            else -> book.filePath
        }

        return book.copy(
            filePath = newFilePath,
            coverImagePath = newCover,
            parsedBundlePath = newBundle,
        )
    }

    private suspend fun importPreferences(prefsFile: File, avatarDest: File) {
        if (!prefsFile.isFile) return
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val map: Map<String, Any> = BackupGson.gson.fromJson(prefsFile.readText(), type) ?: return

        // 已知 int / float 键，避免 Gson 把数字一律当成 Double 后类型错乱
        val intKeys = setOf(
            "reader_font_size",
            "reader_reader_padding_dp",
            "reader_last_highlight_color",
            "bookshelf_grid_columns",
        )
        val floatKeys = setOf("reader_line_spacing_multiplier")

        dataStore.edit { prefs ->
            for ((name, value) in map) {
                if (name in WebDavConfigRepository.WEBDAV_PREF_KEYS) continue
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

    private inline fun <reified T> readJsonList(file: File): List<T> {
        if (!file.isFile) return emptyList()
        val type = object : TypeToken<List<T>>() {}.type
        return BackupGson.gson.fromJson(file.readText(), type) ?: emptyList()
    }

    private fun cleanupTemp() {
        runCatching { workDir.deleteRecursively() }
        runCatching { zipFile.delete() }
    }

    companion object {
        private const val TAG = "BackupManager"
        private const val FORMAT_VERSION = 1
        private const val LATEST_BACKUP_NAME = "backup.zip"
        private const val PARSED_BOOKS_DIR = "parsed_books"
        private const val AVATAR_FILE_NAME = "profile_avatar.jpg"
    }
}
