package com.example.markdownreader.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.markdownreader.data.repository.ReaderSettingsRepository
import com.example.markdownreader.model.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val readerSettingsRepository: ReaderSettingsRepository,
) : ViewModel() {

    val appThemeMode = readerSettingsRepository.appThemeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppThemeMode.SYSTEM,
    )

    fun setAppThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { readerSettingsRepository.setAppThemeMode(mode) }
    }
}
