package space.liushenme.markdownreader.platform

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import space.liushenme.markdownreader.model.AppLanguage

object AppLocaleController {
    fun apply(language: AppLanguage) {
        val desiredTags = language.tag
        val currentTags = AppCompatDelegate.getApplicationLocales()
            .toLanguageTags()
            .substringBefore(',')
        if (currentTags.equals(desiredTags, ignoreCase = true)) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(desiredTags))
    }

    fun current(): AppLanguage {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return AppLanguage.fromTag(tags.substringBefore(',').ifBlank { null })
    }
}
