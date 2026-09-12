package space.liushenme.markdownreader.git

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.local.entity.GitProjectEntity
import space.liushenme.markdownreader.data.repository.GitProjectRepository

enum class GitProjectOpKind {
    PULL,
    CLONE,
}

data class GitPullJobState(
    val projectId: Long,
    val title: String,
    val kind: GitProjectOpKind,
    val progress: GitProgress,
)

/**
 * 应用级 Git 拉取：离开项目页不取消；不同项目并行。
 */
@Singleton
class GitProjectPullCoordinator @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val gitProjectImporter: GitProjectImporter,
    private val gitProjectRepository: GitProjectRepository,
    private val gitDocumentOpener: GitDocumentOpener,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _activePulls = MutableStateFlow<Map<Long, GitPullJobState>>(emptyMap())
    val activePulls: StateFlow<Map<Long, GitPullJobState>> = _activePulls.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    fun isBusy(projectId: Long): Boolean = jobs[projectId]?.isActive == true

    fun isPulling(projectId: Long): Boolean = isBusy(projectId)

    /** @return 是否新开了拉取；已有该项目的 Git 操作时返回 false */
    fun requestPull(project: GitProjectEntity): Boolean {
        return tryStart(project.id) { runPull(project) }
    }

    /** @return 已有工作区或已开克隆为 true；已有该项目的 Git 操作时返回 false */
    fun requestEnsureCloned(project: GitProjectEntity): Boolean {
        if (GitProjectStorage.hasValidRepo(project.localPath)) return true
        return tryStart(project.id) { runClone(project) }
    }

    private fun tryStart(projectId: Long, block: suspend () -> Unit): Boolean {
        var started = false
        jobs.compute(projectId) { _, current ->
            if (current?.isActive == true) {
                current
            } else {
                started = true
                scope.launch {
                    try {
                        block()
                    } finally {
                        jobs.remove(projectId, coroutineContext[Job])
                        _activePulls.update { it - projectId }
                    }
                }
            }
        }
        return started
    }

    fun cancel(projectId: Long) {
        jobs.remove(projectId)?.cancel()
        _activePulls.update { it - projectId }
    }

    fun cancelAll(ids: Collection<Long>) {
        ids.forEach { cancel(it) }
    }

    private suspend fun runPull(project: GitProjectEntity) {
        try {
            publish(project, GitProjectOpKind.PULL, GitProgress(GitProgress.Phase.FETCHING))
            val result = gitProjectImporter.pull(project) { prog ->
                publish(project, GitProjectOpKind.PULL, prog)
            }
            val refreshed = gitProjectRepository.getById(project.id) ?: project
            if (result.changed) {
                gitDocumentOpener.refreshOpenedDocuments(appContext, refreshed)
                val shortSha = refreshed.lastCommitSha.take(7)
                emitToast(
                    appContext.getString(
                        R.string.toast_git_pull_success_project,
                        refreshed.title,
                        shortSha,
                    ),
                )
            } else {
                emitToast(
                    appContext.getString(
                        R.string.toast_git_pull_uptodate_project,
                        refreshed.title,
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emitToast(mapError(e, project.title))
        }
    }

    private suspend fun runClone(project: GitProjectEntity) {
        try {
            publish(project, GitProjectOpKind.CLONE, GitProgress(GitProgress.Phase.CLONING))
            gitProjectImporter.ensureCloned(project) { prog ->
                publish(project, GitProjectOpKind.CLONE, prog)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emitToast(mapError(e, project.title))
        }
    }

    private fun publish(
        project: GitProjectEntity,
        kind: GitProjectOpKind,
        progress: GitProgress,
    ) {
        _activePulls.update { current ->
            current + (
                project.id to GitPullJobState(
                    projectId = project.id,
                    title = project.title,
                    kind = kind,
                    progress = progress,
                )
                )
        }
    }

    private fun emitToast(message: String) {
        if (_toasts.subscriptionCount.value > 0) {
            _toasts.tryEmit(message)
        } else {
            mainHandler.post {
                Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mapError(e: Exception, title: String): String {
        return when {
            e is GitCloneException && e.isLikelyPrivateOrMissing ->
                appContext.getString(R.string.toast_git_import_private)
            else -> appContext.getString(
                R.string.toast_git_pull_failed_project,
                title,
                e.message ?: appContext.getString(R.string.error_unknown),
            )
        }
    }
}
