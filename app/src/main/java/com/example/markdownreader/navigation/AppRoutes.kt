package com.example.markdownreader.navigation

/** 主导航路由常量，避免魔法字符串分散在各 Composable 中。 */
object AppRoutes {
    const val BOOKSHELF = "bookshelf"
    const val PROFILE = "profile"
    const val NOTES = "notes"
    const val STATISTICS = "statistics"
    const val READER = "reader/{bookId}"

    fun reader(bookId: Long): String = "reader/$bookId"
}
