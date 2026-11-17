package space.liushenme.markdownreader.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.data.backup.isSharedPreferenceKeyBackedUp
import space.liushenme.markdownreader.data.preferences.readerPreferencesDataStore
import space.liushenme.markdownreader.markdown.CodeHighlightTheme
import space.liushenme.markdownreader.model.ReaderPageTurnMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderSettingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: ReaderSettingsRepository

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        readerPreferencesDataStore(context).edit { it.clear() }
        repository = ReaderSettingsRepository(context)
    }

    @After
    fun tearDown(): Unit = runBlocking {
        readerPreferencesDataStore(context).edit { it.clear() }
        Unit
    }

    @Test
    fun codeHighlightTheme_defaultsToVsLightPlus() = runBlocking {
        assertEquals(CodeHighlightTheme.VS_LIGHT_PLUS, repository.codeHighlightTheme.first())
    }

    @Test
    fun setCodeHighlightTheme_persistsAcrossRepositoryReads() = runBlocking {
        repository.setCodeHighlightTheme(CodeHighlightTheme.MONOKAI)

        assertEquals(CodeHighlightTheme.MONOKAI, repository.codeHighlightTheme.first())
        assertEquals(
            CodeHighlightTheme.MONOKAI,
            ReaderSettingsRepository(context).codeHighlightTheme.first(),
        )
    }

    @Test
    fun unknownStoredTheme_fallsBackToDefault() = runBlocking {
        readerPreferencesDataStore(context).edit {
            it[stringPreferencesKey(CODE_HIGHLIGHT_THEME_KEY)] = "NOT_A_THEME"
        }

        assertEquals(CodeHighlightTheme.VS_LIGHT_PLUS, repository.codeHighlightTheme.first())
    }

    @Test
    fun codeHighlightThemeParticipatesInSharedBackup() = runBlocking {
        repository.setCodeHighlightTheme(CodeHighlightTheme.GITHUB_LIGHT)

        assertFalse(CODE_HIGHLIGHT_THEME_KEY in ReaderSettingsRepository.DEVICE_LOCAL_PREF_KEYS)
        assertTrue(isSharedPreferenceKeyBackedUp(CODE_HIGHLIGHT_THEME_KEY))

        val stored = readerPreferencesDataStore(context).data.first()[
            stringPreferencesKey(CODE_HIGHLIGHT_THEME_KEY)
        ]
        assertEquals(CodeHighlightTheme.GITHUB_LIGHT.stableId, stored)

        // Restore uses the same preference key/value; a fresh repository must observe it.
        assertEquals(
            CodeHighlightTheme.GITHUB_LIGHT,
            ReaderSettingsRepository(context).codeHighlightTheme.first(),
        )
    }

    @Test
    fun codeLineNumbers_defaultsToVisible() = runBlocking {
        assertEquals(true, ReaderSettingsRepository.DEFAULT_CODE_LINE_NUMBERS)
        assertTrue(repository.codeLineNumbers.first())
    }

    @Test
    fun codeLineNumbers_persistsAndParticipatesInSharedBackup() = runBlocking {
        repository.setCodeLineNumbers(false)

        assertFalse(repository.codeLineNumbers.first())
        assertFalse(ReaderSettingsRepository(context).codeLineNumbers.first())
        assertFalse(CODE_LINE_NUMBERS_KEY in ReaderSettingsRepository.DEVICE_LOCAL_PREF_KEYS)
        assertTrue(isSharedPreferenceKeyBackedUp(CODE_LINE_NUMBERS_KEY))
        assertEquals(
            false,
            readerPreferencesDataStore(context).data.first()[
                booleanPreferencesKey(CODE_LINE_NUMBERS_KEY)
            ],
        )
    }

    @Test
    fun readingStylesAreIndependentPerFormat() = runBlocking {
        repository.setActiveProfile(ReadingFormatProfile.MARKDOWN)
        repository.setFontSize(24)
        repository.setCodeBlockWrap(false)
        repository.setActiveProfile(ReadingFormatProfile.PDF)
        repository.setFontSize(16)
        repository.setActiveProfile(ReadingFormatProfile.CODE)
        repository.setCodeBlockWrap(true)

        val fresh = ReaderSettingsRepository(context)
        fresh.setActiveProfile(ReadingFormatProfile.MARKDOWN)
        assertEquals(24, fresh.fontSize.first())
        assertFalse(fresh.codeBlockWrap.first())
        fresh.setActiveProfile(ReadingFormatProfile.PDF)
        assertEquals(16, fresh.fontSize.first())
        // PDF 未单独设置过代码样式，沿用旧全局默认
        assertTrue(fresh.codeBlockWrap.first())
        fresh.setActiveProfile(ReadingFormatProfile.CODE)
        assertTrue(fresh.codeBlockWrap.first())
        assertEquals(ReaderSettingsRepository.DEFAULT_FONT_SIZE, fresh.fontSize.first())
    }

    private companion object {
        const val CODE_HIGHLIGHT_THEME_KEY = "reader_code_highlight_theme@markdown"
        const val CODE_LINE_NUMBERS_KEY = "reader_code_line_numbers@markdown"
    }

    @Test
    fun showTocInLandscape_isIndependentPerFormatAndFallsBackToLegacyKey() = runBlocking {
        repository.setShowTocInLandscapeFor(ReadingFormatProfile.PLAIN_TEXT, false)
        repository.setShowTocInLandscapeFor(ReadingFormatProfile.MARKDOWN, true)

        val fresh = ReaderSettingsRepository(context)
        assertTrue(fresh.showTocInLandscapeFor(ReadingFormatProfile.MARKDOWN).first())
        assertFalse(fresh.showTocInLandscapeFor(ReadingFormatProfile.PLAIN_TEXT).first())
        // 未单独设置过的格式沿用默认值
        assertTrue(fresh.showTocInLandscapeFor(ReadingFormatProfile.HTML).first())

        // 旧全局键作为回退：存量用户升级后各格式沿用升级前的选择
        readerPreferencesDataStore(context).edit { prefs ->
            prefs[booleanPreferencesKey("reader_show_toc_in_landscape")] = false
        }
        val migrated = ReaderSettingsRepository(context)
        assertFalse(migrated.showTocInLandscapeFor(ReadingFormatProfile.CODE).first())
        // 已显式设置过的格式不受旧全局键影响
        assertTrue(migrated.showTocInLandscapeFor(ReadingFormatProfile.MARKDOWN).first())
    }

    @Test
    fun markdownAlwaysReadsVerticalScrollEvenIfStoredHorizontal() = runBlocking {
        repository.setActiveProfile(ReadingFormatProfile.MARKDOWN)
        repository.setPageTurnMode(ReaderPageTurnMode.HorizontalSwipe)
        // Markdown 只保留上下滚动：写入被归一，读取也强制上下滚动
        assertEquals(ReaderPageTurnMode.VerticalScroll, repository.pageTurnMode.first())
        assertEquals(
            ReaderPageTurnMode.VerticalScroll,
            repository.pageTurnModeFor(ReadingFormatProfile.MARKDOWN).first(),
        )

        // 纯文本仍可左右滑动
        repository.setActiveProfile(ReadingFormatProfile.PLAIN_TEXT)
        repository.setPageTurnMode(ReaderPageTurnMode.HorizontalSwipe)
        assertEquals(
            ReaderPageTurnMode.HorizontalSwipe,
            repository.pageTurnModeFor(ReadingFormatProfile.PLAIN_TEXT).first(),
        )
    }
}
