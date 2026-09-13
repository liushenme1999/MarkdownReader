package space.liushenme.markdownreader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

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

private val HighlightColorLight = Color(0xFFFFFF00)
private val HighlightColorDark = Color(0xFF4A4A00)
private val BookmarkColorDefault = Color(0xFFFF6B6B)

/** 阅读纸面：文字色、背景色、透明度与可选 assets 背景图。 */
data class ReadingTheme(
    val textColor: Color,
    val backgroundColor: Color,
    val backgroundAlpha: Int = 100,
    val backgroundImageAsset: String? = null,
    val name: String = "",
    val presetKey: String? = null,
) {
    val nameRes: Int?
        get() = ReadingThemeStorage.nameResForPreset(presetKey)

    val secondaryTextColor: Color
        get() = textColor.copy(alpha = 0.62f)

    val highlightColor: Color
        get() = if (backgroundColor.luminance() < 0.5f) HighlightColorDark else HighlightColorLight

    val bookmarkColor: Color
        get() = BookmarkColorDefault

    fun contentSignature(): String =
        "${textColor.toArgb()}_${backgroundColor.toArgb()}_$backgroundAlpha" +
            "_${backgroundImageAsset.orEmpty()}"

    fun matchesPreset(preset: ReadingTheme): Boolean =
        textColor.toArgb() == preset.textColor.toArgb() &&
            backgroundColor.toArgb() == preset.backgroundColor.toArgb() &&
            backgroundAlpha == 100 &&
            backgroundImageAsset.isNullOrBlank()

    fun asQuickPreset(): ReadingTheme = copy(
        backgroundAlpha = 100,
        backgroundImageAsset = null,
    )

    companion object {
        val Paper = ReadingTheme(
            presetKey = "Paper",
            backgroundColor = Color(0xFFF5F0E1),
            textColor = Color(0xFF2C2C2C),
        )

        val Dark = ReadingTheme(
            presetKey = "Dark",
            backgroundColor = Color(0xFF1A1A1A),
            textColor = Color(0xFFE0E0E0),
        )

        val White = ReadingTheme(
            presetKey = "White",
            backgroundColor = Color(0xFFFFFFFF),
            textColor = Color(0xFF333333),
        )

        val Green = ReadingTheme(
            presetKey = "Green",
            backgroundColor = Color(0xFFE8F5E9),
            textColor = Color(0xFF1B5E20),
        )

        val Sepia = ReadingTheme(
            presetKey = "Sepia",
            backgroundColor = Color(0xFFF4ECD8),
            textColor = Color(0xFF5B4636),
        )

        fun allThemes() = listOf(Paper, White, Green, Sepia, Dark)

        fun newCustom(): ReadingTheme = ReadingTheme(
            textColor = Color(0xFF3E3D3B),
            backgroundColor = Color(0xFFEEEEEE),
        )
    }
}

data class ReadingStyleState(
    val styles: List<ReadingTheme>,
    val selectedIndex: Int,
) {
    val current: ReadingTheme
        get() {
            if (styles.isEmpty()) return ReadingTheme.Paper
            return styles[selectedIndex.coerceIn(0, styles.lastIndex)]
        }

    companion object {
        val DEFAULT = ReadingStyleState(ReadingTheme.allThemes(), 0)
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
