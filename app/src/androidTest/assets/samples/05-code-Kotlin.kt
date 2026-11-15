package com.example.reader

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 代码阅读测试（Kotlin）：语法高亮、行号、粘性面包屑、超长行横向滚动。
 */
data class Chapter(
    val index: Int,
    val title: String,
    val offset: Int,
    val chars: Int,
)

sealed interface ReaderEvent {
    data class Open(val bookId: Long) : ReaderEvent
    data object Close : ReaderEvent
}

class ChapterRepository(private val dao: ChapterDao) {
    fun chapters(bookId: Long): Flow<List<Chapter>> = dao.observe(bookId).map { rows ->
        rows.mapIndexed { i, row -> Chapter(i + 1, row.title, row.offset, row.chars) }
    }

    suspend fun findByOffset(bookId: Long, offset: Int): Chapter? =
        chapters(bookId).firstOrNull()?.lastOrNull { it.offset <= offset }

    // 这一行故意很长，用来测试「代码块自动换行」关闭后的横向滚动条，以及开启换行时的排版效果，确保整行内容都能完整看到而不被裁切。This line is intentionally very long to verify horizontal scrolling behaviour when code wrapping is disabled and full rendering when it is enabled.
    fun progress(chapter: Chapter, position: Int): Float =
        if (chapter.chars <= 0) 0f else (position.toFloat() / chapter.chars).coerceIn(0f, 1f)
}

fun main() {
    val sample = listOf(1, 2, 3, 4, 5)
    println(sample.filter { it % 2 == 1 }.map { it * it })
}
