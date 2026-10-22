package space.liushenme.markdownreader.ui.screens.reader

import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import space.liushenme.markdownreader.data.repository.IBookmarkRepository
import space.liushenme.markdownreader.ui.screens.reader.anchor.withCapturedAnchor

class ReaderBookmarkManager @Inject constructor(
    private val bookmarkRepository: IBookmarkRepository,
) {
    suspend fun addBookmark(bookmark: BookmarkEntity): Long =
        bookmarkRepository.addBookmark(bookmark)

    suspend fun deleteBookmark(bookmark: BookmarkEntity) =
        bookmarkRepository.deleteBookmark(bookmark)

    suspend fun getBookmarksByBookId(bookId: Long) =
        bookmarkRepository.getBookmarksByBookId(bookId)

    suspend fun deleteBookmarksByBookId(bookId: Long) =
        bookmarkRepository.deleteBookmarksByBookId(bookId)

    fun buildBookmark(
        bookId: Long,
        position: Int,
        previewText: String,
        note: String?,
        createTime: Date = Date(),
    ): BookmarkEntity = BookmarkEntity(
        bookId = bookId,
        position = position,
        previewText = previewText,
        note = note,
        createTime = createTime,
    )
}
