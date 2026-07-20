package space.liushenme.markdownreader.importing

/**
 * 解析出的正文与目录（字符下标与合并后的 [body] 一致）。
 */
data class ImportedTocEntry(
    val level: Int,
    /** 去掉 inline HTML 后的纯文本，用于匹配与搜索。 */
    val title: String,
    val sourceOffset: Int,
    /** 标题行原文（可含 `<strong>` 等），用于目录/顶栏样式展示。 */
    val rawTitle: String = title,
)

data class ExtractedBookText(
    val body: String,
    val toc: List<ImportedTocEntry> = emptyList(),
    /** 解析出的封面（JPEG/PNG 等），由导入侧写入本地文件并记入 [BookEntity.coverImagePath] */
    val coverImageBytes: ByteArray? = null,
    /**
     * 书内引用的图片资源：key 形如 `<hash>.<ext>`，正文 Markdown 里以
     * `book-asset://<key>` 占位引用。落盘阶段会把这些字节写到
     * `parsed_books/<id>/assets/<key>`，并把 Markdown 中的占位替换成 `file://…`。
     */
    val assets: Map<String, ByteArray> = emptyMap()
) {
    companion object {
        fun plainBody(body: String) = ExtractedBookText(body = body, toc = emptyList(), coverImageBytes = null)
    }
}
