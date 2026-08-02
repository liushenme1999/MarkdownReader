package space.liushenme.markdownreader.git

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import space.liushenme.markdownreader.data.local.BookContentHasher
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.local.entity.GitProjectEntity
import space.liushenme.markdownreader.data.repository.BookRepository
import space.liushenme.markdownreader.importing.BookTocEnricher
import space.liushenme.markdownreader.importing.ExtractedBookText
import space.liushenme.markdownreader.importing.ImportedBookFormat
import space.liushenme.markdownreader.importing.ParsedBookStorage
import space.liushenme.markdownreader.markdown.MarkdownPreprocessor

@Singleton
class GitDocumentOpener @Inject constructor(
    private val bookRepository: BookRepository,
) {

    /**
     * 从工作区读取文档，改写相对图片，写入/更新 parsed bundle，返回 bookId。
     */
    suspend fun openOrRefresh(
        context: Context,
        project: GitProjectEntity,
        relativePath: String,
    ): Long {
        val root = File(project.localPath)
        val file = GitProjectStorage.resolveFile(root, relativePath)
            ?: error("非法路径：$relativePath")
        if (!file.isFile) error("文件不存在：$relativePath")

        val format = ImportedBookFormat.fromFileName(file.name)
        val rawBody = file.readText(StandardCharsets.UTF_8)
        val baseDir = file.parentFile ?: root
        val rewritten = if (format == ImportedBookFormat.MARKDOWN) {
            MarkdownPreprocessor.rewriteRelativeImagesToFileUri(rawBody, baseDir)
        } else {
            rawBody
        }
        val extracted = BookTocEnricher.enrichIfEmpty(
            format,
            ExtractedBookText(body = rewritten, toc = emptyList()),
        )

        val filePath = gitFilePath(project.id, relativePath)
        val contentHash = BookContentHasher.hashForGitDocument(project.remoteUrl, relativePath)
        val title = file.nameWithoutExtension.ifBlank { file.name }
        val existing = bookRepository.getBookByGitPath(project.id, relativePath)
            ?: bookRepository.getBookByFilePath(filePath)
            ?: bookRepository.getBookByContentHash(contentHash)

        val bookId = if (existing != null) {
            val updated = existing.copy(
                title = title,
                filePath = filePath,
                importFormat = format.storedKey,
                totalChars = extracted.body.length,
                gitProjectId = project.id,
                gitRelativePath = relativePath,
                contentHash = contentHash,
                author = project.title,
            )
            bookRepository.updateBook(updated)
            val dir = ParsedBookStorage.bundleDir(context, existing.id)
            ParsedBookStorage.writeBundle(dir, extracted, coverBytes = null)
            val withBundle = updated.copy(
                parsedBundlePath = dir.absolutePath,
                currentPosition = existing.currentPosition.coerceIn(0, extracted.body.length.coerceAtLeast(0)),
            )
            bookRepository.updateBook(withBundle)
            existing.id
        } else {
            val placeholder = BookEntity(
                title = title,
                author = project.title,
                filePath = filePath,
                importFormat = format.storedKey,
                coverColor = Random.nextInt(0, 8),
                totalChars = extracted.body.length,
                addTime = Date(),
                contentHash = contentHash,
                gitProjectId = project.id,
                gitRelativePath = relativePath,
                shelfGroup = project.shelfGroup,
            )
            val id = bookRepository.addBook(placeholder)
            val dir = ParsedBookStorage.bundleDir(context, id)
            val ok = ParsedBookStorage.writeBundle(dir, extracted, coverBytes = null)
            val finalBook = placeholder.copy(
                id = id,
                parsedBundlePath = if (ok) dir.absolutePath else null,
            )
            bookRepository.updateBook(finalBook)
            id
        }
        return bookId
    }

    /** pull 后重建该项目下已打开文档的 bundle。 */
    suspend fun refreshOpenedDocuments(context: Context, project: GitProjectEntity) {
        val books = bookRepository.getBooksByGitProjectId(project.id)
        for (book in books) {
            val rel = book.gitRelativePath ?: continue
            runCatching { openOrRefresh(context, project, rel) }
        }
    }

    companion object {
        fun gitFilePath(projectId: Long, relativePath: String): String =
            "gitproj://$projectId/${relativePath.trimStart('/')}"
    }
}
