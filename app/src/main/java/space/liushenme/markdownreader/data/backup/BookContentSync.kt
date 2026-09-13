package space.liushenme.markdownreader.data.backup

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.liushenme.markdownreader.data.local.BookContentHasher
import space.liushenme.markdownreader.data.local.dao.BookDao
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.repository.WebDavConfig
import space.liushenme.markdownreader.data.repository.WebDavConfigRepository
import space.liushenme.markdownreader.data.webdav.Authorization
import space.liushenme.markdownreader.data.webdav.WebDav
import space.liushenme.markdownreader.importing.ParsedBookStorage

data class BookContentBatchResult(
    val success: Int,
    val skipped: Int,
    val failed: Int,
    val total: Int,
)

/**
 * 书籍正文（parsed_books）独立于日常备份包，同步到 WebDAV `books/{contentHash}.zip`。
 */
@Singleton
class BookContentSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webDavConfigRepository: WebDavConfigRepository,
    private val bookDao: BookDao,
) {
    private val tempDir: File
        get() = File(context.cacheDir, "book_content_sync").also { it.mkdirs() }

    suspend fun uploadBookContent(bookId: Long, overwrite: Boolean = true) =
        withContext(Dispatchers.IO) {
            runCatching {
                val book = bookDao.getBookById(bookId) ?: return@runCatching
                if (book.gitProjectId != null) return@runCatching
                val config = webDavConfigRepository.current()
                if (!config.isConfigured) return@runCatching
                val bundle = ParsedBookStorage.bundleDir(context, bookId)
                if (!File(bundle, ParsedBookStorage.BODY_FILE).isFile) return@runCatching

                val hash = upgradeContentHashIfNeeded(book)
                val auth = authorize(config) ?: return@runCatching
                val booksUrl = booksRootUrl(config)
                WebDav(booksUrl, auth).makeAsDir()
                val remote = WebDav(booksUrl + "$hash.zip", auth)
                if (!overwrite && remote.exists()) return@runCatching

                val zip = File(tempDir, "up_${hash}_$bookId.zip")
                if (zip.exists()) zip.delete()
                BackupZip.zipDirectory(bundle, zip)
                remote.upload(zip)
                zip.delete()
            }.onFailure {
                Log.w(TAG, "上传书籍正文失败 bookId=$bookId: ${it.localizedMessage}", it)
            }
        }

    /** 仅为远程尚不存在的书籍补齐正文，不覆盖已有远程包 */
    suspend fun uploadMissingBookContents(bookIds: Collection<Long>) = withContext(Dispatchers.IO) {
        mapLimitedParallel(bookIds, WEBDAV_TRANSFER_PARALLELISM) { bookId ->
            uploadBookContent(bookId, overwrite = false)
        }
    }

    /** 并行补齐本地缺失的 WebDAV 正文（已有正文会跳过）。 */
    suspend fun ensureLocalBookContents(bookIds: Collection<Long>) = withContext(Dispatchers.IO) {
        mapLimitedParallel(bookIds.distinct(), WEBDAV_TRANSFER_PARALLELISM) { bookId ->
            ensureLocalBookContent(bookId)
        }
    }

    /**
     * 手动备份全部本地正文到 WebDAV `books/`（覆盖已有远程包）。
     * 不写入日常备份 zip。
     */
    suspend fun backupAllBookContents(): Result<BookContentBatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val config = webDavConfigRepository.current()
            if (!config.isConfigured) error("请先配置 WebDAV 账号与密码")
            val auth = authorize(config) ?: error("WebDAV 授权失败，请检查账号与应用密码")
            val booksUrl = booksRootUrl(config)
            WebDav(booksUrl, auth).makeAsDir()

            val books = bookDao.getAllBooksList().filter { it.gitProjectId == null }
            val outcomes = mapLimitedParallel(books, WEBDAV_TRANSFER_PARALLELISM) { book ->
                val bundle = ParsedBookStorage.bundleDir(context, book.id)
                if (!File(bundle, ParsedBookStorage.BODY_FILE).isFile) {
                    return@mapLimitedParallel ContentOutcome.SKIPPED
                }
                runCatching {
                    val hash = upgradeContentHashIfNeeded(book)
                    val zip = File(tempDir, "up_${hash}_${book.id}.zip")
                    if (zip.exists()) zip.delete()
                    BackupZip.zipDirectory(bundle, zip)
                    WebDav(booksUrl + "$hash.zip", auth).upload(zip)
                    zip.delete()
                    ContentOutcome.SUCCESS
                }.onFailure {
                    Log.w(TAG, "备份正文失败 bookId=${book.id}: ${it.localizedMessage}", it)
                }.getOrDefault(ContentOutcome.FAILED)
            }
            val success = outcomes.count { it == ContentOutcome.SUCCESS }
            val skipped = outcomes.count { it == ContentOutcome.SKIPPED }
            val failed = outcomes.count { it == ContentOutcome.FAILED }
            if (books.isEmpty()) error("书架为空，没有可备份的正文")
            if (success == 0 && skipped == books.size) {
                error("本地没有可上传的正文文件，请确认本机书籍能正常打开")
            }
            if (success == 0 && failed > 0) error("正文备份全部失败（$failed 本）")
            BookContentBatchResult(success, skipped, failed, books.size)
        }
    }

    /**
     * 手动从 WebDAV `books/` 恢复正文到本机（仅处理书架中已有书籍）。
     * 默认跳过本地已有正文的书籍。
     */
    suspend fun restoreAllBookContents(
        onlyMissing: Boolean = true,
    ): Result<BookContentBatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val config = webDavConfigRepository.current()
            if (!config.isConfigured) error("请先配置 WebDAV 账号与密码")
            val auth = authorize(config) ?: error("WebDAV 授权失败，请检查账号与应用密码")
            val booksUrl = booksRootUrl(config)

            val books = bookDao.getAllBooksList().filter { it.gitProjectId == null }
            if (books.isEmpty()) error("书架为空，请先恢复日常备份（书架/进度）")

            val outcomes = mapLimitedParallel(books, WEBDAV_TRANSFER_PARALLELISM) { book ->
                val bundle = ParsedBookStorage.bundleDir(context, book.id)
                if (onlyMissing && isAcceptableLocalBundle(book, bundle)) {
                    upgradeContentHashIfNeeded(book)
                    persistCanonicalPaths(book.id)
                    return@mapLimitedParallel ContentOutcome.SKIPPED
                }
                val ok = downloadBookContent(book, booksUrl, auth)
                if (ok) {
                    upgradeContentHashIfNeeded(bookDao.getBookById(book.id) ?: book)
                    persistCanonicalPaths(book.id)
                    ContentOutcome.SUCCESS
                } else {
                    ContentOutcome.FAILED
                }
            }
            val success = outcomes.count { it == ContentOutcome.SUCCESS }
            val skipped = outcomes.count { it == ContentOutcome.SKIPPED }
            val failed = outcomes.count { it == ContentOutcome.FAILED }
            if (success == 0 && failed > 0 && skipped == 0) {
                error("正文恢复全部失败。请先在源设备点击「备份正文」")
            }
            BookContentBatchResult(success, skipped, failed, books.size)
        }
    }

    suspend fun deleteRemoteBookContent(
        contentHash: String,
        fallbackNumericId: Long? = null,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val config = webDavConfigRepository.current()
            if (!config.isConfigured) return@runCatching
            val auth = authorize(config) ?: return@runCatching
            val booksUrl = booksRootUrl(config)
            if (contentHash.isNotBlank()) {
                runCatching { WebDav(booksUrl + "$contentHash.zip", auth).delete() }
            }
            // 兼容清理旧版 books/{id}.zip
            fallbackNumericId?.let { id ->
                runCatching { WebDav(booksUrl + "$id.zip", auth).delete() }
            }
        }.onFailure {
            Log.w(TAG, "删除远程书籍正文失败 hash=$contentHash: ${it.localizedMessage}", it)
        }
    }

    /**
     * 若本地缺少可用 parsed bundle，则从 WebDAV 下载。
     * PDF 仅有 body、没有页图，或正文哈希对不上时会重新拉取。
     * @return true 表示本地已有完整正文或下载成功
     */
    suspend fun ensureLocalBookContent(
        bookId: Long,
        remoteNumericId: Long? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val book = bookDao.getBookById(bookId) ?: return@withContext false
        val bundle = ParsedBookStorage.bundleDir(context, bookId)
        if (isAcceptableLocalBundle(book, bundle)) {
            upgradeContentHashIfNeeded(book)
            return@withContext true
        }
        if (book.gitProjectId != null) return@withContext false
        runCatching {
            val config = webDavConfigRepository.current()
            if (!config.isConfigured) return@runCatching false
            val auth = authorize(config) ?: return@runCatching false
            val ok = downloadBookContent(book, booksRootUrl(config), auth, remoteNumericId)
            if (ok) {
                upgradeContentHashIfNeeded(bookDao.getBookById(bookId) ?: book)
                persistCanonicalPaths(bookId)
            }
            ok
        }.onFailure {
            Log.w(TAG, "下载书籍正文失败 bookId=$bookId: ${it.localizedMessage}", it)
        }.getOrDefault(false)
    }

    suspend fun ensureLocalBookContents(remoteToLocal: Map<Long, Long>) = withContext(Dispatchers.IO) {
        mapLimitedParallel(remoteToLocal.entries, WEBDAV_TRANSFER_PARALLELISM) { (remoteId, localId) ->
            ensureLocalBookContent(localId, remoteNumericId = remoteId)
        }
    }

    /**
     * 将 legacy_* 占位升级为正文哈希；若目标哈希已被其他书占用则保持原值并返回目标哈希（供远程路径）。
     */
    suspend fun upgradeContentHashIfNeeded(book: BookEntity): String {
        val current = bookDao.getBookById(book.id) ?: book
        val preferred = book.contentHash.takeIf {
            it.isNotBlank() && !BookContentHasher.isLegacy(it)
        }
        val realHash = preferred ?: BookContentHasher.hashFromBundleOrFallback(
            bundleDir = ParsedBookStorage.bundleDir(context, book.id),
            importFormat = current.importFormat,
            title = current.title,
            filePath = current.filePath,
        )
        if (current.contentHash == realHash) return realHash

        val existing = bookDao.getBookByContentHash(realHash)
        if (existing == null || existing.id == current.id) {
            bookDao.updateBook(current.copy(contentHash = realHash))
            return realHash
        }
        // 哈希冲突：远程仍用真实哈希，本地行暂留原值，避免 UNIQUE 冲突
        return realHash
    }

    private suspend fun downloadBookContent(
        book: BookEntity,
        booksUrl: String,
        auth: Authorization,
        remoteNumericId: Long? = null,
    ): Boolean {
        return runCatching {
            val localBundle = ParsedBookStorage.bundleDir(context, book.id)
            val extraHashes = listOfNotNull(
                BookContentHasher.hashFromBodyFile(File(localBundle, ParsedBookStorage.BODY_FILE)),
                BookContentHasher.hashFromPdfBundle(localBundle),
            )
            val hashCandidates = BookContentHasher.remoteContentZipNames(
                contentHash = book.contentHash,
                localId = book.id,
                remoteId = remoteNumericId,
                extraHashes = extraHashes,
            )

            val bundle = ParsedBookStorage.bundleDir(context, book.id)
            for (name in hashCandidates) {
                val remote = WebDav(booksUrl + "$name.zip", auth)
                if (!remote.exists()) continue
                val localZip = File(tempDir, "dl_${book.id}_$name.zip")
                if (localZip.exists()) localZip.delete()
                remote.downloadTo(localZip.absolutePath, true)
                val unpack = File(tempDir, "unpack_${book.id}_$name").also {
                    it.deleteRecursively()
                    it.mkdirs()
                }
                val accepted = runCatching {
                    BackupZip.unzipTo(localZip, unpack)
                    val source = resolveUnpackedBundle(unpack, book.id, remoteNumericId)
                    if (!isAcceptableLocalBundle(book, source)) {
                        Log.w(TAG, "忽略不匹配的远程正文 zip=$name.zip bookId=${book.id}")
                        false
                    } else {
                        bundle.parentFile?.mkdirs()
                        if (bundle.exists()) bundle.deleteRecursively()
                        source.copyRecursively(bundle, overwrite = true)
                        true
                    }
                }.onFailure {
                    Log.w(TAG, "解压远程正文失败 zip=$name.zip bookId=${book.id}", it)
                }.getOrDefault(false)
                localZip.delete()
                unpack.deleteRecursively()
                if (accepted) return@runCatching true
            }
            false
        }.onFailure {
            Log.w(TAG, "下载书籍正文失败 bookId=${book.id}: ${it.localizedMessage}", it)
        }.getOrDefault(false)
    }

    private fun resolveUnpackedBundle(unpack: File, localId: Long, remoteId: Long?): File {
        if (File(unpack, ParsedBookStorage.BODY_FILE).isFile) return unpack
        remoteId?.let { id ->
            val nested = File(unpack, id.toString())
            if (File(nested, ParsedBookStorage.BODY_FILE).isFile) return nested
        }
        val byLocal = File(unpack, localId.toString())
        if (File(byLocal, ParsedBookStorage.BODY_FILE).isFile) return byLocal
        return unpack.listFiles()?.firstOrNull { child ->
            child.isDirectory && File(child, ParsedBookStorage.BODY_FILE).isFile
        } ?: unpack
    }

    private fun isAcceptableLocalBundle(book: BookEntity, dir: File): Boolean {
        if (!ParsedBookStorage.isCompleteBundle(dir, book.importFormat)) return false
        return BookContentHasher.matchesStoredHash(book.contentHash, dir)
    }

    private suspend fun persistCanonicalPaths(bookId: Long) {
        val book = bookDao.getBookById(bookId) ?: return
        val bundle = ParsedBookStorage.bundleDir(context, bookId)
        val body = File(bundle, ParsedBookStorage.BODY_FILE)
        if (!body.isFile) return
        val cover = when {
            File(bundle, ParsedBookStorage.COVER_JPG).isFile ->
                File(bundle, ParsedBookStorage.COVER_JPG).absolutePath
            File(bundle, ParsedBookStorage.COVER_PNG).isFile ->
                File(bundle, ParsedBookStorage.COVER_PNG).absolutePath
            else -> null
        }
        val localBundle = bundle.absolutePath
        val localBody = body.absolutePath
        if (book.parsedBundlePath == localBundle &&
            book.filePath == localBody &&
            book.coverImagePath == cover
        ) {
            return
        }
        bookDao.updateBook(
            book.copy(
                parsedBundlePath = localBundle,
                filePath = localBody,
                coverImagePath = cover,
            ),
        )
    }

    private fun booksRootUrl(config: WebDavConfig): String =
        webDavConfigRepository.rootUrl(config) + "$BOOKS_DIR/"

    private suspend fun authorize(config: WebDavConfig): Authorization? {
        val auth = Authorization(config.account, config.password)
        val root = webDavConfigRepository.rootUrl(config)
        return if (WebDav(root, auth).check()) auth else null
    }

    private enum class ContentOutcome { SUCCESS, SKIPPED, FAILED }

    companion object {
        private const val TAG = "BookContentSync"
        const val BOOKS_DIR = "books"
    }
}
