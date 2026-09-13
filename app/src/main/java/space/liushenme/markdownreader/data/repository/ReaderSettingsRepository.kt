package space.liushenme.markdownreader.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import space.liushenme.markdownreader.data.preferences.floatPref
import space.liushenme.markdownreader.data.preferences.intPref
import space.liushenme.markdownreader.data.preferences.readerPreferencesDataStore
import space.liushenme.markdownreader.model.AppLanguage
import space.liushenme.markdownreader.model.AppThemeMode
import space.liushenme.markdownreader.model.BookshelfGridColumns
import space.liushenme.markdownreader.model.BookshelfLayoutMode
import space.liushenme.markdownreader.model.GitProjectRecentReadCount
import space.liushenme.markdownreader.model.HighlightStyle
import space.liushenme.markdownreader.model.ReaderPageTurnMode
import androidx.compose.ui.graphics.toArgb
import space.liushenme.markdownreader.ui.theme.ReadingStyleState
import space.liushenme.markdownreader.ui.theme.ReadingTheme
import space.liushenme.markdownreader.ui.theme.ReadingThemeStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class ReaderSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = readerPreferencesDataStore(context)

    val pageTurnMode: Flow<ReaderPageTurnMode> = dataStore.data.map { prefs ->
        ReaderPageTurnMode.fromStored(prefs[KEY_PAGE_TURN_MODE])
    }

    val appLanguage: Flow<AppLanguage> = dataStore.data.map { prefs ->
        AppLanguage.fromStored(prefs[KEY_APP_LANGUAGE])
    }

    val appThemeMode: Flow<AppThemeMode> = dataStore.data.map { prefs ->
        appThemeFromStored(prefs[KEY_APP_THEME_MODE])
    }

    val readingStyleState: Flow<ReadingStyleState> = dataStore.data.map { prefs ->
        ReadingThemeStorage.migrateToStyleState(
            stylesJson = prefs[KEY_READING_STYLES],
            selectedIndex = prefs.intPref(KEY_READING_STYLE_INDEX),
            textColorArgb = prefs.intPref(KEY_TEXT_COLOR),
            bgColorArgb = prefs.intPref(KEY_BG_COLOR),
            bgAlpha = prefs.intPref(KEY_BG_ALPHA),
            bgImage = prefs[KEY_BG_IMAGE],
            legacyName = prefs[KEY_READING_THEME],
        )
    }

    val readingTheme: Flow<ReadingTheme> = readingStyleState.map { it.current }

    val fontSize: Flow<Int> = dataStore.data.map { prefs ->
        prefs.intPref(KEY_FONT_SIZE) ?: DEFAULT_FONT_SIZE
    }

    val readerPaddingDp: Flow<Int> = dataStore.data.map { prefs ->
        prefs.intPref(KEY_READER_PADDING_DP) ?: DEFAULT_PADDING_DP
    }

    val readerLineSpacingMultiplier: Flow<Float> = dataStore.data.map { prefs ->
        prefs.floatPref(KEY_LINE_SPACING_MULT) ?: DEFAULT_LINE_SPACING_MULT
    }

    /** 围栏/缩进代码块是否自动换行；关闭后代码块可左右滑动。默认开启。 */
    val codeBlockWrap: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_CODE_BLOCK_WRAP] ?: DEFAULT_CODE_BLOCK_WRAP
    }

    /** 上次选用的划线颜色 ARGB；缺省为默认黄。 */
    val lastHighlightColorArgb: Flow<Int> = dataStore.data.map { prefs ->
        prefs.intPref(KEY_LAST_HIGHLIGHT_COLOR) ?: DEFAULT_HIGHLIGHT_COLOR_ARGB
    }

    /** 上次选用的划线样式；缺省为背景色。 */
    val lastHighlightStyle: Flow<HighlightStyle> = dataStore.data.map { prefs ->
        HighlightStyle.fromStorageKey(prefs[KEY_LAST_HIGHLIGHT_STYLE])
    }

    val bookshelfLayoutMode: Flow<BookshelfLayoutMode> = dataStore.data.map { prefs ->
        BookshelfLayoutMode.fromStored(prefs[KEY_BOOKSHELF_LAYOUT_MODE])
    }

    val bookshelfGridColumns: Flow<Int> = dataStore.data.map { prefs ->
        BookshelfGridColumns.coerce(prefs.intPref(KEY_BOOKSHELF_GRID_COLUMNS) ?: BookshelfGridColumns.DEFAULT)
    }

    val gitProjectRecentReadCount: Flow<Int> = dataStore.data.map { prefs ->
        GitProjectRecentReadCount.coerce(
            prefs.intPref(KEY_GIT_PROJECT_RECENT_READ_COUNT) ?: GitProjectRecentReadCount.DEFAULT,
        )
    }

    suspend fun setPageTurnMode(mode: ReaderPageTurnMode) {
        val normalized = ReaderPageTurnMode.normalize(mode)
        dataStore.edit { prefs ->
            prefs[KEY_PAGE_TURN_MODE] = normalized.name
        }
    }

    suspend fun setAppThemeMode(mode: AppThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_APP_THEME_MODE] = mode.name
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { prefs ->
            prefs[KEY_APP_LANGUAGE] = language.name
        }
    }

    suspend fun currentAppLanguage(): AppLanguage = appLanguage.first()

    suspend fun setReadingTheme(theme: ReadingTheme) {
        val state = readingStyleState.first()
        val styles = state.styles.toMutableList()
        if (styles.isEmpty()) {
            persistStyles(listOf(theme), 0)
            return
        }
        styles[state.selectedIndex] = theme
        persistStyles(styles, state.selectedIndex)
    }

    suspend fun selectReadingStyle(index: Int) {
        val state = readingStyleState.first()
        persistStyles(state.styles, index)
    }

    suspend fun addReadingStyle(): Int {
        val state = readingStyleState.first()
        val (styles, index) = ReadingThemeStorage.addStyle(state.styles)
        persistStyles(styles, index)
        return index
    }

    suspend fun updateReadingStyle(index: Int, theme: ReadingTheme) {
        val state = readingStyleState.first()
        if (state.styles.isEmpty()) {
            persistStyles(listOf(theme), 0)
            return
        }
        val styles = state.styles.toMutableList()
        val i = ReadingThemeStorage.coerceIndex(index, styles.size)
        styles[i] = theme
        persistStyles(styles, state.selectedIndex)
    }

    suspend fun deleteReadingStyle(index: Int) {
        val state = readingStyleState.first()
        val (styles, selected) = ReadingThemeStorage.deleteStyle(state.styles, index)
        persistStyles(styles, selected)
    }

    suspend fun resetAllReadingStyles(keepCustom: Boolean) {
        val state = readingStyleState.first()
        val (styles, selected) = ReadingThemeStorage.resetAllPresets(
            styles = state.styles,
            selectedIndex = state.selectedIndex,
            keepCustom = keepCustom,
        )
        persistStyles(styles, selected)
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

    suspend fun setCodeBlockWrap(wrap: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_CODE_BLOCK_WRAP] = wrap
        }
    }

    suspend fun setLastHighlightPreference(colorArgb: Int, style: HighlightStyle) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_HIGHLIGHT_COLOR] = colorArgb
            prefs[KEY_LAST_HIGHLIGHT_STYLE] = style.storageKey
        }
    }

    suspend fun setBookshelfLayoutMode(mode: BookshelfLayoutMode) {
        dataStore.edit { prefs ->
            prefs[KEY_BOOKSHELF_LAYOUT_MODE] = mode.name
        }
    }

    suspend fun setBookshelfGridColumns(columns: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_BOOKSHELF_GRID_COLUMNS] = BookshelfGridColumns.coerce(columns)
        }
    }

    suspend fun setGitProjectRecentReadCount(count: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_GIT_PROJECT_RECENT_READ_COUNT] = GitProjectRecentReadCount.coerce(count)
        }
    }

    private suspend fun persistStyles(styles: List<ReadingTheme>, selectedIndex: Int) {
        val safeStyles = styles.ifEmpty { ReadingTheme.allThemes() }
        val index = ReadingThemeStorage.coerceIndex(selectedIndex, safeStyles.size)
        val current = safeStyles[index]
        dataStore.edit { prefs ->
            prefs[KEY_READING_STYLES] = ReadingThemeStorage.encodeStyles(safeStyles)
            prefs[KEY_READING_STYLE_INDEX] = index
            prefs[KEY_TEXT_COLOR] = current.textColor.toArgb()
            prefs[KEY_BG_COLOR] = current.backgroundColor.toArgb()
            prefs[KEY_BG_ALPHA] = ReadingThemeStorage.clampAlpha(current.backgroundAlpha)
            prefs[KEY_BG_IMAGE] = current.backgroundImageAsset.orEmpty()
        }
    }

    companion object {
        const val DEFAULT_FONT_SIZE = 16
        const val DEFAULT_PADDING_DP = 32
        const val DEFAULT_LINE_SPACING_MULT = 1.5f
        const val DEFAULT_CODE_BLOCK_WRAP = true
        const val DEFAULT_HIGHLIGHT_COLOR_ARGB = 0xFFFFFF00.toInt()

        private val KEY_PAGE_TURN_MODE = stringPreferencesKey("reader_page_turn_mode")
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        private val KEY_READING_THEME = stringPreferencesKey("reader_reading_theme")
        private val KEY_TEXT_COLOR = intPreferencesKey("reader_text_color")
        private val KEY_BG_COLOR = intPreferencesKey("reader_bg_color")
        private val KEY_BG_ALPHA = intPreferencesKey("reader_bg_alpha")
        private val KEY_BG_IMAGE = stringPreferencesKey("reader_bg_image")
        private val KEY_READING_STYLES = stringPreferencesKey("reader_reading_styles")
        private val KEY_READING_STYLE_INDEX = intPreferencesKey("reader_reading_style_index")
        private val KEY_BOOKSHELF_LAYOUT_MODE = stringPreferencesKey("bookshelf_layout_mode")
        private val KEY_BOOKSHELF_GRID_COLUMNS = intPreferencesKey("bookshelf_grid_columns")
        private val KEY_GIT_PROJECT_RECENT_READ_COUNT =
            intPreferencesKey("git_project_recent_read_count")

        /** 仅本机生效、不进入 WebDAV 备份/恢复的偏好键 */
        val DEVICE_LOCAL_PREF_KEYS = setOf(
            KEY_BOOKSHELF_LAYOUT_MODE.name,
            KEY_BOOKSHELF_GRID_COLUMNS.name,
            KEY_GIT_PROJECT_RECENT_READ_COUNT.name,
        )

        private fun appThemeFromStored(raw: String?): AppThemeMode =
            AppThemeMode.entries.find { it.name == raw } ?: AppThemeMode.SYSTEM
        private val KEY_FONT_SIZE = intPreferencesKey("reader_font_size")
        private val KEY_READER_PADDING_DP = intPreferencesKey("reader_reader_padding_dp")
        private val KEY_LINE_SPACING_MULT = floatPreferencesKey("reader_line_spacing_multiplier")
        private val KEY_CODE_BLOCK_WRAP = booleanPreferencesKey("reader_code_block_wrap")
        private val KEY_LAST_HIGHLIGHT_COLOR = intPreferencesKey("reader_last_highlight_color")
        private val KEY_LAST_HIGHLIGHT_STYLE = stringPreferencesKey("reader_last_highlight_style")

    }
}
