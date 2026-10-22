package space.liushenme.markdownreader.data.repository

import space.liushenme.markdownreader.data.local.dao.HighlightDao
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HighlightRepository @Inject constructor(
    private val highlightDao: HighlightDao
) : IHighlightRepository {
    override fun getHighlightsByBookId(bookId: Long): Flow<List<HighlightEntity>> = 
        highlightDao.getHighlightsByBookId(bookId)

    override fun getAllHighlights(): Flow<List<HighlightEntity>> = highlightDao.getAllHighlights()

    override suspend fun addHighlight(highlight: HighlightEntity): Long = highlightDao.insertHighlight(highlight)

    override suspend fun updateHighlight(highlight: HighlightEntity) = highlightDao.updateHighlight(highlight)

    override suspend fun deleteHighlight(highlight: HighlightEntity) = highlightDao.deleteHighlight(highlight)

    override suspend fun deleteHighlightsByBookId(bookId: Long) = highlightDao.deleteHighlightsByBookId(bookId)
}
