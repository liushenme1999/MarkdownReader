package space.liushenme.markdownreader.git

import org.eclipse.jgit.api.Git

/**
 * 用 ls-remote 读取远程分支 HEAD，不下载对象。
 */
object GitProjectRemote {

    fun headRefName(branch: String): String = "refs/heads/${branch.trim()}"

    /** 从假 / 真 ref 列表里取出 `refs/heads/<branch>` 的 SHA。 */
    fun shaFromRefs(refs: Iterable<Pair<String, String>>, branch: String): String? {
        val want = headRefName(branch)
        if (want == "refs/heads/") return null
        return refs.firstOrNull { it.first == want }
            ?.second
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * 本地无仓，或与远程 SHA 不同，视为有更新。
     * [remoteSha] 须已成功解析；失败时不要调用本方法，以免误清红点。
     */
    fun shouldMarkRemoteUpdate(
        localSha: String,
        remoteSha: String,
        hasLocalRepo: Boolean,
    ): Boolean {
        if (!hasLocalRepo) return true
        return !localSha.equals(remoteSha, ignoreCase = true)
    }

    /** 只要 `refs/heads/<branch>` 的 SHA；失败返回 null。 */
    fun checkBranchHead(cloneUrl: String, branch: String): String? {
        val url = cloneUrl.trim()
        val track = branch.trim()
        if (url.isEmpty() || track.isEmpty()) return null
        return try {
            GitRepoLock.withLock(GitRepoLock.keyForRemote(url)) {
                val refs = Git.lsRemoteRepository()
                    .setRemote(url)
                    .setHeads(true)
                    .setTags(false)
                    .call()
                shaFromRefs(
                    refs.map { it.name to (it.objectId?.name.orEmpty()) },
                    track,
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
