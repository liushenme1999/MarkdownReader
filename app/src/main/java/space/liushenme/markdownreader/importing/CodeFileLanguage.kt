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
}
