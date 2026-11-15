package space.liushenme.markdownreader.importing

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * 从字节流推断文本编码，解决 Windows 下 GBK/GB18030 等 txt 乱码问题。
 * 不依赖 android.icu，在 JVM 与 Android 上行为一致。
 */
object TextEncodingDetector {

    private val fallbackCharsetNames = listOf(
        "GB18030",
        "GBK",
        "Big5",
        "Shift_JIS",
        "EUC-JP",
        "EUC-KR",
        "windows-1252",
        "ISO-8859-1"
    )

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val charsetName = detectCharsetName(bytes)
        if (charsetName.equals("UTF-8", ignoreCase = true) && hasUtf8Bom(bytes)) {
            val skip = 3
            return String(bytes, skip, bytes.size - skip, StandardCharsets.UTF_8)
        }
        return decodeLenient(bytes, charsetName)
            ?: String(bytes, StandardCharsets.ISO_8859_1)
    }

    /**
     * 判定编码名（只看开头采样，避免对整篇反复试解码）。
     *
     * 调用方应把结果记录下来（导入时写入正文包），之后重新读取同一份源文件时直接按记录的编码
     * 解码，不必再做一遍检测。
     */
    fun detectCharsetName(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "UTF-8"

        val bomCharset = detectBom(bytes)
        if (bomCharset != null) return bomCharset.name()

        // 采样判定：几 MB 的小说上对全文反复试解码既慢又容易被个别坏字节带偏。
        val sample = if (bytes.size > SAMPLE_BYTES) bytes.copyOfRange(0, SAMPLE_BYTES) else bytes

        if (isValidUtf8(sample)) return "UTF-8"

        // 大陆简体 .txt 多为 GB18030。老小说常夹带个别非法双字节/被截断的半个汉字，
        // 严格解码整篇会直接失败，随后被单字节编码抢走而显示成乱码；这里用宽松解码 + 汉字密度判定。
        val gbSample = decodeLenient(sample, "GB18030")
        if (gbSample != null && looksLikeMainlandChinesePlainText(gbSample)) {
            return "GB18030"
        }
        return guessCharsetBySample(sample) ?: "UTF-8"
    }

    private fun hasUtf8Bom(bytes: ByteArray): Boolean =
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()

    private fun detectBom(bytes: ByteArray): Charset? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return StandardCharsets.UTF_8
        }
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return StandardCharsets.UTF_16BE
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return if (bytes.size >= 4 &&
                    bytes[2] == 0x00.toByte() && bytes[3] == 0x00.toByte()
                ) {
                    Charset.forName("UTF-32LE")
                } else {
                    StandardCharsets.UTF_16LE
                }
            }
        }
        if (bytes.size >= 4 &&
            bytes[0] == 0x00.toByte() && bytes[1] == 0x00.toByte() &&
            bytes[2] == 0xFE.toByte() && bytes[3] == 0xFF.toByte()
        ) {
            return Charset.forName("UTF-32BE")
        }
        return null
    }

    private fun bomSkipLength(charset: Charset): Int = when (charset) {
        StandardCharsets.UTF_8 -> 3
        StandardCharsets.UTF_16BE, StandardCharsets.UTF_16LE -> 2
        else -> if (charset.name().startsWith("UTF-32", ignoreCase = true)) 4 else 0
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        return try {
            // 每次新建 Decoder：CharsetDecoder 非线程安全，共享实例在并发导入/阅读时会误判 UTF-8
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val buf = ByteBuffer.wrap(bytes)
            val out = CharBuffer.allocate((bytes.size * 2).coerceAtLeast(16))
            decoder.decode(buf, out, true)
            decoder.flush(out)
            !buf.hasRemaining()
        } catch (_: Exception) {
            false
        }
    }

    private fun looksLikeMainlandChinesePlainText(s: String): Boolean {
        val ideo = countIdeographs(s)
        if (ideo < 20) return false
        // 典型简体小说：汉字占比明显高于「拉丁乱码偶然落在表意区」的情况
        return ideo * 20 >= s.length
    }

    /**
     * 在若干常见编码中选取「乱码少、且表意文字（中文等）比例高」的结果。
     * 避免 GB18030 文本被 ISO-8859-1 / windows-1252 误读（二者对任意字节都能解码且 penalty 常为 0）。
     */
    private fun guessByHeuristic(bytes: ByteArray): String {
        var best: String? = null
        var bestScore = Int.MAX_VALUE
        for (name in fallbackCharsetNames) {
            val s = tryDecode(bytes, name) ?: continue
            val score = scoreTextQuality(s)
            if (score < bestScore) {
                bestScore = score
                best = s
            }
        }
        return best ?: String(bytes, StandardCharsets.ISO_8859_1)
    }

    /**
     * 使用严格解码：避免 GBK 等对非法双字节用「替换字符」凑成看似合法的中文，从而在评分里压过 GB18030。
     */
    private fun tryDecode(bytes: ByteArray, charsetName: String): String? {
        return try {
            val cs = Charset.forName(charsetName)
            val decoder = cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 分数越低越好。
     * 对「任意字节都能解」的单字节编码（Latin-1 等）必须压低分数，否则会胜过 GB18030。
     */
    private fun scoreTextQuality(s: String): Int {
        var penalty = 0
        var printable = 0
        for (ch in s) {
            when {
                ch == '\uFFFD' -> penalty += 50
                ch.code < 32 && ch !in "\n\r\t" -> penalty += 5
                ch.isHighSurrogate() || ch.isLowSurrogate() -> penalty += 2
                else -> printable++
            }
        }
        if (printable == 0) return Int.MAX_VALUE / 4
        val base = penalty * 1000 + (s.length - printable)
        val ideo = countIdeographs(s)
        // 每出现一个表意文字（汉字等）扣 8 分：《活着》等 GB18030 正文会大幅优于 Latin-1 乱码
        val ideoBonus = ideo * -8
        // 单字节编码解中文会偏长且无汉字，略惩罚偏长结果（打破残余平局时略倾向更短、信息更密的解码）
        val lengthTie = s.length / 64
        return base + ideoBonus + lengthTie
    }

    private fun countIdeographs(s: String): Int {
        var n = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (Character.isIdeographic(cp)) n++
            i += Character.charCount(cp)
        }
        return n
    }


    /** 编码判定只采样开头这么多字节，避免大文件多次全文解码。 */
    private const val SAMPLE_BYTES = 256 * 1024

    /** 宽松解码（非法字节替换为 U+FFFD），用于夹带坏字节的老文件。 */
    private fun decodeLenient(bytes: ByteArray, charsetName: String): String? = try {
        val cs = Charset.forName(charsetName)
        val decoder = cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        val out = CharBuffer.allocate((bytes.size * 2).coerceAtLeast(16))
        decoder.decode(ByteBuffer.wrap(bytes), out, true)
        decoder.flush(out)
        out.flip()
        out.toString()
    } catch (_: Exception) {
        null
    }

    /** 在常见编码中按采样文本质量挑一个（宽松解码，避免个别坏字节淘汰整体正确的中文编码）。 */
    private fun guessCharsetBySample(sample: ByteArray): String? {
        var best: String? = null
        var bestScore = Int.MAX_VALUE
        for (name in fallbackCharsetNames) {
            val text = decodeLenient(sample, name) ?: continue
            val score = scoreTextQuality(text)
            if (score < bestScore) {
                bestScore = score
                best = name
            }
        }
        return best
    }

}