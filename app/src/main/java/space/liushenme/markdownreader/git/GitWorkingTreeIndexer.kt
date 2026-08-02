package space.liushenme.markdownreader.git

import java.io.File

sealed class GitTreeNode {
    abstract val name: String
    abstract val relativePath: String

    data class Folder(
        override val name: String,
        override val relativePath: String,
        val children: List<GitTreeNode>,
    ) : GitTreeNode()

    data class Document(
        override val name: String,
        override val relativePath: String,
        val format: DocFormat,
    ) : GitTreeNode()

    enum class DocFormat { MARKDOWN, TXT }
}

/**
 * 扫描 Git 工作区，构建可展示的文档目录树。
 * 忽略 `.git` 与常见噪音目录；资源文件留在磁盘供相对路径解析，但不出现在树中。
 */
object GitWorkingTreeIndexer {

    private val IGNORED_DIR_NAMES = setOf(
        ".git",
        "node_modules",
        ".github",
        ".idea",
        ".vscode",
        "build",
        "dist",
        "target",
        ".gradle",
        "__pycache__",
        ".svn",
        ".hg",
    )

    private val DOC_EXTENSIONS = mapOf(
        "md" to GitTreeNode.DocFormat.MARKDOWN,
        "markdown" to GitTreeNode.DocFormat.MARKDOWN,
        "mdown" to GitTreeNode.DocFormat.MARKDOWN,
        "mkd" to GitTreeNode.DocFormat.MARKDOWN,
        "txt" to GitTreeNode.DocFormat.TXT,
        "text" to GitTreeNode.DocFormat.TXT,
        "log" to GitTreeNode.DocFormat.TXT,
    )

    fun index(root: File): List<GitTreeNode> {
        if (!root.isDirectory) return emptyList()
        return buildChildren(root, relativePrefix = "")
    }

    /** 扁平列出全部文档相对路径（用于 pull 后刷新）。 */
    fun listDocumentPaths(root: File): List<String> {
        val out = ArrayList<String>()
        fun walk(nodes: List<GitTreeNode>) {
            for (node in nodes) {
                when (node) {
                    is GitTreeNode.Document -> out += node.relativePath
                    is GitTreeNode.Folder -> walk(node.children)
                }
            }
        }
        walk(index(root))
        return out
    }

    private fun buildChildren(dir: File, relativePrefix: String): List<GitTreeNode> {
        val entries = dir.listFiles() ?: return emptyList()
        val folders = ArrayList<GitTreeNode.Folder>()
        val docs = ArrayList<GitTreeNode.Document>()
        for (entry in entries.sortedBy { it.name.lowercase() }) {
            val name = entry.name
            if (name.startsWith('.') && name != ".") continue
            val rel = if (relativePrefix.isEmpty()) name else "$relativePrefix/$name"
            when {
                entry.isDirectory -> {
                    if (name.lowercase() in IGNORED_DIR_NAMES) continue
                    val children = buildChildren(entry, rel)
                    if (children.isNotEmpty()) {
                        folders += GitTreeNode.Folder(name = name, relativePath = rel, children = children)
                    }
                }
                entry.isFile -> {
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val format = DOC_EXTENSIONS[ext] ?: continue
                    docs += GitTreeNode.Document(name = name, relativePath = rel, format = format)
                }
            }
        }
        return folders + docs
    }
}
