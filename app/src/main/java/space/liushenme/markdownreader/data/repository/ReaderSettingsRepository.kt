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
import space.liushenme.markdownreader.markdown.CodeHighlightTheme
import androidx.compose.ui.graphics.toArgb
import space.liushenme.markdownreader.ui.theme.ReadingStyleState
import space.liushenme.markdownreader.ui.theme.ReadingTheme
import space.liushenme.markdownreader.ui.theme.ReadingThemeStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@Singleton
class ReaderSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = readerPreferencesDataStore(context)

    // 当前阅读样式档位：由阅读器在打开书籍时设置，设置界面切换 Tab 时也会切换。
    private val activeProfile = MutableStateFlow(ReadingFormatProfile.MARKDOWN)

    fun setActiveProfile(profile: ReadingFormatProfile) {
        activeProfile.value = profile
    }

    fun activeProfile(): ReadingFormatProfile = activeProfile.value

    /** 以「当前档位」为上游组合出可随档位切换的设置流。 */
    private fun <T> withProfile(block: (ReadingFormatProfile) -> Flow<T>): Flow<T> =
        activeProfile.flatMapLatest(block)

    // ===== 按文件格式独立的阅读样式 =====
    // 每个格式一套独立键（`键名@格式`）；读取时回退到旧全局键，
    // 因此存量用户无需数据迁移，会直接沿用升级前的样式。

    private class FormatPrefKeys(profile: ReadingFormatProfile) {
        private val suffix = "@" + profile.storageKey
        val pageTurnMode = stringPreferencesKey(KEY_PAGE_TURN_MODE.name + suffix)
        val readingStyles = stringPreferencesKey(KEY_READING_STYLES.name + suffix)
        val readingStyleIndex = intPreferencesKey(KEY_READING_STYLE_INDEX.name + suffix)
        val textColor = intPreferencesKey(KEY_TEXT_COLOR.name + suffix)
        val bgColor = intPreferencesKey(KEY_BG_COLOR.name + suffix)
        val bgAlpha = intPreferencesKey(KEY_BG_ALPHA.name + suffix)
        val bgImage = stringPreferencesKey(KEY_BG_IMAGE.name + suffix)
        val legacyTheme = stringPreferencesKey(KEY_READING_THEME.name + suffix)
        val fontSize = intPreferencesKey(KEY_FONT_SIZE.name + suffix)
        val paddingDp = intPreferencesKey(KEY_READER_PADDING_DP.name + suffix)
        val lineSpacing = floatPreferencesKey(KEY_LINE_SPACING_MULT.name + suffix)
        val codeBlockWrap = booleanPreferencesKey(KEY_CODE_BLOCK_WRAP.name + suffix)
        val codeLineNumbers = booleanPreferencesKey(KEY_CODE_LINE_NUMBERS.name + suffix)
        val codeHighlightTheme = stringPreferencesKey(KEY_CODE_HIGHLIGHT_THEME.name + suffix)
    }

    private val formatPrefKeys: Map<ReadingFormatProfile, FormatPrefKeys> =
        ReadingFormatProfile.entries.associateWith { FormatPrefKeys(it) }

    fun pageTurnModeFor(profile: ReadingFormatProfile): Flow<ReaderPageTurnMode> =
        dataStore.data.map { prefs ->
            val k = formatPrefKeys.getValue(profile)
            ReaderPageTurnMode.fromStored(prefs[k.pageTurnMode] ?: prefs[KEY_PAGE_TURN_MODE])
        }

    fun readingStyleStateFor(profile: ReadingFormatProfile): Flow<ReadingStyleState> =
        dataStore.data.map { prefs ->
            val k = formatPrefKeys.getValue(profile)
            ReadingThemeStorage.migrateToStyleState(
                stylesJson = prefs[k.readingStyles] ?: prefs[KEY_READING_STYLES],
                selectedIndex = prefs.intPref(k.readingStyleIndex)
                    ?: prefs.intPref(KEY_READING_STYLE_INDEX),
                textColorArgb = prefs.intPref(k.textColor) ?: prefs.intPref(KEY_TEXT_COLOR),
                bgColorArgb = prefs.intPref(k.bgColor) ?: prefs.intPref(KEY_BG_COLOR),
                bgAlpha = prefs.intPref(k.bgAlpha) ?: prefs.intPref(KEY_BG_ALPHA),
                bgImage = prefs[k.bgImage] ?: prefs[KEY_BG_IMAGE],
                legacyName = prefs[k.legacyTheme] ?: prefs[KEY_READING_THEME],
            )
        }

    fun readingThemeFor(profile: ReadingFormatProfile): Flow<ReadingTheme> =
        readingStyleStateFor(profile).map { it.current }

    fun fontSizeFor(profile: ReadingFormatProfile): Flow<Int> = dataStore.data.map { prefs ->
        val k = formatPrefKeys.getValue(profile)
        prefs.intPref(k.fontSize) ?: prefs.intPref(KEY_FONT_SIZE) ?: DEFAULT_FONT_SIZE
    }

    fun readerPaddingDpFor(profile: ReadingFormatProfile): Flow<Int> = dataStore.data.map { prefs ->
        val k = formatPrefKeys.getValue(profile)
        prefs.intPref(k.paddingDp) ?: prefs.intPref(KEY_READER_PADDING_DP) ?: DEFAULT_PADDING_DP
    }

    fun readerLineSpacingMultiplierFor(profile: ReadingFormatProfile): Flow<Float> =
        dataStore.data.map { prefs ->
            val k = formatPrefKeys.getValue(profile)
            prefs.floatPref(k.lineSpacing) ?: prefs.floatPref(KEY_LINE_SPACING_MULT)
                ?: DEFAULT_LINE_SPACING_MULT
        }

    fun codeBlockWrapFor(profile: ReadingFormatProfile): Flow<Boolean> = dataStore.data.map { prefs ->
        val k = formatPrefKeys.getValue(profile)
        prefs[k.codeBlockWrap] ?: prefs[KEY_CODE_BLOCK_WRAP] ?: DEFAULT_CODE_BLOCK_WRAP
    }

    fun codeLineNumbersFor(profile: ReadingFormatProfile): Flow<Boolean> =
        dataStore.data.map { prefs ->
            val k = formatPrefKeys.getValue(profile)
            prefs[k.codeLineNumbers] ?: prefs[KEY_CODE_LINE_NUMBERS] ?: DEFAULT_CODE_LINE_NUMBERS
        }

    fun codeHighlightThemeFor(profile: ReadingFormatProfile): Flow<CodeHighlightTheme> =
        dataStore.data.map { prefs ->
            val k = formatPrefKeys.getValue(profile)
            CodeHighlightTheme.fromStored(
                prefs[k.codeHighlightTheme] ?: prefs[KEY_CODE_HIGHLIGHT_THEME],
            )
        }

    val pageTurnMode: Flow<ReaderPageTurnMode> = withProfile { pageTurnModeFor(it) }

    val appLanguage: Flow<AppLanguage> = dataStore.data.map { prefs ->
        AppLanguage.fromStored(prefs[KEY_APP_LANGUAGE])
    }

    val appThemeMode: Flow<AppThemeMode> = dataStore.data.map { prefs ->
        appThemeFromStored(prefs[KEY_APP_THEME_MODE])
    }

    val readingStyleState: Flow<ReadingStyleState> = withProfile { readingStyleStateFor(it) }

    val readingTheme: Flow<ReadingTheme> = readingStyleState.map { it.current }

    val fontSize: Flow<Int> = withProfile { fontSizeFor(it) }

    val readerPaddingDp: Flow<Int> = withProfile { readerPaddingDpFor(it) }

    val readerLineSpacingMultiplier: Flow<Float> = withProfile { readerLineSpacingMultiplierFor(it) }

    /** 围栏/缩进代码块是否自动换行；关闭后代码块可左右滑动。默认开启。 */
    val codeBlockWrap: Flow<Boolean> = withProfile { codeBlockWrapFor(it) }

    /** 纯代码文件左侧是否显示源码行号；默认开启。 */
    val codeLineNumbers: Flow<Boolean> = withProfile { codeLineNumbersFor(it) }

    /** 全局代码高亮主题；缺失或未知值回退到 VS Light+。 */
    val codeHighlightTheme: Flow<CodeHighlightTheme> = withProfile { codeHighlightThemeFor(it) }

    /** 沉浸阅读时隐藏状态栏与底部导航条；唤出功能栏时再显示。默认开启。 */
    val hideSystemBars: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_HIDE_SYSTEM_BARS] ?: DEFAULT_HIDE_SYSTEM_BARS
    }

    /**
     * 右滑加书签是隐藏手势，首次触发时给一次说明提示，之后不再打扰。
     * true 表示提示已展示过。
     */
    val swipeBookmarkHintShown: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SWIPE_BOOKMARK_HINT_SHOWN] ?: false
    }

    /** 横屏阅读时是否显示左侧目录栏；默认开启，竖屏布局不会使用该开关。 */
    val showTocInLandscape: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SHOW_TOC_IN_LANDSCAPE] ?: DEFAULT_SHOW_TOC_IN_LANDSCAPE
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
            prefs[formatPrefKeys.getValue(activeProfile.value).pageTurnMode] = normalized.name
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
            prefs[formatPrefKeys.getValue(activeProfile.value).fontSize] = size.coerceIn(10, 40)
        }
    }

    suspend fun setReaderPaddingDp(dp: Int) {
        dataStore.edit { prefs ->
            prefs[formatPrefKeys.getValue(activeProfile.value).paddingDp] = dp.coerceIn(8, 56)
        }
    }

    suspend fun setReaderLineSpacingMultiplier(mult: Float) {
        dataStore.edit { prefs ->
            prefs[formatPrefKeys.getValue(activeProfile.value).lineSpacing] = mult.coerceIn(1f, 2.5f)
        }
    }

    suspend fun setCodeBlockWrap(wrap: Boolean) {
        dataStore.edit { prefs ->
            prefs[formatPrefKeys.getValue(activeProfile.value).codeBlockWrap] = wrap
        }
    }

    suspend fun setCodeLineNumbers(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[formatPrefKeys.getValue(activeProfile.value).codeLineNumbers] = show
        }
    }

    suspend fun setCodeHighlightTheme(theme: CodeHighlightTheme) {
        dataStore.edit { prefs ->
            prefs[formatPrefKeys.getValue(activeProfile.value).codeHighlightTheme] = theme.stableId
        }
    }

    suspend fun setHideSystemBars(hide: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_HIDE_SYSTEM_BARS] = hide
        }
    }

    suspend fun setSwipeBookmarkHintShown(shown: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SWIPE_BOOKMARK_HINT_SHOWN] = shown
        }
    }

    suspend fun setShowTocInLandscape(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_TOC_IN_LANDSCAPE] = show
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
        val k = formatPrefKeys.getValue(activeProfile.value)
        dataStore.edit { prefs ->
            prefs[k.readingStyles] = ReadingThemeStorage.encodeStyles(safeStyles)
            prefs[k.readingStyleIndex] = index
            prefs[k.textColor] = current.textColor.toArgb()
            prefs[k.bgColor] = current.backgroundColor.toArgb()
            prefs[k.bgAlpha] = ReadingThemeStorage.clampAlpha(current.backgroundAlpha)
            prefs[k.bgImage] = current.backgroundImageAsset.orEmpty()
        }
    }

    companion object {
        const val DEFAULT_FONT_SIZE = 16
        const val DEFAULT_PADDING_DP = 32
        const val DEFAULT_LINE_SPACING_MULT = 1.5f
        const val DEFAULT_CODE_BLOCK_WRAP = true
        const val DEFAULT_CODE_LINE_NUMBERS = true
        const val DEFAULT_HIDE_SYSTEM_BARS = true
        const val DEFAULT_SHOW_TOC_IN_LANDSCAPE = true
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
        private val KEY_CODE_LINE_NUMBERS =
            booleanPreferencesKey("reader_code_line_numbers")
        private val KEY_CODE_HIGHLIGHT_THEME =
            stringPreferencesKey("reader_code_highlight_theme")
        private val KEY_HIDE_SYSTEM_BARS = booleanPreferencesKey("reader_hide_system_bars")
        private val KEY_SWIPE_BOOKMARK_HINT_SHOWN =
            booleanPreferencesKey("reader_swipe_bookmark_hint_shown")
        private val KEY_SHOW_TOC_IN_LANDSCAPE = booleanPreferencesKey("reader_show_toc_in_landscape")
        private val KEY_LAST_HIGHLIGHT_COLOR = intPreferencesKey("reader_last_highlight_color")
        private val KEY_LAST_HIGHLIGHT_STYLE = stringPreferencesKey("reader_last_highlight_style")

    }
}
