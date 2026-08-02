package space.liushenme.markdownreader.git

import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.transport.RefSpec

data class GitPullResult(
    val previousSha: String,
    val currentSha: String,
    val changed: Boolean,
)

object GitProjectPuller {

    fun pull(
        localPath: String,
        branch: String,
        onProgress: (GitProgress) -> Unit = {},
    ): GitPullResult {
        val dir = File(localPath)
        if (!dir.isDirectory || !File(dir, ".git").exists()) {
            throw GitCloneException("本地仓库不存在或已损坏")
        }

        onProgress(GitProgress(GitProgress.Phase.FETCHING))
        return try {
            Git.open(dir).use { git ->
                val repo = git.repository
                val previous = repo.resolve("HEAD")?.name.orEmpty()
                val trackBranch = branch.trim().ifEmpty { repo.branch ?: "main" }
                val remoteRef = "refs/remotes/origin/$trackBranch"

                val monitor = object : org.eclipse.jgit.lib.ProgressMonitor {
                    private var title = ""
                    private var total = 0
                    private var done = 0
                    override fun start(totalTasks: Int) = Unit
                    override fun beginTask(title: String, totalWork: Int) {
                        this.title = title
                        total = totalWork
                        done = 0
                        onProgress(GitProgress(GitProgress.Phase.FETCHING, if (totalWork > 0) 0 else -1, title))
                    }
                    override fun update(completed: Int) {
                        done += completed
                        val pct = if (total > 0) ((done.toLong() * 100L) / total).toInt().coerceIn(0, 100) else -1
                        onProgress(GitProgress(GitProgress.Phase.FETCHING, pct, title))
                    }
                    override fun endTask() = Unit
                    override fun isCancelled(): Boolean = false
                    override fun showDuration(enabled: Boolean) = Unit
                }

                git.fetch()
                    .setRemote("origin")
                    .setRefSpecs(RefSpec("+refs/heads/$trackBranch:$remoteRef"))
                    .setDepth(1)
                    .setProgressMonitor(monitor)
                    .call()

                onProgress(GitProgress(GitProgress.Phase.UPDATING, detail = trackBranch))
                val remoteHead = repo.resolve(remoteRef)
                    ?: throw GitCloneException("无法获取远程分支 origin/$trackBranch")

                git.reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .setRef(remoteHead.name)
                    .call()

                val current = repo.resolve("HEAD")?.name.orEmpty()
                onProgress(GitProgress(GitProgress.Phase.DONE, percent = 100))
                GitPullResult(
                    previousSha = previous,
                    currentSha = current,
                    changed = previous.isNotEmpty() && current.isNotEmpty() && previous != current,
                )
            }
        } catch (e: GitCloneException) {
            onProgress(GitProgress(GitProgress.Phase.FAILED, detail = e.message.orEmpty()))
            throw e
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            val privateOrMissing = msg.contains("not found", ignoreCase = true) ||
                msg.contains("401") ||
                msg.contains("403") ||
                msg.contains("404") ||
                msg.contains("Authentication", ignoreCase = true)
            val wrapped = GitCloneException(
                message = msg.ifBlank { "更新失败" },
                cause = e,
                isLikelyPrivateOrMissing = privateOrMissing,
            )
            onProgress(GitProgress(GitProgress.Phase.FAILED, detail = wrapped.message.orEmpty()))
            throw wrapped
        }
    }
}
