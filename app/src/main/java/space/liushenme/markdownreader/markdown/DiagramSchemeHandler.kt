package space.liushenme.markdownreader.markdown

import android.content.Context
import android.net.Uri
import io.noties.markwon.image.ImageItem
import io.noties.markwon.image.SchemeHandler
import java.util.Collections

/**
 * diagram:// 图片：优先返回导入阶段预渲染的磁盘图，未命中时才返回固定占位图。
 */
internal class DiagramSchemeHandler(
    private val context: Context,
) : SchemeHandler() {

    override fun handle(raw: String, uri: Uri): ImageItem {
        val widthPx = context.resources.displayMetrics.widthPixels.coerceAtLeast(320)
        val drawable = DiagramImageLoader.cachedDrawable(context, raw, widthPx)
            ?: DiagramImageLoader.placeholder(context, widthPx)
        return ImageItem.withResult(drawable)
    }

    override fun supportedSchemes(): Collection<String> =
        Collections.singleton(SCHEME)

    companion object {
        const val SCHEME = "diagram"

        fun create(context: Context): DiagramSchemeHandler =
            DiagramSchemeHandler(context.applicationContext)
    }
}
