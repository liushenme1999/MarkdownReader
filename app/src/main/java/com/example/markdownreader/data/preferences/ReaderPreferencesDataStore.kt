package com.example.markdownreader.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.readerPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reader_preferences"
)

fun readerPreferencesDataStore(context: Context): DataStore<Preferences> =
    context.readerPreferencesDataStore
