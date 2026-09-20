package space.liushenme.markdownreader.ui.screens.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import space.liushenme.markdownreader.update.AppUpdateRepository

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
) : ViewModel() {

    val checking: StateFlow<Boolean> = appUpdateRepository.checking
    val availableUpdate = appUpdateRepository.availableUpdate

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val messageChannel = Channel<AboutUpdateMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    fun onUpdateActionClick() {
        if (availableUpdate.value != null) {
            _showUpdateDialog.value = true
            return
        }
        checkForUpdate()
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            when (val result = appUpdateRepository.refresh()) {
                is AppUpdateRepository.CheckResult.Newer -> {
                    _showUpdateDialog.value = true
                }
                AppUpdateRepository.CheckResult.AlreadyLatest -> {
                    messageChannel.trySend(AboutUpdateMessage.AlreadyLatest)
                }
                is AppUpdateRepository.CheckResult.Failed -> {
                    Log.w("AboutViewModel", "check for update failed", result.error)
                    messageChannel.trySend(AboutUpdateMessage.CheckFailed)
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }
}

enum class AboutUpdateMessage {
    AlreadyLatest,
    CheckFailed,
}
