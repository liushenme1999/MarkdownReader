package space.liushenme.markdownreader.git

import android.content.Context
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import space.liushenme.markdownreader.data.local.entity.GitProjectEntity
import space.liushenme.markdownreader.data.repository.GitProjectRepository

data class GitImportOutcome(
    val projectId: Long,
    val title: String,
    val sizeBytes: Long,
    val sizeWarn: Boolean,
)

@Singleton
class GitProjectImporter @Inject constructor(
    private val gitProjectRepository: GitProjectRepository,
) {

    suspend fun importPublicRepo(
        context: Context,
        rawUrl: String,
        branch: String?,
        shelfGroup: String = "",
        isFavorite: Boolean = false,
        onProgress: (GitProgress) -> Unit = {},
    ): GitImportOutcome = withContext(Dispatchers.IO) {
        val parsed = GitHubRepoUrlParser.parse(rawUrl)
            ?: throw GitCloneException("无法识别的 GitHub 仓库地址")

        val existing = gitProjectRepository.getByRemoteUrl(parsed.httpsBrowseUrl)
        if (existing != null) {
            throw GitCloneException("该仓库已导入：${existing.title}")
        }

        onProgress(GitProgress(GitProgress.Phase.PREPARING, detail = "preparing"))
        val displayTitle = parsed.repo

        val placeholder = GitProjectEntity(
            title = displayTitle,
            remoteUrl = parsed.httpsBrowseUrl,
            defaultBranch = branch?.trim().orEmpty(),
            localPath = "",
            addTime = Date(),
            shelfGroup = shelfGroup.trim(),
            isFavorite = isFavorite,
        )
        val projectId = gitProjectRepository.insert(placeholder)
        val dest = GitProjectStorage.projectDir(context, projectId)
        try {
            val result = GitProjectCloner.clone(
                cloneUrl = parsed.cloneUrl,
                destination = dest,
                branch = branch,
                onProgress = onProgress,
            )
            val updated = placeholder.copy(
                id = projectId,
                localPath = dest.absolutePath,
                defaultBranch = result.branch,
                lastCommitSha = result.commitSha,
                lastPulledAt = Date(),
            )
            gitProjectRepository.update(updated)
            GitImportOutcome(
                projectId = projectId,
                title = displayTitle,
                sizeBytes = result.sizeBytes,
                sizeWarn = result.sizeBytes >= GitProjectStorage.SIZE_WARN_BYTES,
            )
        } catch (e: Exception) {
            runCatching {
                gitProjectRepository.getById(projectId)?.let { row ->
                    gitProjectRepository.deleteProjectCascade(
                        row.copy(localPath = dest.absolutePath),
                    )
                }
            }
            throw e
        }
    }

    suspend fun pull(
        project: GitProjectEntity,
        onProgress: (GitProgress) -> Unit = {},
    ): GitPullResult = withContext(Dispatchers.IO) {
        val result = GitProjectPuller.pull(
            localPath = project.localPath,
            branch = project.defaultBranch,
            onProgress = onProgress,
        )
        val refreshedTitle = GitHubRepoUrlParser.parse(project.remoteUrl)?.repo
            ?: project.title
        gitProjectRepository.update(
            project.copy(
                title = refreshedTitle,
                lastCommitSha = result.currentSha.ifEmpty { project.lastCommitSha },
                lastPulledAt = Date(),
            ),
        )
        result
    }

    suspend fun listRemoteBranches(project: GitProjectEntity): List<String> =
        withContext(Dispatchers.IO) {
            GitProjectBranches.listRemoteBranches(project.localPath)
        }

    suspend fun checkoutBranch(
        project: GitProjectEntity,
        branch: String,
        onProgress: (GitProgress) -> Unit = {},
    ): GitCheckoutResult = withContext(Dispatchers.IO) {
        val result = GitProjectBranches.checkoutBranch(
            localPath = project.localPath,
            branch = branch,
            onProgress = onProgress,
        )
        val refreshedTitle = GitHubRepoUrlParser.parse(project.remoteUrl)?.repo
            ?: project.title
        gitProjectRepository.update(
            project.copy(
                title = refreshedTitle,
                defaultBranch = result.branch,
                lastCommitSha = result.commitSha.ifEmpty { project.lastCommitSha },
                lastPulledAt = Date(),
            ),
        )
        result
    }

    /** 将已导入项目的 title 统一为仓库名（如 hello-agents）。 */
    suspend fun refreshDisplayTitlesIfNeeded() = withContext(Dispatchers.IO) {
        val projects = gitProjectRepository.getAllProjectsList()
        for (project in projects) {
            val parsed = GitHubRepoUrlParser.parse(project.remoteUrl) ?: continue
            if (project.title != parsed.repo) {
                gitProjectRepository.update(project.copy(title = parsed.repo))
            }
        }
    }
}
