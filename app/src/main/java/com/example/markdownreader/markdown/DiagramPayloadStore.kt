package com.example.markdownreader.markdown

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Mermaid/ECharts 源码缓存：diagram URI 只携带短 id，避免超长 URL 导致 Markwon 无法加载。
 * id 由 type+source 哈希生成，同一图表重复渲染可复用 payload。
 */
internal object DiagramPayloadStore {

    private const val MAX_ENTRIES = 128
    private val cache = ConcurrentHashMap<String, Payload>()

    data class Payload(val type: String, val source: String)

    fun put(type: String, source: String): String {
        val id = hashId(type, source)
        if (cache.size >= MAX_ENTRIES && !cache.containsKey(id)) {
            cache.keys.firstOrNull()?.let { cache.remove(it) }
        }
        cache[id] = Payload(type, source)
        return id
    }

    fun get(id: String): Payload? = cache[id]

    fun remove(id: String) {
        cache.remove(id)
    }

    private fun hashId(type: String, source: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$type\u0000$source".toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
