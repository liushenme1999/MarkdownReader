package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.TypedValue
import io.noties.markwon.image.AsyncDrawable
import java.io.File
import kotlin.math.roundToInt

/**
 * 若图片已在磁盘缓存（或本地 file://），同步给出带正确 bounds 的占位 Drawable，
 * 避免二次进入时先空白再弹出导致 scroll 跳动。
 *
 * 仅读取图片 header 计算 bounds，不在主线程完整 decode。
 * 占位宽高会按大致正文宽度缩放，避免原图像素尺寸撑高行距。
 */
internal object NetworkImagePlaceholderProvider {

    fun provide(context: Context, drawable: AsyncDrawable): Drawable? {
        val file = resolveCachedFile(context, drawable.destination) ?: return null
        val bounds = NetworkImageCache.decodeBoundsFromFile(file) ?: return null
        val (width, height) = scaleToContentWidth(context, bounds.first, bounds.second)
        return ColorDrawable(0x00000000).apply {
            setBounds(0, 0, width, height)
        }
    }

    /** 与阅读页左右边距大致对齐的内容区宽度估算。 */
    internal fun scaleToContentWidth(
        context: Context,
        rawWidth: Int,
        rawHeight: Int,
    ): Pair<Int, Int> {
        if (rawWidth <= 0 || rawHeight <= 0) return 1 to 1
        val metrics = context.resources.displayMetrics
        val horizontalPaddingPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            64f, // 左右各约 32dp
            metrics,
        ).roundToInt()
        val contentW = (metrics.widthPixels - horizontalPaddingPx).coerceAtLeast(1)
        if (rawWidth <= contentW) return rawWidth to rawHeight
        val scaledH = (rawHeight.toFloat() * contentW / rawWidth).roundToInt().coerceAtLeast(1)
        return contentW to scaledH
    }

    private fun resolveCachedFile(context: Context, destination: String?): File? {
        if (destination.isNullOrBlank()) return null
        return when {
            destination.startsWith("file://", ignoreCase = true) -> {
                val path = Uri.parse(destination).path ?: return null
                File(path).takeIf { it.isFile && it.length() > 0L }
            }
            destination.startsWith("http://", ignoreCase = true) ||
                destination.startsWith("https://", ignoreCase = true) -> {
                NetworkImageCache.getCachedFile(context, destination)
            }
            else -> null
        }
    }
}
