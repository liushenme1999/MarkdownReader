package space.liushenme.markdownreader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import space.liushenme.markdownreader.R

/** 阅读纸面配置的持久化编解码（纯函数，便于单测）。 */
object ReadingThemeStorage {
    const val DEFAULT_BG_ALPHA = 100

    private val gson = Gson()
    private val stylesType = object : TypeToken<List<StoredReadingStyleDto>>() {}.type

    fun clampAlpha(alpha: Int): Int = alpha.coerceIn(0, 100)

    fun normalizeImageAsset(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }

    fun nameResForPreset(presetKey: String?): Int? = when (presetKey) {
        "Paper" -> R.string.reading_theme_paper
        "Dark" -> R.string.reading_theme_dark
        "White" -> R.string.reading_theme_white
        "Green" -> R.string.reading_theme_green
        "Sepia" -> R.string.reading_theme_sepia
        else -> null
    }

    fun fromLegacyName(raw: String?): ReadingTheme = when (raw) {
        "Paper" -> ReadingTheme.Paper
        "Dark" -> ReadingTheme.Dark
        "White" -> ReadingTheme.White
        "Green" -> ReadingTheme.Green
        "Sepia" -> ReadingTheme.Sepia
        else -> ReadingTheme.Paper
    }

    fun indexFromLegacyName(raw: String?): Int {
        val key = when (raw) {
            "Paper", "Dark", "White", "Green", "Sepia" -> raw
            else -> "Paper"
        }
        val index = ReadingTheme.allThemes().indexOfFirst { it.presetKey == key }
        return index.coerceAtLeast(0)
    }

    fun paperBaseColor(backgroundColor: Color, imageMeanColor: Color?): Color =
        imageMeanColor ?: backgroundColor

    fun coerceIndex(index: Int, size: Int): Int {
        if (size <= 0) return 0
        return index.coerceIn(0, size - 1)
    }

    fun fromStored(
        textColorArgb: Int?,
        bgColorArgb: Int?,
        bgAlpha: Int?,
        bgImage: String?,
        legacyName: String?,
    ): ReadingTheme {
        val hasNewKeys = textColorArgb != null ||
            bgColorArgb != null ||
            bgAlpha != null ||
            bgImage != null
        if (!hasNewKeys) {
            return fromLegacyName(legacyName)
        }
        val fallback = fromLegacyName(legacyName)
        return ReadingTheme(
            textColor = Color(textColorArgb ?: fallback.textColor.toArgb()),
            backgroundColor = Color(bgColorArgb ?: fallback.backgroundColor.toArgb()),
            backgroundAlpha = clampAlpha(bgAlpha ?: DEFAULT_BG_ALPHA),
            backgroundImageAsset = normalizeImageAsset(bgImage),
            name = fallback.name,
            presetKey = fallback.presetKey,
        )
    }

    fun migrateToStyleState(
        stylesJson: String?,
        selectedIndex: Int?,
        textColorArgb: Int?,
        bgColorArgb: Int?,
        bgAlpha: Int?,
        bgImage: String?,
        legacyName: String?,
    ): ReadingStyleState {
        decodeStyles(stylesJson)?.takeIf { it.isNotEmpty() }?.let { styles ->
            return ReadingStyleState(styles, coerceIndex(selectedIndex ?: 0, styles.size))
        }
        val defaults = ReadingTheme.allThemes().toMutableList()
        val index = coerceIndex(selectedIndex ?: indexFromLegacyName(legacyName), defaults.size)
        val hasCustomKeys = textColorArgb != null ||
            bgColorArgb != null ||
            bgAlpha != null ||
            bgImage != null
        if (hasCustomKeys) {
            val custom = fromStored(textColorArgb, bgColorArgb, bgAlpha, bgImage, legacyName)
            val base = defaults[index]
            defaults[index] = custom.copy(name = base.name, presetKey = base.presetKey)
        }
        return ReadingStyleState(defaults, index)
    }

    fun encodeStyles(styles: List<ReadingTheme>): String {
        val dtos = styles.map { theme ->
            StoredReadingStyleDto(
                name = theme.name,
                presetKey = theme.presetKey,
                textColor = theme.textColor.toArgb(),
                backgroundColor = theme.backgroundColor.toArgb(),
                backgroundAlpha = clampAlpha(theme.backgroundAlpha),
                backgroundImage = theme.backgroundImageAsset.orEmpty(),
            )
        }
        return gson.toJson(dtos)
    }

    fun decodeStyles(raw: String?): List<ReadingTheme>? {
        if (raw.isNullOrBlank()) return null
        return try {
            val dtos = gson.fromJson<List<StoredReadingStyleDto>>(raw, stylesType) ?: return null
            dtos.map { dto ->
                ReadingTheme(
                    textColor = Color(dto.textColor),
                    backgroundColor = Color(dto.backgroundColor),
                    backgroundAlpha = clampAlpha(dto.backgroundAlpha),
                    backgroundImageAsset = normalizeImageAsset(dto.backgroundImage),
                    name = dto.name.orEmpty(),
                    presetKey = dto.presetKey?.takeIf { it.isNotBlank() },
                )
            }.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    fun addStyle(styles: List<ReadingTheme>): Pair<List<ReadingTheme>, Int> {
        val next = styles + ReadingTheme.newCustom()
        return next to next.lastIndex
    }

    fun deleteStyle(styles: List<ReadingTheme>, selectedIndex: Int): Pair<List<ReadingTheme>, Int> {
        if (styles.size <= 1) {
            return styles to coerceIndex(selectedIndex, styles.size)
        }
        val index = coerceIndex(selectedIndex, styles.size)
        val next = styles.toMutableList().also { it.removeAt(index) }
        return next to coerceIndex(index, next.size)
    }

    /**
     * 恢复五套出厂预设。
     * [keepCustom] 为 true 时保留没有 presetKey 的新增样式，并尽量保住当前选中项。
     */
    fun resetAllPresets(
        styles: List<ReadingTheme>,
        selectedIndex: Int,
        keepCustom: Boolean,
    ): Pair<List<ReadingTheme>, Int> {
        val defaults = ReadingTheme.allThemes()
        val current = styles.getOrNull(coerceIndex(selectedIndex, styles.size))
        val custom = if (keepCustom) {
            styles.filter { it.presetKey.isNullOrBlank() }
        } else {
            emptyList()
        }
        val next = defaults + custom
        val nextIndex = when {
            current == null -> 0
            !current.presetKey.isNullOrBlank() -> {
                defaults.indexOfFirst { it.presetKey == current.presetKey }.coerceAtLeast(0)
            }
            keepCustom -> {
                val match = next.indexOfFirst {
                    it.presetKey.isNullOrBlank() &&
                        it.name == current.name &&
                        it.contentSignature() == current.contentSignature()
                }
                if (match >= 0) match else 0
            }
            else -> 0
        }
        return next to coerceIndex(nextIndex, next.size)
    }

    /** 将当前槽恢复为该预设的出厂值；自定义槽回到新增时的默认浅色。 */
    fun initialStyle(theme: ReadingTheme): ReadingTheme {
        val preset = theme.presetKey?.let { key ->
            ReadingTheme.allThemes().firstOrNull { it.presetKey == key }
        }
        return preset ?: ReadingTheme.newCustom()
    }

    fun parseHexColor(raw: String): Color? {
        val cleaned = raw.trim()
            .removePrefix("#")
            .removePrefix("0x")
            .removePrefix("0X")
        val hex = when (cleaned.length) {
            3 -> cleaned.map { "$it$it" }.joinToString("")
            6 -> cleaned
            8 -> cleaned.takeLast(6)
            else -> return null
        }
        if (hex.any { it !in HEX_CHARS }) return null
        return Color(0xFF000000.toInt() or hex.toInt(16))
    }

    private val HEX_CHARS = ('0'..'9') + ('a'..'f') + ('A'..'F')
}

internal data class StoredReadingStyleDto(
    val name: String? = "",
    val presetKey: String? = null,
    val textColor: Int = 0,
    val backgroundColor: Int = 0,
    val backgroundAlpha: Int = 100,
    val backgroundImage: String? = "",
)
