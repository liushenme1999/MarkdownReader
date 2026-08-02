package space.liushenme.markdownreader.navigation

import android.net.Uri

/** 主导航路由常量，避免魔法字符串分散在各 Composable 中。 */
object AppRoutes {
    const val BOOKSHELF = "bookshelf"
    const val BOOKSHELF_SEARCH = "bookshelf_search"
    const val BOOKSHELF_GROUPS = "bookshelf_groups"
    const val BOOKSHELF_LAYOUT = "bookshelf_layout"
    const val PROFILE = "profile"
    const val NOTES = "notes"
    const val STATISTICS = "statistics"
    const val READING_SETTINGS = "reading_settings"
    const val BACKUP_RESTORE = "backup_restore"
    const val BACKUP_HELP = "backup_help"
    const val USER_AGREEMENT = "user_agreement"
    const val PRIVACY_POLICY = "privacy_policy"
    const val ABOUT = "about"
    const val READER = "reader/{bookId}"
    const val PROJECT = "project/{projectId}"
    const val WEB_LINK = "web_link?url={url}"

    /** 使用书架统一页面背景与状态栏配色的路由 */
    val shelfStyleRoutes = setOf(
        BOOKSHELF,
        BOOKSHELF_SEARCH,
        BOOKSHELF_GROUPS,
        BOOKSHELF_LAYOUT,
        PROFILE,
        NOTES,
        STATISTICS,
        READING_SETTINGS,
        BACKUP_RESTORE,
        BACKUP_HELP,
        USER_AGREEMENT,
        PRIVACY_POLICY,
        ABOUT,
        PROJECT,
    )

    fun reader(bookId: Long): String = "reader/$bookId"

    fun project(projectId: Long): String = "project/$projectId"

    fun webLink(url: String): String = "web_link?url=${Uri.encode(url)}"
}
