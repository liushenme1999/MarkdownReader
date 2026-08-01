package space.liushenme.markdownreader.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import space.liushenme.markdownreader.data.backup.BackupManager
import space.liushenme.markdownreader.data.repository.WebDavConfig
import space.liushenme.markdownreader.data.repository.WebDavConfigRepository

sealed interface BackupUiEvent {
    data class BackupSuccess(val fileName: String) : BackupUiEvent
    data class BackupFailed(val detail: String) : BackupUiEvent
    data object RestoreListEmpty : BackupUiEvent
    data class RestoreListFailed(val detail: String) : BackupUiEvent
    data object RestoreSuccess : BackupUiEvent
    data class RestoreFailed(val detail: String) : BackupUiEvent
}

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    private val webDavConfigRepository: WebDavConfigRepository,
    private val backupManager: BackupManager,
) : ViewModel() {

    val config: StateFlow<WebDavConfig> = webDavConfigRepository.config.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WebDavConfig(),
    )

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = MutableStateFlow<BackupUiEvent?>(null)
    val events: StateFlow<BackupUiEvent?> = _events.asStateFlow()

    private val _restoreCandidates = MutableStateFlow<List<String>?>(null)
    val restoreCandidates: StateFlow<List<String>?> = _restoreCandidates.asStateFlow()

    fun clearEvent() {
        _events.value = null
    }

    fun dismissRestoreDialog() {
        _restoreCandidates.value = null
    }

    fun setUrl(url: String) {
        viewModelScope.launch { webDavConfigRepository.update(url = url) }
    }

    fun setAccount(account: String) {
        viewModelScope.launch { webDavConfigRepository.update(account = account) }
    }

    fun setPassword(password: String) {
        viewModelScope.launch { webDavConfigRepository.update(password = password) }
    }

    fun setDir(dir: String) {
        viewModelScope.launch { webDavConfigRepository.update(dir = dir) }
    }

    fun setDeviceName(name: String) {
        viewModelScope.launch { webDavConfigRepository.update(deviceName = name) }
    }

    fun setOnlyLatestBackup(enabled: Boolean) {
        viewModelScope.launch { webDavConfigRepository.update(onlyLatestBackup = enabled) }
    }

    fun backupNow(
        url: String,
        account: String,
        password: String,
        dir: String,
        deviceName: String,
    ) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            webDavConfigRepository.update(
                url = url,
                account = account,
                password = password,
                dir = dir,
                deviceName = deviceName,
            )
            val result = backupManager.backup()
            _busy.value = false
            _events.value = result.fold(
                onSuccess = { BackupUiEvent.BackupSuccess(it) },
                onFailure = {
                    BackupUiEvent.BackupFailed(it.localizedMessage ?: it.toString())
                },
            )
        }
    }

    fun prepareRestore(
        url: String,
        account: String,
        password: String,
        dir: String,
        deviceName: String,
    ) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            webDavConfigRepository.update(
                url = url,
                account = account,
                password = password,
                dir = dir,
                deviceName = deviceName,
            )
            val result = backupManager.listRemoteBackups()
            _busy.value = false
            result.fold(
                onSuccess = { names ->
                    if (names.isEmpty()) {
                        _events.value = BackupUiEvent.RestoreListEmpty
                    } else {
                        _restoreCandidates.value = names
                    }
                },
                onFailure = {
                    _events.value = BackupUiEvent.RestoreListFailed(
                        it.localizedMessage ?: it.toString(),
                    )
                },
            )
        }
    }

    fun restore(fileName: String) {
        if (_busy.value) return
        _restoreCandidates.value = null
        viewModelScope.launch {
            _busy.value = true
            val result = backupManager.restore(fileName)
            _busy.value = false
            _events.value = result.fold(
                onSuccess = { BackupUiEvent.RestoreSuccess },
                onFailure = {
                    BackupUiEvent.RestoreFailed(it.localizedMessage ?: it.toString())
                },
            )
        }
    }
}
