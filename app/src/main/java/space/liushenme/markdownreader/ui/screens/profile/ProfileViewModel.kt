package space.liushenme.markdownreader.ui.screens.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import space.liushenme.markdownreader.data.repository.AppCacheRepository
import space.liushenme.markdownreader.data.repository.IReadingProgressRepository
import space.liushenme.markdownreader.data.repository.ReaderSettingsRepository
import space.liushenme.markdownreader.data.repository.UserProfile
import space.liushenme.markdownreader.data.repository.UserProfileRepository
import space.liushenme.markdownreader.model.AppLanguage
import space.liushenme.markdownreader.model.AppThemeMode
import space.liushenme.markdownreader.update.AppUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val appCacheRepository: AppCacheRepository,
    private val readerSettingsRepository: ReaderSettingsRepository,
    readingProgressRepository: IReadingProgressRepository,
    appUpdateRepository: AppUpdateRepository,
) : ViewModel() {

    val profile = userProfileRepository.profile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserProfile(
            nickname = userProfileRepository.defaultNickname,
            signature = userProfileRepository.defaultSignature,
            avatarPath = null,
        ),
    )

    val todayReadingMinutes = readingProgressRepository.observeTodayReadMinutes().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    val totalReadingDays = readingProgressRepository.observeTotalReadingDays().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    val continuousReadingDays = readingProgressRepository.observeContinuousReadingDays().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    val availableUpdate = appUpdateRepository.availableUpdate.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = appUpdateRepository.availableUpdate.value,
    )

    val appLanguage = readerSettingsRepository.appLanguage.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppLanguage.DEFAULT,
    )

    val appThemeMode = readerSettingsRepository.appThemeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppThemeMode.SYSTEM,
    )

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch { readerSettingsRepository.setAppLanguage(language) }
    }

    fun setAppThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { readerSettingsRepository.setAppThemeMode(mode) }
    }

    fun setNickname(nickname: String) {
        viewModelScope.launch { userProfileRepository.setNickname(nickname) }
    }

    fun setSignature(signature: String) {
        viewModelScope.launch { userProfileRepository.setSignature(signature) }
    }

    fun updateAvatar(uri: Uri) {
        viewModelScope.launch { userProfileRepository.saveAvatarFromUri(uri) }
    }

    fun clearAvatar() {
        viewModelScope.launch { userProfileRepository.clearAvatar() }
    }

    fun clearCache(onComplete: (bytesFreed: Long) -> Unit) {
        viewModelScope.launch {
            val bytesFreed = appCacheRepository.clearAll()
            onComplete(bytesFreed)
        }
    }
}
