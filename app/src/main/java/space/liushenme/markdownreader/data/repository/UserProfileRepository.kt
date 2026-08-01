package space.liushenme.markdownreader.data.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.preferences.readerPreferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class UserProfile(
    val nickname: String,
    val signature: String,
    val avatarPath: String?,
)

@Singleton
class UserProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = readerPreferencesDataStore(context)

    val defaultNickname: String
        get() = context.getString(R.string.profile_default_nickname)

    val defaultSignature: String
        get() = context.getString(R.string.profile_default_signature)

    val profile: Flow<UserProfile> = dataStore.data.map { prefs ->
        UserProfile(
            nickname = prefs[KEY_NICKNAME] ?: defaultNickname,
            signature = prefs[KEY_SIGNATURE] ?: defaultSignature,
            avatarPath = prefs[KEY_AVATAR_PATH]?.takeIf { path ->
                File(path).exists()
            },
        )
    }

    suspend fun setNickname(nickname: String) {
        val trimmed = nickname.trim().take(MAX_NICKNAME_LENGTH)
        dataStore.edit { prefs ->
            prefs[KEY_NICKNAME] = trimmed.ifEmpty { defaultNickname }
        }
    }

    suspend fun setSignature(signature: String) {
        val trimmed = signature.trim().take(MAX_SIGNATURE_LENGTH)
        dataStore.edit { prefs ->
            prefs[KEY_SIGNATURE] = trimmed.ifEmpty { defaultSignature }
        }
    }

    suspend fun saveAvatarFromUri(uri: Uri) {
        withContext(Dispatchers.IO) {
            val dest = avatarFile()
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext
            dataStore.edit { prefs ->
                prefs[KEY_AVATAR_PATH] = dest.absolutePath
            }
        }
    }

    suspend fun clearAvatar() {
        withContext(Dispatchers.IO) {
            avatarFile().delete()
            dataStore.edit { prefs ->
                prefs.remove(KEY_AVATAR_PATH)
            }
        }
    }

    private fun avatarFile(): File = File(context.filesDir, AVATAR_FILE_NAME)

    companion object {
        const val MAX_NICKNAME_LENGTH = 20
        const val MAX_SIGNATURE_LENGTH = 60
        private const val AVATAR_FILE_NAME = "profile_avatar.jpg"

        private val KEY_NICKNAME = stringPreferencesKey("profile_nickname")
        private val KEY_SIGNATURE = stringPreferencesKey("profile_signature")
        private val KEY_AVATAR_PATH = stringPreferencesKey("profile_avatar_path")
    }
}
