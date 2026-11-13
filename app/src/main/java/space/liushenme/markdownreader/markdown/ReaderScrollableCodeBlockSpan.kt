package space.liushenme.markdownreader.markdown

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ReplacementSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LineHeightSpan
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.utils.SpanUtils
import kotlin.math.max
import kotlin.math.min

/** Top and bottom source offsets visible inside one full-screen code layout. */
internal data class CodeSourceViewport(
    val top: Int,
    val bottom: Int,
)

/**
 * 围栏/缩进代码块窗口：正文只占一个 `\uFFFC`。
 * 顶部语言标签 + 复制按钮；关闭换行且超宽时底部画横向滚动条。
 */
internal class ReaderScrollableCodeBlockSpan(
    private val theme: MarkwonTheme,
    internal val background: Drawable,
    internal val codeHighlightTheme: CodeHighlightTheme = CodeHighlightTheme.DEFAULT,
    internal val fullScreenCodeDocument: Boolean = false,
    private val padH: Int,
    private val padV: Int,
    content: CharSequence,
    rawCode: String,
    languageInfo: String?,
    private val wrapEnabled: Boolean,
    private val density: Float,
    initialSyntaxHighlightRanges: List<CodeSyntaxRange> = emptyList(),
) : ReplacementSpan(), LineHeightSpan {

    private val content: CharSequence = normalizeReaderCodeContent(content)
    val rawCode: String = normalizeReaderCodeContent(rawCode).toString()
    private val hasBidiText: Boolean = hasBidiDirection(content)
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
    private var cachedLineStarts: IntArray? = null
    private var cachedLineEnds: IntArray? = null
    private var cachedLineBaselines: IntArray? = null
    private var cachedLineStartXs: FloatArray? = null
    private var layoutGeneration: Int = 0
    private var syntaxGeneration: Int = 0
    private var styledSyntaxLayout: StyledSyntaxLayout? = null
    private var measuredHeight: Int = 0
    private var syntaxLineMap: IntArray? = null
    private var syntaxLineMapLayoutGeneration = -1
    private var syntaxLineMapSyntaxGeneration = -1

    private val scrollBarH = (3f * density + 0.5f).toInt().coerceAtLeast(2)
    private val scrollBarGap = (6f * density + 0.5f).toInt()
    private val copyIconSize = 16f * density
    private val copyHitW = (44f * density).toInt().coerceAtLeast(1)
    private val palette = codeHighlightTheme.palette
    private val chromeColor = palette.chrome
    private val dividerColor = palette.divider
    private val trackColor = palette.scrollTrack
    private val thumbColor = palette.scrollThumb

    private var drawnLeft = 0f
    private var drawnTop = 0f
    internal var lastDrawnHeightForTest = 0
    private var copyLeft = 0f
    private var copyTop = 0f
    private var copyRight = 0f
    private var copyBottom = 0f

    var selectionStart: Int = -1
        private set
    var selectionEnd: Int = -1
        private set
    var selectionFillColor: Int = palette.selection

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
    private val syntaxTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    @Volatile
    private var syntaxHighlightRanges: List<CodeSyntaxRange> = initialSyntaxHighlightRanges
    private var fullScreenRenderedByHost = false

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

    /**
     * Replaces the published syntax ranges atomically. Full-screen files compute these on a
     * background thread after the source is already visible; draw always reads one immutable list.
     */
    fun setSyntaxHighlightRanges(
        ranges: List<CodeSyntaxRange>,
        styledLayout: StyledSyntaxLayout? = null,
    ) {
        syntaxHighlightRanges = ranges
        syntaxGeneration++
        syntaxLineMap = null
        if (styledLayout != null && styledLayout.layoutGeneration == layoutGeneration) {
            styledSyntaxLayout = styledLayout
        } else {
            styledSyntaxLayout = null
        }
    }

    /**
     * Builds small layouts whose text owns the syntax foreground spans. The full file can contain
     * thousands of layout lines, and `StaticLayout.draw` always visits every line. Chunking keeps
     * each scroll frame to one or two short layouts while still reusing Android's shaped text runs.
     * This is intentionally called on the background render thread.
     */
    fun prepareStyledSyntaxLayout(
        basePaint: Paint,
        viewportPx: Int,
        ranges: List<CodeSyntaxRange>,
    ): StyledSyntaxLayout {
        val codePaint = TextPaint(basePaint)
        theme.applyCodeBlockTextStyle(codePaint)
        codePaint.color = palette.foreground
        val viewport = viewportPx.takeIf { it > 1 }
            ?: lastViewportWidth.takeIf { it > 1 }
            ?: ReaderCodeBlockSettings.viewportWidthPx
        val wrapWidth = (viewport - padH * 2).coerceAtLeast(80)
        val fullLayout = cachedLayout
            ?: error("full-screen code layout must be prewarmed before chunk highlighting")
        val lineStarts = cachedLineStarts
            ?: error("full-screen code line metrics are unavailable")
        val generation = layoutGeneration
        val unwrappedWidth = if (wrapEnabled) 0 else max(1, measureUnwrappedWidth(content, codePaint))
        val layoutWidth = if (wrapEnabled) wrapWidth else unwrappedWidth + 1
        // A wrapped Android layout line can begin in the middle of a source line. Chunking on
        // those lines makes a chunk start with an orphaned wrapped fragment, after which every
        // chunk's line offset drifts and scrolling eventually exposes unpainted vertical gaps.
        // Keep chunk boundaries on complete source lines, then map those boundaries back to the
        // full layout for positioning.
        val logicalLineStarts = sourceLineStarts(content)
        val chunks = ArrayList<StyledLayoutChunk>(
            (logicalLineStarts.size + STYLED_CHUNK_LINES - 1) / STYLED_CHUNK_LINES,
        )
        var firstLogicalLine = 0
        while (firstLogicalLine < logicalLineStarts.size) {
            val sourceStart = logicalLineStarts[firstLogicalLine]
            val firstLayoutLine = lineForOffset(lineStarts, sourceStart)
            var nextLogicalLine = firstLogicalLine + 1
            while (nextLogicalLine < logicalLineStarts.size &&
                lineForOffset(lineStarts, logicalLineStarts[nextLogicalLine]) - firstLayoutLine <
                STYLED_CHUNK_LINES
            ) {
                nextLogicalLine++
            }
            val sourceEnd = logicalLineStarts.getOrElse(nextLogicalLine) { content.length }
            var contentEnd = sourceEnd
            while (contentEnd > sourceStart &&
                (content[contentEnd - 1] == '\n' || content[contentEnd - 1] == '\r')
            ) {
                contentEnd--
            }
            val lastLayoutLine = lineForOffset(
                lineStarts,
                (contentEnd - 1).coerceAtLeast(sourceStart),
            )
            if (contentEnd > sourceStart) {
                val styledContent = SpannableString(content.subSequence(sourceStart, contentEnd))
                ranges.forEach { range ->
                    val start = (range.start - sourceStart).coerceIn(0, styledContent.length)
                    val end = (range.end - sourceStart).coerceIn(start, styledContent.length)
                    if (end > start) {
                        styledContent.setSpan(
                            ForegroundColorSpan(range.color),
                            start,
                            end,
                            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                }
                val chunkLayout = StaticLayout.Builder
                    .obtain(styledContent, 0, styledContent.length, codePaint, layoutWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .setLineSpacing(0f, 1f)
                    .build()
                chunks += StyledLayoutChunk(
                    firstLayoutLine = firstLayoutLine,
                    lastLayoutLine = lastLayoutLine,
                    sourceStart = sourceStart,
                    layout = chunkLayout,
                )
            }
            firstLogicalLine = nextLogicalLine
        }
        return StyledSyntaxLayout(
            chunks = chunks,
            ranges = ranges,
            layoutGeneration = generation,
            textSize = codePaint.textSize,
            color = codePaint.color,
            wrapWidth = if (wrapEnabled) wrapWidth else -1,
            maxLineWidth = if (wrapEnabled) viewport.coerceAtLeast(1) else unwrappedWidth + padH * 2,
        )
    }

    /**
     * Builds the expensive full-document [StaticLayout] while the code is still being prepared
     * off the main thread. The first measure/draw then hits [cachedLayout] instead of skipping
     * dozens of frames while laying out a large source file.
     */
    fun prewarmLayout(basePaint: Paint, viewportPx: Int) {
        if (viewportPx > 1) lastViewportWidth = viewportPx
        ensureLayout(basePaint)
    }

    /**
     * Full-screen documents are painted by [SafeReaderTextView.onDraw] instead of the TextView
     * text-line cache. This keeps vertical scrolling correct without reattaching the span on
     * every frame, which forced an expensive text relayout on MIUI devices.
     */
    fun renderThroughHostView() {
        if (fullScreenCodeDocument) fullScreenRenderedByHost = true
    }

    fun addHighlightRange(range: CodeBlockHighlightRange) {
        highlightRanges.removeAll {
            if (range.highlightId != 0L) it.highlightId == range.highlightId
            else it.highlightId == 0L && it.start == range.start && it.end == range.end
        }
        highlightRanges += range
    }

    fun hasHighlightId(highlightId: Long): Boolean =
        highlightRanges.any { it.highlightId == highlightId }

    fun replaceHighlightId(oldId: Long, newId: Long): Boolean {
        var changed = false
        for (index in highlightRanges.indices) {
            val range = highlightRanges[index]
            if (range.highlightId != oldId) continue
            highlightRanges[index] = range.copy(highlightId = newId)
            changed = true
        }
        return changed
    }

    fun removeHighlightRangesById(highlightId: Long): Boolean =
        highlightRanges.removeAll { it.highlightId == highlightId }

    fun removeHighlightRangeMatching(snippet: String) {
        if (snippet.isEmpty()) return
        highlightRanges.removeAll { range ->
            range.end <= content.length &&
                content.subSequence(range.start, range.end).toString() == snippet
        }
    }

    fun highlightRangeCovering(offset: Int): CodeBlockHighlightRange? =
        highlightRanges.firstOrNull { offset in it.start until it.end }

    fun indexOfSnippet(snippet: String): Int {
        return rangeOfSnippet(snippet)?.first ?: -1
    }

    /**
     * Finds the code range while tolerating renderer whitespace differences. The returned range
     * always uses the displayed content offsets, which are the offsets consumed by the span layout.
     */
    fun rangeOfSnippet(snippet: String): IntRange? {
        if (snippet.isEmpty()) return null
        val displayed = content.toString()
        displayed.indexOf(snippet).takeIf { it >= 0 }?.let { return it until it + snippet.length }
        rawCode.indexOf(snippet).takeIf { it >= 0 && it + snippet.length <= displayed.length }?.let {
            return it until it + snippet.length
        }
        val normalizedDisplayed = normalizedCodeWithOffsets(displayed)
        val normalizedSnippet = normalizeCodeForSearch(snippet)
        if (normalizedSnippet.isEmpty()) return null
        val normalizedStart = normalizedDisplayed.text.indexOf(normalizedSnippet)
        if (normalizedStart < 0) return null
        val start = normalizedDisplayed.offsets[normalizedStart]
        val endIndex = normalizedStart + normalizedSnippet.length - 1
        val end = (normalizedDisplayed.offsets.getOrNull(endIndex)?.plus(1) ?: start)
        return start until end
    }

    private data class NormalizedCode(val text: String, val offsets: IntArray)

    private fun normalizedCodeWithOffsets(value: String): NormalizedCode {
        val text = StringBuilder(value.length)
        val offsets = ArrayList<Int>(value.length)
        var pendingSpace = false
        value.forEachIndexed { index, ch ->
            if (ch.isWhitespace()) {
                pendingSpace = text.isNotEmpty()
            } else {
                if (pendingSpace && text.isNotEmpty()) {
                    text.append(' ')
                    offsets += index - 1
                }
                text.append(ch)
                offsets += index
                pendingSpace = false
            }
        }
        return NormalizedCode(text.toString(), offsets.toIntArray())
    }

    private fun normalizeCodeForSearch(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")

    /**
     * 代码行在这一整块 ReplacementSpan 里的纵向偏移。
     * 外层 TextView 把整块代码当成一行，只滚到行顶会停在代码开头。
     */
    fun offsetTopInLine(contentOffset: Int, paint: Paint): Int {
        if (content.isEmpty()) return 0
        val layout = ensureLayout(paint)
        val safe = contentOffset.coerceIn(0, (content.length - 1).coerceAtLeast(0))
        val line = layout.getLineForOffset(safe).coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))
        return headerHeight(paint) + padV + layout.getLineTop(line)
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

    /**
     * Maps a TextView layout-space Y coordinate to the first source character on that line.
     * Full-screen code documents deliberately have no language/copy header, so their viewport
     * top maps directly into the code outline.
     */
    fun sourceOffsetAtLayoutY(
        contentY: Float,
        originTop: Float,
        paint: Paint,
    ): Int? {
        val layout = ensureLayout(paint)
        if (layout.lineCount <= 0) return null
        val localY = contentY - originTop - headerHeight(paint) - padV
        val line = layout.getLineForVertical(localY.toInt().coerceAtLeast(0))
            .coerceIn(0, layout.lineCount - 1)
        return layout.getLineStart(line)
    }

    /**
     * Resolves the visible top and bottom source offsets with one layout lookup.
     *
     * Full-screen code documents are a single ReplacementSpan, so ReaderScreen cannot map
     * their viewport through the normal Markdown text offsets. Returning both offsets here
     * also avoids re-entering [ensureLayout] once per scroll consumer.
     */
    fun sourceViewportAtLayout(
        contentTopY: Float,
        contentBottomY: Float,
        originTop: Float,
        viewportPx: Int,
        paint: Paint,
    ): CodeSourceViewport? {
        if (viewportPx > 1) {
            lastViewportWidth = viewportPx
        }
        val layout = ensureLayout(paint)
        if (layout.lineCount <= 0) return null
        val header = headerHeight(paint)
        val topLocalY = contentTopY - originTop - header - padV
        val bottomLocalY = contentBottomY - originTop - header - padV
        val topLine = layout.getLineForVertical(topLocalY.toInt().coerceAtLeast(0))
            .coerceIn(0, layout.lineCount - 1)
        val bottomLine = layout.getLineForVertical(
            bottomLocalY.toInt().coerceAtLeast(layout.getLineTop(topLine)),
        ).coerceIn(topLine, layout.lineCount - 1)
        return CodeSourceViewport(
            top = layout.getLineStart(topLine),
            bottom = layout.getLineEnd(bottomLine).coerceAtMost(content.length),
        )
    }

    internal fun requiredHeightForTest(paint: Paint): Int = chromeHeight(ensureLayout(paint), paint)

    /**
     * The line containing a code card also contains its trailing newline. That newline's default
     * font descent is merged after [getSize], so metrics set there cannot describe the complete
     * row. Resolve the final row height after all span metrics have been collected instead.
     *
     * StaticLayout applies the TextView line-spacing multiplier after this callback, except for
     * a final line without a trailing newline. Compensate only for the case where that multiplier
     * will actually be applied; otherwise the card would be smaller than its own code layout.
     */
    override fun chooseHeight(
        text: CharSequence,
        start: Int,
        end: Int,
        spanstartv: Int,
        lineHeight: Int,
        fm: Paint.FontMetricsInt,
    ) {
        val desiredHeight = measuredHeight.takeIf { it > 0 } ?: return
        val spanned = text as? Spanned ?: return
        val codeStart = spanned.getSpanStart(this)
        if (codeStart < start || codeStart >= end) return
        val isLastLine = end == text.length &&
            (text.isEmpty() || (text[text.length - 1] != '\n' && text[text.length - 1] != '\r'))
        val multiplier = if (isLastLine) 1f else ReaderTableSpacing.lineSpacingMultiplier
        val metricsHeight = if (multiplier > 1.001f) {
            (desiredHeight / multiplier + 0.5f).toInt().coerceAtLeast(1)
        } else {
            desiredHeight
        }
        fm.ascent = -metricsHeight
        fm.descent = 0
        fm.top = fm.ascent
        fm.bottom = 0
    }

    fun highlightRangesForTest(): List<CodeBlockHighlightRange> = highlightRanges.toList()

    internal fun contentSliceForTest(start: Int, end: Int): String =
        content.subSequence(start.coerceAtLeast(0), end.coerceIn(0, content.length)).toString()

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
        if (fullScreenCodeDocument) return false
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
        val viewport = resolvedViewportWidth()
        lastViewportWidth = viewport
        syncScrollX()
        if (fm != null) {
            measuredHeight = chromeHeight(layout, paint)
            fm.ascent = -measuredHeight
            fm.descent = 0
            fm.top = fm.ascent
            fm.bottom = 0
        }
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
        if (fullScreenCodeDocument && fullScreenRenderedByHost) return
        val viewport = SpanUtils.width(canvas, text).takeIf { it > 0 }
            ?: lastViewportWidth.takeIf { it > 1 }
            ?: ReaderCodeBlockSettings.viewportWidthPx
        drawBlock(canvas, x, top, bottom, paint, viewport)
    }

    /**
     * Paints a full-screen document directly in the host View's display list. Unlike TextView's
     * cached TextLine display list, the host View is re-recorded after vertical scrolling, so the
     * visible source window follows scrollY without changing the span or re-laying out the text.
     */
    fun drawFullScreenByHost(
        canvas: Canvas,
        x: Float,
        top: Int,
        bottom: Int,
        paint: Paint,
        viewportPx: Int,
    ) {
        if (!fullScreenCodeDocument) return
        drawBlock(canvas, x, top, bottom, paint, viewportPx)
    }

    private fun drawBlock(
        canvas: Canvas,
        x: Float,
        top: Int,
        bottom: Int,
        paint: Paint,
        viewport: Int,
    ) {
        if (viewport > 0) {
            lastViewportWidth = viewport
        }
        val layout = ensureLayout(paint)
        syncScrollX()

        val clipRight = x + lastViewportWidth
        drawnLeft = x
        drawnTop = top.toFloat()
        lastDrawnHeightForTest = bottom - top
        val bg = background.mutate()
        bg.setBounds(x.toInt(), top, clipRight.toInt().coerceAtLeast(x.toInt() + 1), bottom)
        bg.draw(canvas)

        val headerH = headerHeight(paint)
        if (headerH > 0) {
            drawHeader(canvas, x, top.toFloat(), clipRight, headerH, paint)
            drawCopyHitRect(x, top.toFloat(), clipRight, headerH)
        } else {
            copyLeft = 0f
            copyTop = 0f
            copyRight = 0f
            copyBottom = 0f
        }

        val codeTop = top + headerH + padV
        val barSpace = if (canScrollHorizontally()) scrollBarGap + scrollBarH else 0
        val codeBottom = (bottom - barSpace).toFloat()

        val save = canvas.save()
        try {
            canvas.clipRect(x, codeTop.toFloat(), clipRight, codeBottom)
            val viewportHeight = ReaderCodeBlockSettings.viewportHeightPx
            val clip = canvas.clipBounds
            val visibleTopPx = if (fullScreenCodeDocument && viewportHeight > 0) {
                (ReaderCodeBlockSettings.viewportTopPx - codeTop)
                    .coerceIn(0, layout.height)
            } else {
                (clip.top - codeTop).coerceIn(0, layout.height)
            }
            val visibleBottomPx = if (fullScreenCodeDocument && viewportHeight > 0) {
                (ReaderCodeBlockSettings.viewportTopPx + viewportHeight - codeTop)
                    .coerceIn(visibleTopPx, layout.height)
            } else {
                (clip.bottom - codeTop).coerceIn(visibleTopPx, layout.height)
            }
            val overlaySave = canvas.save()
            try {
                canvas.translate(x + padH - scrollX, codeTop.toFloat())
                drawHighlightRanges(canvas, layout)
                if (hasSelection()) {
                    drawSelectionRange(
                        canvas,
                        layout,
                        selectionStart,
                        selectionEnd,
                        selectionFillColor,
                    )
                }
            } finally {
                canvas.restoreToCount(overlaySave)
            }

            val fallbackSave = canvas.save()
            try {
                canvas.translate(x + padH - scrollX, codeTop.toFloat())
                val styled = styledSyntaxLayout
                if (fullScreenCodeDocument && styled != null && !hasBidiText) {
                    drawStyledCodeLayout(
                        canvas = canvas,
                        layout = layout,
                        styled = styled,
                        visibleTopPx = visibleTopPx,
                        visibleBottomPx = visibleBottomPx,
                    )
                } else if (fullScreenCodeDocument && !hasBidiText) {
                    drawFullScreenCodeLayout(canvas, layout, visibleTopPx, visibleBottomPx)
                } else {
                    drawCodeLayout(canvas, layout, visibleTopPx, visibleBottomPx)
                    drawSyntaxHighlightRanges(
                        canvas = canvas,
                        layout = layout,
                        visibleTopPx = visibleTopPx,
                        visibleBottomPx = visibleBottomPx,
                    )
                }
            } finally {
                canvas.restoreToCount(fallbackSave)
            }
        } finally {
            canvas.restoreToCount(save)
        }

        if (canScrollHorizontally()) {
            drawScrollBar(canvas, x, clipRight, bottom.toFloat())
        }
    }

    private fun drawStyledCodeLayout(
        canvas: Canvas,
        layout: StaticLayout,
        styled: StyledSyntaxLayout,
        visibleTopPx: Int,
        visibleBottomPx: Int,
    ) {
        if (layout.lineCount <= 0 || styled.chunks.isEmpty()) return
        val firstVisibleLine = layout.getLineForVertical(visibleTopPx)
            .coerceIn(0, layout.lineCount - 1)
        val lastVisibleLine = layout.getLineForVertical(
            (visibleBottomPx - 1).coerceAtLeast(visibleTopPx),
        ).coerceIn(firstVisibleLine, layout.lineCount - 1)
        for (chunk in styled.chunks) {
            if (chunk.lastLayoutLine < firstVisibleLine) continue
            if (chunk.firstLayoutLine > lastVisibleLine) break
            val save = canvas.save()
            try {
                canvas.translate(0f, layout.getLineTop(chunk.firstLayoutLine).toFloat())
                chunk.layout.draw(canvas)
            } finally {
                canvas.restoreToCount(save)
            }
        }
    }

    private fun headerHeight(paint: Paint): Int {
        if (fullScreenCodeDocument) return 0
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
        drawHeaderBackground(canvas, left, top, right, headerH)
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

    private fun drawHeaderBackground(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        headerH: Int,
    ) {
        val safeLeft = left
        val safeRight = right.coerceAtLeast(safeLeft + 1f)
        val header = RectF(safeLeft, top, safeRight, top + headerH)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.headerBackground
        }
        if (fullScreenCodeDocument) {
            canvas.drawRect(header, headerPaint)
        } else {
            val radius = 10f * density
            val path = Path().apply {
                addRoundRect(
                    header,
                    floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f),
                    Path.Direction.CW,
                )
            }
            canvas.drawPath(path, headerPaint)
        }
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

    @Synchronized
    private fun ensureLayout(paint: Paint): StaticLayout {
        val codePaint = TextPaint(paint)
        theme.applyCodeBlockTextStyle(codePaint)
        codePaint.color = palette.foreground
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
        // Wrapped code only needs the viewport width. Measuring every source line here was a
        // large first-open cost and the result was discarded by maxLineWidth below.
        val unwrappedWidth = if (wrapEnabled) 0 else max(1, measureUnwrappedWidth(content, codePaint))
        val layoutWidth = if (wrapEnabled) wrapWidth else unwrappedWidth + 1
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
        cacheLineMetrics(layout)
        maxLineWidth = if (wrapEnabled) {
            viewport.coerceAtLeast(1)
        } else {
            unwrappedWidth + padH * 2
        }
        return layout
    }

    /**
     * Large source files reuse the same immutable layout for every scroll frame. Resolve the
     * layout's per-line geometry once instead of asking StaticLayout for baselines and horizontal
     * positions again for every visible line and syntax token.
     */
    private fun cacheLineMetrics(layout: StaticLayout) {
        if (!fullScreenCodeDocument || hasBidiText || layout.lineCount <= 0) {
            cachedLineStarts = null
            cachedLineEnds = null
            cachedLineBaselines = null
            cachedLineStartXs = null
            return
        }
        layoutGeneration++
        val lineCount = layout.lineCount
        val starts = IntArray(lineCount)
        val ends = IntArray(lineCount)
        val baselines = IntArray(lineCount)
        val startXs = FloatArray(lineCount)
        for (line in 0 until lineCount) {
            val start = layout.getLineStart(line)
            starts[line] = start
            ends[line] = layout.getLineEnd(line)
            baselines[line] = layout.getLineBaseline(line)
            startXs[line] = layout.getPrimaryHorizontal(start)
        }
        cachedLineStarts = starts
        cachedLineEnds = ends
        cachedLineBaselines = baselines
        cachedLineStartXs = startXs
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

    /**
     * [StaticLayout.draw] walks every line in the layout even when Canvas clips most of them.
     * Full-screen source files can contain tens of thousands of lines, so draw only the visible
     * rows. The original path remains as a correctness fallback for right-to-left/bidirectional
     * text, where Canvas.drawText would not reproduce StaticLayout's run ordering.
     */
    private fun drawCodeLayout(
        canvas: Canvas,
        layout: StaticLayout,
        visibleTopPx: Int,
        visibleBottomPx: Int,
    ) {
        if (!fullScreenCodeDocument || hasBidiText || layout.lineCount <= 0) {
            layout.draw(canvas)
            return
        }
        val firstLine = layout.getLineForVertical(visibleTopPx)
            .coerceIn(0, layout.lineCount - 1)
        val lastLine = layout.getLineForVertical(
            (visibleBottomPx - 1).coerceAtLeast(visibleTopPx),
        ).coerceIn(firstLine, layout.lineCount - 1)
        for (line in firstLine..lastLine) {
            drawVisibleLine(canvas, layout, line)
        }
    }

    /**
     * Paints each visible source line once. Gaps use the normal foreground paint and syntax
     * ranges switch paint in place, instead of painting the entire row and then overpainting
     * every colored token. The per-line range index keeps scrolling independent of file size.
     */
    private fun drawFullScreenCodeLayout(
        canvas: Canvas,
        layout: StaticLayout,
        visibleTopPx: Int,
        visibleBottomPx: Int,
    ) {
        if (layout.lineCount <= 0) return
        val firstLine = layout.getLineForVertical(visibleTopPx)
            .coerceIn(0, layout.lineCount - 1)
        val lastLine = layout.getLineForVertical(
            (visibleBottomPx - 1).coerceAtLeast(visibleTopPx),
        ).coerceIn(firstLine, layout.lineCount - 1)
        ensureSyntaxLineMap(layout)
        syntaxTextPaint.set(layout.paint)
        val lineStarts = cachedLineStarts
        val lineEnds = cachedLineEnds
        val lineBaselines = cachedLineBaselines
        val lineStartXs = cachedLineStartXs
        val lineMap = syntaxLineMap
        val ranges = syntaxHighlightRanges

        for (line in firstLine..lastLine) {
            val lineStart = lineStarts?.getOrNull(line) ?: layout.getLineStart(line)
            val lineEnd = lineEnds?.getOrNull(line) ?: layout.getLineEnd(line)
            if (lineStart >= lineEnd) continue
            val baseline = (lineBaselines?.getOrNull(line) ?: layout.getLineBaseline(line)).toFloat()
            val lineStartX = lineStartXs?.getOrNull(line) ?: layout.getPrimaryHorizontal(lineStart)
            drawFullScreenCodeLine(
                canvas = canvas,
                layout = layout,
                line = line,
                lineStart = lineStart,
                lineEnd = lineEnd,
                baseline = baseline,
                lineStartX = lineStartX,
                firstRangeIndex = lineMap?.getOrNull(line) ?: -1,
                ranges = ranges,
            )
        }
    }

    private fun drawFullScreenCodeLine(
        canvas: Canvas,
        layout: StaticLayout,
        line: Int,
        lineStart: Int,
        lineEnd: Int,
        baseline: Float,
        lineStartX: Float,
        firstRangeIndex: Int,
        ranges: List<CodeSyntaxRange>,
    ) {
        if (firstRangeIndex < 0) {
            drawCodeTextRange(
                canvas, layout, lineStart, lineEnd, baseline, lineStartX, layout.paint,
            )
            return
        }

        var cursor = lineStart
        var cursorX = lineStartX
        var rangeIndex = firstRangeIndex
        while (rangeIndex < ranges.size) {
            val range = ranges[rangeIndex]
            if (range.start >= lineEnd) break
            if (range.end <= cursor) {
                rangeIndex++
                continue
            }
            val tokenStart = max(range.start, lineStart).coerceAtLeast(cursor)
            val tokenEnd = min(range.end, lineEnd)
            if (tokenEnd <= tokenStart) {
                rangeIndex++
                continue
            }
            if (tokenStart > cursor) {
                drawCodeTextRange(
                    canvas,
                    layout,
                    cursor,
                    tokenStart,
                    baseline,
                    cursorX,
                    layout.paint,
                )
                cursorX = layout.getPrimaryHorizontal(tokenStart)
            }
            if (range.color == layout.paint.color) {
                drawCodeTextRange(
                    canvas,
                    layout,
                    tokenStart,
                    tokenEnd,
                    baseline,
                    cursorX,
                    layout.paint,
                )
            } else {
                syntaxTextPaint.color = range.color
                drawCodeTextRange(
                    canvas,
                    layout,
                    tokenStart,
                    tokenEnd,
                    baseline,
                    cursorX,
                    syntaxTextPaint,
                )
            }
            cursor = tokenEnd
            cursorX = layout.getPrimaryHorizontal(cursor)
            rangeIndex++
        }
        if (cursor < lineEnd) {
            drawCodeTextRange(
                canvas,
                layout,
                cursor,
                lineEnd,
                baseline,
                cursorX,
                layout.paint,
            )
        }
    }

    /**
     * Canvas.drawText renders a tab as a glyph when the text run contains it. Draw around tabs
     * and let StaticLayout's cached horizontal positions place the following segment.
     */
    private fun drawCodeTextRange(
        canvas: Canvas,
        layout: StaticLayout,
        start: Int,
        end: Int,
        baseline: Float,
        startX: Float,
        paint: Paint,
    ) {
        if (start >= end) return
        var segmentStart = start
        var segmentX = startX
        var index = start
        while (index < end) {
            if (content[index] != '\t') {
                index++
                continue
            }
            if (segmentStart < index) {
                canvas.drawText(content, segmentStart, index, segmentX, baseline, paint)
            }
            index++
            segmentStart = index
            if (segmentStart < end) {
                segmentX = layout.getPrimaryHorizontal(segmentStart)
            }
        }
        if (segmentStart < end) {
            canvas.drawText(content, segmentStart, end, segmentX, baseline, paint)
        }
    }

    /**
     * Maps each layout line to its first syntax range once per layout/highlight generation.
     * Building this on demand after ranges arrive keeps the very large regex pass off the main
     * thread while making every subsequent scroll frame a linear walk over visible ranges only.
     */
    private fun ensureSyntaxLineMap(layout: StaticLayout) {
        val ranges = syntaxHighlightRanges
        if (ranges.isEmpty()) {
            syntaxLineMap = EMPTY_LINE_MAP
            syntaxLineMapLayoutGeneration = layoutGeneration
            syntaxLineMapSyntaxGeneration = syntaxGeneration
            return
        }
        if (syntaxLineMapLayoutGeneration == layoutGeneration &&
            syntaxLineMapSyntaxGeneration == syntaxGeneration
        ) {
            return
        }
        val mapped = IntArray(layout.lineCount) { -1 }
        val starts = cachedLineStarts
        ranges.forEachIndexed { rangeIndex, range ->
            val start = range.start.coerceIn(0, (content.length - 1).coerceAtLeast(0))
            val end = range.end.coerceIn(start + 1, content.length)
            if (end <= start) return@forEachIndexed
            val firstLine = lineForOffset(starts, start)
            val lastLine = lineForOffset(starts, end - 1)
            for (line in firstLine..lastLine) {
                if (mapped.getOrNull(line) == -1) mapped[line] = rangeIndex
            }
        }
        syntaxLineMap = mapped
        syntaxLineMapLayoutGeneration = layoutGeneration
        syntaxLineMapSyntaxGeneration = syntaxGeneration
    }

    private fun drawVisibleLine(
        canvas: Canvas,
        layout: StaticLayout,
        line: Int,
    ) {
        val lineStarts = cachedLineStarts
        val lineEnds = cachedLineEnds
        val lineBaselines = cachedLineBaselines
        val lineStartXs = cachedLineStartXs
        val lineStart = lineStarts?.getOrNull(line) ?: layout.getLineStart(line)
        val lineEnd = lineEnds?.getOrNull(line) ?: layout.getLineEnd(line)
        if (lineStart >= lineEnd) return
        val baseline = (lineBaselines?.getOrNull(line) ?: layout.getLineBaseline(line)).toFloat()
        val lineStartX = lineStartXs?.getOrNull(line) ?: layout.getPrimaryHorizontal(lineStart)
        var segmentStart = lineStart
        var index = lineStart
        while (index < lineEnd) {
            if (content[index] != '\t') {
                index++
                continue
            }
            drawCodeSegment(
                canvas = canvas,
                layout = layout,
                start = segmentStart,
                end = index,
                baseline = baseline,
                startX = if (segmentStart == lineStart) {
                    lineStartX
                } else {
                    layout.getPrimaryHorizontal(segmentStart)
                },
            )
            segmentStart = index + 1
            index++
        }
        drawCodeSegment(
            canvas = canvas,
            layout = layout,
            start = segmentStart,
            end = lineEnd,
            baseline = baseline,
            startX = if (segmentStart == lineStart) {
                lineStartX
            } else {
                layout.getPrimaryHorizontal(segmentStart)
            },
        )
    }

    private fun drawCodeSegment(
        canvas: Canvas,
        layout: StaticLayout,
        start: Int,
        end: Int,
        baseline: Float,
        startX: Float,
    ) {
        if (start >= end) return
        canvas.drawText(
            content,
            start,
            end,
            startX,
            baseline,
            layout.paint,
        )
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

    /**
     * Full-screen code keeps its syntax colors as ranges instead of Android spans. Only tokens
     * intersecting the visible layout lines are painted, so a long file does not paint thousands
     * of off-screen token rectangles on every scroll frame.
     */
    private fun drawSyntaxHighlightRanges(
        canvas: Canvas,
        layout: StaticLayout,
        visibleTopPx: Int,
        visibleBottomPx: Int,
    ) {
        if (syntaxHighlightRanges.isEmpty() || layout.lineCount <= 0) return
        val firstVisibleLine = layout.getLineForVertical(visibleTopPx)
            .coerceIn(0, layout.lineCount - 1)
        val lastVisibleLine = layout.getLineForVertical(
            (visibleBottomPx - 1).coerceAtLeast(visibleTopPx),
        )
            .coerceIn(firstVisibleLine, layout.lineCount - 1)
        val visibleStart = layout.getLineStart(firstVisibleLine)
        val visibleEnd = layout.getLineEnd(lastVisibleLine)
        if (visibleEnd <= visibleStart) return

        var low = 0
        var high = syntaxHighlightRanges.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (syntaxHighlightRanges[mid].end <= visibleStart) low = mid + 1 else high = mid - 1
        }

        syntaxTextPaint.set(layout.paint)
        val lineStarts = cachedLineStarts
        val lineEnds = cachedLineEnds
        val lineBaselines = cachedLineBaselines
        val lineStartXs = cachedLineStartXs
        for (index in low..syntaxHighlightRanges.lastIndex) {
            val range = syntaxHighlightRanges[index]
            if (range.start >= visibleEnd) break
            val start = max(range.start, visibleStart)
            val end = min(range.end, visibleEnd)
            if (end <= start) continue
            val firstLine = lineForOffset(lineStarts, start)
                .coerceIn(firstVisibleLine, lastVisibleLine)
            val lastLine = lineForOffset(lineStarts, (end - 1).coerceAtLeast(start))
                .coerceIn(firstLine, lastVisibleLine)
            for (line in firstLine..lastLine) {
                val lineStart = lineStarts?.getOrNull(line) ?: layout.getLineStart(line)
                val lineEnd = lineEnds?.getOrNull(line) ?: layout.getLineEnd(line)
                val drawStart = max(start, lineStart)
                val drawEnd = min(end, lineEnd)
                if (drawEnd <= drawStart) continue
                syntaxTextPaint.color = range.color
                val startX = if (drawStart == lineStart) {
                    lineStartXs?.getOrNull(line) ?: layout.getPrimaryHorizontal(drawStart)
                } else {
                    layout.getPrimaryHorizontal(drawStart)
                }
                canvas.drawText(
                    content,
                    drawStart,
                    drawEnd,
                    startX,
                    (lineBaselines?.getOrNull(line) ?: layout.getLineBaseline(line)).toFloat(),
                    syntaxTextPaint,
                )
            }
        }
    }

    private fun lineForOffset(lineStarts: IntArray?, offset: Int): Int {
        if (lineStarts == null || lineStarts.isEmpty()) {
            return cachedLayout?.getLineForOffset(offset) ?: 0
        }
        var low = 0
        var high = lineStarts.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lineStarts[mid] <= offset) low = mid + 1 else high = mid - 1
        }
        return high.coerceAtLeast(0)
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

    /** Returns the source offset of every logical line, including a non-empty final line. */
    private fun sourceLineStarts(code: CharSequence): IntArray {
        val starts = ArrayList<Int>()
        starts += 0
        for (index in 0 until code.length) {
            if (code[index] == '\n' && index + 1 < code.length) {
                starts += index + 1
            }
        }
        return starts.toIntArray()
    }

    private fun hasBidiDirection(code: CharSequence): Boolean {
        var index = 0
        while (index < code.length) {
            val codePoint = Character.codePointAt(code, index)
            when (Character.getDirectionality(codePoint)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE,
                -> return true
            }
            index += Character.charCount(codePoint)
        }
        return false
    }

    companion object {
        internal const val SELECTION_EDGE_DP = 36f
        internal const val SELECTION_EDGE_MAX_STEP_DP = 10f
        private const val STYLED_CHUNK_LINES = 16
        private val EMPTY_LINE_MAP = IntArray(0)
    }

    class StyledSyntaxLayout internal constructor(
        internal val chunks: List<StyledLayoutChunk>,
        internal val ranges: List<CodeSyntaxRange>,
        internal val layoutGeneration: Int,
        internal val textSize: Float,
        internal val color: Int,
        internal val wrapWidth: Int,
        internal val maxLineWidth: Int,
    )

    class StyledLayoutChunk internal constructor(
        internal val firstLayoutLine: Int,
        internal val lastLayoutLine: Int,
        internal val sourceStart: Int,
        internal val layout: StaticLayout,
    )

}

/**
 * Removes layout-only trailing blank lines from a code block.
 *
 * CommonMark keeps whitespace-only lines immediately before a closing fence in the block
 * literal. Rendering those rows adds visible empty height below the source. Only the trailing
 * whitespace-only rows are removed: leading indentation, internal blank lines and whitespace at
 * the end of the last non-blank source line remain untouched.
 */
internal fun normalizeReaderCodeContent(code: CharSequence): CharSequence {
    var end = code.length
    while (end > 0 && (code[end - 1] == '\n' || code[end - 1] == '\r')) {
        end--
    }

    while (end > 0) {
        var lineStart = end - 1
        while (lineStart >= 0 && code[lineStart] != '\n' && code[lineStart] != '\r') {
            lineStart--
        }
        var blank = true
        for (index in lineStart + 1 until end) {
            if (!code[index].isWhitespace()) {
                blank = false
                break
            }
        }
        if (!blank) break

        end = lineStart
        while (end > 0 && (code[end - 1] == '\n' || code[end - 1] == '\r')) {
            end--
        }
    }

    if (end <= 0) return " "
    return if (end == code.length) code else code.subSequence(0, end)
}

internal data class CodeBlockHighlightRange(
    val start: Int,
    val end: Int,
    val color: Int,
    val underline: Boolean = false,
    val wavy: Boolean = false,
    val highlightId: Long = 0L,
)
