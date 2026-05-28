package com.example.markdownreader.navigation

import android.net.Uri

/** 主导航路由常量，避免魔法字符串分散在各 Composable 中。 */
object AppRoutes {
    const val BOOKSHELF = "bookshelf"
    const val PROFILE = "profile"
    const val NOTES = "notes"
    const val STATISTICS = "statistics"
    const val READING_SETTINGS = "reading_settings"
    const val READER = "reader/{bookId}"
    const val WEB_LINK = "web_link?url={url}"

    /** 使用书架统一页面背景与状态栏配色的路由 */
    val shelfStyleRoutes = setOf(BOOKSHELF, PROFILE, NOTES, STATISTICS, READING_SETTINGS)

    fun reader(bookId: Long): String = "reader/$bookId"

    fun webLink(url: String): String = "web_link?url=${Uri.encode(url)}"
}
