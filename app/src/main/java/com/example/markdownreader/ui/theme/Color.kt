package com.example.markdownreader.ui.theme

import androidx.compose.ui.graphics.Color

/** 书架页浅灰背景（浅色模式） */
val BookshelfPageBackground = Color(0xFFF2F2F4)

/** 书架页背景（深色模式，略带灰） */
val BookshelfPageBackgroundDark = Color(0xFF252528)

/** 底部主导航栏纯白 */
val MainNavigationBarBackground = Color(0xFFFFFFFF)

/** 主导航栏选中项：图标与文字淡蓝（无选中底色时使用） */
val MainNavTabSelectedTint = Color(0xFF6BB3E8)

// 默认主题
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// 阅读主题
sealed class ReadingTheme(
    val name: String,
    val backgroundColor: Color,
    val textColor: Color,
    val secondaryTextColor: Color,
    val highlightColor: Color,
    val bookmarkColor: Color
) {
    object Paper : ReadingTheme(
        name = "纸质书",
        backgroundColor = Color(0xFFF5F0E1),
        textColor = Color(0xFF2C2C2C),
        secondaryTextColor = Color(0xFF666666),
        highlightColor = Color(0xFFFFFF00),
        bookmarkColor = Color(0xFFFF6B6B)
    )

    object Dark : ReadingTheme(
        name = "夜间模式",
        backgroundColor = Color(0xFF1A1A1A),
        textColor = Color(0xFFE0E0E0),
        secondaryTextColor = Color(0xFF999999),
        highlightColor = Color(0xFF4A4A00),
        bookmarkColor = Color(0xFFFF6B6B)
    )

    object White : ReadingTheme(
        name = "纯净白",
        backgroundColor = Color(0xFFFFFFFF),
        textColor = Color(0xFF333333),
        secondaryTextColor = Color(0xFF666666),
        highlightColor = Color(0xFFFFFF00),
        bookmarkColor = Color(0xFFFF6B6B)
    )

    object Green : ReadingTheme(
        name = "护眼绿",
        backgroundColor = Color(0xFFE8F5E9),
        textColor = Color(0xFF1B5E20),
        secondaryTextColor = Color(0xFF4CAF50),
        highlightColor = Color(0xFFFFFF00),
        bookmarkColor = Color(0xFFFF6B6B)
    )

    object Sepia : ReadingTheme(
        name = "复古棕",
        backgroundColor = Color(0xFFF4ECD8),
        textColor = Color(0xFF5B4636),
        secondaryTextColor = Color(0xFF8B7355),
        highlightColor = Color(0xFFFFFF00),
        bookmarkColor = Color(0xFFFF6B6B)
    )

    companion object {
        fun allThemes() = listOf(Paper, White, Green, Sepia, Dark)
    }
}

// 书架封面颜色
val BookCoverColors = listOf(
    Color(0xFFE57373),
    Color(0xFFF06292),
    Color(0xFFBA68C8),
    Color(0xFF9575CD),
    Color(0xFF7986CB),
    Color(0xFF64B5F6),
    Color(0xFF4FC3F7),
    Color(0xFF4DD0E1),
    Color(0xFF4DB6AC),
    Color(0xFF81C784),
    Color(0xFFAED581),
    Color(0xFFFF8A65),
    Color(0xFFFFB74D),
    Color(0xFFFFCC80),
    Color(0xFF90A4AE),
    Color(0xFFB0BEC5)
)
