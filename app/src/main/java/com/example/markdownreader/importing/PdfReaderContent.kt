package com.example.markdownreader.importing

/**
 * PDF 导入正文的规范化与分页（阅读器侧复用）。
 */
object PdfReaderContent {

    /**
     * 页图横向铺满：仅用百分比宽度，勿写死像素 width/height（否则右侧会留白）。
     * Markwon HtmlPlugin 支持 style / width 中的百分比。
     */
    private const val PDF_PAGE_IMG_STYLE =
        "display:block;width:100%;height:auto;margin:0;padding:0;border:0"

    private val LEGACY_PAGE_HEADING = Regex("""##\s*第\s*\d+\s*页\s*\n+""")

    private val PAGE_IMG_TAG =
        Regex("""<img\s[^>]*src=["'][^"']*pdf_page_\d+\.png[^"']*["'][^>]*/>""", RegexOption.IGNORE_CASE)

    private val IMG_SRC = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    fun buildPageImgTag(src: String, @Suppress("UNUSED_PARAMETER") width: Int, @Suppress("UNUSED_PARAMETER") height: Int): String =
        buildPageImgTag(src)

    fun buildPageImgTag(src: String): String =
        """<img src="${escapeAttr(src)}" width="100%" style="$PDF_PAGE_IMG_STYLE"/>"""

    /** 去掉旧版导入残留的「## 第 N 页」标题行，并将页图标签改为 100% 宽。 */
    fun sanitizeStoredBody(body: String): String =
        normalizePageImageTags(body.replace(LEGACY_PAGE_HEADING, ""))

    fun normalizePageImageTags(body: String): String =
        PAGE_IMG_TAG.replace(body) { match ->
            val src = IMG_SRC.find(match.value)?.groupValues?.getOrNull(1) ?: return@replace match.value
            buildPageImgTag(src)
        }

    /**
     * 横向翻页：每页仅含一张 PDF 页图（按 `<img … pdf_page_…>` 切分）。
     * 返回 (片段, 在全文中的起始下标)。
     */
    fun splitToPages(body: String): List<Pair<String, Int>> {
        val sanitized = sanitizeStoredBody(body)
        val matches = PAGE_IMG_TAG.findAll(sanitized).toList()
        if (matches.isEmpty()) return listOf(sanitized.trim() to 0)
        return matches.map { match ->
            match.value.trim() to match.range.first
        }
    }

    fun tocEntriesFromBody(body: String): List<ImportedTocEntry> {
        val sanitized = sanitizeStoredBody(body)
        return PAGE_IMG_TAG.findAll(sanitized).mapIndexed { index, match ->
            ImportedTocEntry(
                level = 2,
                title = "第 ${index + 1} 页",
                sourceOffset = match.range.first,
            )
        }.toList()
    }

    private fun escapeAttr(s: String): String =
        s.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
