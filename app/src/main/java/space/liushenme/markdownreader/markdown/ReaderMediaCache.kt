package space.liushenme.markdownreader.markdown

import android.content.Context
import java.io.File
import space.liushenme.markdownreader.document.DocumentMediaResource
import space.liushenme.markdownreader.document.DocumentSnapshot
import space.liushenme.markdownreader.document.mediaResources

/** One cache policy and entry point for network images, formulas/diagrams and decoded document media. */
internal object ReaderMediaCache {
    data class Policy(
        val maxDiskBytes: Long = 192L * 1024L * 1024L,
        val pdfBitmapEntries: Int = 8,
    )

    data class WarmupResult(
        val requestedNetworkImages: Int,
        val cachedNetworkImages: Int,
        val failedNetworkImages: Int,
        val cachedDiagrams: Int,
    )

    val policy = Policy()

    suspend fun prepare(context: Context, markdown: String): WarmupResult {
        val network = NetworkImageCache.preloadFromMarkdown(context, markdown)
        val diagrams = DiagramImageLoader.preloadFromMarkdown(context, markdown)
        trimToBudget(context)
        return WarmupResult(
            requestedNetworkImages = network.requested,
            cachedNetworkImages = network.cached,
            failedNetworkImages = network.failed,
            cachedDiagrams = diagrams,
        )
    }

    /** Unified asynchronous warm-up entry for every media kind in a document snapshot. */
    suspend fun prepare(context: Context, snapshot: DocumentSnapshot): WarmupResult {
        val resources = snapshot.mediaResources()
        val hasNetworkOrDiagram = resources.any {
            it is DocumentMediaResource.NetworkImage || it is DocumentMediaResource.Diagram
        }
        return if (hasNetworkOrDiagram) {
            prepare(context, snapshot.content)
        } else {
            WarmupResult(0, 0, 0, 0)
        }
    }

    fun rewriteCachedReferences(context: Context, markdown: String): String =
        NetworkImageCache.rewriteCachedUrls(context, markdown)

    fun clearAll(context: Context): Long =
        NetworkImageCache.clearAll(context) + DiagramImageLoader.clearDiskCache(context)

    fun trimToBudget(context: Context, maxBytes: Long = policy.maxDiskBytes): Long {
        val roots = listOf(
            NetworkImageCache.cacheRoot(context),
            File(context.applicationContext.filesDir, "diagram_image_cache"),
        )
        val files = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile }.toList()
        }.sortedBy { it.lastModified() }
        var total = files.sumOf(File::length)
        var freed = 0L
        for (file in files) {
            if (total <= maxBytes.coerceAtLeast(0L)) break
            val bytes = file.length()
            if (file.delete()) {
                total -= bytes
                freed += bytes
            }
        }
        return freed
    }
}
