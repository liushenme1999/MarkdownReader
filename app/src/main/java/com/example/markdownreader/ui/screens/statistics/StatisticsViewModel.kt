package com.example.markdownreader.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.markdownreader.data.local.entity.BookEntity
import com.example.markdownreader.data.repository.BookRepository
import com.example.markdownreader.data.repository.BookmarkRepository
import com.example.markdownreader.data.repository.HighlightRepository
import com.example.markdownreader.data.repository.ReadingProgressRepository
import com.example.markdownreader.data.repository.ReadingTrendAggregator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val highlightRepository: HighlightRepository,
    private val readingProgressRepository: ReadingProgressRepository,
) : ViewModel() {

    private val _statistics = MutableStateFlow(ReadingStatistics())
    val statistics: StateFlow<ReadingStatistics> = _statistics.asStateFlow()

    private val _readingTrend = MutableStateFlow(
        ReadingTrendAggregator.buildLast7DaysTrend(emptyList()).map {
            DailyReading(dayOfWeek = it.weekdayLabel, minutes = it.minutes)
        },
    )
    val readingTrend: StateFlow<List<DailyReading>> = _readingTrend.asStateFlow()

    private val _books = MutableStateFlow<List<BookEntity>>(emptyList())
    val books: StateFlow<List<BookEntity>> = _books.asStateFlow()

    init {
        loadStatistics()
        loadReadingTrend()
        loadBooks()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            combine(
                bookRepository.getAllBooks(),
                bookmarkRepository.getAllBookmarks(),
                highlightRepository.getAllHighlights(),
                readingProgressRepository.observeTotalReadTimeMinutes(),
                readingProgressRepository.observeTotalReadChars(),
            ) { books, bookmarks, highlights, totalMinutesFromDb, totalCharsFromDb ->
                val finishedBooks = books.count { it.readingProgress >= 0.95f }
                val charsFromBooks = books.sumOf { (it.readingProgress * it.totalChars).toInt() }
                val totalReadChars = if (totalCharsFromDb > 0) totalCharsFromDb else charsFromBooks
                val totalReadTimeHours = if (totalMinutesFromDb > 0) {
                    totalMinutesFromDb / 60
                } else {
                    calculateTotalReadTimeHoursFromBooks(books)
                }

                ReadingStatistics(
                    totalBooks = books.size,
                    finishedBooks = finishedBooks,
                    totalReadTime = totalReadTimeHours,
                    totalReadChars = totalReadChars,
                    totalBookmarks = bookmarks.size,
                    totalHighlights = highlights.size,
                )
            }.collect { _statistics.value = it }
        }
    }

    private fun loadReadingTrend() {
        viewModelScope.launch {
            readingProgressRepository.observeLast7DaysTrend()
                .map { trend ->
                    trend.map { DailyReading(dayOfWeek = it.weekdayLabel, minutes = it.minutes) }
                }
                .collect { _readingTrend.value = it }
        }
    }

    private fun loadBooks() {
        viewModelScope.launch {
            bookRepository.getAllBooks()
                .map { books ->
                    books
                        .filter { it.lastReadTime != null }
                        .sortedByDescending { it.lastReadTime }
                }
                .collect { _books.value = it }
        }
    }

    private fun calculateTotalReadTimeHoursFromBooks(books: List<BookEntity>): Int {
        val totalChars = books.sumOf { (it.readingProgress * it.totalChars).toInt() }
        return (totalChars / ReadingTrendAggregator.CHARS_PER_HOUR).coerceAtLeast(0)
    }
}
