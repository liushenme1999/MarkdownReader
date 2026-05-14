package com.example.markdownreader.importing

import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

private const val MAX_MOBI_INPUT_BYTES = 36 * 1024 * 1024
private const val MAX_TEXT_CHARS = 8 * 1024 * 1024

private val MOBI_MAGIC = byteArrayOf(0x4d, 0x4f, 0x42, 0x49) // MOBI

private const val EXTH_TYPE_BOUNDARY = 121
private const val EXTH_TYPE_COVER_OFFSET = 201
private const val EXTH_TYPE_THUMB_OFFSET = 202

private data class MobiPdbExtract(
    val plainText: String,
    val toc: List<ImportedTocEntry>,
    val coverImageBytes: ByteArray?,
    val assets: Map<String, ByteArray> = emptyMap()
)

/**
 * 从 MOBI / AZW3（Palm PDB + MOBI）提取纯文本、目录与封面。
 *
 * 关键设计：
 * - 仅解析一次全局 PDB record 表；主 MOBI part 与（可选的）KF8 part 都基于同一份 `recordStarts`。
 * - KF8 嵌套：EXTH 121 给出的是 KF8 record0 在**全局 records 中的 record 索引**（不是字节偏移）。
 *   旧实现把它当字节偏移并通过 "BOOKMOBI" 头校验，几乎永远 false，导致 AZW3 / 含 KF8 的 MOBI
 *   完全识别不到第二段正文与封面。
 * - 正文压缩仅支持 type=1（无压缩）与 type=2（PalmDOC）；Huff/CDIC (17480) 跳过。
 * - 目录：优先解析 HTML 中 `<reference filepos>` / `<a filepos>`（字节偏移映射到去标签正文下标），
 *   否则回退到中文/英文常见章节行启发式。
 * - first_image_index 字段在国产 MOBI 里常被写成 0 / 0xFFFFFFFF 等无效值，此时退化为
 *   扫描全部 record 找到第一张 JPEG/PNG/GIF 作为 firstImage。
 * - 部分文件把图像 / 资源 record 写在主 part 的 text range 之内（last_idx 把图也囊括了），
 *   text 拼接前会先在 range 上裁掉首个 image/resource record，避免二进制污染正文。
 * - 封面：依次尝试 firstImage+EXTH201、firstImage+EXTH202（缩略图）、firstImage 本身、
 *   把 EXTH201 当作绝对索引（兼容异常文件），最后兜底扫描全 PDB 选最大的 JPEG/PNG/GIF
 *   （过滤 <2KB 的小图标/装饰图）。
 */
internal object MobiAzw3Extractor {

    fun extractFromFile(file: File): ExtractedBookText? {
        if (file.length() > MAX_MOBI_INPUT_BYTES) return null
        return extractFromBytes(file.readBytes())
    }

    fun extractFromBytes(bytes: ByteArray): ExtractedBookText? {
        if (bytes.size < 86) return null
        val nRecords = readUInt16BE(bytes, 76)
        if (nRecords <= 0 || nRecords > 32767) return null
        if (78 + nRecords * 8 > bytes.size) return null
        val recordStarts = IntArray(nRecords) { i ->
            readUInt32BEAsInt(bytes, 78 + i * 8)
        }
        // 单调校验：损坏文件常表现为乱序，避免后续 sliceRecord 切到负长度。
        for (i in 1 until nRecords) {
            if (recordStarts[i] < recordStarts[i - 1]) return null
        }

        val primary = extractMobiPart(bytes, recordStarts, partRecordIndex = 0)
        val kf8RecordIdx = sliceRecord(bytes, recordStarts, 0)?.let { r0 ->
            readExthUint32(r0, EXTH_TYPE_BOUNDARY)
        }
        val alt = kf8RecordIdx
            ?.takeIf { it in 1 until nRecords }
            ?.let { extractMobiPart(bytes, recordStarts, partRecordIndex = it) }

        val merged = when {
            primary == null && alt == null -> return null
            primary != null && alt != null -> mergePdbExtracts(primary, alt)
            primary != null -> primary
            else -> alt!!
        }
        return packResult(merged)
    }

    private fun packResult(ex: MobiPdbExtract): ExtractedBookText? {
        val t = ex.plainText.trim().takeIf { it.isNotEmpty() } ?: return null
        val clipped = t.take(MAX_TEXT_CHARS)
        val maxOff = (clipped.length - 1).coerceAtLeast(0)
        val toc = ex.toc
            .map { e -> e.copy(sourceOffset = e.sourceOffset.coerceIn(0, maxOff)) }
            .distinctBy { it.sourceOffset to it.title }
        val cover = ex.coverImageBytes?.takeIf { it.isNotEmpty() }
        return ExtractedBookText(body = clipped, toc = toc, coverImageBytes = cover, assets = ex.assets)
    }

    /** 正文优先更长的 part（KF8 part 多为更完整的版本）；封面与 assets 任一侧解析成功即可合并。 */
    private fun mergePdbExtracts(primary: MobiPdbExtract, alt: MobiPdbExtract): MobiPdbExtract {
        val useAltBody = alt.plainText.length > primary.plainText.length
        val mergedAssets = LinkedHashMap<String, ByteArray>().apply {
            putAll(primary.assets)
            for ((k, v) in alt.assets) putIfAbsent(k, v)
        }
        return if (useAltBody) {
            MobiPdbExtract(
                plainText = alt.plainText,
                toc = alt.toc,
                coverImageBytes = alt.coverImageBytes ?: primary.coverImageBytes,
                assets = mergedAssets
            )
        } else {
            MobiPdbExtract(
                plainText = primary.plainText,
                toc = primary.toc,
                coverImageBytes = primary.coverImageBytes ?: alt.coverImageBytes,
                assets = mergedAssets
            )
        }
    }

    /** 处理单个 MOBI part（主 part 或 KF8 part）：解出正文、目录、封面。 */
    private fun extractMobiPart(
        bytes: ByteArray,
        recordStarts: IntArray,
        partRecordIndex: Int
    ): MobiPdbExtract? {
        val nRecords = recordStarts.size
        val record0 = sliceRecord(bytes, recordStarts, partRecordIndex) ?: return null
        if (record0.size < 0xE8 || !byteMatch(record0, 16, MOBI_MAGIC)) return null
        val compression = readUInt16BE(record0, 0)
        val textLength = readUInt32BEAsInt(record0, 4).coerceIn(0, MAX_TEXT_CHARS)
        val encryption = readUInt16BE(record0, 12)
        if (encryption != 0) return null
        if (compression == 17480) return null // Huff/CDIC，不支持

        // first/last text record index：主 part 通常是全局 record 索引；KF8 part 实际上写的是
        // 「相对该 part 起始 record 的偏移」（如本例 KF8 record0 内 first=0/last=126 → 全局 116..242）。
        // 用 partRecordIndex 作启发式区分：firstIdx <= partRecordIndex 就视为相对偏移。
        val firstIdxRaw = readUInt16BE(record0, 0xc0)
        val lastIdxRaw = readUInt16BE(record0, 0xc2)
        val globalFirst: Int
        val globalLastRaw: Int
        if (firstIdxRaw <= partRecordIndex) {
            globalFirst = partRecordIndex + 1 + firstIdxRaw
            globalLastRaw = partRecordIndex + 1 + lastIdxRaw
        } else {
            globalFirst = firstIdxRaw
            globalLastRaw = lastIdxRaw
        }
        if (globalFirst <= partRecordIndex || globalFirst >= nRecords) return null
        var globalLast = maxOf(globalLastRaw, globalFirst).coerceAtMost(nRecords - 1)

        // 不少 MOBI 把图像 / 资源 record 写在 text range 内（如本例 last=111 含 109..111 三张 JPEG），
        // 解码前必须先在 text range 上裁掉它们，否则二进制会被当文本解码出现乱码。
        for (i in globalFirst..globalLast) {
            val rec = sliceRecord(bytes, recordStarts, i) ?: continue
            if (isImageRecord(rec) || isMobiResourceRecord(rec)) {
                globalLast = i - 1
                break
            }
        }
        if (globalLast < globalFirst) return null

        val encoding = mobiCharset(record0)
        val raw = when (compression) {
            1 -> concatUncompressed(bytes, recordStarts, globalFirst, globalLast, textLength)
            2 -> concatPalmdoc(bytes, recordStarts, globalFirst, globalLast, textLength)
            else -> return null
        } ?: return null
        val htmlish = decodeMobiBytes(raw, encoding)

        // 把 HTML/XHTML 段转成 Markdown，再把 `<img recindex="00109">` 引用替换为 book-asset 占位。
        val converted = HtmlToMarkdownConverter.convert(htmlish)
        val firstImgIdx = computeFirstImageRecord(bytes, recordStarts, record0)
        val assets = linkedMapOf<String, ByteArray>()
        val markdown = rewriteMobiImageRefs(
            converted.markdown, converted.imageSources,
            bytes, recordStarts, firstImgIdx, assets
        )

        val toc = parseMarkdownHeadingsForToc(markdown).ifEmpty { parsePlainTextTocForMobi(markdown) }
        val cover = extractCoverFromPart(bytes, recordStarts, record0, globalLast)
        return MobiPdbExtract(
            plainText = markdown,
            toc = toc,
            coverImageBytes = cover,
            assets = assets
        )
    }

    /**
     * 把 `![alt](recindex:00109)` / `![alt](kindle:embed:0003)` 风格的占位替换为
     * 带尺寸预声明的 HTML `<img src="book-asset://<id>" width=".." height="..">`，并把对应
     * PDB 图像字节塞进 assets 表。
     *
     * 之所以一定要换成 HTML `<img>` 而非保留 Markdown `![]()`：见 [ImageAssetUtils] 注释——
     * 预声明 width/height 让 TextView 首次 measure 即预留图片高度，避免异步加载完成后整段 relayout。
     */
    private fun rewriteMobiImageRefs(
        markdown: String,
        sources: List<String>,
        bytes: ByteArray,
        starts: IntArray,
        firstImgIdx: Int,
        assets: MutableMap<String, ByteArray>
    ): String {
        var result = markdown
        if (firstImgIdx >= 0) {
            for (src in sources.distinct()) {
                val recidx = parseRecindex(src) ?: continue
                // recindex / kindle:embed 均为「从 firstImage 起的 1-based 偏移」
                val absRec = firstImgIdx + (recidx - 1)
                val rec = sliceRecord(bytes, starts, absRec) ?: continue
                if (!isImageRecord(rec)) continue
                val ext = imageExtension(rec) ?: continue
                val assetId = "mobi_${recidx.toString().padStart(5, '0')}.$ext"
                assets.putIfAbsent(assetId, rec)
                val (w, h) = ImageAssetUtils.decodeImageSize(rec) ?: (null to null)
                result = replaceMarkdownImageWithHtmlImg(result, src, assetId, w, h)
            }
        }
        // 兜底：把剩余仍指向未知 scheme（kindle:* / recindex:* / data:* / 自定义协议）的图片
        // 整段降级成 `[图片]`，避免 Markwon 加载时抛 "No scheme-handler" 异常。
        return stripUnsupportedImageRefs(result)
    }

    /** 把 Markdown 中所有 `![alt](src)`（含 URL-encoded 变体）整段替换为 `<img …>`。 */
    private fun replaceMarkdownImageWithHtmlImg(
        markdown: String,
        originalSrc: String,
        assetId: String,
        width: Int?,
        height: Int?
    ): String {
        val encoded = escapeUrlForReplace(originalSrc)
        val candidates = if (encoded != originalSrc) listOf(originalSrc, encoded) else listOf(originalSrc)
        var result = markdown
        for (cand in candidates) {
            val re = Regex("""!\[([^\]]*)\]\(${Regex.escape(cand)}\)""")
            result = re.replace(result) { m ->
                val alt = m.groupValues[1].trim().ifEmpty { null }
                ImageAssetUtils.buildImgTag("book-asset://${assetId}", alt, width, height)
            }
        }
        return result
    }

    /** 把 `![alt](url)` 里 url 不是 book-asset/file/http(s) 的图片，整段替换成 `[图片]` 占位。 */
    private fun stripUnsupportedImageRefs(md: String): String {
        if ("![" !in md) return md
        val re = Regex("""!\[([^\]]*)\]\(([^)]*)\)""")
        return re.replace(md) { m ->
            val url = m.groupValues[2].trim().lowercase()
            val supported = url.startsWith("book-asset://") ||
                url.startsWith("file://") ||
                url.startsWith("http://") ||
                url.startsWith("https://")
            if (supported) m.value else "[图片]"
        }
    }

    private fun escapeUrlForReplace(s: String): String =
        s.replace(" ", "%20").replace("(", "%28").replace(")", "%29")

    /**
     * 把 MOBI HTML 里的图片引用解析为「从 firstImage 起的 1-based 偏移」。
     *
     * 主 MOBI 用 `<img recindex="00109">` 风格；KF8 / AZW3 改用 `<img src="kindle:embed:0003?mime=image/jpeg">`，
     * 其中 0003 是 1-based 的嵌入资源序号，含义和 recindex 等价。
     */
    private fun parseRecindex(src: String): Int? {
        Regex("recindex[:=](\\d+)", RegexOption.IGNORE_CASE).find(src)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?.let { return it }
        // KF8: kindle:embed:NNNN[?mime=image/...] 或 kindle:flow:NNNN
        Regex("kindle:(?:embed|flow|hdembed)[:=](\\d+)", RegexOption.IGNORE_CASE).find(src)
            ?.groupValues?.get(1)?.toIntOrNull()
            ?.let { return it }
        return null
    }

    private fun imageExtension(rec: ByteArray): String? = when {
        isJpegMagic(rec) -> "jpg"
        isPngMagic(rec) -> "png"
        isGifMagic(rec) -> "gif"
        else -> null
    }

    /** 与 [extractCoverFromPart] 一致的「first image record」定位，单独抽出以便资源映射复用。 */
    private fun computeFirstImageRecord(bytes: ByteArray, starts: IntArray, record0: ByteArray): Int {
        val nRecords = starts.size
        if (record0.size < 16 + 0x6c + 4) return -1
        val firstImgU = readUInt32BEUnsigned(record0, 16 + 0x6c)
        val valid = firstImgU in 1L until nRecords.toLong() && firstImgU < 0xffff0000L
        return if (valid) firstImgU.toInt() else findFirstImageRecord(bytes, starts)
    }

    /** Markdown 的 ATX 标题 (`# title`) 是最稳定的目录信号，作为新主路径。 */
    private fun parseMarkdownHeadingsForToc(markdown: String): List<ImportedTocEntry> {
        val out = mutableListOf<ImportedTocEntry>()
        val re = Regex("(?m)^(#{1,6})\\s+(.+?)\\s*$")
        for (m in re.findAll(markdown)) {
            val level = m.groupValues[1].length.coerceIn(1, 6)
            val title = m.groupValues[2].trim().take(120)
            if (title.isEmpty()) continue
            out += ImportedTocEntry(level = level, title = title, sourceOffset = m.range.first)
        }
        return out.distinctBy { it.sourceOffset to it.title }
    }

    private fun isImageRecord(rec: ByteArray): Boolean =
        isJpegMagic(rec) || isPngMagic(rec) || isGifMagic(rec)

    /** 常见 MOBI 内部资源块 magic，遇到这些表示 text 段已结束，必须停下避免污染正文。 */
    private fun isMobiResourceRecord(rec: ByteArray): Boolean {
        if (rec.size < 4) return false
        val tag = (rec[0].toInt() and 0xff shl 24) or
            (rec[1].toInt() and 0xff shl 16) or
            (rec[2].toInt() and 0xff shl 8) or
            (rec[3].toInt() and 0xff)
        // FCIS / FLIS / FDST / DATP / SRCS / CMET / CRES / CONT / BOUN(DARY)
        return tag == 0x46434953 || tag == 0x464C4953 || tag == 0x46445354 ||
            tag == 0x44415450 || tag == 0x53524353 || tag == 0x434D4554 ||
            tag == 0x43524553 || tag == 0x434F4E54 || tag == 0x424F554E ||
            tag == 0x464F4E54 /* FONT */ || tag == 0x52455343 /* RESC */
    }

    private fun isGifMagic(b: ByteArray): Boolean =
        b.size >= 6 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() &&
            b[2] == 'F'.code.toByte() && b[3] == '8'.code.toByte()

    private fun parsePlainTextTocForMobi(text: String): List<ImportedTocEntry> {
        val lines = text.split('\n')
        val out = mutableListOf<ImportedTocEntry>()
        var offset = 0
        lines.forEachIndexed { index, line ->
            val lineStart = offset
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                val level = plainTextTocLevelForMobi(trimmed)
                if (level != null) {
                    out += ImportedTocEntry(level = level, title = trimmed, sourceOffset = lineStart)
                }
            }
            offset += line.length
            if (index < lines.lastIndex) offset += 1
        }
        return out
    }

    private val CN_NUM = "0-9０-９〇一二三四五六七八九十百千万亿两"
    private val RE_DI_ZHANG = Regex("^第[$CN_NUM]+章.*$")
    private val RE_DI_JIE = Regex("^第[$CN_NUM]+节.*$")
    private val RE_DI_HUI = Regex("^第[$CN_NUM]+回.*$")
    private val RE_DI_JUAN = Regex("^第[$CN_NUM]+卷.*$")
    private val RE_SPECIAL = Regex(
        "^(楔子|序章|序言|引子|前言|后记|尾声|跋|番外篇?|[上下中]卷).*$"
    )
    private val RE_CH_EN = Regex("(?i)^Chapter\\s+[0-9IVXLC]+\\b.*$")
    private val RE_PT_EN = Regex("(?i)^Part\\s+[0-9IVXLC]+\\b.*$")

    private fun plainTextTocLevelForMobi(line: String): Int? {
        if (RE_DI_JUAN.matches(line)) return 1
        if (RE_SPECIAL.matches(line)) return 1
        if (RE_DI_ZHANG.matches(line)) return 1
        if (RE_DI_HUI.matches(line)) return 1
        if (RE_DI_JIE.matches(line)) return 1
        if (RE_CH_EN.matches(line)) return 1
        if (RE_PT_EN.matches(line)) return 1
        return null
    }

    private fun extractCoverFromPart(
        fileBytes: ByteArray,
        recordStarts: IntArray,
        record0: ByteArray,
        @Suppress("UNUSED_PARAMETER") lastTextRecordIndex: Int
    ): ByteArray? {
        val nRecords = recordStarts.size
        if (record0.size < 16 + 0x6c + 4) return null

        val firstImgU = readUInt32BEUnsigned(record0, 16 + 0x6c)
        val coverDelta = readExthUint32(record0, EXTH_TYPE_COVER_OFFSET)
            ?.toLong()?.and(0xFFFFFFFFL)
        val thumbDelta = readExthUint32(record0, EXTH_TYPE_THUMB_OFFSET)
            ?.toLong()?.and(0xFFFFFFFFL)
        val firstImgFieldValid = firstImgU in 1L until nRecords.toLong() && firstImgU < 0xffff0000L
        // first_image_index 字段在不少（尤其国产工具生成的）MOBI 里写成 0 或 0xFFFFFFFF。
        // 这时退化为「扫描全部 record，第一个 JPEG/PNG/GIF 即视为 first image」。
        val firstImg = if (firstImgFieldValid) firstImgU.toInt()
            else findFirstImageRecord(fileBytes, recordStarts)

        if (firstImg >= 0) {
            val roomFromFirst = (nRecords - firstImg).toLong()
            // 1) EXTH 201 cover_offset：规范定义就是封面位置
            if (coverDelta != null &&
                coverDelta != 0xFFFFFFFFL &&
                coverDelta in 0L until roomFromFirst
            ) {
                sliceRecord(fileBytes, recordStarts, firstImg + coverDelta.toInt())?.let { rec ->
                    if (isImageRecord(rec)) return rec
                }
            }
            // 2) EXTH 202 thumb_offset：部分工具只写缩略图位置
            if (thumbDelta != null &&
                thumbDelta != 0xFFFFFFFFL &&
                thumbDelta in 0L until roomFromFirst
            ) {
                sliceRecord(fileBytes, recordStarts, firstImg + thumbDelta.toInt())?.let { rec ->
                    if (isImageRecord(rec)) return rec
                }
            }
            // 3) firstImage 本身（无 EXTH 201/202 时第一张通常就是封面）
            sliceRecord(fileBytes, recordStarts, firstImg)?.let { rec ->
                if (isImageRecord(rec)) return rec
            }
        }
        // 4) 个别异常文件把 EXTH 201 当作「绝对 record 索引」
        if (coverDelta != null && coverDelta in 1L until nRecords.toLong()) {
            sliceRecord(fileBytes, recordStarts, coverDelta.toInt())?.let { rec ->
                if (isImageRecord(rec)) return rec
            }
        }
        // 5) 兜底：扫描全 records，挑最大的 JPEG/PNG/GIF（封面通常分辨率最高），过滤 < 2KB 的图标/装饰
        var best: ByteArray? = null
        for (i in 1 until nRecords) {
            val rec = sliceRecord(fileBytes, recordStarts, i) ?: continue
            if (!isImageRecord(rec) || rec.size < 2048) continue
            if (best == null || rec.size > best.size) best = rec
        }
        return best
    }

    private fun findFirstImageRecord(bytes: ByteArray, starts: IntArray): Int {
        for (i in 1 until starts.size) {
            val rec = sliceRecord(bytes, starts, i) ?: continue
            if (isImageRecord(rec)) return i
        }
        return -1
    }

    private fun isJpegMagic(b: ByteArray): Boolean =
        b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()

    private fun isPngMagic(b: ByteArray): Boolean =
        b.size >= 8 &&
            b[0] == 0x89.toByte() &&
            b[1] == 0x50.toByte() &&
            b[2] == 0x4E.toByte() &&
            b[3] == 0x47.toByte()

    private fun parseMobiReferenceToc(html: String, raw: ByteArray, charset: Charset): List<ImportedTocEntry> {
        val byOffset = linkedMapOf<Int, String>()
        val refRe = Regex("""<\s*reference\s+([^>]+)/?\s*>""", RegexOption.IGNORE_CASE)
        for (m in refRe.findAll(html)) {
            val attrs = m.groupValues[1]
            val fp = Regex("""filepos\s*=\s*["']?(\d+)["']?""", RegexOption.IGNORE_CASE)
                .find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val titleMatch = Regex("""title\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE).find(attrs)
                ?: Regex("""title\s*=\s*([^\s>]+)""", RegexOption.IGNORE_CASE).find(attrs)
            val rawTitle = titleMatch?.groupValues?.get(1)?.trim() ?: continue
            val title = decodeBasicEntities(rawTitle).trim().ifEmpty { continue }
            val charIdx = bytePrefixToPlainCharCount(raw, charset, fp)
            byOffset.putIfAbsent(charIdx, title)
        }
        // 大量 MOBI 用 <a filepos="…">章节名</a> 做目录链
        val aRe = Regex(
            """<\s*a\s+([^>]*?)>\s*([^<]*?)\s*</\s*a\s*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        for (m in aRe.findAll(html)) {
            val attrs = m.groupValues[1]
            val fp = Regex("""filepos\s*=\s*["']?(\d+)["']?""", RegexOption.IGNORE_CASE)
                .find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val inner = decodeBasicEntities(m.groupValues[2].replace(Regex("""<[^>]+>"""), "").trim())
                .ifEmpty { continue }
            val charIdx = bytePrefixToPlainCharCount(raw, charset, fp)
            byOffset.putIfAbsent(charIdx, inner)
        }
        return byOffset.entries.sortedBy { it.key }.map { (off, title) ->
            ImportedTocEntry(level = 1, title = title, sourceOffset = off)
        }
    }

    private fun bytePrefixToPlainCharCount(raw: ByteArray, charset: Charset, byteOff: Int): Int {
        val lim = byteOff.coerceIn(0, raw.size)
        val cut = when (charset.name().uppercase()) {
            "UTF-8" -> alignUtf8Boundary(raw, lim)
            else -> lim
        }
        val prefix = try {
            String(raw, 0, cut, charset)
        } catch (_: Exception) {
            String(raw, 0, cut.coerceAtMost(raw.size), StandardCharsets.UTF_8)
        }
        return stripHtmlLike(prefix).length
    }

    private fun alignUtf8Boundary(bytes: ByteArray, off: Int): Int {
        var o = off.coerceIn(0, bytes.size)
        while (o > 0 && (bytes[o - 1].toInt() and 0xC0) == 0x80) o--
        return o
    }

    private fun decodeBasicEntities(s: String): String =
        s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: it.value }
            .replace(Regex("&#x([0-9a-fA-F]+);", RegexOption.IGNORE_CASE)) {
                it.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: it.value
            }

    /** 从给定的 record0 中读取指定类型的 EXTH uint32 字段。 */
    private fun readExthUint32(record0: ByteArray, wantedType: Int): Int? {
        if (record0.size < 0x100 || !byteMatch(record0, 16, MOBI_MAGIC)) return null
        val mobiLen = readUInt32BEAsInt(record0, 20)
        val exthFlags = readUInt32BEAsInt(record0, 0x80)
        if (exthFlags and 0x40 == 0) return null
        val exthPos = 16 + mobiLen
        if (exthPos + 12 > record0.size) return null
        if (!byteMatch(record0, exthPos, byteArrayOf(0x45, 0x58, 0x54, 0x48))) return null
        val exthLen = readUInt32BEAsInt(record0, exthPos + 4)
        val exthEnd = (exthPos + exthLen).coerceAtMost(record0.size)
        var p = exthPos + 12
        while (p + 8 <= exthEnd) {
            val typ = readUInt32BEAsInt(record0, p)
            val len = readUInt32BEAsInt(record0, p + 4)
            if (len < 8 || p + len > exthEnd) break
            if (typ == wantedType && len >= 12) {
                return readUInt32BEAsInt(record0, p + 8)
            }
            p += len
        }
        return null
    }

    private fun mobiCharset(record0: ByteArray): Charset {
        if (record0.size < 0x20) return StandardCharsets.UTF_8
        val enc = readUInt32BEAsInt(record0, 0x1c)
        return when (enc) {
            65001 -> StandardCharsets.UTF_8
            1252 -> Charset.forName("windows-1252")
            else -> StandardCharsets.UTF_8
        }
    }

    private fun sliceBetween(bytes: ByteArray, a: Int, b: Int): ByteArray? {
        if (a < 0 || b > bytes.size || b < a) return null
        return bytes.copyOfRange(a, b)
    }

    private fun sliceRecord(bytes: ByteArray, starts: IntArray, idx: Int): ByteArray? {
        if (idx < 0 || idx >= starts.size) return null
        val a = starts[idx]
        val b = if (idx + 1 < starts.size) starts[idx + 1] else bytes.size
        return sliceBetween(bytes, a, b)
    }

    private fun concatUncompressed(
        bytes: ByteArray,
        starts: IntArray,
        from: Int,
        to: Int,
        textLength: Int
    ): ByteArray? {
        val out = java.io.ByteArrayOutputStream(textLength.coerceAtMost(512 * 1024).coerceAtLeast(4096))
        for (i in from..to) {
            val a = starts.getOrNull(i) ?: return null
            val b = starts.getOrNull(i + 1) ?: bytes.size
            val rec = sliceBetween(bytes, a, b) ?: return null
            out.write(rec)
            if (out.size() >= textLength) break
        }
        val arr = out.toByteArray()
        return arr.copyOf(arr.size.coerceAtMost(textLength).coerceAtLeast(0))
    }

    private fun concatPalmdoc(
        bytes: ByteArray,
        starts: IntArray,
        from: Int,
        to: Int,
        textLength: Int
    ): ByteArray? {
        val out = java.io.ByteArrayOutputStream(textLength.coerceAtMost(512 * 1024).coerceAtLeast(4096))
        for (i in from..to) {
            val a = starts.getOrNull(i) ?: return null
            val b = starts.getOrNull(i + 1) ?: bytes.size
            val rec = sliceBetween(bytes, a, b) ?: return null
            out.write(PalmDocDecompressor.decompress(rec))
            if (out.size() >= textLength) break
        }
        val arr = out.toByteArray()
        return arr.copyOf(arr.size.coerceAtMost(textLength).coerceAtLeast(0))
    }

    private fun decodeMobiBytes(raw: ByteArray, charset: Charset): String {
        return try {
            String(raw, charset)
        } catch (_: Exception) {
            String(raw, StandardCharsets.UTF_8)
        }
    }

    private fun stripHtmlLike(s: String): String {
        val noScript = s.replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), "")
        val noTags = noScript.replace(Regex("<[^>]+>"), "\n")
        return noTags.replace(Regex("[ \t]+\r?\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun byteMatch(buf: ByteArray, at: Int, sig: ByteArray): Boolean {
        if (at + sig.size > buf.size) return false
        for (i in sig.indices) {
            if (buf[at + i] != sig[i]) return false
        }
        return true
    }

    private fun readUInt16BE(b: ByteArray, o: Int): Int {
        if (o + 2 > b.size) return 0
        return ((b[o].toInt() and 0xff) shl 8) or (b[o + 1].toInt() and 0xff)
    }

    private fun readUInt32BEAsInt(b: ByteArray, o: Int): Int {
        if (o + 4 > b.size) return 0
        return ((b[o].toInt() and 0xff) shl 24) or
            ((b[o + 1].toInt() and 0xff) shl 16) or
            ((b[o + 2].toInt() and 0xff) shl 8) or
            (b[o + 3].toInt() and 0xff)
    }

    private fun readUInt32BEUnsigned(b: ByteArray, o: Int): Long {
        if (o + 4 > b.size) return 0L
        return ((b[o].toLong() and 0xff) shl 24) or
            ((b[o + 1].toLong() and 0xff) shl 16) or
            ((b[o + 2].toLong() and 0xff) shl 8) or
            (b[o + 3].toLong() and 0xff)
    }
}
