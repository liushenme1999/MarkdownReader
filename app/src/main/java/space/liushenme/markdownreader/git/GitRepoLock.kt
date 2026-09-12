package space.liushenme.markdownreader.git

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * 同一本地仓库 / 同一远程地址的 JGit 操作串行化。
 * [ReentrantLock] 可重入，便于 Importer 与 Cloner/Puller 嵌套加锁。
 */
object GitRepoLock {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    fun keyForPath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return trimmed
        return runCatching { File(trimmed).canonicalPath }.getOrDefault(trimmed)
    }

    fun keyForRemote(url: String): String =
        GitHubRepoUrlParser.canonicalBrowseUrl(url) ?: url.trim()

    fun <T> withLock(key: String, block: () -> T): T {
        val normalized = key.trim()
        if (normalized.isEmpty()) return block()
        val lock = locks.getOrPut(normalized) { ReentrantLock() }
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
