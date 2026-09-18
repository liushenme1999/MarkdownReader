package space.liushenme.markdownreader.data.repository

import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import space.liushenme.markdownreader.data.local.BookContentHasher
import space.liushenme.markdownreader.data.local.dao.BookDao
import space.liushenme.markdownreader.data.local.dao.DeletedBookDao
import space.liushenme.markdownreader.data.local.dao.DeletedGitProjectDao
import space.liushenme.markdownreader.data.local.dao.GitProjectDao
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.local.entity.DeletedBookEntity
import space.liushenme.markdownreader.data.local.entity.DeletedGitProjectEntity
import space.liushenme.markdownreader.data.local.entity.GitProjectEntity
import space.liushenme.markdownreader.git.GitHubRepoUrlParser
import space.liushenme.markdownreader.git.GitProjectStorage
import space.liushenme.markdownreader.git.GitRecentOpenedPaths
import space.liushenme.markdownreader.importing.ParsedBookStorage

@Singleton
class GitProjectRepository @Inject constructor(
    private val gitProjectDao: GitProjectDao,
    private val bookDao: BookDao,
    private val deletedBookDao: DeletedBookDao,
    private val deletedGitProjectDao: DeletedGitProjectDao,
) {
    fun getAllProjects(): Flow<List<GitProjectEntity>> = gitProjectDao.getAllProjects()

    suspend fun getAllProjectsList(): List<GitProjectEntity> = gitProjectDao.getAllProjectsList()

    suspend fun getById(id: Long): GitProjectEntity? = gitProjectDao.getById(id)

    fun observeById(id: Long): Flow<GitProjectEntity?> = gitProjectDao.observeById(id)

    suspend fun getByRemoteUrl(remoteUrl: String): GitProjectEntity? {
        for (url in GitHubRepoUrlParser.lookupUrls(remoteUrl)) {
            gitProjectDao.getByRemoteUrl(url)?.let { return it }
        }
        return gitProjectDao.getAllProjectsList().firstOrNull { local ->
            GitHubRepoUrlParser.sameRepo(local.remoteUrl, remoteUrl)
        }
    }

    suspend fun insert(project: GitProjectEntity): Long {
        val canonical = GitHubRepoUrlParser.canonicalBrowseUrl(project.remoteUrl)
            ?: project.remoteUrl
        if (canonical.isNotBlank()) {
            clearTombstonesForRemote(canonical)
        }
        return gitProjectDao.insert(
            if (canonical == project.remoteUrl) project else project.copy(remoteUrl = canonical),
        )
    }

    suspend fun update(project: GitProjectEntity) = gitProjectDao.update(project)

    suspend fun markLastOpened(projectId: Long, relativePath: String) {
        val project = gitProjectDao.getById(projectId) ?: return
        val seeded = GitRecentOpenedPaths.encode(
            GitRecentOpenedPaths.resolveList(
                project.recentOpenedPathsJson,
                project.lastOpenedRelativePath,
            ),
        )
        val historyJson = GitRecentOpenedPaths.prepend(seeded, relativePath)
        gitProjectDao.update(
            project.copy(
                lastOpenedRelativePath = relativePath,
                lastOpenedAt = Date(),
                recentOpenedPathsJson = historyJson,
            ),
        )
    }

    suspend fun getBooksForProject(projectId: Long): List<BookEntity> =
        bookDao.getBooksByGitProjectId(projectId)

    suspend fun pinByIds(ids: Collection<Long>, pinOrder: Long) {
        if (ids.isEmpty()) return
        gitProjectDao.pinByIds(ids.toList(), pinOrder)
    }

    suspend fun unpinByIds(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        gitProjectDao.unpinByIds(ids.toList())
    }

    suspend fun updateShelfGroupByIds(ids: Collection<Long>, groupName: String) {
        if (ids.isEmpty()) return
        gitProjectDao.updateShelfGroupByIds(ids.toList(), groupName)
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        gitProjectDao.updateFavorite(id, isFavorite)
    }

    suspend fun updateFavoriteByIds(ids: Collection<Long>, isFavorite: Boolean) {
        if (ids.isEmpty()) return
        gitProjectDao.updateFavoriteByIds(ids.toList(), isFavorite)
    }

    suspend fun updateHasRemoteUpdate(id: Long, hasRemoteUpdate: Boolean) {
        gitProjectDao.updateHasRemoteUpdate(id, hasRemoteUpdate)
    }

    /** 删除项目、关联书籍解析包，以及本地 clone 目录。 */
    suspend fun deleteProjectCascade(project: GitProjectEntity) {
        val now = System.currentTimeMillis()
        if (project.remoteUrl.isNotBlank()) {
            val canonical = GitHubRepoUrlParser.canonicalBrowseUrl(project.remoteUrl)
                ?: project.remoteUrl
            clearTombstonesForRemote(canonical)
            deletedGitProjectDao.upsert(
                DeletedGitProjectEntity(
                    remoteUrl = canonical,
                    deletedAt = now,
                ),
            )
        }
        val books = bookDao.getBooksByGitProjectId(project.id)
        for (book in books) {
            for (hash in gitBookTombstoneHashes(project.remoteUrl, book)) {
                deletedBookDao.upsert(DeletedBookEntity(contentHash = hash, deletedAt = now))
            }
            ParsedBookStorage.deleteBundleDir(book.parsedBundlePath)
            book.coverImagePath?.let { path ->
                runCatching { java.io.File(path).delete() }
            }
        }
        if (books.isNotEmpty()) {
            bookDao.deleteBooksByIds(books.map { it.id })
        }
        GitProjectStorage.deleteProjectDir(project.localPath)
        gitProjectDao.delete(project)
    }

    private suspend fun clearTombstonesForRemote(remoteUrl: String) {
        for (url in GitHubRepoUrlParser.lookupUrls(remoteUrl)) {
            deletedGitProjectDao.deleteByRemoteUrl(url)
        }
        deletedGitProjectDao.getAll()
            .filter { GitHubRepoUrlParser.sameRepo(it.remoteUrl, remoteUrl) }
            .forEach { deletedGitProjectDao.deleteByRemoteUrl(it.remoteUrl) }
    }

    suspend fun deleteProjectsByIds(ids: Collection<Long>) {
        for (id in ids) {
            val project = gitProjectDao.getById(id) ?: continue
            deleteProjectCascade(project)
        }
    }

    private fun gitBookTombstoneHashes(remoteUrl: String, book: BookEntity): Set<String> {
        val hashes = linkedSetOf<String>()
        if (book.contentHash.isNotBlank()) hashes += book.contentHash
        val path = book.gitRelativePath?.trim().orEmpty()
        if (path.isNotEmpty() && remoteUrl.isNotBlank()) {
            hashes += BookContentHasher.hashForGitDocument(remoteUrl, path)
        }
        if (hashes.isEmpty()) {
            hashes += BookContentHasher.hashEmptyFallback(
                book.importFormat,
                book.title,
                book.filePath,
            )
        }
        return hashes
    }
}
