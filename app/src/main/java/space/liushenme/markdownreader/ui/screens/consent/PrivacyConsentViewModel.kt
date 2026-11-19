package space.liushenme.markdownreader.ui.screens.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import space.liushenme.markdownreader.data.repository.PrivacyConsentRepository

/**
 * 首次启动隐私同意状态。
 *
 * `null` 表示尚未从 DataStore 读出（避免首帧闪一下弹窗），
 * `false` 需要展示弹窗，`true` 直接进入主界面。
 */
@HiltViewModel
class PrivacyConsentViewModel @Inject constructor(
    private val repository: PrivacyConsentRepository,
) : ViewModel() {

    val consentState: StateFlow<Boolean?> = repository.accepted
        .map<Boolean, Boolean?> { accepted -> accepted }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    fun accept() {
        viewModelScope.launch { repository.accept() }
    }
}
