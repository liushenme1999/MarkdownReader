package space.liushenme.markdownreader.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import space.liushenme.markdownreader.data.preferences.readerPreferencesDataStore

data class WebDavConfig(
    val url: String = WebDavConfigRepository.DEFAULT_WEBDAV_URL,
    val account: String = "",
    val password: String = "",
    val dir: String = WebDavConfigRepository.DEFAULT_WEBDAV_DIR,
    val deviceName: String = "",
    val onlyLatestBackup: Boolean = true,
    val lastBackupTime: Long = 0L,
) {
    val isConfigured: Boolean
        get() = account.isNotBlank() && password.isNotBlank()
}

@Singleton
class WebDavConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = readerPreferencesDataStore(context)

    val config: Flow<WebDavConfig> = dataStore.data.map { prefs ->
        WebDavConfig(
            url = prefs[KEY_URL]?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_WEBDAV_URL,
            account = prefs[KEY_ACCOUNT].orEmpty(),
            password = prefs[KEY_PASSWORD].orEmpty(),
            dir = prefs[KEY_DIR]?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_WEBDAV_DIR,
            deviceName = prefs[KEY_DEVICE_NAME].orEmpty(),
            onlyLatestBackup = prefs[KEY_ONLY_LATEST] ?: true,
            lastBackupTime = prefs[KEY_LAST_BACKUP] ?: 0L,
        )
    }

    suspend fun current(): WebDavConfig = config.first()

    suspend fun update(
        url: String? = null,
        account: String? = null,
        password: String? = null,
        dir: String? = null,
        deviceName: String? = null,
        onlyLatestBackup: Boolean? = null,
    ) {
        dataStore.edit { prefs ->
            url?.let {
                val trimmed = it.trim()
                if (trimmed.isEmpty()) prefs.remove(KEY_URL) else prefs[KEY_URL] = trimmed
            }
            account?.let { prefs[KEY_ACCOUNT] = it.trim() }
            password?.let { prefs[KEY_PASSWORD] = it }
            dir?.let {
                val trimmed = it.trim()
                if (trimmed.isEmpty()) {
                    prefs[KEY_DIR] = DEFAULT_WEBDAV_DIR
                } else {
                    prefs[KEY_DIR] = trimmed
                }
            }
            deviceName?.let { prefs[KEY_DEVICE_NAME] = it.trim() }
            onlyLatestBackup?.let { prefs[KEY_ONLY_LATEST] = it }
        }
    }

    suspend fun setLastBackupTime(timeMillis: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_BACKUP] = timeMillis
        }
    }

    /** 根路径：url + 可选子目录，始终以 / 结尾 */
    fun rootUrl(config: WebDavConfig): String {
        var url = config.url.trim().ifEmpty { DEFAULT_WEBDAV_URL }
        if (!url.endsWith("/")) url += "/"
        val dir = config.dir.trim().trim('/')
        if (dir.isNotEmpty()) {
            url += "$dir/"
        }
        return url
    }

    companion object {
        const val DEFAULT_WEBDAV_URL = "https://dav.jianguoyun.com/dav/"
        const val DEFAULT_WEBDAV_DIR = "MarkdownReader"

        private val KEY_URL = stringPreferencesKey("webdav_url")
        private val KEY_ACCOUNT = stringPreferencesKey("webdav_account")
        private val KEY_PASSWORD = stringPreferencesKey("webdav_password")
        private val KEY_DIR = stringPreferencesKey("webdav_dir")
        private val KEY_DEVICE_NAME = stringPreferencesKey("webdav_device_name")
        private val KEY_ONLY_LATEST = booleanPreferencesKey("webdav_only_latest_backup")
        private val KEY_LAST_BACKUP = longPreferencesKey("webdav_last_backup_time")

        /** 恢复偏好时跳过这些 WebDAV 相关键，避免覆盖当前机凭证 */
        val WEBDAV_PREF_KEYS = setOf(
            "webdav_url",
            "webdav_account",
            "webdav_password",
            "webdav_dir",
            "webdav_device_name",
            "webdav_only_latest_backup",
            "webdav_last_backup_time",
        )
    }
}
