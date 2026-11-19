package space.liushenme.markdownreader.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import space.liushenme.markdownreader.data.preferences.readerPreferencesDataStore

/**
 * 首次启动隐私政策同意状态。
 *
 * 只有用户主动点击「同意并继续」后才写入 `true`；在此之前应用不得发起网络请求，
 * 也不得注册后台备份任务。隐私政策发生实质性变更时递增 [KEY_PRIVACY_ACCEPTED] 的
 * 版本后缀，可让老用户重新确认。
 */
@Singleton
class PrivacyConsentRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = readerPreferencesDataStore(context)

    val accepted: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_PRIVACY_ACCEPTED] ?: false
    }

    suspend fun isAccepted(): Boolean = accepted.first()

    suspend fun accept() {
        dataStore.edit { prefs ->
            prefs[KEY_PRIVACY_ACCEPTED] = true
        }
    }

    private companion object {
        val KEY_PRIVACY_ACCEPTED = booleanPreferencesKey("privacy_policy_accepted_v1")
    }
}
