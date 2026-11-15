package space.liushenme.markdownreader.importing

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/** 纯文本正文的一个分段（按行首切分），记录全局字符范围与文件字节范围。 */
data class TxtSegment(
    val startChar: Int,
    val charCount: Int,
    val startByte: Long,
    val endByte: Long,
    /** 段首行文本（通常是章节标题），用于窗口模式生成目录。 */
    val title: String? = null,
)

/** 一次按窗口加载出来的正文片段，附带它在全书中的全局字符范围。 */
data class TxtTextWindow(
    val text: String,
    val startChar: Int,
    val endChar: Int,
    val totalChars: Int,
)

/**
 * 纯文本的「分段索引 + 按窗口加载」。
 *
 * 参考 Legado 的本地 TXT 方案（`model/localBook/TextFile.kt`）：不把全文读进内存，
 * 导入时流式扫描一遍记录每段的字节偏移与字符偏移；打开/翻页时只按需读取当前位置附近
 * 的若干段，用随机访问（seek）跳过无关内容。
 *
 * 索引体积很小（12MB 文本约 1500 段），读取是毫秒级。
 */
object TxtSegmentIndex {

    const val FILE_NAME = "txt_segments.json"

    /** 目标段长（字符数）：太小索引多、太大单次加载慢。 */
    const val TARGET_CHARS = 8_000

    /**
     * 扫描正文文件生成分段索引。
     *
     * 逐行读取字节（行尾 `\n`）并按 UTF-8 解码计数，保证 [startChar] 与阅读器使用的字符偏移一致；
     * 同时在行首切段，避免把一个字符切成两半。
     */
    fun build(bodyFile: File, targetChars: Int = TARGET_CHARS): List<TxtSegment>? {
        if (!bodyFile.isFile) return null
        val segments = ArrayList<TxtSegment>(bodyFile.length().div(16 * 1024L).toInt().coerceAtLeast(4))
        var globalChar = 0
        var firstLineOfSegment: String? = null
        var segStartChar = 0
        var segStartByte = 0L
        var byteOffset = 0L

        RandomAccessFile(bodyFile, "r").use { raf ->
            val buffer = ByteArray(64 * 1024)
            val line = java.io.ByteArrayOutputStream(256)
            fun flushLine(lineBytes: ByteArray) {
                if (lineBytes.isEmpty()) return
                val lineText = String(lineBytes, StandardCharsets.UTF_8)
                val lineChars = lineText.length
                if (firstLineOfSegment == null) {
                    firstLineOfSegment = lineText.trim().takeIf { it.isNotEmpty() }?.take(40)
                }
                globalChar += lineChars
            }
            while (true) {
                val read = raf.read(buffer)
                if (read <= 0) break
                var i = 0
                while (i < read) {
                    val b = buffer[i]
                    line.write(b.toInt())
                    i++
                    if (b == '\n'.code.toByte()) {
                        val lineBytes = line.toByteArray()
                        line.reset()
                        byteOffset += lineBytes.size
                        flushLine(lineBytes)
                        if (globalChar - segStartChar >= targetChars) {
                            segments += TxtSegment(
                                startChar = segStartChar,
                                charCount = globalChar - segStartChar,
                                startByte = segStartByte,
                                endByte = byteOffset,
                                title = firstLineOfSegment,
                            )
                            firstLineOfSegment = null
                            segStartChar = globalChar
                            segStartByte = byteOffset
                        }
                    }
                }
            }
            // 文件末尾没有换行的残行
            if (line.size() > 0) {
                val lineBytes = line.toByteArray()
                byteOffset += lineBytes.size
                flushLine(lineBytes)
            }
            if (globalChar > segStartChar) {
                segments += TxtSegment(
                    startChar = segStartChar,
                    charCount = globalChar - segStartChar,
                    startByte = segStartByte,
                    endByte = byteOffset,
                    title = firstLineOfSegment,
                )
            }
        }
        return segments.ifEmpty { null }
    }

    fun write(dir: File, segments: List<TxtSegment>): Boolean = runCatching {
        val json = org.json.JSONArray()
        segments.forEach { s ->
            json.put(
                org.json.JSONObject()
                    .put("sc", s.startChar)
                    .put("cc", s.charCount)
                    .put("sb", s.startByte)
                    .put("eb", s.endByte)
                    .put("t", s.title.orEmpty()),
            )
        }
        File(dir, FILE_NAME).writeText(json.toString(), StandardCharsets.UTF_8)
        true
    }.getOrDefault(false)

    fun read(dir: File): List<TxtSegment>? = runCatching {
        val file = File(dir, FILE_NAME)
        if (!file.isFile) return null
        val array = org.json.JSONArray(file.readText(StandardCharsets.UTF_8))
        val out = ArrayList<TxtSegment>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            out += TxtSegment(
                startChar = o.getInt("sc"),
                charCount = o.getInt("cc"),
                startByte = o.getLong("sb"),
                endByte = o.getLong("eb"),
                title = o.optString("t").takeIf { it.isNotEmpty() },
            )
        }
        out.ifEmpty { null }
    }.getOrNull()

    /** 按全局字符位置定位所在分段下标。 */
    fun segmentIndexFor(segments: List<TxtSegment>, charOffset: Int): Int {
        if (segments.isEmpty()) return -1
        var lo = 0
        var hi = segments.lastIndex
        var best = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (segments[mid].startChar <= charOffset) {
                best = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best
    }

    fun totalChars(segments: List<TxtSegment>): Int =
        segments.lastOrNull()?.let { it.startChar + it.charCount } ?: 0

    /**
     * 加载 [anchorChar] 附近 [before]~[after] 段，返回文本与全局字符范围。
     * 未命中缓存时只读取这些段的字节区间，不触碰其余内容。
     */
    fun loadWindow(
        bodyFile: File,
        segments: List<TxtSegment>,
        anchorChar: Int,
        before: Int = 1,
        after: Int = 2,
    ): TxtTextWindow? {
        if (segments.isEmpty() || !bodyFile.isFile) return null
        val center = segmentIndexFor(segments, anchorChar.coerceAtLeast(0))
        if (center < 0) return null
        val from = (center - before).coerceAtLeast(0)
        val to = (center + after).coerceAtMost(segments.lastIndex)
        val startByte = segments[from].startByte
        val endByte = segments[to].endByte
        val byteCount = (endByte - startByte).toInt()
        if (byteCount <= 0) return null
        val bytes = ByteArray(byteCount)
        RandomAccessFile(bodyFile, "r").use { raf ->
            raf.seek(startByte)
            raf.readFully(bytes)
        }
        val startChar = segments[from].startChar
        return TxtTextWindow(
            text = String(bytes, StandardCharsets.UTF_8),
            startChar = startChar,
            endChar = segments[to].startChar + segments[to].charCount,
            totalChars = totalChars(segments),
        )
    }
}
