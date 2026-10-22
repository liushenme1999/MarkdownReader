package space.liushenme.markdownreader.ui.screens.reader

import java.util.Date
import javax.inject.Inject
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.data.repository.IHighlightRepository
import space.liushenme.markdownreader.data.repository.ReaderSettingsRepository
import space.liushenme.markdownreader.model.HighlightStyle
import space.liushenme.markdownreader.ui.screens.reader.anchor.withCapturedAnchor

class ReaderHighlightManager @Inject constructor(
    private val highlightRepository: IHighlightRepository,
    private val readerSettingsRepository: ReaderSettingsRepository,
) {
    suspend fun addHighlight(highlight: HighlightEntity): Long =
        highlightRepository.addHighlight(highlight)

    suspend fun updateHighlight(highlight: HighlightEntity) =
        highlightRepository.updateHighlight(highlight)

    suspend fun deleteHighlight(highlight: HighlightEntity) =
        highlightRepository.deleteHighlight(highlight)

    suspend fun getHighlightsByBookId(bookId: Long) =
        highlightRepository.getHighlightsByBookId(bookId)

    suspend fun deleteHighlightsByBookId(bookId: Long) =
        highlightRepository.deleteHighlightsByBookId(bookId)

    suspend fun saveLastHighlightPreference(colorArgb: Int, style: HighlightStyle) {
        readerSettingsRepository.setLastHighlightPreference(colorArgb, style)
    }

    fun buildHighlight(
        bookId: Long,
        startPosition: Int,
        endPosition: Int,
        highlightedText: String,
        colorArgb: Int,
        style: HighlightStyle,
        createTime: Date = Date(),
    ): HighlightEntity = HighlightEntity(
        bookId = bookId,
        startPosition = startPosition,
        endPosition = endPosition,
        highlightedText = highlightedText,
        color = colorArgb,
        style = style.storageKey,
        createTime = createTime,
    )
}
