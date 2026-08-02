package space.liushenme.markdownreader.data.repository

import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import space.liushenme.markdownreader.data.backup.BookContentSync
import space.liushenme.markdownreader.data.local.dao.BookDao
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.importing.ParsedBookStorage

@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao,
    private val bookContentSync: BookContentSync,
) {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getAllBooks(): Flow<List<BookEntity>> = bookDao.getAllBooks()

    /** 书架展示用：排除 Git 项目内文档（它们在项目浏览器中打开）。 */
    fun getStandaloneBooks(): Flow<List<BookEntity>> = bookDao.getStandaloneBooks()

    fun getFavoriteBooks(): Flow<List<BookEntity>> = bookDao.getFavoriteBooks()

    suspend fun getBookById(id: Long): BookEntity? = bookDao.getBookById(id)

    suspend fun getBookByFilePath(filePath: String): BookEntity? =
        bookDao.getBookByFilePath(filePath)

    suspend fun getBookByContentHash(contentHash: String): BookEntity? =
        bookDao.getBookByContentHash(contentHash)

    suspend fun getBookByGitPath(projectId: Long, relativePath: String): BookEntity? =
        bookDao.getBookByGitPath(projectId, relativePath)

    suspend fun getBooksByGitProjectId(projectId: Long): List<BookEntity> =
        bookDao.getBooksByGitProjectId(projectId)

    suspend fun addBook(book: BookEntity): Long = bookDao.insertBook(book)

    suspend fun updateBook(book: BookEntity) = bookDao.updateBook(book)

    /** 正文落盘成功后调用，异步上传到 WebDAV books/（Git 文档会被同步层跳过）。 */
    fun scheduleUploadBookContent(bookId: Long) {
        syncScope.launch {
            bookContentSync.uploadBookContent(bookId)
        }
    }

    suspend fun deleteBook(book: BookEntity) {
        val id = book.id
        val hash = book.contentHash
        deleteStoredAssets(book)
        bookDao.deleteBook(book)
        syncScope.launch {
            bookContentSync.deleteRemoteBookContent(hash, fallbackNumericId = id)
        }
    }

    suspend fun updateReadingProgress(
        bookId: Long,
        progress: Float,
        position: Int,
        previewText: String = "",
    ) {
        bookDao.updateReadingProgress(bookId, progress, position, previewText, Date().time)
    }

    suspend fun toggleFavorite(bookId: Long, isFavorite: Boolean) {
        bookDao.updateFavoriteStatus(bookId, isFavorite)
    }

    suspend fun getBookCount(): Int = bookDao.getBookCount()

    suspend fun deleteBooksByIds(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val snapshots = ids.mapNotNull { bookDao.getBookById(it) }
        for (book in snapshots) {
            deleteStoredAssets(book)
        }
        bookDao.deleteBooksByIds(ids.toList())
        syncScope.launch {
            snapshots.forEach { book ->
                bookContentSync.deleteRemoteBookContent(
                    book.contentHash,
                    fallbackNumericId = book.id,
                )
            }
        }
    }

    private fun deleteStoredAssets(book: BookEntity) {
        ParsedBookStorage.deleteBundleDir(book.parsedBundlePath)
        book.coverImagePath?.let { path ->
            runCatching { File(path).delete() }
        }
    }

    suspend fun pinBooksByIds(ids: Collection<Long>, pinOrder: Long) {
        if (ids.isEmpty()) return
        bookDao.pinBooksByIds(ids.toList(), pinOrder)
    }

    suspend fun unpinBooksByIds(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        bookDao.unpinBooksByIds(ids.toList())
    }

    suspend fun updateShelfGroupByIds(ids: Collection<Long>, groupName: String) {
        if (ids.isEmpty()) return
        bookDao.updateShelfGroupByIds(ids.toList(), groupName)
    }
}
