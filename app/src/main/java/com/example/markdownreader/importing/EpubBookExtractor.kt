package com.example.markdownreader.importing

import androidx.core.text.HtmlCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

private const val MAX_EPUB_BODY_CHARS = 8 * 1024 * 1024

/**
 * EPUB：按 spine 合并正文，正文产出 **Markdown**（h1-h6 / 列表 / 引用 / 表格 / 强调 / 链接 / 图片占位）。
 * 内嵌图片字节随 [ExtractedBookText.assets] 一并带出，src 改写为 `book-asset://<id>` 占位，
 * 由上层在落盘阶段替换为最终 file:// 路径。
 *
 * 目录：NCX 或 EPUB3 nav，sourceOffset 对齐到合并后 Markdown 字符下标（spineStarts 记录每个
 * spine 文档在合并 Markdown 中的起点，spine 内的 #fragment 锚点统一映射到 spine 起点）。
 */
internal object EpubBookExtractor {

    fun extract(zipPath: File): ExtractedBookText? {
        return ZipFile(zipPath).use { zip ->
            val containerEntry = zip.entries().asSequence()
                .firstOrNull { it.name.equals("META-INF/container.xml", ignoreCase = true) }
                ?: return null
            val containerXml = zip.getInputStream(containerEntry).use { it.readBytes().decodeToString() }
            val opfPath = parseContainerRootfile(containerXml) ?: return null
            val opfEntry = zip.entries().asSequence()
                .firstOrNull { it.name.equals(opfPath, ignoreCase = true) }
                ?: return null
            val opfXml = zip.getInputStream(opfEntry).use { it.readBytes().decodeToString() }
            val opfDir = opfPath.substringBeforeLast('/', "")
            val opf = parseOpfExtended(opfXml) ?: return null
            val spineStarts = linkedMapOf<String, Int>()
            val assets = linkedMapOf<String, ByteArray>()
            val sb = StringBuilder()
            for (id in opf.spineIds) {
                val href = opf.manifestHref[id] ?: continue
                val pathOnly = href.substringBefore('#').trim()
                if (pathOnly.isEmpty()) continue
                val entryPath = normalizeZipPath(resolveRelativePath(opfDir, pathOnly))
                val ent = zip.entries().asSequence()
                    .firstOrNull { normalizeZipPath(it.name) == entryPath }
                    ?: continue
                val raw = zip.getInputStream(ent).use { it.readBytes() }
                val charset = sniffXmlCharset(raw) ?: StandardCharsets.UTF_8
                val html = try {
                    String(raw, charset)
                } catch (_: Exception) {
                    String(raw, StandardCharsets.UTF_8)
                }
                val spineDir = entryPath.substringBeforeLast('/', "")
                val converted = HtmlToMarkdownConverter.convert(html)
                val markdown = rewriteImageSrcs(converted.markdown, converted.imageSources, spineDir, zip, assets)
                if (markdown.isBlank()) continue
                if (!spineStarts.containsKey(entryPath)) {
                    spineStarts[entryPath] = sb.length
                }
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append(markdown.trim())
                if (sb.length >= MAX_EPUB_BODY_CHARS) break
            }
            val rawBody = sb.toString()
            if (rawBody.isBlank()) return null
            val body = rawBody.take(MAX_EPUB_BODY_CHARS)
            val toc = buildToc(zip, opf, opfDir, spineStarts, body.length)
            val coverBytes = findEpubCoverBytes(zip, opfDir, opf, opfXml)
            ExtractedBookText(
                body = body,
                toc = toc,
                coverImageBytes = coverBytes,
                assets = assets
            )
        }
    }

    /**
     * 把 Markdown 里的图片相对路径全部改写成「带尺寸的 HTML `<img>` 标签」，src 用 `book-asset://<id>`
     * 占位，并把对应 zip 资源读到 assets 表。
     *
     * 之所以一定要换成 `<img width=".." height="..">`：见 [ImageAssetUtils] 注释——预声明尺寸
     * 能让 TextView 首次 measure 时就给图片占位预留高度，避免后续异步加载完成时的整段 relayout。
     */
    private fun rewriteImageSrcs(
        markdown: String,
        sources: List<String>,
        spineDir: String,
        zip: ZipFile,
        assets: MutableMap<String, ByteArray>
    ): String {
        var result = markdown
        for (src in sources.distinct()) {
            if (src.isBlank()) continue
            if (src.startsWith("data:", ignoreCase = true)) continue
            if (src.startsWith("http://", true) || src.startsWith("https://", true)) continue
            val pathOnly = src.substringBefore('#').trim()
            if (pathOnly.isEmpty()) continue
            val entryPath = normalizeZipPath(resolveRelativePath(spineDir, pathOnly))
            val ent = zip.entries().asSequence()
                .firstOrNull { normalizeZipPath(it.name) == entryPath }
                ?: continue
            val bytes = runCatching { zip.getInputStream(ent).use { it.readBytes() } }
                .getOrNull() ?: continue
            if (bytes.isEmpty() || bytes.size > MAX_COVER_BYTES) continue
            val assetId = assetIdFromPath(entryPath)
            assets.putIfAbsent(assetId, bytes)
            val (w, h) = ImageAssetUtils.decodeImageSize(bytes) ?: (null to null)
            result = replaceMarkdownImageWithHtmlImg(result, src, assetId, w, h)
        }
        // 兜底：把剩余 data:/外链等不支持的图片整段降级成 `[图片]`，避免 Markwon 抛 scheme 异常
        return stripUnsupportedImageRefs(result)
    }

    /**
     * 把所有 `![alt](src)` 整段替换为 `<img src="book-asset://id" alt=".." width=".." height="..">`。
     * 同时兼容 src 被 URL-encode 过的写法（空格 → %20、括号 → %28/%29）。
     */
    private fun replaceMarkdownImageWithHtmlImg(
        markdown: String,
        originalSrc: String,
        assetId: String,
        width: Int?,
        height: Int?
    ): String {
        val encodedSrc = originalSrc.replace(" ", "%20").replace("(", "%28").replace(")", "%29")
        val candidates = if (encodedSrc != originalSrc) listOf(originalSrc, encodedSrc) else listOf(originalSrc)
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

    private fun assetIdFromPath(path: String): String {
        // 用 hash 避免文件名冲突 / 包含奇怪字符；保留扩展名让 Markwon 能按 MIME 推断
        val ext = path.substringAfterLast('.', "").lowercase().take(8)
        val safe = path.lowercase()
        val hash = safe.hashCode().toUInt().toString(16).padStart(8, '0')
        return if (ext.isNotBlank()) "$hash.$ext" else hash
    }

    private const val MAX_COVER_BYTES = 8 * 1024 * 1024

    private fun findEpubCoverBytes(
        zip: ZipFile,
        opfDir: String,
        opf: OpfExtended,
        opfXml: String
    ): ByteArray? {
        val hrefs = linkedSetOf<String>()
        for ((id, props) in opf.propertiesById) {
            if (props.split(Regex("\\s+")).any { it.equals("cover-image", ignoreCase = true) }) {
                opf.manifestHref[id]?.let { hrefs += it }
            }
        }
        val metaId = Regex(
            """<meta\s+[^>]*name\s*=\s*["']cover["'][^>]*content\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        ).find(opfXml)?.groupValues?.get(1)?.trim()
            ?: Regex(
                """<meta\s+[^>]*content\s*=\s*["']([^"']+)["'][^>]*name\s*=\s*["']cover["']""",
                RegexOption.IGNORE_CASE
            ).find(opfXml)?.groupValues?.get(1)?.trim()
        if (!metaId.isNullOrBlank()) {
            opf.manifestHref[metaId]?.let { hrefs += it }
        }
        Regex(
            """<reference\s+[^>]*type\s*=\s*["']cover["'][^>]*href\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        ).find(opfXml)?.groupValues?.get(1)?.trim()?.let { hrefs += it }
        Regex(
            """<reference\s+[^>]*href\s*=\s*["']([^"']+)["'][^>]*type\s*=\s*["']cover["']""",
            RegexOption.IGNORE_CASE
        ).find(opfXml)?.groupValues?.get(1)?.trim()?.let { hrefs += it }

        for (rel in hrefs) {
            val pathOnly = rel.substringBefore('#').trim()
            if (pathOnly.isEmpty()) continue
            val entryPath = normalizeZipPath(resolveRelativePath(opfDir, pathOnly))
            val ent = zip.entries().asSequence()
                .firstOrNull { normalizeZipPath(it.name) == entryPath }
                ?: continue
            val bytes = zip.getInputStream(ent).use { it.readBytes() }
            if (bytes.isEmpty() || bytes.size > MAX_COVER_BYTES) continue
            if (isEpubCoverImageMagic(bytes)) return bytes
        }
        return null
    }

    private fun isEpubCoverImageMagic(b: ByteArray): Boolean {
        if (b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()) return true
        if (b.size >= 8 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte() &&
            b[2] == 0x4E.toByte() && b[3] == 0x47.toByte()
        ) return true
        return false
    }

    private fun normalizeZipPath(p: String): String =
        p.replace('\\', '/').trimStart('/').lowercase()

    private fun buildToc(
        zip: ZipFile,
        opf: OpfExtended,
        opfDir: String,
        spineStarts: Map<String, Int>,
        bodyLen: Int
    ): List<ImportedTocEntry> {
        val navHref = opf.navManifestHref()
        if (navHref != null) {
            val navPath = normalizeZipPath(resolveRelativePath(opfDir, navHref))
            val ent = zip.entries().asSequence().firstOrNull { normalizeZipPath(it.name) == navPath }
            if (ent != null) {
                val xml = zip.getInputStream(ent).use { it.readBytes().decodeToString() }
                val fromNav = parseNavDocumentToc(xml, opfDir, navHref.substringBeforeLast('/'), spineStarts)
                if (fromNav.isNotEmpty()) return clampToc(fromNav, bodyLen)
            }
        }
        val ncxHref = opf.ncxManifestHref()
        if (ncxHref != null) {
            val ncxPath = normalizeZipPath(resolveRelativePath(opfDir, ncxHref))
            val ent = zip.entries().asSequence().firstOrNull { normalizeZipPath(it.name) == ncxPath }
            if (ent != null) {
                val ncxXml = zip.getInputStream(ent).use { it.readBytes().decodeToString() }
                val ncxDir = ncxHref.substringBeforeLast('/', "")
                val fromNcx = parseNcxToc(ncxXml, ncxDir, spineStarts)
                if (fromNcx.isNotEmpty()) return clampToc(fromNcx, bodyLen)
            }
        }
        return emptyList()
    }

    private fun clampToc(entries: List<ImportedTocEntry>, bodyLen: Int): List<ImportedTocEntry> =
        entries.filter { it.sourceOffset in 0 until bodyLen }

    private fun parseContainerRootfile(xml: String): String? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val p = factory.newPullParser()
            p.setInput(xml.reader())
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val name = p.name?.lowercase() ?: ""
                    if (name == "rootfile") {
                        val path = p.getAttributeValue(null, "full-path")
                        if (!path.isNullOrBlank()) return path.replace('\\', '/')
                    }
                }
                event = p.next()
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private data class OpfExtended(
        val manifestHref: Map<String, String>,
        val manifestMedia: Map<String, String>,
        val propertiesById: Map<String, String>,
        val spineIds: List<String>,
        val spineTocId: String?
    ) {
        fun ncxManifestHref(): String? {
            spineTocId?.let { tid ->
                manifestHref[tid]?.let { return it }
            }
            return manifestMedia.entries.firstOrNull { (_, m) ->
                m.contains("ncx", ignoreCase = true)
            }?.let { manifestHref[it.key] }
        }

        fun navManifestHref(): String? {
            return propertiesById.entries.firstOrNull { (_, props) ->
                props.split(Regex("\\s+")).any { it.equals("nav", ignoreCase = true) }
            }?.let { manifestHref[it.key] }
        }
    }

    private fun parseOpfExtended(xml: String): OpfExtended? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val p = factory.newPullParser()
            p.setInput(xml.reader())
            val manifestHref = linkedMapOf<String, String>()
            val manifestMedia = linkedMapOf<String, String>()
            val propertiesById = linkedMapOf<String, String>()
            val spine = mutableListOf<String>()
            var spineTocId: String? = null
            var inManifest = false
            var inSpine = false
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val rawName = p.name ?: ""
                    val name = rawName.substringAfter(':').lowercase()
                    when {
                        name == "manifest" -> inManifest = true
                        name == "spine" -> {
                            inSpine = true
                            spineTocId = p.getAttributeValue(null, "toc")
                                ?: p.getAttributeValue("http://www.idpf.org/2007/opf", "toc")
                        }
                        inManifest && name == "item" -> {
                            val id = p.getAttributeValue(null, "id") ?: continue
                            val href = p.getAttributeValue(null, "href") ?: continue
                            manifestHref[id] = href.replace('\\', '/')
                            p.getAttributeValue(null, "media-type")?.let { manifestMedia[id] = it }
                            p.getAttributeValue(null, "properties")
                                ?: p.getAttributeValue("http://www.idpf.org/2007/opf", "properties")
                                ?.let { propertiesById[id] = it }
                        }
                        inSpine && name == "itemref" -> {
                            val idref = p.getAttributeValue(null, "idref")
                                ?: p.getAttributeValue("http://www.idpf.org/2007/opf", "idref")
                            if (!idref.isNullOrBlank()) spine.add(idref)
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    val name = (p.name ?: "").substringAfter(':').lowercase()
                    if (name == "manifest") inManifest = false
                    if (name == "spine") inSpine = false
                }
                event = p.next()
            }
            if (manifestHref.isEmpty() || spine.isEmpty()) null
            else OpfExtended(manifestHref, manifestMedia, propertiesById, spine, spineTocId)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseNcxToc(
        ncxXml: String,
        ncxDir: String,
        spineStarts: Map<String, Int>
    ): List<ImportedTocEntry> {
        val out = mutableListOf<ImportedTocEntry>()
        return try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val p = factory.newPullParser()
            p.setInput(ncxXml.reader())
            val titleStack = ArrayDeque<StringBuilder>()
            var inNavLabel = false
            var inNavLabelText = false
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val name = (p.name ?: "").substringAfter(':').lowercase()
                    when (name) {
                        "navpoint" -> titleStack.addLast(StringBuilder())
                        "navlabel" -> inNavLabel = true
                        "text" -> if (inNavLabel) inNavLabelText = true
                        "content" -> {
                            val src = p.getAttributeValue(null, "src") ?: ""
                            val pathOnly = src.substringBefore('#').trim()
                            if (pathOnly.isNotEmpty() && titleStack.isNotEmpty()) {
                                val key = normalizeZipPath(resolveRelativePath(ncxDir, pathOnly))
                                val off = spineStarts[key] ?: spineStarts.entries
                                    .firstOrNull { (k, _) -> k.endsWith("/$key") }
                                    ?.value
                                if (off != null) {
                                    val title = titleStack.last().toString().trim()
                                        .ifEmpty { key.substringAfterLast('/') }
                                    if (title.isNotEmpty()) {
                                        out += ImportedTocEntry(
                                            level = titleStack.size.coerceIn(1, 6),
                                            title = title,
                                            sourceOffset = off
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    val name = (p.name ?: "").substringAfter(':').lowercase()
                    when (name) {
                        "navpoint" -> if (titleStack.isNotEmpty()) titleStack.removeLast()
                        "navlabel" -> {
                            inNavLabel = false
                            inNavLabelText = false
                        }
                        "text" -> inNavLabelText = false
                    }
                } else if (event == XmlPullParser.TEXT) {
                    if (inNavLabelText && titleStack.isNotEmpty()) {
                        titleStack.last().append(p.text)
                    }
                }
                event = p.next()
            }
            out.distinctBy { it.sourceOffset to it.title }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractNavInnerHtml(xhtml: String): String? {
        val ordered = listOf(
            Regex("""(?is)<nav[^>]*epub:type\s*=\s*["']toc["'][^>]*>(.*?)</nav>"""),
            Regex("""(?is)<nav[^>]*type\s*=\s*["']toc["'][^>]*>(.*?)</nav>"""),
            Regex("""(?is)<nav[^>]*role\s*=\s*["']doc-toc["'][^>]*>(.*?)</nav>""")
        )
        for (re in ordered) {
            re.find(xhtml)?.let { return it.groupValues[1] }
        }
        return Regex("""(?is)<nav[^>]*>(.*?)</nav>""").find(xhtml)?.groupValues?.get(1)
    }

    private fun parseNavDocumentToc(
        xhtml: String,
        opfDir: String,
        navDir: String,
        spineStarts: Map<String, Int>
    ): List<ImportedTocEntry> {
        val inner = extractNavInnerHtml(xhtml) ?: return emptyList()
        val out = mutableListOf<ImportedTocEntry>()
        val re = Regex(
            """<a[^>]+href\s*=\s*["']([^"']+)["'][^>]*>([\s\S]*?)</a>""",
            RegexOption.IGNORE_CASE
        )
        val baseDir = navDir.ifEmpty { opfDir }
        for (m in re.findAll(inner)) {
            val href = m.groupValues[1].trim()
            val titleRaw = m.groupValues[2]
            val title = HtmlCompat.fromHtml(
                titleRaw.replace("\n", "<br/>"),
                HtmlCompat.FROM_HTML_MODE_LEGACY
            ).toString().trim()
            if (title.isEmpty()) continue
            val pathOnly = href.substringBefore('#').trim()
            if (pathOnly.isEmpty()) continue
            val key = normalizeZipPath(resolveRelativePath(baseDir, pathOnly))
            val off = spineStarts[key] ?: spineStarts.entries
                .firstOrNull { (k, _) -> k.endsWith("/$key") }
                ?.value
                ?: continue
            out += ImportedTocEntry(level = 1, title = title, sourceOffset = off)
        }
        return out.distinctBy { it.sourceOffset to it.title }
    }

    private fun resolveRelativePath(opfDir: String, href: String): String {
        val base = opfDir.trimEnd('/')
        val combined = if (base.isEmpty()) href else "$base/$href"
        val parts = combined.split('/').filter { it.isNotEmpty() && it != "." }
        val stack = ArrayDeque<String>()
        for (part in parts) {
            when (part) {
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun sniffXmlCharset(bytes: ByteArray): Charset? {
        val head = bytes.decodeToString(0, bytes.size.coerceAtMost(200)).lowercase()
        val m = Regex("encoding\\s*=\\s*[\"']([^\"']+)[\"']").find(head)
        val name = m?.groupValues?.get(1) ?: return null
        return try {
            Charset.forName(name)
        } catch (_: Exception) {
            null
        }
    }

}
