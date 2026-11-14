package space.liushenme.markdownreader.markdown

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.caverock.androidsvg.SVG
import io.noties.markwon.image.MediaDecoder
import java.io.InputStream
import kotlin.math.roundToInt

/**
 * SVG 解码器，先做 AndroidSVG 兼容性清洗再渲染。
 *
 * 背景：项目里大量图表由 Mermaid / 绘图工具导出，常带 SVG2 语法。
 * AndroidSVG 不认识 `orient="auto-start-reverse"`（箭头标记自动反向），
 * 会在 SVGParser.parseAttributesMarker 抛 `Invalid float value: auto-start-reverse`，
 * 导致整张图解析失败、正文里只剩空白（Markwon 只在 Logcat 打 MARKWON-IMAGE 错误）。
 *
 * 这里把该值降级为等价的 `auto`（仅影响标记朝向），其余内容原样交给 AndroidSVG。
 */
internal class SanitizedSvgMediaDecoder(
    private val resources: Resources,
) : MediaDecoder() {

    override fun supportedTypes(): Collection<String> = listOf(CONTENT_TYPE)

    override fun decode(contentType: String?, inputStream: InputStream): Drawable {
        val raw = inputStream.use { it.readBytes() }
        val svg = SVG.getFromString(sanitize(String(raw, Charsets.UTF_8)))

        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val docWidth = svg.documentWidth.takeIf { it > 0f } ?: DEFAULT_WIDTH
        val docHeight = svg.documentHeight.takeIf { it > 0f } ?: DEFAULT_HEIGHT
        // 按屏幕密度放大，避免高分屏上发虚；上限防止异常大图占用过多内存。
        val width = (docWidth * density).roundToInt().coerceIn(1, MAX_DIMENSION)
        val height = (docHeight * density).roundToInt().coerceIn(1, MAX_DIMENSION)
        svg.setDocumentWidth(width.toFloat())
        svg.setDocumentHeight(height.toFloat())

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(bitmap))
        return BitmapDrawable(resources, bitmap)
    }

    companion object {
        const val CONTENT_TYPE = "image/svg+xml"
        private const val DEFAULT_WIDTH = 300f
        private const val DEFAULT_HEIGHT = 150f
        private const val MAX_DIMENSION = 4096

        /** 把 AndroidSVG 不支持的 SVG2 取值降级为等价写法。 */
        internal fun sanitize(source: String): String {
            if (source.isEmpty()) return source
            return source
                // marker 朝向：auto-start-reverse → auto（箭头仍随路径方向绘制）
                .replace("auto-start-reverse", "auto")
        }
    }
}
