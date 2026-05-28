package com.example.markdownreader.ui.screens.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.markdownreader.data.repository.AppCacheRepository
import com.example.markdownreader.data.repository.UserProfile
import com.example.markdownreader.data.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val appCacheRepository: AppCacheRepository,
) : ViewModel() {

    val profile = userProfileRepository.profile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserProfile(
            nickname = UserProfileRepository.DEFAULT_NICKNAME,
            signature = UserProfileRepository.DEFAULT_SIGNATURE,
            avatarPath = null,
        ),
    )

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
