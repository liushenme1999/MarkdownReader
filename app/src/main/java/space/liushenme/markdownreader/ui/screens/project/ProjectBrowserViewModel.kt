package space.liushenme.markdownreader.ui.screens.project

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.local.entity.GitProjectEntity
import space.liushenme.markdownreader.data.repository.GitProjectRepository
import space.liushenme.markdownreader.data.repository.ReaderSettingsRepository
import space.liushenme.markdownreader.git.GitCloneException
import space.liushenme.markdownreader.git.GitDocumentOpener
import space.liushenme.markdownreader.git.GitPathUtils
import space.liushenme.markdownreader.git.GitProgress
import space.liushenme.markdownreader.git.GitProjectImporter
import space.liushenme.markdownreader.git.GitRecentOpenedPaths
import space.liushenme.markdownreader.git.GitTreeNode
import space.liushenme.markdownreader.git.GitWorkingTreeIndexer
import space.liushenme.markdownreader.model.GitProjectRecentReadCount

data class RecentReadEntry(
    val relativePath: String,
    /** 0f–1f */
    val readingProgress: Float,
)

@HiltViewModel
class ProjectBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gitProjectRepository: GitProjectRepository,
    private val gitProjectImporter: GitProjectImporter,
    private val gitDocumentOpener: GitDocumentOpener,
    readerSettingsRepository: ReaderSettingsRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val projectId: Long = savedStateHandle.get<String>("projectId")?.toLongOrNull() ?: 0L

    private val _projectState = MutableStateFlow<GitProjectEntity?>(null)
    val projectState: StateFlow<GitProjectEntity?> = _projectState.asStateFlow()

    private val _tree = MutableStateFlow<List<GitTreeNode>>(emptyList())
    val tree: StateFlow<List<GitTreeNode>> = _tree.asStateFlow()

    private val _expandedPaths = MutableStateFlow<Set<String>>(emptySet())
    val expandedPaths: StateFlow<Set<String>> = _expandedPaths.asStateFlow()

    private val _lastOpenedRelativePath = MutableStateFlow<String?>(null)
    val lastOpenedRelativePath: StateFlow<String?> = _lastOpenedRelativePath.asStateFlow()

    private val _recentReadEntries = MutableStateFlow<List<RecentReadEntry>>(emptyList())
    val recentReadEntries: StateFlow<List<RecentReadEntry>> = _recentReadEntries.asStateFlow()

    val recentReadDisplayCount = readerSettingsRepository.gitProjectRecentReadCount
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            GitProjectRecentReadCount.DEFAULT,
        )

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _progress = MutableStateFlow<GitProgress?>(null)
    val progress: StateFlow<GitProgress?> = _progress.asStateFlow()

    /** 忙碌时顶部进度条旁的说明文案资源 id */
    private val _busyStatusRes = MutableStateFlow<Int?>(null)
    val busyStatusRes: StateFlow<Int?> = _busyStatusRes.asStateFlow()

    private val _remoteBranches = MutableStateFlow<List<String>>(emptyList())
    val remoteBranches: StateFlow<List<String>> = _remoteBranches.asStateFlow()

    private val _loadingBranches = MutableStateFlow(false)
    val loadingBranches: StateFlow<Boolean> = _loadingBranches.asStateFlow()

    private val toastChannel = Channel<String>(Channel.BUFFERED)
    val toastMessages = toastChannel.receiveAsFlow()

    private val openReaderChannel = Channel<Long>(Channel.BUFFERED)
    val openReaderRequests = openReaderChannel.receiveAsFlow()

    private val deletedChannel = Channel<Unit>(Channel.BUFFERED)
    val deletedEvents = deletedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            refreshProjectAndTree()
        }
    }

    fun toggleFolder(path: String) {
        val current = _expandedPaths.value
        _expandedPaths.value = if (path in current) current - path else current + path
    }

    fun pull() {
        val p = _projectState.value ?: return
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _busyStatusRes.value = R.string.git_project_pulling_title
            _progress.value = GitProgress(GitProgress.Phase.FETCHING)
            try {
                val result = gitProjectImporter.pull(p) { prog ->
                    _progress.value = prog
                }
                val refreshed = gitProjectRepository.getById(p.id)
                if (refreshed != null) {
                    applyProjectState(refreshed)
                    if (result.changed) {
                        withContext(Dispatchers.IO) {
                            gitDocumentOpener.refreshOpenedDocuments(appContext, refreshed)
                        }
                        reloadTree(refreshed)
                        val shortSha = refreshed.lastCommitSha.take(7)
                        toastChannel.trySend(
                            appContext.getString(R.string.toast_git_pull_success, shortSha),
                        )
                    } else {
                        toastChannel.trySend(appContext.getString(R.string.toast_git_pull_uptodate))
                    }
                }
            } catch (e: Exception) {
                toastChannel.trySend(mapGitError(e, R.string.toast_git_pull_failed))
            } finally {
                _busy.value = false
                _progress.value = null
                _busyStatusRes.value = null
            }
        }
    }

    fun loadRemoteBranches() {
        val p = _projectState.value ?: return
        if (_loadingBranches.value) return
        viewModelScope.launch {
            _loadingBranches.value = true
            _remoteBranches.value = emptyList()
            try {
                val branches = gitProjectImporter.listRemoteBranches(p)
                _remoteBranches.value = branches
                if (branches.isEmpty()) {
                    toastChannel.trySend(appContext.getString(R.string.toast_git_branch_list_empty))
                }
            } catch (e: Exception) {
                toastChannel.trySend(mapGitError(e, R.string.toast_git_branch_list_failed))
            } finally {
                _loadingBranches.value = false
            }
        }
    }

    fun checkoutBranch(branch: String) {
        val p = _projectState.value ?: return
        val target = branch.trim()
        if (target.isEmpty() || _busy.value) return
        if (target == p.defaultBranch) {
            toastChannel.trySend(appContext.getString(R.string.toast_git_branch_already_current))
            return
        }
        viewModelScope.launch {
            _busy.value = true
            _busyStatusRes.value = R.string.git_project_switching_branch_title
            _progress.value = GitProgress(GitProgress.Phase.FETCHING, detail = target)
            try {
                gitProjectImporter.checkoutBranch(p, target) { prog ->
                    _progress.value = prog
                }
                val refreshed = gitProjectRepository.getById(p.id)
                if (refreshed != null) {
                    applyProjectState(refreshed)
                    withContext(Dispatchers.IO) {
                        gitDocumentOpener.refreshOpenedDocuments(appContext, refreshed)
                    }
                    reloadTree(refreshed)
                    toastChannel.trySend(
                        appContext.getString(R.string.toast_git_branch_switched, target),
                    )
                }
            } catch (e: Exception) {
                toastChannel.trySend(mapGitError(e, R.string.toast_git_branch_switch_failed))
            } finally {
                _busy.value = false
                _progress.value = null
                _busyStatusRes.value = null
            }
        }
    }

    fun openDocument(relativePath: String) {
        val p = _projectState.value ?: return
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val bookId = withContext(Dispatchers.IO) {
                    gitDocumentOpener.openOrRefresh(appContext, p, relativePath)
                }
                withContext(Dispatchers.IO) {
                    gitProjectRepository.markLastOpened(p.id, relativePath)
                }
                val refreshed = gitProjectRepository.getById(p.id)
                if (refreshed != null) {
                    applyProjectState(refreshed)
                } else {
                    _lastOpenedRelativePath.value = relativePath
                }
                openReaderChannel.trySend(bookId)
            } catch (e: Exception) {
                toastChannel.trySend(
                    appContext.getString(
                        R.string.toast_git_open_failed,
                        e.message ?: appContext.getString(R.string.error_unknown),
                    ),
                )
            } finally {
                _busy.value = false
            }
        }
    }

    fun deleteProject() {
        val p = _projectState.value ?: return
        viewModelScope.launch {
            _busy.value = true
            try {
                withContext(Dispatchers.IO) {
                    gitProjectRepository.deleteProjectCascade(p)
                }
                toastChannel.trySend(
                    appContext.getString(R.string.toast_git_project_deleted, p.title),
                )
                deletedChannel.trySend(Unit)
            } catch (e: Exception) {
                toastChannel.trySend(
                    appContext.getString(
                        R.string.toast_git_import_failed,
                        e.message ?: appContext.getString(R.string.error_unknown),
                    ),
                )
            } finally {
                _busy.value = false
            }
        }
    }

    private fun mapGitError(e: Exception, genericRes: Int): String {
        return when {
            e is GitCloneException && e.isLikelyPrivateOrMissing ->
                appContext.getString(R.string.toast_git_import_private)
            else -> appContext.getString(
                genericRes,
                e.message ?: appContext.getString(R.string.error_unknown),
            )
        }
    }

    /** 从阅读器返回时刷新最近阅读进度。 */
    fun refreshRecentReadProgress() {
        val p = _projectState.value ?: return
        viewModelScope.launch {
            applyProjectState(p)
        }
    }

    private suspend fun refreshProjectAndTree() {
        val p = withContext(Dispatchers.IO) { gitProjectRepository.getById(projectId) }
        if (p != null) {
            applyProjectState(p)
            reloadTree(p)
        } else {
            _projectState.value = null
            _lastOpenedRelativePath.value = null
            _recentReadEntries.value = emptyList()
        }
    }

    private suspend fun applyProjectState(project: GitProjectEntity) {
        val latest = withContext(Dispatchers.IO) {
            gitProjectRepository.getById(project.id) ?: project
        }
        _projectState.value = latest
        _lastOpenedRelativePath.value = latest.lastOpenedRelativePath
        val paths = GitRecentOpenedPaths.resolveList(
            latest.recentOpenedPathsJson,
            latest.lastOpenedRelativePath,
        )
        val progressByPath = withContext(Dispatchers.IO) {
            gitProjectRepository.getBooksForProject(latest.id)
                .mapNotNull { book ->
                    val rel = book.gitRelativePath?.trim()?.takeIf { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    rel to book.readingProgress.coerceIn(0f, 1f)
                }
                .toMap()
        }
        _recentReadEntries.value = paths.map { path ->
            RecentReadEntry(
                relativePath = path,
                readingProgress = progressByPath[path] ?: 0f,
            )
        }
    }

    private suspend fun reloadTree(project: GitProjectEntity) {
        val nodes = withContext(Dispatchers.IO) {
            GitWorkingTreeIndexer.index(File(project.localPath))
        }
        _tree.value = nodes
        val lastOpened = project.lastOpenedRelativePath
        _expandedPaths.value = if (!lastOpened.isNullOrBlank()) {
            val ancestors = GitPathUtils.ancestorPaths(lastOpened)
            if (ancestors.isNotEmpty()) {
                ancestors
            } else {
                nodes.filterIsInstance<GitTreeNode.Folder>().map { it.relativePath }.toSet()
            }
        } else {
            nodes.filterIsInstance<GitTreeNode.Folder>().map { it.relativePath }.toSet()
        }
    }
}
