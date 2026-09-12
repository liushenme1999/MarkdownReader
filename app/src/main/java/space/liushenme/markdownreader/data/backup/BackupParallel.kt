package space.liushenme.markdownreader.data.backup

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** WebDAV 正文同时上传 / 下载路数，避免坚果云等限流。 */
internal const val WEBDAV_TRANSFER_PARALLELISM = 4

/** 恢复时同时浅克隆 Git 的路数。 */
internal const val GIT_RESTORE_CLONE_PARALLELISM = 3

internal suspend fun <T, R> mapLimitedParallel(
    items: Collection<T>,
    parallelism: Int,
    transform: suspend (T) -> R,
): List<R> = coroutineScope {
    if (items.isEmpty()) return@coroutineScope emptyList()
    val limit = Semaphore(parallelism.coerceAtLeast(1))
    items.map { item ->
        async {
            limit.withPermit { transform(item) }
        }
    }.awaitAll()
}
