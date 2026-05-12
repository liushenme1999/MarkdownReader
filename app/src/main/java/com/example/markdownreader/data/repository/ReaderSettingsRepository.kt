package com.example.markdownreader.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.markdownreader.data.preferences.readerPreferencesDataStore
import com.example.markdownreader.model.ReaderPageTurnMode
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
        ReaderPageTurnMode.values().find { it.name == raw } ?: ReaderPageTurnMode.VerticalScroll
    }

    suspend fun setPageTurnMode(mode: ReaderPageTurnMode) {
        dataStore.edit { prefs ->
            prefs[KEY_PAGE_TURN_MODE] = mode.name
        }
    }

    companion object {
        private val KEY_PAGE_TURN_MODE = stringPreferencesKey("reader_page_turn_mode")
    }
}
