package space.liushenme.markdownreader.model

import androidx.annotation.StringRes
import space.liushenme.markdownreader.R

/**
 * 应用内界面语言。新增语言时：增加枚举值、[values-xx/strings.xml]、locales_config。
 */
enum class AppLanguage(
    val tag: String,
    @StringRes val labelRes: Int,
) {
    ZH_CN("zh-CN", R.string.language_zh_cn),
    ZH_TW("zh-TW", R.string.language_zh_tw),
    EN("en", R.string.language_en),
    ;

    companion object {
        val DEFAULT: AppLanguage = ZH_CN

        fun fromTag(tag: String?): AppLanguage {
            if (tag.isNullOrBlank()) return DEFAULT
            val normalized = tag.replace('_', '-').substringBefore(',')
            entries.find { it.tag.equals(normalized, ignoreCase = true) }?.let { return it }
            return when {
                normalized.startsWith("zh-Hant", ignoreCase = true) ||
                    normalized.startsWith("zh-TW", ignoreCase = true) ||
                    normalized.startsWith("zh-HK", ignoreCase = true) -> ZH_TW
                normalized.startsWith("zh", ignoreCase = true) -> ZH_CN
                normalized.startsWith("en", ignoreCase = true) -> EN
                else -> DEFAULT
            }
        }

        fun fromStored(raw: String?): AppLanguage =
            entries.find { it.name == raw } ?: DEFAULT
    }
}
