package space.liushenme.markdownreader.importing

import android.content.Context
import space.liushenme.markdownreader.R

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
     * 返回在 [sanitizeStoredBody] 后正文中的字符区间。
     */
    fun splitToPages(body: String): List<IntRange> {
        val sanitized = sanitizeStoredBody(body)
        val matches = pageImageMatches(sanitized)
        if (matches.isEmpty()) {
            val trimmed = sanitized.trim()
            val start = sanitized.indexOf(trimmed).coerceAtLeast(0)
            return listOf(start until (start + trimmed.length))
        }
        return matches.map { match ->
            match.range.first until (match.range.last + 1)
        }
    }

    fun tocEntriesFromBody(body: String, context: Context): List<ImportedTocEntry> {
        val sanitized = sanitizeStoredBody(body)
        return pageImageMatches(sanitized).mapIndexed { index, match ->
            ImportedTocEntry(
                level = 2,
                title = context.getString(R.string.pdf_page_title, index + 1),
                sourceOffset = match.range.first,
            )
        }
    }

    /** 根据正文中的源码下标定位 PDF 页码（0-based），与 [tocEntriesFromBody] 顺序一致。 */
    fun pageIndexForSourceOffset(body: String, sourceOffset: Int): Int {
        val matches = pageImageMatches(sanitizeStoredBody(body))
        if (matches.isEmpty()) return 0
        return matches.indexOfLast { it.range.first <= sourceOffset }.coerceAtLeast(0)
    }

    fun pageCount(body: String): Int = pageImageMatches(sanitizeStoredBody(body)).count()

    private fun pageImageMatches(sanitized: String) =
        PAGE_IMG_TAG.findAll(sanitized).toList()

    private fun escapeAttr(s: String): String =
        s.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
