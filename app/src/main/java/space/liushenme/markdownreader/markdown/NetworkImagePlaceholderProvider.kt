package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import io.noties.markwon.image.AsyncDrawable
import java.io.File

/**
 * 若图片已在磁盘缓存，同步给出带正确 bounds 的占位 Drawable，
 * 避免二次进入时先空白再弹出导致 scroll 跳动。
 *
 * 仅读取图片 header 计算 bounds，不在主线程完整 decode，避免部分机型 gralloc/ion 告警。
 */
internal object NetworkImagePlaceholderProvider {

    fun provide(context: Context, drawable: AsyncDrawable): Drawable? {
        val file = resolveCachedFile(context, drawable.destination) ?: return null
        val bounds = NetworkImageCache.decodeBoundsFromFile(file) ?: return null
        return ColorDrawable(0x00000000).apply {
            setBounds(0, 0, bounds.first, bounds.second)
        }
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
