package space.liushenme.markdownreader.markdown

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ReplacementSpan
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.utils.SpanUtils
import kotlin.math.max
import kotlin.math.min

/**
 * 围栏/缩进代码块窗口：正文只占一个 `\uFFFC`。
 * 顶部语言标签 + 复制按钮；关闭换行且超宽时底部画横向滚动条。
 */
internal class ReaderScrollableCodeBlockSpan(
    private val theme: MarkwonTheme,
    private val background: Drawable,
    private val padH: Int,
    private val padV: Int,
    content: CharSequence,
    val rawCode: String,
    languageInfo: String?,
    private val wrapEnabled: Boolean,
    private val density: Float,
    private val isDark: Boolean,
) : ReplacementSpan() {

    private val content: CharSequence = normalizeContent(content)
    val languageLabel: String = ReaderCodeBlockLanguage.label(languageInfo)

    var scrollX: Int = 0
        private set

    private var scrollXF: Float = 0f
    private var maxLineWidth: Int = 0
    var lastViewportWidth: Int = 0
        private set
    private var cachedLayout: StaticLayout? = null
    private var cachedTextSize: Float = -1f
    private var cachedColor: Int = 0
    private var cachedWrapWidth: Int = -1

    private val scrollBarH = (3f * density + 0.5f).toInt().coerceAtLeast(2)
    private val scrollBarGap = (6f * density + 0.5f).toInt()
    private val copyIconSize = 16f * density
    private val copyHitW = (44f * density).toInt().coerceAtLeast(1)
    private val chromeColor = if (isDark) 0xFF9AA6B8.toInt() else 0xFF5C6B7A.toInt()
    private val dividerColor = if (isDark) 0xFF536179.toInt() else 0xFFC8D3E0.toInt()
    private val trackColor = if (isDark) 0x66536179 else 0x66C8D3E0
    private val thumbColor = if (isDark) 0xFF8B9BB0.toInt() else 0xFF7A8B9C.toInt()

    private var drawnLeft = 0f
    private var drawnTop = 0f
    private var copyLeft = 0f
    private var copyTop = 0f
    private var copyRight = 0f
    private var copyBottom = 0f

    var selectionStart: Int = -1
        private set
    var selectionEnd: Int = -1
        private set
    var selectionFillColor: Int = 0x663A7AFE.toInt()

    private val highlightRanges = mutableListOf<CodeBlockHighlightRange>()
    private val selectionFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val highlightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val highlightStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    fun codeLength(): Int = content.length

    fun hasSelection(): Boolean =
        selectionStart >= 0 && selectionEnd > selectionStart && selectionStart < content.length

    fun setSelection(start: Int, end: Int) {
        val from = min(start, end).coerceIn(0, content.length)
        val to = max(start, end).coerceIn(0, content.length)
        selectionStart = from
        selectionEnd = to
    }

    fun clearSelection() {
        selectionStart = -1
        selectionEnd = -1
    }

    fun selectedText(): String {
        if (!hasSelection()) return ""
        return content.subSequence(selectionStart, selectionEnd).toString()
    }

    fun setHighlightRanges(ranges: List<CodeBlockHighlightRange>) {
        highlightRanges.clear()
        highlightRanges.addAll(ranges)
    }

    fun clearHighlightRanges() {
        highlightRanges.clear()
    }

    fun addHighlightRange(range: CodeBlockHighlightRange) {
        highlightRanges.removeAll { it.start == range.start && it.end == range.end }
        highlightRanges += range
    }

    fun removeHighlightRangeMatching(snippet: String) {
        if (snippet.isEmpty()) return
        highlightRanges.removeAll { range ->
            range.end <= content.length &&
                content.subSequence(range.start, range.end).toString() == snippet
        }
    }

    fun indexOfSnippet(snippet: String): Int {
        if (snippet.isEmpty()) return -1
        val inRaw = rawCode.indexOf(snippet)
        if (inRaw >= 0 && inRaw + snippet.length <= content.length) return inRaw
        return content.toString().indexOf(snippet)
    }

    /**
     * [contentX]/[contentY] 为 TextView layout 坐标（已扣 padding、加上 scrollY）。
     * [originLeft]/[originTop] 为 ReplacementSpan 所在行的左上角。
     */
    fun offsetAt(
        contentX: Float,
        contentY: Float,
        paint: Paint,
        originLeft: Float,
        originTop: Float,
        clampToContent: Boolean = false,
    ): Int? {
        val layout = ensureLayout(paint)
        if (layout.lineCount <= 0) return null
        val headerH = headerHeight(paint)
        val localX = contentX - originLeft - padH + scrollX
        var localY = contentY - originTop - headerH - padV
        if (clampToContent) {
            val maxY = (layout.height - 1).toFloat().coerceAtLeast(0f)
            localY = localY.coerceIn(0f, maxY)
        } else if (localY < 0f) {
            return null
        }
        val line = layout.getLineForVertical(localY.toInt().coerceAtLeast(0))
            .coerceIn(0, layout.lineCount - 1)
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line)
        val lastOnLine = (lineEnd - 1).coerceAtLeast(lineStart)
        var offset = layout.getOffsetForHorizontal(line, localX)
            .coerceIn(lineStart, lastOnLine)
        if (offset > lineStart &&
            layout.getParagraphDirection(line) == Layout.DIR_LEFT_TO_RIGHT &&
            layout.getPrimaryHorizontal(offset) > localX
        ) {
            offset = (offset - 1).coerceAtLeast(lineStart)
        }
        return offset.coerceIn(0, (content.length - 1).coerceAtLeast(0))
    }

    /** 是否点在代码块内部选区高亮上（含少量容错）。 */
    fun isSelectionHit(
        contentX: Float,
        contentY: Float,
        paint: Paint,
        originLeft: Float,
        originTop: Float,
        slop: Float = 8f,
    ): Boolean {
        if (!hasSelection()) return false
        val layout = ensureLayout(paint)
        val headerH = headerHeight(paint)
        val localX = contentX - originLeft - padH + scrollX
        val localY = contentY - originTop - headerH - padV
        if (localY < 0f) return false
        var hit = false
        forEachLineRange(layout, selectionStart, selectionEnd) { left, right, baseline ->
            val top = baseline - layout.paint.textSize
            val bottom = baseline + layout.paint.descent()
            if (localX >= left - slop && localX <= right + slop &&
                localY >= top - slop && localY <= bottom + slop
            ) {
                hit = true
            }
        }
        return hit
    }

    /**
     * 内部选区在 TextView layout 坐标中的包围盒（已含代码块横滑，未含 TextView padding/scrollY）。
     * 供 Floating ActionMode 锚在选中文字旁，而不是整块代码框顶部。
     */
    fun selectionBoundsInLayout(
        paint: Paint,
        originLeft: Float,
        originTop: Float,
    ): RectF? {
        if (!hasSelection()) return null
        val layout = ensureLayout(paint)
        if (layout.lineCount <= 0) return null
        val headerH = headerHeight(paint)
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        var any = false
        forEachLineRange(layout, selectionStart, selectionEnd) { rangeLeft, rangeRight, baseline ->
            val lineTop = baseline - layout.paint.textSize
            val lineBottom = baseline + layout.paint.descent()
            val contentL = originLeft + padH - scrollX + rangeLeft
            val contentR = originLeft + padH - scrollX + rangeRight
            val contentT = originTop + headerH + padV + lineTop
            val contentB = originTop + headerH + padV + lineBottom
            left = min(left, contentL)
            right = max(right, contentR)
            top = min(top, contentT)
            bottom = max(bottom, contentB)
            any = true
        }
        if (!any) {
            val start = handlePositionInLayout(
                selectionStart,
                isEnd = false,
                paint = paint,
                originLeft = originLeft,
                originTop = originTop,
            ) ?: return null
            val end = handlePositionInLayout(
                selectionEnd,
                isEnd = true,
                paint = paint,
                originLeft = originLeft,
                originTop = originTop,
            ) ?: start
            left = min(start.first, end.first)
            right = max(start.first, end.first)
            val glyphTop = min(start.second, end.second) - layout.paint.textSize
            top = glyphTop
            bottom = max(start.second, end.second)
        }
        return RectF(
            left,
            top,
            right.coerceAtLeast(left + 1f),
            bottom.coerceAtLeast(top + 1f),
        )
    }

    fun handlePositionInLayout(
        offset: Int,
        isEnd: Boolean,
        paint: Paint,
        originLeft: Float,
        originTop: Float,
    ): Pair<Float, Float>? {
        val layout = ensureLayout(paint)
        if (layout.lineCount <= 0 || content.isEmpty()) return null
        val safe = offset.coerceIn(0, content.length)
        val lineOffset = if (isEnd && safe > 0) safe - 1 else safe.coerceAtMost(content.length - 1)
        val line = layout.getLineForOffset(lineOffset.coerceIn(0, content.length - 1))
        val x = when {
            isEnd && safe >= layout.getLineEnd(line) -> layout.getLineRight(line)
            isEnd -> layout.getPrimaryHorizontal(safe.coerceIn(0, content.length))
            else -> layout.getPrimaryHorizontal(safe.coerceIn(0, content.length))
        }
        val fontBottom = layout.getLineBaseline(line) + layout.paint.fontMetricsInt.descent
        val y = min(fontBottom, layout.getLineBottom(line)).toFloat()
        val contentX = originLeft + padH - scrollX + x
        val contentY = originTop + headerHeight(paint) + padV + y
        return contentX to contentY
    }

    fun headerHeightPx(paint: Paint): Int = headerHeight(paint)

    fun highlightRangesForTest(): List<CodeBlockHighlightRange> = highlightRanges.toList()

    fun maxScrollX(): Int {
        if (wrapEnabled) return 0
        val viewport = resolvedViewportWidth()
        return (maxLineWidth - viewport).coerceAtLeast(0)
    }

    /** 触摸开始时用 TextView 实宽刷新视口，避免 getSize 阶段 viewport=1 导致不能滑。 */
    fun prepareForTouch(paint: Paint, viewportPx: Int) {
        if (viewportPx > 1) {
            lastViewportWidth = viewportPx
        }
        ensureLayout(paint)
        syncScrollX()
    }

    private fun resolvedViewportWidth(): Int {
        val fromDraw = lastViewportWidth
        val fromSettings = ReaderCodeBlockSettings.viewportWidthPx
        return when {
            fromDraw > 1 -> fromDraw
            fromSettings > 1 -> fromSettings
            else -> 1
        }
    }

    fun canScrollHorizontally(): Boolean = maxScrollX() > 0

    fun scrollBy(dx: Float): Boolean {
        val max = maxScrollX()
        if (max <= 0) {
            scrollX = 0
            scrollXF = 0f
            return false
        }
        val next = (scrollXF + dx).coerceIn(0f, max.toFloat())
        if (kotlin.math.abs(next - scrollXF) < 0.001f) return false
        scrollXF = next
        scrollX = scrollXF.toInt()
        return true
    }

    /**
     * 拖选区时手指贴近代码框左右边缘（或已拖出可视区）应横滑的增量。
     * 向右为正（露出更靠右的代码），向左为负。
     */
    fun selectionEdgeScrollDelta(
        contentX: Float,
        originLeft: Float,
        viewportWidth: Int,
    ): Float {
        if (!canScrollHorizontally()) return 0f
        val left = originLeft
        val right = originLeft + viewportWidth.coerceAtLeast(1)
        val edge = SELECTION_EDGE_DP * density
        val maxStep = SELECTION_EDGE_MAX_STEP_DP * density
        return when {
            contentX >= right - edge -> {
                val t = ((contentX - (right - edge)) / edge).coerceIn(0.25f, 2f)
                maxStep * t
            }
            contentX <= left + edge -> {
                val t = (((left + edge) - contentX) / edge).coerceIn(0.25f, 2f)
                -maxStep * t
            }
            else -> 0f
        }
    }

    fun scrollBy(dx: Int): Boolean = scrollBy(dx.toFloat())

    fun isOnCopyButton(contentX: Float, contentY: Float): Boolean {
        if (copyRight <= copyLeft) return false
        return contentX >= copyLeft &&
            contentX <= copyRight &&
            contentY >= copyTop &&
            contentY <= copyBottom
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val layout = ensureLayout(paint)
        if (fm != null) {
            fm.ascent = -chromeHeight(layout, paint)
            fm.descent = 0
            fm.top = fm.ascent
            fm.bottom = 0
            ReaderTableSpacing.compensateLineSpacing(
                fm,
                ReaderTableSpacing.lineSpacingMultiplier,
            )
        }
        val viewport = resolvedViewportWidth()
        lastViewportWidth = viewport
        syncScrollX()
        return viewport
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val viewport = SpanUtils.width(canvas, text).takeIf { it > 0 }
            ?: lastViewportWidth.takeIf { it > 1 }
            ?: ReaderCodeBlockSettings.viewportWidthPx
        if (viewport > 0) {
            lastViewportWidth = viewport
        }
        val layout = ensureLayout(paint)
        syncScrollX()

        val clipRight = x + lastViewportWidth
        drawnLeft = x
        drawnTop = top.toFloat()
        val bg = background.mutate()
        bg.setBounds(x.toInt(), top, clipRight.toInt().coerceAtLeast(x.toInt() + 1), bottom)
        bg.draw(canvas)

        val headerH = headerHeight(paint)
        drawHeader(canvas, x, top.toFloat(), clipRight, headerH, paint)
        drawCopyHitRect(x, top.toFloat(), clipRight, headerH)

        val codeTop = top + headerH + padV
        val barSpace = if (canScrollHorizontally()) scrollBarGap + scrollBarH else 0
        val codeBottom = (bottom - barSpace).toFloat()

        val save = canvas.save()
        try {
            canvas.clipRect(x, codeTop.toFloat(), clipRight, codeBottom)
            canvas.translate(x + padH - scrollX, codeTop.toFloat())
            drawHighlightRanges(canvas, layout)
            if (hasSelection()) {
                drawSelectionRange(canvas, layout, selectionStart, selectionEnd, selectionFillColor)
            }
            layout.draw(canvas)
        } finally {
            canvas.restoreToCount(save)
        }

        if (canScrollHorizontally()) {
            drawScrollBar(canvas, x, clipRight, bottom.toFloat())
        }
    }

    private fun headerHeight(paint: Paint): Int {
        val labelPaint = languagePaint(paint)
        val textH = labelPaint.descent() - labelPaint.ascent()
        val pad = 8f * density
        return (textH + pad * 2).toInt().coerceAtLeast((copyIconSize + pad * 2).toInt())
    }

    private fun languagePaint(basePaint: Paint): TextPaint {
        return TextPaint(basePaint).also { labelPaint ->
            theme.applyCodeBlockTextStyle(labelPaint)
            labelPaint.color = chromeColor
            labelPaint.typeface = android.graphics.Typeface.SANS_SERIF
            labelPaint.isFakeBoldText = true
        }
    }

    private fun chromeHeight(layout: StaticLayout, paint: Paint): Int {
        val bar = if (!wrapEnabled && maxLineWidth > lastViewportWidth.coerceAtLeast(1)) {
            scrollBarGap + scrollBarH
        } else {
            0
        }
        return headerHeight(paint) + padV + layout.height + padV + bar
    }

    private fun drawHeader(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        headerH: Int,
        basePaint: Paint,
    ) {
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = dividerColor
            strokeWidth = (1f * density).coerceAtLeast(1f)
        }
        val dividerY = top + headerH
        canvas.drawLine(left, dividerY, right, dividerY, divider)

        val labelPaint = languagePaint(basePaint)
        copyLeft = right - copyHitW
        copyTop = top
        copyRight = right
        copyBottom = top + headerH

        if (languageLabel.isNotEmpty()) {
            val maxLangW = (copyLeft - left - padH * 2).coerceAtLeast(0f)
            val shown = TextUtils.ellipsize(
                languageLabel,
                labelPaint,
                maxLangW,
                TextUtils.TruncateAt.END,
            )
            val baseline = top + headerH / 2f - (labelPaint.ascent() + labelPaint.descent()) / 2f
            canvas.drawText(shown, 0, shown.length, left + padH, baseline, labelPaint)
        }

        drawCopyIcon(
            canvas,
            (copyLeft + copyRight) / 2f,
            (copyTop + copyBottom) / 2f,
            copyIconSize,
            chromeColor,
        )
    }

    private fun drawCopyHitRect(blockLeft: Float, blockTop: Float, blockRight: Float, headerH: Int) {
        if (copyRight <= copyLeft) {
            copyLeft = blockRight - copyHitW
            copyTop = blockTop
            copyRight = blockRight
            copyBottom = blockTop + headerH
        }
        if (copyLeft < blockLeft) copyLeft = blockLeft
    }

    private fun drawCopyIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = (1.4f * density).coerceAtLeast(1f)
        }
        val back = RectF(
            cx - size * 0.12f,
            cy - size * 0.32f,
            cx + size * 0.32f,
            cy + size * 0.12f,
        )
        val front = RectF(
            cx - size * 0.32f,
            cy - size * 0.12f,
            cx + size * 0.12f,
            cy + size * 0.32f,
        )
        val radius = 2f * density
        canvas.drawRoundRect(back, radius, radius, stroke)
        canvas.drawRoundRect(front, radius, radius, stroke)
    }

    private fun drawScrollBar(canvas: Canvas, left: Float, right: Float, bottom: Float) {
        val trackL = left + padH
        val trackR = right - padH
        val trackW = (trackR - trackL).coerceAtLeast(1f)
        val trackT = bottom - scrollBarGap - scrollBarH
        val trackB = trackT + scrollBarH
        val radius = scrollBarH / 2f
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor }
        canvas.drawRoundRect(RectF(trackL, trackT, trackR, trackB), radius, radius, trackPaint)

        val viewport = lastViewportWidth.coerceAtLeast(1)
        val contentW = maxLineWidth.coerceAtLeast(viewport + 1)
        val thumbW = (trackW * viewport / contentW).coerceIn(16f * density, trackW)
        val max = maxScrollX().coerceAtLeast(1)
        val thumbL = trackL + (trackW - thumbW) * (scrollX.toFloat() / max)
        val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = thumbColor }
        canvas.drawRoundRect(RectF(thumbL, trackT, thumbL + thumbW, trackB), radius, radius, thumbPaint)
    }

    private fun ensureLayout(paint: Paint): StaticLayout {
        val codePaint = TextPaint(paint)
        theme.applyCodeBlockTextStyle(codePaint)
        val viewport = when {
            lastViewportWidth > 1 -> lastViewportWidth
            ReaderCodeBlockSettings.viewportWidthPx > 1 -> ReaderCodeBlockSettings.viewportWidthPx
            else -> 0
        }
        val wrapWidth = (viewport - padH * 2).coerceAtLeast(80)
        val cached = cachedLayout
        if (cached != null &&
            cachedTextSize == codePaint.textSize &&
            cachedColor == codePaint.color &&
            cachedWrapWidth == if (wrapEnabled) wrapWidth else -1
        ) {
            return cached
        }
        val innerW = max(1, measureUnwrappedWidth(content, codePaint))
        val layoutWidth = if (wrapEnabled) wrapWidth else innerW + 1
        val layout = StaticLayout.Builder
            .obtain(content, 0, content.length, codePaint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .build()
        cachedLayout = layout
        cachedTextSize = codePaint.textSize
        cachedColor = codePaint.color
        cachedWrapWidth = if (wrapEnabled) wrapWidth else -1
        var renderedMax = innerW.toFloat()
        for (i in 0 until layout.lineCount) {
            renderedMax = max(renderedMax, layout.getLineWidth(i))
        }
        maxLineWidth = if (wrapEnabled) {
            viewport.coerceAtLeast(1)
        } else {
            (renderedMax + padH * 2).toInt().coerceAtLeast(innerW + padH * 2)
        }
        return layout
    }

    private fun syncScrollX() {
        val max = maxScrollX()
        scrollXF = scrollXF.coerceIn(0f, max.toFloat())
        scrollX = scrollXF.toInt()
    }

    private fun measureUnwrappedWidth(text: CharSequence, paint: TextPaint): Int {
        var maxW = 0f
        var i = 0
        val n = text.length
        while (i < n) {
            var j = i
            while (j < n && text[j] != '\n') j++
            maxW = max(maxW, paint.measureText(text, i, j))
            i = if (j < n) j + 1 else j
        }
        return maxW.toInt().coerceAtLeast(1)
    }

    private fun drawHighlightRanges(canvas: Canvas, layout: StaticLayout) {
        for (range in highlightRanges) {
            val start = range.start.coerceIn(0, content.length)
            val end = range.end.coerceIn(0, content.length)
            if (end <= start) continue
            if (range.underline || range.wavy) {
                highlightStrokePaint.color = range.color
                highlightStrokePaint.strokeWidth = (1.15f * density).coerceIn(2.5f, 4.5f)
                forEachLineRange(layout, start, end) { left, right, baseline ->
                    val y = baseline + (5.5f * density).coerceIn(8f, 22f)
                    canvas.drawLine(left, y, right, y, highlightStrokePaint)
                }
            } else {
                highlightFillPaint.color = range.color
                forEachLineRange(layout, start, end) { left, right, baseline ->
                    val top = baseline - layout.paint.textSize
                    canvas.drawRect(left, top, right, baseline + layout.paint.descent(), highlightFillPaint)
                }
            }
        }
    }

    private fun drawSelectionRange(
        canvas: Canvas,
        layout: StaticLayout,
        start: Int,
        end: Int,
        color: Int,
    ) {
        selectionFillPaint.color = color
        forEachLineRange(layout, start, end) { left, right, baseline ->
            val top = baseline - layout.paint.textSize
            canvas.drawRect(left, top, right, baseline + layout.paint.descent(), selectionFillPaint)
        }
    }

    private inline fun forEachLineRange(
        layout: StaticLayout,
        start: Int,
        end: Int,
        block: (left: Float, right: Float, baseline: Int) -> Unit,
    ) {
        val last = (end - 1).coerceAtLeast(start)
        val firstLine = layout.getLineForOffset(start)
        val lastLine = layout.getLineForOffset(last)
        for (line in firstLine..lastLine) {
            val lineStart = layout.getLineStart(line)
            val lineEnd = layout.getLineEnd(line)
            val drawStart = max(start, lineStart)
            val drawEnd = min(end, lineEnd)
            if (drawStart >= drawEnd) continue
            val xStart = layout.getPrimaryHorizontal(drawStart)
            val xEnd = if (drawEnd >= lineEnd) {
                layout.getLineRight(line)
            } else {
                layout.getPrimaryHorizontal(drawEnd)
            }
            val left = min(xStart, xEnd)
            val right = max(xStart, xEnd)
            if (right <= left + 1f) continue
            block(left, right, layout.getLineBaseline(line))
        }
    }

    private fun normalizeContent(code: CharSequence): CharSequence {
        var end = code.length
        while (end > 0 && (code[end - 1] == '\n' || code[end - 1] == '\r')) end--
        if (end <= 0) return " "
        return if (end == code.length) code else code.subSequence(0, end)
    }

    companion object {
        internal const val SELECTION_EDGE_DP = 36f
        internal const val SELECTION_EDGE_MAX_STEP_DP = 10f
    }
}

internal data class CodeBlockHighlightRange(
    val start: Int,
    val end: Int,
    val color: Int,
    val underline: Boolean = false,
    val wavy: Boolean = false,
)
