package space.liushenme.markdownreader.markdown

/** 从围栏 info（如 `javascript {.line-numbers}`）解析展示用语言名。 */
internal object ReaderCodeBlockLanguage {

    fun label(info: String?): String {
        val token = info
            ?.trim()
            ?.substringBefore(' ')
            ?.substringBefore('{')
            ?.trim()
            .orEmpty()
        if (token.isEmpty()) return ""
        return DISPLAY[token.lowercase()] ?: token
    }

    private val DISPLAY = mapOf(
        "js" to "JavaScript",
        "javascript" to "JavaScript",
        "jsx" to "JavaScript",
        "mjs" to "JavaScript",
        "cjs" to "JavaScript",
        "ts" to "TypeScript",
        "typescript" to "TypeScript",
        "tsx" to "TypeScript",
        "py" to "Python",
        "pyw" to "Python",
        "python" to "Python",
        "kt" to "Kotlin",
        "kts" to "Kotlin",
        "kotlin" to "Kotlin",
        "vue" to "Vue",
        "java" to "Java",
        "c" to "C",
        "h" to "C",
        "cpp" to "C++",
        "c++" to "C++",
        "cxx" to "C++",
        "cc" to "C++",
        "hpp" to "C++",
        "cs" to "C#",
        "csharp" to "C#",
        "go" to "Go",
        "golang" to "Go",
        "rs" to "Rust",
        "rust" to "Rust",
        "rb" to "Ruby",
        "ruby" to "Ruby",
        "php" to "PHP",
        "swift" to "Swift",
        "sql" to "SQL",
        "json" to "JSON",
        "xml" to "XML",
        "html" to "HTML",
        "htm" to "HTML",
        "css" to "CSS",
        "scss" to "SCSS",
        "less" to "Less",
        "sh" to "Shell",
        "bash" to "Shell",
        "shell" to "Shell",
        "zsh" to "Shell",
        "ps1" to "PowerShell",
        "powershell" to "PowerShell",
        "yaml" to "YAML",
        "yml" to "YAML",
        "toml" to "TOML",
        "ini" to "INI",
        "properties" to "Properties",
        "md" to "Markdown",
        "markdown" to "Markdown",
        "text" to "Text",
        "txt" to "Text",
        "plaintext" to "Text",
    )
}
