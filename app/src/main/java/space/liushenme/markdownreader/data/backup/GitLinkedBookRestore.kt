package space.liushenme.markdownreader.data.backup

import space.liushenme.markdownreader.data.local.BookContentHasher

/**
 * 删除本地 Git 项目后，云端 books.json 里仍可能留着该仓库打开过的文档。
 * 若 merge 时找不到 gitProjectId，这些书会变成独立书出现在书架上。
 */
internal object GitLinkedBookRestore {
    /**
     * 非 Git 文档（无相对路径）照常恢复。
     * Git 文档仅当哈希未进书墓碑、且能对上本机仍在的仓库时才恢复。
     */
    fun shouldRestore(
        gitRelativePath: String?,
        contentHash: String?,
        deletedBookHashes: Set<String>,
        deletedProjectUrls: Collection<String>,
        liveProjectUrls: Collection<String>,
    ): Boolean {
        val path = gitRelativePath?.trim().orEmpty()
        if (path.isBlank()) return true
        val hash = contentHash.orEmpty()
        if (hash.isNotBlank() && hash in deletedBookHashes) return false
        if (hash.isNotBlank() &&
            deletedProjectUrls.any { BookContentHasher.matchesGitDocument(hash, it, path) }
        ) {
            return false
        }
        if (hash.isNotBlank()) {
            return liveProjectUrls.any { BookContentHasher.matchesGitDocument(hash, it, path) }
        }
        return false
    }
}
