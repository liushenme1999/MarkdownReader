package com.example.markdownreader.markdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException

/** 将 Mermaid 输出的 SVG 字符串渲染为位图。 */
internal object DiagramSvgRenderer {

    private const val TAG = "DiagramSvgRenderer"

    /**
     * @param contentHeightPx WebView DOM 实测高度；优先于 SVG viewBox（甘特图 viewBox 常偏小）。
     */
    fun renderToBitmap(svg: String, maxWidthPx: Int, contentHeightPx: Int): Bitmap? {
        if (svg.isBlank()) return null
        val width = maxWidthPx.coerceAtLeast(1)
        val height = contentHeightPx.coerceIn(120, 8192)
        return try {
            val doc = parseSvg(svg) ?: return null
            doc.documentWidth = width.toFloat()
            doc.documentHeight = height.toFloat()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            doc.renderToCanvas(canvas)
            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "SVG to bitmap failed", e)
            null
        }
    }

    private fun parseSvg(svg: String): SVG? {
        val candidates = listOf(svg, sanitizeSvg(svg))
        for (candidate in candidates) {
            try {
                return SVG.getFromString(candidate)
            } catch (_: SVGParseException) {
                // try sanitized fallback
            }
        }
        return null
    }

    /** 移除 AndroidSVG CSS 解析器无法处理的 style / foreignObject。 */
    private fun sanitizeSvg(svg: String): String {
        var out = svg.replace(STYLE_BLOCK, "")
        out = out.replace(FOREIGN_OBJECT_BLOCK, "")
        return out
    }

    private val STYLE_BLOCK = Regex("<style\\b[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
    private val FOREIGN_OBJECT_BLOCK = Regex(
        "<foreignObject\\b[\\s\\S]*?</foreignObject>",
        RegexOption.IGNORE_CASE,
    )
}
