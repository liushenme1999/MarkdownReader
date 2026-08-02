package space.liushenme.markdownreader.git

import org.json.JSONArray

object GitRecentOpenedPaths {
    const val MAX_HISTORY = 50

    fun decode(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val path = arr.optString(i).trim()
                    if (path.isNotEmpty()) add(path)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encode(paths: List<String>): String {
        val arr = JSONArray()
        paths.take(MAX_HISTORY).forEach { arr.put(it) }
        return arr.toString()
    }

    /** 将 [path] 置顶，去重并截断。 */
    fun prepend(existingJson: String?, path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return existingJson ?: "[]"
        val next = buildList {
            add(trimmed)
            decode(existingJson).forEach { if (it != trimmed) add(it) }
        }.take(MAX_HISTORY)
        return encode(next)
    }

    fun mergePreferFirst(preferred: List<String>, other: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        preferred.forEach { if (it.isNotBlank()) seen.add(it) }
        other.forEach { if (it.isNotBlank()) seen.add(it) }
        return seen.take(MAX_HISTORY)
    }

    fun resolveList(
        recentJson: String?,
        lastOpenedRelativePath: String?,
    ): List<String> {
        val decoded = decode(recentJson)
        if (decoded.isNotEmpty()) return decoded
        val last = lastOpenedRelativePath?.trim().orEmpty()
        return if (last.isNotEmpty()) listOf(last) else emptyList()
    }
}
