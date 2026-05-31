package space.liushenme.markdownreader.data.repository

import android.content.Context
import space.liushenme.markdownreader.markdown.DiagramImageLoader
import space.liushenme.markdownreader.markdown.NetworkImageCache
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AppCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 清理应用缓存（含 Markdown 阅读器下载的网络图片和预渲染图表）。 */
    suspend fun clearAll(): Long = withContext(Dispatchers.IO) {
        NetworkImageCache.clearAll(context) + DiagramImageLoader.clearDiskCache(context)
    }
}
