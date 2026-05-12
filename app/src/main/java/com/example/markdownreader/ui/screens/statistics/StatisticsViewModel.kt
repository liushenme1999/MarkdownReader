package com.example.markdownreader.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.markdownreader.data.local.entity.BookEntity
import com.example.markdownreader.data.repository.BookRepository
import com.example.markdownreader.data.repository.BookmarkRepository
import com.example.markdownreader.data.repository.HighlightRepository
import com.example.markdownreader.data.repository.ReadingProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val highlightRepository: HighlightRepository,
    private val readingProgressRepository: ReadingProgressRepository
) : ViewModel() {

    private val _statistics = MutableStateFlow(ReadingStatistics())
    val statistics: StateFlow<ReadingStatistics> = _statistics.asStateFlow()

    private val _readingTrend = MutableStateFlow<List<DailyReading>>(emptyList())
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
                highlightRepository.getAllHighlights()
            ) { books, bookmarks, highlights ->
                val finishedBooks = books.count { it.readingProgress >= 0.95 }
                val totalReadTime = calculateTotalReadTime(books)
                val totalReadChars = books.sumOf { (it.readingProgress * it.totalChars).toInt() }

                ReadingStatistics(
                    totalBooks = books.size,
                    finishedBooks = finishedBooks,
                    totalReadTime = totalReadTime,
                    totalReadChars = totalReadChars,
                    totalBookmarks = bookmarks.size,
                    totalHighlights = highlights.size
                )
            }.collect { _statistics.value = it }
        }
    }

    private fun loadReadingTrend() {
        viewModelScope.launch {
            // 生成最近7天的数据
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val trend = mutableListOf<DailyReading>()

            for (i in 6 downTo 0) {
                val dayCalendar = calendar.clone() as Calendar
                dayCalendar.add(Calendar.DAY_OF_YEAR, -i)

                trend.add(
                    DailyReading(
                        dayOfWeek = dateFormat.format(dayCalendar.time),
                        minutes = (0..120).random() // 模拟数据，实际应从数据库读取
                    )
                )
            }

            _readingTrend.value = trend
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

    private fun calculateTotalReadTime(books: List<BookEntity>): Int {
        // 假设平均阅读速度：每小时 20000 字
        val avgSpeedPerHour = 20000
        val totalChars = books.sumOf { (it.readingProgress * it.totalChars).toInt() }
        return (totalChars / avgSpeedPerHour).coerceAtLeast(0)
    }
}
