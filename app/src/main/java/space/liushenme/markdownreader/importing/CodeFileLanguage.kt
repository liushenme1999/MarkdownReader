package space.liushenme.markdownreader.importing

/** 代码文件扩展名对应的 Markdown 围栏语言与界面显示名。 */
data class CodeLanguage(
    val token: String,
    val label: String,
)

object CodeFileLanguage {

    private val BY_EXTENSION = mapOf(
        "py" to CodeLanguage("python", "Python"),
        "pyw" to CodeLanguage("python", "Python"),
        "java" to CodeLanguage("java", "Java"),
        "kt" to CodeLanguage("kotlin", "Kotlin"),
        "kts" to CodeLanguage("kotlin", "Kotlin"),
        "vue" to CodeLanguage("vue", "Vue"),
        "js" to CodeLanguage("javascript", "JavaScript"),
        "jsx" to CodeLanguage("javascript", "JavaScript"),
        "mjs" to CodeLanguage("javascript", "JavaScript"),
        "cjs" to CodeLanguage("javascript", "JavaScript"),
        "ts" to CodeLanguage("typescript", "TypeScript"),
        "tsx" to CodeLanguage("typescript", "TypeScript"),
        "html" to CodeLanguage("html", "HTML"),
        "htm" to CodeLanguage("html", "HTML"),
        "css" to CodeLanguage("css", "CSS"),
        "scss" to CodeLanguage("scss", "SCSS"),
        "less" to CodeLanguage("less", "Less"),
        "xml" to CodeLanguage("xml", "XML"),
        "json" to CodeLanguage("json", "JSON"),
        "yaml" to CodeLanguage("yaml", "YAML"),
        "yml" to CodeLanguage("yaml", "YAML"),
        "toml" to CodeLanguage("toml", "TOML"),
        "ini" to CodeLanguage("ini", "INI"),
        "properties" to CodeLanguage("properties", "Properties"),
        "sh" to CodeLanguage("shell", "Shell"),
        "bash" to CodeLanguage("shell", "Shell"),
        "zsh" to CodeLanguage("shell", "Shell"),
        "ps1" to CodeLanguage("powershell", "PowerShell"),
        "c" to CodeLanguage("c", "C"),
        "h" to CodeLanguage("c", "C"),
        "cpp" to CodeLanguage("cpp", "C++"),
        "cc" to CodeLanguage("cpp", "C++"),
        "cxx" to CodeLanguage("cpp", "C++"),
        "hpp" to CodeLanguage("cpp", "C++"),
        "go" to CodeLanguage("go", "Go"),
        "rs" to CodeLanguage("rust", "Rust"),
        "rb" to CodeLanguage("ruby", "Ruby"),
        "php" to CodeLanguage("php", "PHP"),
        "swift" to CodeLanguage("swift", "Swift"),
    )

    fun fromFileName(name: String?): CodeLanguage? {
        val normalized = name?.trim().orEmpty()
        if (normalized.isEmpty() || !normalized.contains('.')) return null
        val extension = normalized.substringAfterLast('.', "").lowercase()
        return BY_EXTENSION[extension]
    }

    fun isCodeFile(name: String?): Boolean = fromFileName(name) != null

    /** 全部受支持的代码扩展名（本地导入白名单与标题清理复用，避免两处清单漂移）。 */
    val supportedExtensions: Set<String> = BY_EXTENSION.keys

    /**
     * 本地文件选择器的 MIME 白名单：代码文件在 Android 上常见多种 MIME
     * （部分机型甚至没有注册扩展名 → 无 MIME），这里尽量列全，让源码文件可选。
     * 选中后仍以扩展名为准（见 [BookImportSupport.detectFormat]）。
     */
    val importMimeTypes: Array<String> = arrayOf(
        "text/x-python",
        "application/x-python",
        "text/x-script.python",
        "text/javascript",
        "application/javascript",
        "application/x-javascript",
        "text/typescript",
        "application/typescript",
        "application/json",
        "application/xml",
        "text/xml",
        "text/css",
        "text/x-scss",
        "text/x-less",
        "text/x-java-source",
        "application/x-kotlin",
        "text/x-kotlin",
        "application/x-sh",
        "text/x-shellscript",
        "text/x-c",
        "text/x-csrc",
        "text/x-c++src",
        "text/x-go",
        "text/x-rust",
        "text/x-ruby",
        "application/x-httpd-php",
        "text/x-php",
        "text/x-swift",
        "text/x-vue",
        "application/toml",
        "application/x-yaml",
        "text/x-yaml",
        "application/ini",
    )
}
