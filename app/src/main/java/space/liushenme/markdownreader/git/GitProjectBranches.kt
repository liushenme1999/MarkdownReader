package space.liushenme.markdownreader.git

import java.io.File
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.transport.RefSpec

data class GitCheckoutResult(
    val branch: String,
    val commitSha: String,
)

object GitProjectBranches {

    /** 通过 ls-remote 拉取远程全部分支名（不含 refs/heads/ 前缀）。 */
    fun listRemoteBranches(localPath: String): List<String> = GitRepoLock.withLock(GitRepoLock.keyForPath(localPath)) {
        val dir = File(localPath)
        if (!dir.isDirectory || !File(dir, ".git").exists()) {
            throw GitCloneException("本地仓库不存在或已损坏")
        }
        try {
            Git.open(dir).use { git ->
                val refs = git.lsRemote()
                    .setRemote("origin")
                    .setHeads(true)
                    .call()
                normalizeHeadNames(refs.map { it.name })
            }
        } catch (e: GitCloneException) {
            throw e
        } catch (e: Exception) {
            throw wrapTransportError(e, fallback = "获取远程分支失败")
        }
    }

    /** 不依赖本地工作区，直接对 clone URL 做 ls-remote。 */
    fun listRemoteBranchesByUrl(cloneUrl: String): List<String> {
        val url = cloneUrl.trim()
        if (url.isEmpty()) {
            throw GitCloneException("缺少远程仓库地址")
        }
        return try {
            val refs = Git.lsRemoteRepository()
                .setRemote(url)
                .setHeads(true)
                .call()
            normalizeHeadNames(refs.map { it.name })
        } catch (e: GitCloneException) {
            throw e
        } catch (e: Exception) {
            throw wrapTransportError(e, fallback = "获取远程分支失败")
        }
    }

    private fun normalizeHeadNames(names: Collection<String>): List<String> {
        return names.mapNotNull { name ->
            name.removePrefix("refs/heads/").trim().takeIf { it.isNotEmpty() }
        }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    /**
     * 浅拉取并切换到指定远程分支（hard reset 到 origin/branch）。
     */
    fun checkoutBranch(
        localPath: String,
        branch: String,
        onProgress: (GitProgress) -> Unit = {},
    ): GitCheckoutResult = GitRepoLock.withLock(GitRepoLock.keyForPath(localPath)) {
        val trackBranch = branch.trim()
        if (trackBranch.isEmpty()) {
            throw GitCloneException("分支名不能为空")
        }
        val dir = File(localPath)
        if (!dir.isDirectory || !File(dir, ".git").exists()) {
            throw GitCloneException("本地仓库不存在或已损坏")
        }

        onProgress(GitProgress(GitProgress.Phase.FETCHING, detail = trackBranch))
        try {
            Git.open(dir).use { git ->
                val repo = git.repository
                val remoteRef = "refs/remotes/origin/$trackBranch"
                val localRef = "refs/heads/$trackBranch"

                val monitor = object : org.eclipse.jgit.lib.ProgressMonitor {
                    private var title = ""
                    private var total = 0
                    private var done = 0
                    override fun start(totalTasks: Int) = Unit
                    override fun beginTask(title: String, totalWork: Int) {
                        this.title = title
                        total = totalWork
                        done = 0
                        onProgress(
                            GitProgress(
                                GitProgress.Phase.FETCHING,
                                if (totalWork > 0) 0 else -1,
                                title,
                            ),
                        )
                    }
                    override fun update(completed: Int) {
                        done += completed
                        val pct = if (total > 0) {
                            ((done.toLong() * 100L) / total).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }
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
                repo.resolve(remoteRef)
                    ?: throw GitCloneException("无法获取远程分支 origin/$trackBranch")

                val hasLocal = git.branchList()
                    .setListMode(ListBranchCommand.ListMode.ALL)
                    .call()
                    .any { it.name == localRef }

                if (hasLocal) {
                    git.checkout()
                        .setName(trackBranch)
                        .setForced(true)
                        .call()
                } else {
                    git.checkout()
                        .setCreateBranch(true)
                        .setName(trackBranch)
                        .setStartPoint(remoteRef)
                        .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
                        .call()
                }

                git.reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .setRef(remoteRef)
                    .call()

                val sha = repo.resolve("HEAD")?.name.orEmpty()
                onProgress(GitProgress(GitProgress.Phase.DONE, percent = 100))
                GitCheckoutResult(branch = trackBranch, commitSha = sha)
            }
        } catch (e: GitCloneException) {
            onProgress(GitProgress(GitProgress.Phase.FAILED, detail = e.message.orEmpty()))
            throw e
        } catch (e: Exception) {
            val wrapped = wrapTransportError(e, fallback = "切换分支失败")
            onProgress(GitProgress(GitProgress.Phase.FAILED, detail = wrapped.message.orEmpty()))
            throw wrapped
        }
    }

    private fun wrapTransportError(e: Exception, fallback: String): GitCloneException {
        val msg = e.message.orEmpty()
        val privateOrMissing = msg.contains("not found", ignoreCase = true) ||
            msg.contains("401") ||
            msg.contains("403") ||
            msg.contains("404") ||
            msg.contains("Authentication", ignoreCase = true) ||
            msg.contains("access rights", ignoreCase = true)
        return GitCloneException(
            message = msg.ifBlank { fallback },
            cause = e,
            isLikelyPrivateOrMissing = privateOrMissing,
        )
    }
}
