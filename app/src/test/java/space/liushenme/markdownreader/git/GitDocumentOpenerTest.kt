package space.liushenme.markdownreader.git

import android.app.Application
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.local.BookContentHasher
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.local.entity.GitProjectEntity
import space.liushenme.markdownreader.data.repository.IBookRepository
import space.liushenme.markdownreader.importing.ImportedBookFormat
import space.liushenme.markdownreader.importing.ParsedBookStorage

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GitDocumentOpenerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var context: Application
    private lateinit var repository: InMemoryBookRepository
    private lateinit var opener: GitDocumentOpener

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        repository = InMemoryBookRepository()
        opener = GitDocumentOpener(repository)
    }

    @Test
    fun codeDocument_preservesSourceAndRefreshesWithStableBookIdentity() = runBlocking {
        val root = tmp.newFolder("repo")
        val sourceDir = File(root, "src").apply { mkdirs() }
        val file = File(sourceDir, "App.vue")
        val firstSource = """
            # not a heading
            ![not an image](book-asset://missing.png)
            <template><button>{{ label }}</button></template>
        """.trimIndent()
        file.writeText(firstSource)
        val project = project(root)

        val bookId = opener.openOrRefresh(context, project, "src/App.vue")
        val firstBook = requireNotNull(repository.getBookById(bookId))
        val bundleDir = ParsedBookStorage.bundleDir(context, bookId)

        assertEquals(ImportedBookFormat.CODE.storedKey, firstBook.importFormat)
        assertEquals("src/App.vue", firstBook.gitRelativePath)
        assertEquals(
            BookContentHasher.hashForGitDocument(project.remoteUrl, "src/App.vue"),
            firstBook.contentHash,
        )
        assertEquals(firstSource, ParsedBookStorage.readBundle(bundleDir, ImportedBookFormat.CODE)?.body)

        val refreshedSource = "$firstSource\n<script setup>const count = 1</script>"
        file.writeText(refreshedSource)
        val refreshedBookId = opener.openOrRefresh(context, project, "src/App.vue")

        assertEquals(bookId, refreshedBookId)
        assertEquals(refreshedSource.length, repository.getBookById(bookId)?.totalChars)
        assertEquals(refreshedSource, ParsedBookStorage.readBundle(bundleDir, ImportedBookFormat.CODE)?.body)
    }

    @Test
    fun htmlFile_opensAsCodeInsteadOfHtmlDocument() = runBlocking {
        val root = tmp.newFolder("html-repo")
        val source = "<!DOCTYPE html><html><body><h1>Source</h1></body></html>"
        File(root, "page.html").writeText(source)

        val bookId = opener.openOrRefresh(context, project(root), "page.html")
        val book = requireNotNull(repository.getBookById(bookId))

        assertEquals(ImportedBookFormat.CODE.storedKey, book.importFormat)
        assertEquals(
            source,
            ParsedBookStorage.readBundle(
                ParsedBookStorage.bundleDir(context, bookId),
                ImportedBookFormat.CODE,
            )?.body,
        )
    }

    @Test
    fun codeSizeBoundaryAndEncodingFailuresUseReaderErrors() {
        val root = tmp.newFolder("errors")
        val project = project(root)
        val exact = File(root, "exact.py").apply {
            writeBytes(ByteArray(GitCodeFileReader.MAX_BYTES) { 'a'.code.toByte() })
        }
        val over = File(root, "over.py").apply {
            writeBytes(ByteArray(GitCodeFileReader.MAX_BYTES + 1) { 'a'.code.toByte() })
        }
        val invalid = File(root, "Invalid.java").apply {
            writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
        }

        runBlocking {
            val bookId = opener.openOrRefresh(context, project, exact.name)
            assertEquals(GitCodeFileReader.MAX_BYTES, repository.getBookById(bookId)?.totalChars)
        }
        val tooLarge = assertThrows(IllegalStateException::class.java) {
            runBlocking { opener.openOrRefresh(context, project, over.name) }
        }
        assertEquals(context.getString(R.string.git_code_file_too_large), tooLarge.message)
        val unsupported = assertThrows(IllegalStateException::class.java) {
            runBlocking { opener.openOrRefresh(context, project, invalid.name) }
        }
        assertEquals(context.getString(R.string.git_code_file_encoding_unsupported), unsupported.message)
    }

    private fun project(root: File) = GitProjectEntity(
        id = 1L,
        title = "sample",
        remoteUrl = "https://github.com/example/sample",
        localPath = root.absolutePath,
    )

    private class InMemoryBookRepository : IBookRepository {
        private val books = linkedMapOf<Long, BookEntity>()
        private var nextId = 1L

        override fun getAllBooks(): Flow<List<BookEntity>> = flowOf(books.values.toList())

        override fun getStandaloneBooks(): Flow<List<BookEntity>> = getAllBooks()

        override fun getFavoriteBooks(): Flow<List<BookEntity>> = getAllBooks()

        override suspend fun getBookById(id: Long): BookEntity? = books[id]

        override suspend fun getBookByFilePath(filePath: String): BookEntity? =
            books.values.firstOrNull { it.filePath == filePath }

        override suspend fun getBookByContentHash(contentHash: String): BookEntity? =
            books.values.firstOrNull { it.contentHash == contentHash }

        override suspend fun getBookByGitPath(projectId: Long, relativePath: String): BookEntity? =
            books.values.firstOrNull {
                it.gitProjectId == projectId && it.gitRelativePath == relativePath
            }

        override suspend fun getBooksByGitProjectId(projectId: Long): List<BookEntity> =
            books.values.filter { it.gitProjectId == projectId }

        override suspend fun addBook(book: BookEntity): Long {
            val id = nextId++
            books[id] = book.copy(id = id)
            return id
        }

        override suspend fun updateBook(book: BookEntity) {
            books[book.id] = book
        }

        override fun scheduleUploadBookContent(bookId: Long) = Unit

        override suspend fun deleteBook(book: BookEntity, deleteCloudBackup: Boolean) {
            books.remove(book.id)
        }

        override suspend fun updateReadingProgress(
            bookId: Long,
            progress: Float,
            position: Int,
            previewText: String,
        ) {
            books[bookId]?.let {
                books[bookId] = it.copy(
                    readingProgress = progress,
                    currentPosition = position,
                    progressPreviewText = previewText,
                )
            }
        }

        override suspend fun toggleFavorite(bookId: Long, isFavorite: Boolean) {
            books[bookId]?.let { books[bookId] = it.copy(isFavorite = isFavorite) }
        }

        override suspend fun getBookCount(): Int = books.size

        override suspend fun deleteBooksByIds(ids: Collection<Long>, deleteCloudBackup: Boolean) {
            ids.forEach(books::remove)
        }

        override suspend fun pinBooksByIds(ids: Collection<Long>, pinOrder: Long) {
            ids.forEach { id -> books[id]?.let { books[id] = it.copy(isPinned = true, pinOrder = pinOrder) } }
        }

        override suspend fun unpinBooksByIds(ids: Collection<Long>) {
            ids.forEach { id -> books[id]?.let { books[id] = it.copy(isPinned = false) } }
        }

        override suspend fun updateShelfGroupByIds(ids: Collection<Long>, groupName: String) {
            ids.forEach { id -> books[id]?.let { books[id] = it.copy(shelfGroup = groupName) } }
        }
    }
}
