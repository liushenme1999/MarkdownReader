package com.example.markdownreader.importing

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

        val bomCharset = detectBom(bytes)
        if (bomCharset != null) {
            val skip = bomSkipLength(bomCharset)
            return String(bytes, skip, bytes.size - skip, bomCharset)
        }

        if (isValidUtf8(bytes)) {
            return String(bytes, StandardCharsets.UTF_8)
        }

        // 大陆简体 .txt 多为 GB18030；部分机型上启发式会与单字节编码平局，这里优先接受「严格 GB18030 + 汉字密度」结果
        val gb = tryDecode(bytes, "GB18030")
        if (gb != null && looksLikeMainlandChinesePlainText(gb)) {
            return gb
        }

        return guessByHeuristic(bytes)
    }

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
}
