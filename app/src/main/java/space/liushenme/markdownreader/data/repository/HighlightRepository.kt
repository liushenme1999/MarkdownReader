package space.liushenme.markdownreader.data.repository

import space.liushenme.markdownreader.data.local.dao.HighlightDao
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HighlightRepository @Inject constructor(
    private val highlightDao: HighlightDao
) {
    fun getHighlightsByBookId(bookId: Long): Flow<List<HighlightEntity>> = 
        highlightDao.getHighlightsByBookId(bookId)

    fun getAllHighlights(): Flow<List<HighlightEntity>> = highlightDao.getAllHighlights()

    suspend fun addHighlight(highlight: HighlightEntity): Long = highlightDao.insertHighlight(highlight)

    suspend fun updateHighlight(highlight: HighlightEntity) = highlightDao.updateHighlight(highlight)

    suspend fun deleteHighlight(highlight: HighlightEntity) = highlightDao.deleteHighlight(highlight)

    suspend fun deleteHighlightsByBookId(bookId: Long) = highlightDao.deleteHighlightsByBookId(bookId)
}
