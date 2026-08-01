package space.liushenme.markdownreader.data.local

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import space.liushenme.markdownreader.data.local.dao.BookDao
import space.liushenme.markdownreader.data.local.dao.BookmarkDao
import space.liushenme.markdownreader.data.local.dao.HighlightDao
import space.liushenme.markdownreader.data.local.dao.ReadingProgressDao
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.importing.ParsedBookStorage

/**
 * 将 migration 写入的 legacy_{id} 升级为正文 SHA-256；
 * 若目标哈希已存在则合并到已有书并删除重复行。
 */
@Singleton
class BookContentHashBackfill @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao,
    private val highlightDao: HighlightDao,
    private val readingProgressDao: ReadingProgressDao,
) {
    private val mutex = Mutex()
    private var done = false

    suspend fun runIfNeeded() {
        mutex.withLock {
            if (done) return
            withContext(Dispatchers.IO) {
                runCatching { backfill() }
                    .onFailure { Log.w(TAG, "contentHash 回填失败: ${it.localizedMessage}", it) }
            }
            done = true
        }
    }

    private suspend fun backfill() {
        val legacyBooks = bookDao.getAllBooksList()
            .filter { BookContentHasher.isLegacy(it.contentHash) }
        if (legacyBooks.isEmpty()) return

        for (book in legacyBooks) {
            val bundle = ParsedBookStorage.bundleDir(context, book.id)
            val realHash = BookContentHasher.hashFromBundleOrFallback(
                bundleDir = bundle,
                importFormat = book.importFormat,
                title = book.title,
                filePath = book.filePath,
            )
            val existing = bookDao.getBookByContentHash(realHash)
            if (existing == null) {
                bookDao.updateBook(book.copy(contentHash = realHash))
                continue
            }
            if (existing.id == book.id) continue
            mergeDuplicateInto(keep = existing, drop = book)
        }
    }

    private suspend fun mergeDuplicateInto(keep: BookEntity, drop: BookEntity) {
        val keepTime = keep.lastReadTime?.time ?: 0L
        val dropTime = drop.lastReadTime?.time ?: 0L
        val preferDrop = dropTime > keepTime
        val merged = keep.copy(
            title = if (preferDrop) drop.title else keep.title,
            author = drop.author ?: keep.author,
            totalChars = maxOf(keep.totalChars, drop.totalChars),
            currentPosition = if (preferDrop) drop.currentPosition else keep.currentPosition,
            progressPreviewText = if (preferDrop) {
                drop.progressPreviewText
            } else {
                keep.progressPreviewText
            },
            readingProgress = if (preferDrop) drop.readingProgress else keep.readingProgress,
            lastReadTime = if (preferDrop) drop.lastReadTime else keep.lastReadTime,
            isFavorite = keep.isFavorite || drop.isFavorite,
            shelfGroup = keep.shelfGroup.ifBlank { drop.shelfGroup },
            isPinned = keep.isPinned || drop.isPinned,
            pinOrder = maxOf(keep.pinOrder, drop.pinOrder),
            contentHash = keep.contentHash.takeUnless { BookContentHasher.isLegacy(it) }
                ?: drop.contentHash.takeUnless { BookContentHasher.isLegacy(it) }
                ?: keep.contentHash,
        )
        bookDao.updateBook(merged)

        // 若 keep 缺正文而 drop 有，拷贝目录
        val keepBundle = ParsedBookStorage.bundleDir(context, keep.id)
        val dropBundle = ParsedBookStorage.bundleDir(context, drop.id)
        if (!File(keepBundle, ParsedBookStorage.BODY_FILE).isFile &&
            File(dropBundle, ParsedBookStorage.BODY_FILE).isFile
        ) {
            keepBundle.parentFile?.mkdirs()
            if (keepBundle.exists()) keepBundle.deleteRecursively()
            dropBundle.copyRecursively(keepBundle, overwrite = true)
            val cover = when {
                File(keepBundle, ParsedBookStorage.COVER_JPG).isFile ->
                    File(keepBundle, ParsedBookStorage.COVER_JPG).absolutePath
                File(keepBundle, ParsedBookStorage.COVER_PNG).isFile ->
                    File(keepBundle, ParsedBookStorage.COVER_PNG).absolutePath
                else -> merged.coverImagePath
            }
            bookDao.updateBook(
                merged.copy(
                    parsedBundlePath = keepBundle.absolutePath,
                    filePath = File(keepBundle, ParsedBookStorage.BODY_FILE).absolutePath,
                    coverImagePath = cover,
                ),
            )
        }

        bookmarkDao.reassignBookId(drop.id, keep.id)
        highlightDao.reassignBookId(drop.id, keep.id)
        readingProgressDao.mergeReassignBookId(drop.id, keep.id)
        ParsedBookStorage.deleteBundleDir(drop.parsedBundlePath)
        bookDao.deleteBook(drop)
    }

    companion object {
        private const val TAG = "BookContentHashBackfill"
    }
}
