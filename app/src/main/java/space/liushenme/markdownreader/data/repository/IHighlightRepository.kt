package space.liushenme.markdownreader.data.repository

import kotlinx.coroutines.flow.Flow
import space.liushenme.markdownreader.data.local.entity.HighlightEntity

interface IHighlightRepository {
    fun getHighlightsByBookId(bookId: Long): Flow<List<HighlightEntity>>
    fun getAllHighlights(): Flow<List<HighlightEntity>>
    suspend fun addHighlight(highlight: HighlightEntity): Long
    suspend fun updateHighlight(highlight: HighlightEntity)
    suspend fun deleteHighlight(highlight: HighlightEntity)
    suspend fun deleteHighlightsByBookId(bookId: Long)
}
