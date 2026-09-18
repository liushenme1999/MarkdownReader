package space.liushenme.markdownreader.ui.screens.reader

import space.liushenme.markdownreader.importing.ParsedBookStorage
import space.liushenme.markdownreader.importing.PdfReaderContent
import java.io.File

/**
 * PDF 阅读页目录：把正文里的页图解析成可独立绘制的文件列表。
 * 进度用 [PdfViewportAnchor]（页码 + 页内比例），并可编回现有 `position` 字段。
 */
internal data class PdfPageRef(
    val index: Int,
    val path: String,
    val sourceOffset: Int,
    val assetName: String,
)

/** 视口顶对应的页内位置。以后划词可在此扩展页内归一化坐标。 */
internal data class PdfViewportAnchor(
    val pageIndex: Int,
    val fractionInPage: Float,
)

/**
 * 以后划词/命中用的页内归一化坐标口（第一期不接 UI）。
 * [nx]/[ny] 相对页图，范围 0..1。
 */
@Suppress("unused")
internal data class PdfPageHit(
    val pageIndex: Int,
    val nx: Float,
    val ny: Float,
)

internal object PdfPageCatalog {

    private val PAGE_FILE_NAME = Regex("""pdf_page_(\d+)\.png""", RegexOption.IGNORE_CASE)

    fun parse(body: String, bundleDir: File): List<PdfPageRef> {
        val assetsDir = File(bundleDir, ParsedBookStorage.ASSETS_DIR)
        val fromBody = PdfReaderContent.pageImageSources(body).mapIndexedNotNull { index, (offset, src) ->
            val file = resolvePageFile(src, assetsDir) ?: return@mapIndexedNotNull null
            if (!file.isFile) return@mapIndexedNotNull null
            PdfPageRef(
                index = index,
                path = file.absolutePath,
                sourceOffset = offset,
                assetName = file.name,
            )
        }
        if (fromBody.isNotEmpty()) return fromBody
        return scanAssetPages(assetsDir)
    }

    internal fun scanAssetPages(assetsDir: File): List<PdfPageRef> {
        if (!assetsDir.isDirectory) return emptyList()
        val files = assetsDir.listFiles { _, name ->
            PAGE_FILE_NAME.containsMatchIn(name)
        }?.filter { it.isFile }?.sortedBy { pageIndexFromName(it.name) } ?: return emptyList()
        return files.mapIndexed { index, file ->
            PdfPageRef(
                index = index,
                path = file.absolutePath,
                sourceOffset = index,
                assetName = file.name,
            )
        }
    }

    private fun pageIndexFromName(name: String): Int =
        PAGE_FILE_NAME.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE

    fun encodeProgress(
        pages: List<PdfPageRef>,
        pageIndex: Int,
        fractionInPage: Float,
        contentLength: Int,
    ): Int {
        if (pages.isEmpty() || contentLength <= 0) return 0
        val index = pageIndex.coerceIn(0, pages.lastIndex)
        val start = pages[index].sourceOffset.coerceIn(0, contentLength)
        val end = pages.getOrNull(index + 1)?.sourceOffset?.coerceIn(0, contentLength)
            ?: contentLength
        val span = (end - start).coerceAtLeast(1)
        val frac = fractionInPage.coerceIn(0f, 1f)
        return (start + ((span - 1) * frac).toInt()).coerceIn(0, (contentLength - 1).coerceAtLeast(0))
    }

    fun decodeProgress(
        pages: List<PdfPageRef>,
        charPos: Int,
        contentLength: Int,
    ): PdfViewportAnchor {
        if (pages.isEmpty()) return PdfViewportAnchor(0, 0f)
        val pos = charPos.coerceIn(0, contentLength.coerceAtLeast(0))
        val index = pages.indexOfLast { it.sourceOffset <= pos }.coerceAtLeast(0)
        val start = pages[index].sourceOffset
        val end = pages.getOrNull(index + 1)?.sourceOffset ?: contentLength.coerceAtLeast(start + 1)
        val span = (end - start).coerceAtLeast(1)
        val fraction = ((pos - start).toFloat() / span).coerceIn(0f, 1f)
        return PdfViewportAnchor(index, fraction)
    }

    internal fun resolvePageFile(src: String, assetsDir: File): File? {
        val trimmed = src.trim()
        if (trimmed.isEmpty()) return null
        val byScheme = when {
            trimmed.startsWith("file://", ignoreCase = true) -> {
                File(trimmed.substring(7))
            }
            trimmed.startsWith("book-asset://", ignoreCase = true) -> {
                File(assetsDir, trimmed.substringAfter("://"))
            }
            else -> File(assetsDir, trimmed.substringAfterLast('/'))
        }
        if (byScheme.isFile) return byScheme
        val name = trimmed.substringAfterLast('/').substringBefore('?')
        val fallback = File(assetsDir, name)
        return fallback.takeIf { it.isFile }
    }
}
