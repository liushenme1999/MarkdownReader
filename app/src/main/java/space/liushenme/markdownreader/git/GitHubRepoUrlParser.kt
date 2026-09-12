package space.liushenme.markdownreader.git

/**
 * 将用户输入的 GitHub 仓库地址规范化为 HTTPS clone URL。
 * 仅支持公开的 github.com 仓库。
 */
object GitHubRepoUrlParser {

    data class ParsedRepo(
        val owner: String,
        val repo: String,
        /** 不含 .git 后缀的 HTTPS 浏览 URL */
        val httpsBrowseUrl: String,
        /** 用于 JGit clone 的 HTTPS URL（带 .git） */
        val cloneUrl: String,
        val displayName: String,
    )

    fun parse(raw: String): ParsedRepo? {
        val input = raw.trim()
        if (input.isEmpty()) return null

        parseOwnerRepoShorthand(input)?.let { return it }

        val normalized = input
            .removePrefix("git+")
            .let { if (it.startsWith("git@github.com:", ignoreCase = true)) {
                "https://github.com/" + it.removePrefix("git@github.com:").removePrefix("git@GITHUB.COM:")
            } else {
                it
            } }
            .let { if (it.startsWith("ssh://git@github.com/", ignoreCase = true)) {
                "https://github.com/" + it.substringAfter("github.com/")
            } else {
                it
            } }

        val uri = runCatching { java.net.URI(normalized) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "github.com" && host != "www.github.com") return null
        if (uri.scheme != null &&
            !uri.scheme.equals("https", ignoreCase = true) &&
            !uri.scheme.equals("http", ignoreCase = true)
        ) {
            return null
        }

        val segments = uri.path
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() }
        if (segments.size < 2) return null
        val owner = segments[0]
        val repo = segments[1]
            .removeSuffix(".git")
            .trim()
        if (!isValidSegment(owner) || !isValidSegment(repo)) return null

        return ParsedRepo(
            owner = owner,
            repo = repo,
            httpsBrowseUrl = "https://github.com/$owner/$repo",
            cloneUrl = "https://github.com/$owner/$repo.git",
            displayName = "$owner/$repo",
        )
    }

    /** 规范化为不含 `.git` 的 HTTPS 浏览地址；无法解析时返回弱规范化结果。 */
    fun canonicalBrowseUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        parse(trimmed)?.httpsBrowseUrl?.let { return it }
        val weak = weakNormalize(trimmed)
        return weak.takeIf { it.isNotEmpty() }
    }

    fun sameRepo(a: String, b: String): Boolean {
        val left = canonicalBrowseUrl(a) ?: return false
        val right = canonicalBrowseUrl(b) ?: return false
        return left.equals(right, ignoreCase = true)
    }

    /** 查库 / 清墓碑时同时尝试的 URL 形态。 */
    fun lookupUrls(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        val parsed = parse(trimmed)
        return buildList {
            parsed?.httpsBrowseUrl?.let { add(it) }
            parsed?.cloneUrl?.let { add(it) }
            add(trimmed)
            val weak = weakNormalize(trimmed)
            if (weak.isNotEmpty()) add(weak)
        }.distinct()
    }

    internal fun weakNormalize(raw: String): String {
        var value = raw.trim().trimEnd('/')
        value = value.removePrefix("git+")
        if (value.startsWith("git@github.com:", ignoreCase = true)) {
            value = "https://github.com/" +
                value.removePrefix("git@github.com:").removePrefix("git@GITHUB.COM:")
        }
        if (value.startsWith("http://", ignoreCase = true)) {
            value = "https://" + value.substring(7)
        }
        value = value.replace(Regex("^https://www\\.github\\.com/", RegexOption.IGNORE_CASE), "https://github.com/")
        if (value.endsWith(".git", ignoreCase = true)) {
            value = value.dropLast(4)
        }
        return value
    }

    private fun parseOwnerRepoShorthand(input: String): ParsedRepo? {
        if (input.contains("://") || input.contains('@') || input.contains(' ')) return null
        val parts = input.trim('/').split('/')
        if (parts.size != 2) return null
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".git")
        if (!isValidSegment(owner) || !isValidSegment(repo)) return null
        return ParsedRepo(
            owner = owner,
            repo = repo,
            httpsBrowseUrl = "https://github.com/$owner/$repo",
            cloneUrl = "https://github.com/$owner/$repo.git",
            displayName = "$owner/$repo",
        )
    }

    private fun isValidSegment(value: String): Boolean {
        if (value.isEmpty() || value.length > 100) return false
        if (value == "." || value == "..") return false
        return value.all { ch ->
            ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.'
        }
    }
}
