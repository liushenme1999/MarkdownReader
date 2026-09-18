package space.liushenme.markdownreader.ui.screens.reader

import kotlin.math.max
import kotlin.math.roundToInt

/** 页间缝：连续阅读贴合，无空隙。 */
internal const val PDF_PAGE_GAP_PX = 0f

internal fun pdfPageDisplayWidth(viewportWidth: Float, scale: Float): Float =
    (viewportWidth * clampPdfZoom(scale)).coerceAtLeast(1f)

internal fun pdfPageHeightPx(
    viewportWidth: Float,
    scale: Float,
    intrinsicWidth: Int,
    intrinsicHeight: Int,
): Float {
    val w = pdfPageDisplayWidth(viewportWidth, scale)
    val iw = intrinsicWidth.coerceAtLeast(1)
    val ih = intrinsicHeight.coerceAtLeast(1)
    return w * ih / iw
}

internal fun pdfPageHeights(
    viewportWidth: Float,
    scale: Float,
    intrinsicSizes: List<Pair<Int, Int>>,
): FloatArray = FloatArray(intrinsicSizes.size) { i ->
    val (w, h) = intrinsicSizes[i]
    pdfPageHeightPx(viewportWidth, scale, w, h)
}

internal fun pdfScaledPageGap(scale: Float): Float = PDF_PAGE_GAP_PX * clampPdfZoom(scale)

/**
 * 缩放时让焦点下的文档点留在手指下（UIScrollView.zoomScale 同一套）。
 * [scrollY] / [focalY] 与当前倍率下的内容像素一致。
 */
internal fun pdfDocumentScrollAfterZoom(
    scrollY: Float,
    focalY: Float,
    oldScale: Float,
    newScale: Float,
): Float {
    val from = oldScale.coerceAtLeast(0.01f)
    return (scrollY + focalY) * (newScale / from) - focalY
}

internal fun pdfDocumentMaxPanX(viewportWidth: Float, scale: Float): Float {
    if (!pdfZoomIsActive(scale) || viewportWidth <= 0f) return 0f
    return (pdfPageDisplayWidth(viewportWidth, scale) - viewportWidth).coerceAtLeast(0f)
}

/**
 * 页图从视口左边铺开，放大后只允许在纸张范围内平移：
 * panX = 0 时左缘贴齐视口左边，panX = -max 时右缘贴齐视口右边，两侧都不会露出底色。
 */
internal fun clampPdfDocumentPanX(panX: Float, viewportWidth: Float, scale: Float): Float {
    val max = pdfDocumentMaxPanX(viewportWidth, scale)
    return panX.coerceIn(-max, 0f)
}

/** 缩放时让焦点下的文档横向位置留在手指下，再交给 [clampPdfDocumentPanX] 夹紧。 */
internal fun pdfDocumentPanXAfterZoom(
    panX: Float,
    focalX: Float,
    oldScale: Float,
    newScale: Float,
): Float {
    val from = oldScale.coerceAtLeast(0.01f)
    return focalX - (focalX - panX) * (newScale / from)
}

/** 捏合过程中的图层倍率，使 `docScale * visual` 落在 [PDF_ZOOM_MIN, PDF_ZOOM_MAX]。 */
internal fun pdfPinchVisualScale(docScale: Float, proposedPinch: Float): Float {
    val doc = docScale.coerceAtLeast(0.01f)
    val nextDoc = clampPdfZoom(doc * proposedPinch)
    return nextDoc / doc
}

internal fun pdfDocumentScrollY(
    firstVisibleIndex: Int,
    firstVisibleOffset: Int,
    pageHeights: FloatArray,
    gap: Float,
): Float {
    var y = 0f
    val last = pageHeights.lastIndex
    val until = firstVisibleIndex.coerceIn(0, (last + 1).coerceAtLeast(0))
    for (i in 0 until until) {
        y += pageHeights.getOrElse(i) { 0f } + gap
    }
    return y + firstVisibleOffset.coerceAtLeast(0)
}

internal fun pdfScrollToOffset(
    scrollY: Float,
    pageHeights: FloatArray,
    gap: Float,
): Pair<Int, Int> {
    if (pageHeights.isEmpty()) return 0 to 0
    var remaining = scrollY.coerceAtLeast(0f)
    for (i in pageHeights.indices) {
        val block = pageHeights[i] + if (i < pageHeights.lastIndex) gap else 0f
        if (remaining <= block || i == pageHeights.lastIndex) {
            val maxOffset = pageHeights[i].roundToInt().coerceAtLeast(0)
            val offset = remaining.roundToInt().coerceIn(0, maxOffset)
            return i to offset
        }
        remaining -= block
    }
    return pageHeights.lastIndex to 0
}

internal fun pdfScrollYForPageFraction(
    pageIndex: Int,
    fractionInPage: Float,
    pageHeights: FloatArray,
    gap: Float,
): Float {
    if (pageHeights.isEmpty()) return 0f
    val index = pageIndex.coerceIn(0, pageHeights.lastIndex)
    var y = 0f
    for (i in 0 until index) {
        y += pageHeights[i] + gap
    }
    y += pageHeights[index] * fractionInPage.coerceIn(0f, 1f)
    return y
}

internal fun pdfAnchorAtScrollY(
    scrollY: Float,
    pageHeights: FloatArray,
    gap: Float,
): PdfViewportAnchor {
    if (pageHeights.isEmpty()) return PdfViewportAnchor(0, 0f)
    var remaining = scrollY.coerceAtLeast(0f)
    for (i in pageHeights.indices) {
        val height = pageHeights[i].coerceAtLeast(1f)
        val block = height + if (i < pageHeights.lastIndex) gap else 0f
        if (remaining <= block || i == pageHeights.lastIndex) {
            val inPage = (remaining / height).coerceIn(0f, 1f)
            return PdfViewportAnchor(i, inPage)
        }
        remaining -= block
    }
    return PdfViewportAnchor(pageHeights.lastIndex, 1f)
}

internal fun pdfVisiblePageIndices(
    scrollY: Float,
    viewportHeight: Float,
    pageHeights: FloatArray,
    gap: Float,
    extra: Int = 1,
): IntRange {
    if (pageHeights.isEmpty() || viewportHeight <= 0f) return IntRange.EMPTY
    val top = pdfAnchorAtScrollY(scrollY, pageHeights, gap).pageIndex
    val bottom = pdfAnchorAtScrollY(scrollY + viewportHeight, pageHeights, gap).pageIndex
    val start = (top - extra).coerceAtLeast(0)
    val end = (bottom + extra).coerceAtMost(pageHeights.lastIndex)
    return start..end
}

internal fun pdfDocumentContentHeight(pageHeights: FloatArray, gap: Float): Float {
    if (pageHeights.isEmpty()) return 0f
    var y = 0f
    for (i in pageHeights.indices) {
        y += pageHeights[i]
        if (i < pageHeights.lastIndex) y += gap
    }
    return y
}

internal fun pdfDocumentReachedEnd(
    scrollY: Float,
    viewportHeight: Float,
    pageHeights: FloatArray,
    gap: Float,
): Boolean {
    if (pageHeights.isEmpty()) return false
    val maxScroll = max(0f, pdfDocumentContentHeight(pageHeights, gap) - viewportHeight)
    return scrollY >= maxScroll - 2f
}

internal fun pdfDecodeWidthForScale(viewportWidth: Float, scale: Float, maxWidth: Int): Int {
    val raw = (viewportWidth * clampPdfZoom(scale)).toInt()
    return raw.coerceIn(1, maxWidth.coerceAtLeast(1))
}
