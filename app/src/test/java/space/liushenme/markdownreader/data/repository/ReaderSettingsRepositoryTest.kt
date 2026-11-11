package space.liushenme.markdownreader.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
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

    private companion object {
        const val CODE_HIGHLIGHT_THEME_KEY = "reader_code_highlight_theme"
    }
}
