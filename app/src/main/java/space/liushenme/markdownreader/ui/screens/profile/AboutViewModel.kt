package space.liushenme.markdownreader.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.liushenme.markdownreader.BuildConfig
import space.liushenme.markdownreader.update.AppUpdateChecker
import space.liushenme.markdownreader.update.AppUpdateInfo

@HiltViewModel
class AboutViewModel @Inject constructor() : ViewModel() {

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _newerRelease = MutableStateFlow<AppUpdateInfo?>(null)
    val newerRelease: StateFlow<AppUpdateInfo?> = _newerRelease.asStateFlow()

    private val messageChannel = Channel<AboutUpdateMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    fun checkForUpdate() {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            try {
                val info = withContext(Dispatchers.IO) { AppUpdateChecker.fetchLatest() }
                if (info.isNewerThan(BuildConfig.VERSION_CODE)) {
                    _newerRelease.value = info
                } else {
                    messageChannel.trySend(AboutUpdateMessage.AlreadyLatest)
                }
            } catch (e: Exception) {
                Log.w("AboutViewModel", "check for update failed", e)
                messageChannel.trySend(AboutUpdateMessage.CheckFailed)
            } finally {
                _checking.value = false
            }
        }
    }

    fun dismissNewerRelease() {
        _newerRelease.value = null
    }
}

enum class AboutUpdateMessage {
    AlreadyLatest,
    CheckFailed,
}
