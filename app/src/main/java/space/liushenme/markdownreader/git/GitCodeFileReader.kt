package space.liushenme.markdownreader.git

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Git 工作区代码文件的严格 UTF-8 读取器。 */
object GitCodeFileReader {

    const val MAX_BYTES = 2 * 1024 * 1024

    enum class Failure {
        TooLarge,
        UnsupportedEncoding,
    }

    class ReadException(
        val failure: Failure,
        cause: Throwable? = null,
    ) : Exception(failure.name, cause)

    fun read(file: File): String {
        if (file.length() > MAX_BYTES) throw ReadException(Failure.TooLarge)
        val bytes = file.readBytes()
        if (bytes.size > MAX_BYTES) throw ReadException(Failure.TooLarge)
        return decode(bytes)
    }

    fun decode(bytes: ByteArray): String {
        val decoded = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (error: Exception) {
            throw ReadException(Failure.UnsupportedEncoding, error)
        }
        return decoded.removePrefix("\uFEFF")
    }
}
