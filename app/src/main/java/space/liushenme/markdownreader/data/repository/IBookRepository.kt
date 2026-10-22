package space.liushenme.markdownreader.data.repository

import kotlinx.coroutines.flow.Flow
import space.liushenme.markdownreader.data.local.entity.BookEntity

interface IBookRepository {
    fun getAllBooks(): Flow<List<BookEntity>>
    fun getStandaloneBooks(): Flow<List<BookEntity>>
    fun getFavoriteBooks(): Flow<List<BookEntity>>
    suspend fun getBookById(id: Long): BookEntity?
    suspend fun getBookByFilePath(filePath: String): BookEntity?
    suspend fun getBookByContentHash(contentHash: String): BookEntity?
    suspend fun getBookByGitPath(projectId: Long, relativePath: String): BookEntity?
    suspend fun getBooksByGitProjectId(projectId: Long): List<BookEntity>
    suspend fun addBook(book: BookEntity): Long
    suspend fun updateBook(book: BookEntity)
    fun scheduleUploadBookContent(bookId: Long)
    suspend fun deleteBook(book: BookEntity, deleteCloudBackup: Boolean = false)
    suspend fun updateReadingProgress(bookId: Long, progress: Float, position: Int, previewText: String = "")
    suspend fun toggleFavorite(bookId: Long, isFavorite: Boolean)
    suspend fun getBookCount(): Int
    suspend fun deleteBooksByIds(ids: Collection<Long>, deleteCloudBackup: Boolean = false)
    suspend fun pinBooksByIds(ids: Collection<Long>, pinOrder: Long)
    suspend fun unpinBooksByIds(ids: Collection<Long>)
    suspend fun updateShelfGroupByIds(ids: Collection<Long>, groupName: String)
}
