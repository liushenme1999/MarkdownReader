package space.liushenme.markdownreader.data.webdav

import okhttp3.Credentials
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

data class Authorization(
    val username: String,
    val password: String,
    val charset: Charset = StandardCharsets.ISO_8859_1,
) {
    val name: String = "Authorization"
    val data: String = Credentials.basic(username, password, charset)
}
