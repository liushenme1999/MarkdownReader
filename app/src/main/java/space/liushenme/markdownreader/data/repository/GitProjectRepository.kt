package space.liushenme.markdownreader.data.repository

import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import space.liushenme.markdownreader.data.local.dao.BookDao
import space.liushenme.markdownreader.data.local.dao.GitProjectDao
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.local.entity.GitProjectEntity
import space.liushenme.markdownreader.git.GitProjectStorage
import space.liushenme.markdownreader.git.GitRecentOpenedPaths
import space.liushenme.markdownreader.importing.ParsedBookStorage

@Singleton
class GitProjectRepository @Inject constructor(
    private val gitProjectDao: GitProjectDao,
    private val bookDao: BookDao,
) {
    fun getAllProjects(): Flow<List<GitProjectEntity>> = gitProjectDao.getAllProjects()

    suspend fun getAllProjectsList(): List<GitProjectEntity> = gitProjectDao.getAllProjectsList()

    suspend fun getById(id: Long): GitProjectEntity? = gitProjectDao.getById(id)

    suspend fun getByRemoteUrl(remoteUrl: String): GitProjectEntity? =
        gitProjectDao.getByRemoteUrl(remoteUrl)

    suspend fun insert(project: GitProjectEntity): Long = gitProjectDao.insert(project)

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

    /** 删除项目、关联书籍解析包，以及本地 clone 目录。 */
    suspend fun deleteProjectCascade(project: GitProjectEntity) {
        val books = bookDao.getBooksByGitProjectId(project.id)
        for (book in books) {
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

    suspend fun deleteProjectsByIds(ids: Collection<Long>) {
        for (id in ids) {
            val project = gitProjectDao.getById(id) ?: continue
            deleteProjectCascade(project)
        }
    }
}
