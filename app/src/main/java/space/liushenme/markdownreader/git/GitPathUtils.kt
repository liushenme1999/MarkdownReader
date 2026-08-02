package space.liushenme.markdownreader.git

/** Git 仓库相对路径辅助。 */
object GitPathUtils {

    /** `docs/a/b.md` → `["docs", "docs/a"]` */
    fun ancestorPaths(relativePath: String): Set<String> {
        val normalized = relativePath.trim().trimStart('/').replace('\\', '/')
        if (normalized.isEmpty() || !normalized.contains('/')) return emptySet()
        val parts = normalized.split('/').filter { it.isNotEmpty() }
        if (parts.size < 2) return emptySet()
        val out = LinkedHashSet<String>()
        val sb = StringBuilder()
        for (i in 0 until parts.lastIndex) {
            if (sb.isNotEmpty()) sb.append('/')
            sb.append(parts[i])
            out += sb.toString()
        }
        return out
    }

    fun fileName(relativePath: String): String =
        relativePath.trim().trimStart('/').substringAfterLast('/')
}
