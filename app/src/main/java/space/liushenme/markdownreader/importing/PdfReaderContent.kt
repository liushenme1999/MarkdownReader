package space.liushenme.markdownreader.importing

import android.content.Context
import space.liushenme.markdownreader.R

/**
 * PDF 导入正文的规范化与分页（阅读器侧复用）。
 */
object PdfReaderContent {

    /**
     * 页图横向铺满：仅用百分比宽度，勿写死像素 width/height（否则右侧会留白）。
     * 真实像素写在 [data-w]/[data-h]，阅读器用来算页高，避免打开时扫全部文件头。
     */
    private const val PDF_PAGE_IMG_STYLE =
        "display:block;width:100%;height:auto;margin:0;padding:0;border:0"

    private val LEGACY_PAGE_HEADING = Regex("""##\s*第\s*\d+\s*页\s*\n+""")

    private val PAGE_IMG_TAG =
        Regex(
            """<img\b[^>]*\bsrc=["'][^"']*pdf_page_\d+\.(?:png|jpe?g)[^"']*["'][^>]*/?>""",
            RegexOption.IGNORE_CASE,
        )

    private val IMG_SRC = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val IMG_DATA_W = Regex("""data-w=["'](\d+)["']""", RegexOption.IGNORE_CASE)
    private val IMG_DATA_H = Regex("""data-h=["'](\d+)["']""", RegexOption.IGNORE_CASE)
    private val PAGE_ASSET_NAME = Regex("""pdf_page_\d+\.(?:png|jpe?g)""", RegexOption.IGNORE_CASE)

    data class PageImageRef(
        val sourceOffset: Int,
        val src: String,
        val width: Int = 0,
        val height: Int = 0,
    )

    fun buildPageImgTag(src: String, width: Int = 0, height: Int = 0): String {
        val dims = if (width > 0 && height > 0) {
            """ data-w="$width" data-h="$height""""
        } else {
            ""
        }
        return """<img src="${escapeAttr(src)}" width="100%"$dims style="$PDF_PAGE_IMG_STYLE"/>"""
    }

    /** 去掉旧版导入残留的「## 第 N 页」标题行，并将页图标签改为 100% 宽（保留 data-w/h）。 */
    fun sanitizeStoredBody(body: String): String =
        normalizePageImageTags(body.replace(LEGACY_PAGE_HEADING, ""))

    fun normalizePageImageTags(body: String): String =
        PAGE_IMG_TAG.replace(body) { match ->
            val src = IMG_SRC.find(match.value)?.groupValues?.getOrNull(1)
                ?: return@replace match.value
            val width = IMG_DATA_W.find(match.value)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val height = IMG_DATA_H.find(match.value)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            buildPageImgTag(src, width, height)
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

    /** 书签列表展示用：记录页码，避免页图占位符变成乱码。 */
    fun bookmarkPreview(context: Context, body: String, sourceOffset: Int): String {
        val page = pageIndexForSourceOffset(body, sourceOffset) + 1
        return context.getString(R.string.pdf_page_title, page)
    }

    fun pageCount(body: String): Int = pageImageMatches(sanitizeStoredBody(body)).count()

    /** 正文是否含 PDF 页图（用于恢复后补识别，避免 importFormat 丢失时按普通 Markdown 打开）。 */
    fun looksLikePdfBody(body: String): Boolean = pageCount(body) > 0

    /** 图片 destination 是否为 PDF 栅格页（单击不应进图表预览）。 */
    fun isPageImageDestination(destination: String?): Boolean {
        val d = destination.orEmpty()
        return d.contains("pdf_page_", ignoreCase = true)
    }

    fun referencedPageAssetNames(body: String): List<String> {
        val names = linkedSetOf<String>()
        PAGE_ASSET_NAME.findAll(body).forEach { names += it.value }
        return names.toList()
    }

    /** 页图在正文中的源码起点与 src（已 sanitize）。 */
    fun pageImageSources(body: String): List<Pair<Int, String>> =
        pageImageEntries(body).map { it.sourceOffset to it.src }

    fun pageImageEntries(body: String): List<PageImageRef> {
        val sanitized = sanitizeStoredBody(body)
        return pageImageMatches(sanitized).mapNotNull { match ->
            val src = IMG_SRC.find(match.value)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
            val width = IMG_DATA_W.find(match.value)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val height = IMG_DATA_H.find(match.value)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            PageImageRef(
                sourceOffset = match.range.first,
                src = src,
                width = width,
                height = height,
            )
        }
    }

    private fun pageImageMatches(sanitized: String) =
        PAGE_IMG_TAG.findAll(sanitized).toList()

    private fun escapeAttr(s: String): String =
        s.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
