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
        scrollXF = (scrollXF + dx).coerceIn(0f, max.toFloat())
        scrollX = scrollXF.toInt()
        return true
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

    private fun normalizeContent(code: CharSequence): CharSequence {
        var end = code.length
        while (end > 0 && (code[end - 1] == '\n' || code[end - 1] == '\r')) end--
        if (end <= 0) return " "
        return if (end == code.length) code else code.subSequence(0, end)
    }

}
