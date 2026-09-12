package space.liushenme.markdownreader.git

import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.transport.URIish

data class GitCloneResult(
    val branch: String,
    val commitSha: String,
    val sizeBytes: Long,
)

class GitCloneException(
    message: String,
    cause: Throwable? = null,
    val isLikelyPrivateOrMissing: Boolean = false,
) : Exception(message, cause)

object GitProjectCloner {

    fun clone(
        cloneUrl: String,
        destination: File,
        branch: String?,
        onProgress: (GitProgress) -> Unit = {},
    ): GitCloneResult = GitRepoLock.withLock(GitRepoLock.keyForPath(destination.absolutePath)) {
        if (destination.exists()) {
            destination.deleteRecursively()
        }
        destination.mkdirs()

        onProgress(GitProgress(GitProgress.Phase.PREPARING))
        val monitor = CallbackProgressMonitor { title, percent ->
            onProgress(
                GitProgress(
                    phase = GitProgress.Phase.CLONING,
                    percent = percent,
                    detail = title,
                ),
            )
        }

        try {
            val cmd = Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(destination)
                .setCloneAllBranches(false)
                .setDepth(1)
                .setProgressMonitor(monitor)
            val trimmedBranch = branch?.trim()?.takeIf { it.isNotEmpty() }
            if (trimmedBranch != null) {
                cmd.setBranch(trimmedBranch)
            }
            cmd.call().use { git ->
                val repo = git.repository
                val head = repo.resolve("HEAD")
                    ?: throw GitCloneException("仓库为空或无法解析 HEAD")
                val resolvedBranch = repo.branch
                    ?: trimmedBranch
                    ?: "HEAD"
                val size = GitProjectStorage.directorySizeBytes(destination)
                onProgress(GitProgress(GitProgress.Phase.DONE, percent = 100))
                GitCloneResult(
                    branch = resolvedBranch,
                    commitSha = head.name,
                    sizeBytes = size,
                )
            }
        } catch (e: GitCloneException) {
            destination.deleteRecursively()
            onProgress(GitProgress(GitProgress.Phase.FAILED, detail = e.message.orEmpty()))
            throw e
        } catch (e: Exception) {
            destination.deleteRecursively()
            val msg = e.message.orEmpty()
            val privateOrMissing = msg.contains("not found", ignoreCase = true) ||
                msg.contains("401") ||
                msg.contains("403") ||
                msg.contains("404") ||
                msg.contains("Authentication", ignoreCase = true) ||
                msg.contains("access rights", ignoreCase = true)
            val wrapped = GitCloneException(
                message = msg.ifBlank { "克隆失败" },
                cause = e,
                isLikelyPrivateOrMissing = privateOrMissing,
            )
            onProgress(GitProgress(GitProgress.Phase.FAILED, detail = wrapped.message.orEmpty()))
            throw wrapped
        }
    }

    /** 校验 URL 形状（不发起网络）。 */
    fun isPlausibleCloneUrl(url: String): Boolean =
        runCatching { URIish(url) }.isSuccess
}

private class CallbackProgressMonitor(
    private val onUpdate: (title: String, percent: Int) -> Unit,
) : ProgressMonitor {
    private var currentTitle = ""
    private var totalWork = 0
    private var completed = 0

    override fun start(totalTasks: Int) = Unit

    override fun beginTask(title: String, totalWork: Int) {
        currentTitle = title
        this.totalWork = totalWork
        completed = 0
        onUpdate(title, if (totalWork > 0) 0 else -1)
    }

    override fun update(completed: Int) {
        this.completed += completed
        val percent = if (totalWork > 0) {
            ((this.completed.toLong() * 100L) / totalWork).toInt().coerceIn(0, 100)
        } else {
            -1
        }
        onUpdate(currentTitle, percent)
    }

    override fun endTask() = Unit

    override fun isCancelled(): Boolean = false

    override fun showDuration(enabled: Boolean) = Unit
}
