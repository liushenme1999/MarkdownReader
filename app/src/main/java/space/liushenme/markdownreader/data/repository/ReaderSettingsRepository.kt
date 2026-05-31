package space.liushenme.markdownreader.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import space.liushenme.markdownreader.data.preferences.readerPreferencesDataStore
import space.liushenme.markdownreader.model.AppThemeMode
import space.liushenme.markdownreader.model.ReaderPageTurnMode
import space.liushenme.markdownreader.ui.theme.ReadingTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ReaderSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = readerPreferencesDataStore(context)

    val pageTurnMode: Flow<ReaderPageTurnMode> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_PAGE_TURN_MODE]
        ReaderPageTurnMode.entries.find { it.name == raw } ?: ReaderPageTurnMode.VerticalScroll
    }

    val appThemeMode: Flow<AppThemeMode> = dataStore.data.map { prefs ->
        appThemeFromStored(prefs[KEY_APP_THEME_MODE])
    }

    val readingTheme: Flow<ReadingTheme> = dataStore.data.map { prefs ->
        themeFromStoredName(prefs[KEY_READING_THEME])
    }

    val fontSize: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_FONT_SIZE] ?: DEFAULT_FONT_SIZE
    }

    val readerPaddingDp: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_READER_PADDING_DP] ?: DEFAULT_PADDING_DP
    }

    val readerLineSpacingMultiplier: Flow<Float> = dataStore.data.map { prefs ->
        prefs[KEY_LINE_SPACING_MULT] ?: DEFAULT_LINE_SPACING_MULT
    }

    suspend fun setPageTurnMode(mode: ReaderPageTurnMode) {
        dataStore.edit { prefs ->
            prefs[KEY_PAGE_TURN_MODE] = mode.name
        }
    }

    suspend fun setAppThemeMode(mode: AppThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_APP_THEME_MODE] = mode.name
        }
    }

    suspend fun setReadingTheme(theme: ReadingTheme) {
        dataStore.edit { prefs ->
            prefs[KEY_READING_THEME] = themeToStoredName(theme)
        }
    }

    suspend fun setFontSize(size: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_FONT_SIZE] = size.coerceIn(10, 40)
        }
    }

    suspend fun setReaderPaddingDp(dp: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_READER_PADDING_DP] = dp.coerceIn(8, 56)
        }
    }

    suspend fun setReaderLineSpacingMultiplier(mult: Float) {
        dataStore.edit { prefs ->
            prefs[KEY_LINE_SPACING_MULT] = mult.coerceIn(1f, 2.5f)
        }
    }

    companion object {
        const val DEFAULT_FONT_SIZE = 16
        const val DEFAULT_PADDING_DP = 32
        const val DEFAULT_LINE_SPACING_MULT = 1.5f

        private val KEY_PAGE_TURN_MODE = stringPreferencesKey("reader_page_turn_mode")
        private val KEY_APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        private val KEY_READING_THEME = stringPreferencesKey("reader_reading_theme")

        private fun appThemeFromStored(raw: String?): AppThemeMode =
            AppThemeMode.entries.find { it.name == raw } ?: AppThemeMode.SYSTEM
        private val KEY_FONT_SIZE = intPreferencesKey("reader_font_size")
        private val KEY_READER_PADDING_DP = intPreferencesKey("reader_reader_padding_dp")
        private val KEY_LINE_SPACING_MULT = floatPreferencesKey("reader_line_spacing_multiplier")

        private fun themeToStoredName(theme: ReadingTheme): String = when (theme) {
            ReadingTheme.Paper -> "Paper"
            ReadingTheme.Dark -> "Dark"
            ReadingTheme.White -> "White"
            ReadingTheme.Green -> "Green"
            ReadingTheme.Sepia -> "Sepia"
        }

        private fun themeFromStoredName(raw: String?): ReadingTheme = when (raw) {
            "Paper" -> ReadingTheme.Paper
            "Dark" -> ReadingTheme.Dark
            "White" -> ReadingTheme.White
            "Green" -> ReadingTheme.Green
            "Sepia" -> ReadingTheme.Sepia
            else -> ReadingTheme.Paper
        }
    }
}
