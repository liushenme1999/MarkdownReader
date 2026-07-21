package space.liushenme.markdownreader.testutil

import java.io.File

object TestFixtures {
    /** 全语法验收样例：优先读仓库内 test resources，兼容本机 Downloads 旧路径。 */
    fun fullFormatSampleMarkdown(): String {
        val resource = requireNotNull(javaClass.classLoader) {
            "classLoader missing"
        }.getResource("fixtures/全格式示例markdown.md")
        if (resource != null) {
            return resource.readText()
        }
        val downloads = File("/Users/liukejun/Downloads/全格式示例markdown.md")
        check(downloads.isFile) {
            "missing fixture fixtures/全格式示例markdown.md (and Downloads fallback)"
        }
        return downloads.readText()
    }
}
