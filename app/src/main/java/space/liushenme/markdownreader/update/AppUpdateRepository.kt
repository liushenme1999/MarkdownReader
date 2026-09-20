package space.liushenme.markdownreader.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AppUpdateRepository(
    private val fetcher: AppUpdateFetcher,
    private val localVersionCode: Int,
) {
    private val mutex = Mutex()

    private val _availableUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val availableUpdate: StateFlow<AppUpdateInfo?> = _availableUpdate.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    suspend fun refresh(): CheckResult = mutex.withLock {
        _checking.value = true
        try {
            val info = withContext(Dispatchers.IO) { fetcher.fetchLatest() }
            if (info.isNewerThan(localVersionCode)) {
                _availableUpdate.value = info
                CheckResult.Newer(info)
            } else {
                _availableUpdate.value = null
                CheckResult.AlreadyLatest
            }
        } catch (e: Exception) {
            Log.w(TAG, "refresh failed", e)
            CheckResult.Failed(e)
        } finally {
            _checking.value = false
        }
    }

    sealed class CheckResult {
        data class Newer(val info: AppUpdateInfo) : CheckResult()
        data object AlreadyLatest : CheckResult()
        data class Failed(val error: Exception) : CheckResult()
    }

    private companion object {
        const val TAG = "AppUpdateRepository"
    }
}
