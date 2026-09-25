package space.liushenme.markdownreader.ui.screens.bookshelf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.backup.BackupManager
import space.liushenme.markdownreader.data.local.BookContentHasher
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.data.local.entity.GitProjectEntity
import space.liushenme.markdownreader.data.local.entity.ShelfGroups
import space.liushenme.markdownreader.data.repository.BookRepository
import space.liushenme.markdownreader.data.repository.GitProjectRepository
import space.liushenme.markdownreader.data.repository.ReaderSettingsRepository
import space.liushenme.markdownreader.data.repository.ShelfGroupRepository
import space.liushenme.markdownreader.git.GitCloneException
import space.liushenme.markdownreader.git.GitHubRepoUrlParser
import space.liushenme.markdownreader.git.GitProgress
import space.liushenme.markdownreader.git.GitProjectImporter
import space.liushenme.markdownreader.git.GitProjectPullCoordinator
import space.liushenme.markdownreader.git.GitProjectRemote
import space.liushenme.markdownreader.git.GitProjectStorage
import space.liushenme.markdownreader.git.GitRepoLock
import space.liushenme.markdownreader.model.BookshelfGridColumns
import space.liushenme.markdownreader.model.BookshelfLayoutMode
import space.liushenme.markdownreader.importing.BookContentLoader
import space.liushenme.markdownreader.importing.BookImportSupport
import space.liushenme.markdownreader.importing.BookTocEnricher
import space.liushenme.markdownreader.importing.ExtractedBookText
import space.liushenme.markdownreader.importing.ImportedBookFormat
import space.liushenme.markdownreader.importing.ParsedBookStorage
import space.liushenme.markdownreader.importing.PdfReaderContent
import space.liushenme.markdownreader.importing.UrlBookDownloader
import space.liushenme.markdownreader.markdown.DiagramImageLoader
import space.liushenme.markdownreader.markdown.MarkdownPreprocessor
import space.liushenme.markdownreader.markdown.NetworkImageCache
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val gitProjectRepository: GitProjectRepository,
    private val gitProjectImporter: GitProjectImporter,
    private val shelfGroupRepository: ShelfGroupRepository,
    private val readerSettingsRepository: ReaderSettingsRepository,
    private val backupManager: BackupManager,
    private val gitPullCoordinator: GitProjectPullCoordinator,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val books = bookRepository.getStandaloneBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val gitProjects = gitProjectRepository.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shelfGroups = shelfGroupRepository.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val layoutMode = readerSettingsRepository.bookshelfLayoutMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookshelfLayoutMode.Grid)

    val gridColumns = readerSettingsRepository.bookshelfGridColumns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookshelfGridColumns.DEFAULT)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val toastChannel = Channel<String>(Channel.BUFFERED)
    val toastMessages = toastChannel.receiveAsFlow()
    val gitPullToasts = gitPullCoordinator.toasts

    val pullingProjectIds = gitPullCoordinator.activePulls
        .map { it.keys }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val readerOpenRequestChannel = Channel<Long>(Channel.BUFFERED)
    val readerOpenRequests = readerOpenRequestChannel.receiveAsFlow()

    private val projectOpenRequestChannel = Channel<Long>(Channel.BUFFERED)
    val projectOpenRequests = projectOpenRequestChannel.receiveAsFlow()

    private val _gitImportProgress = MutableStateFlow<GitProgress?>(null)
    val gitImportProgress: StateFlow<GitProgress?> = _gitImportProgress.asStateFlow()

    private val _gitImporting = MutableStateFlow(false)
    val gitImporting: StateFlow<Boolean> = _gitImporting.asStateFlow()

    private val _localImportJobs = MutableStateFlow<List<LocalImportJob>>(emptyList())
    val localImportJobs: StateFlow<List<LocalImportJob>> = _localImportJobs.asStateFlow()

    private val remoteCheckMutex = Mutex()

    init {
        viewModelScope.launch {
            shelfGroupRepository.syncFromBooks()
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { gitProjectImporter.refreshDisplayTitlesIfNeeded() }
        }
        viewModelScope.launch {
            delay(600)
            checkRemoteGitUpdates()
        }
    }

    fun importFromGitHub(
        rawUrl: String,
        branch: String?,
        target: BookImportTarget = BookImportTarget.None,
    ) {
        if (_gitImporting.value) return
        if (GitHubRepoUrlParser.parse(rawUrl) == null) {
            toastChannel.trySend(appContext.getString(R.string.toast_git_import_invalid))
            return
        }
        viewModelScope.launch {
            _gitImporting.value = true
            _gitImportProgress.value = GitProgress(GitProgress.Phase.PREPARING)
            try {
                val outcome = gitProjectImporter.importPublicRepo(
                    context = appContext,
                    rawUrl = rawUrl,
                    branch = branch,
                    shelfGroup = target.shelfGroup,
                    isFavorite = target.isFavorite,
                    onProgress = { _gitImportProgress.value = it },
                )
                toastChannel.trySend(
                    appContext.getString(R.string.toast_git_import_success, outcome.title),
                )
                val backupError = withContext(Dispatchers.IO) {
                    backupManager.backupIfConfigured().exceptionOrNull()
                }
                if (backupError != null) {
                    toastChannel.trySend(
                        appContext.getString(
                            R.string.backup_toast_backup_failed,
                            backupError.message ?: appContext.getString(R.string.error_unknown),
                        ),
                    )
                }
                if (outcome.sizeWarn) {
                    val mb = (outcome.sizeBytes / (1024L * 1024L)).toInt().coerceAtLeast(1)
                    toastChannel.trySend(
                        appContext.getString(R.string.toast_git_import_size_warn, mb),
                    )
                }
                projectOpenRequestChannel.trySend(outcome.projectId)
            } catch (e: Exception) {
                val msg = when {
                    e is GitCloneException && e.message?.contains("已导入") == true ->
                        appContext.getString(R.string.toast_git_import_already_exists)
                    e is GitCloneException && e.isLikelyPrivateOrMissing ->
                        appContext.getString(R.string.toast_git_import_private)
                    else -> appContext.getString(
                        R.string.toast_git_import_failed,
                        e.message ?: appContext.getString(R.string.error_unknown),
                    )
                }
                toastChannel.trySend(msg)
            } finally {
                _gitImporting.value = false
                _gitImportProgress.value = null
            }
        }
    }

    fun syncProgressWithCloud() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = withContext(Dispatchers.IO) {
                backupManager.syncReadingProgress()
            }
            _isRefreshing.value = false
            result.fold(
                onSuccess = { sync ->
                    val msg = when {
                        sync.pulledCount > 0 && sync.pushed ->
                            appContext.getString(
                                R.string.bookshelf_toast_sync_pulled_and_pushed,
                                sync.pulledCount,
                            )
                        sync.pulledCount > 0 ->
                            appContext.getString(
                                R.string.bookshelf_toast_sync_pulled,
                                sync.pulledCount,
                            )
                        sync.pushed ->
                            appContext.getString(R.string.bookshelf_toast_sync_pushed)
                        else ->
                            appContext.getString(R.string.bookshelf_toast_sync_uptodate)
                    }
                    toastChannel.trySend(msg)
                    checkRemoteGitUpdates()
                },
                onFailure = {
                    toastChannel.trySend(
                        appContext.getString(
                            R.string.bookshelf_toast_sync_failed,
                            it.localizedMessage ?: it.toString(),
                        ),
                    )
                },
            )
        }
    }

    private suspend fun checkRemoteGitUpdates() {
        remoteCheckMutex.withLock {
            withContext(Dispatchers.IO) {
                val projects = gitProjectRepository.getAllProjectsList()
                if (projects.isEmpty()) return@withContext
                val semaphore = Semaphore(2)
                coroutineScope {
                    projects.map { project ->
                        async {
                            semaphore.withPermit {
                                checkOneRemoteUpdate(project)
                            }
                        }
                    }.awaitAll()
                }
            }
        }
    }

    private suspend fun checkOneRemoteUpdate(project: GitProjectEntity) {
        val parsed = GitHubRepoUrlParser.parse(project.remoteUrl) ?: return
        val branch = project.defaultBranch.trim()
        if (branch.isEmpty()) return
        val remoteSha = run {
            val pathKey = GitRepoLock.keyForPath(project.localPath)
            if (pathKey.isNotEmpty()) {
                GitRepoLock.withLock(pathKey) {
                    GitProjectRemote.checkBranchHead(parsed.cloneUrl, branch)
                }
            } else {
                GitProjectRemote.checkBranchHead(parsed.cloneUrl, branch)
            }
        } ?: return
        val flag = GitProjectRemote.shouldMarkRemoteUpdate(
            localSha = project.lastCommitSha,
            remoteSha = remoteSha,
            hasLocalRepo = GitProjectStorage.hasValidRepo(project.localPath),
        )
        if (flag != project.hasRemoteUpdate) {
            gitProjectRepository.updateHasRemoteUpdate(project.id, flag)
        }
    }

    fun importFromLocalUris(
        context: Context,
        uris: List<Uri>,
        target: BookImportTarget = BookImportTarget.None,
    ) {
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            importFromLocalUri(context, uris[0], target = target)
            return
        }
        viewModelScope.launch {
            var successCount = 0
            var duplicateCount = 0
            var failCount = 0
            for (uri in uris) {
                when (
                    importLocalUriInternal(
                        context = context,
                        uri = uri,
                        notify = false,
                        target = target,
                    )
                ) {
                    is LocalImportOutcome.Success -> successCount++
                    is LocalImportOutcome.Duplicate -> duplicateCount++
                    is LocalImportOutcome.Failed -> failCount++
                }
            }
            val skippedCount = duplicateCount + failCount
            toastChannel.trySend(
                when {
                    successCount > 0 && skippedCount == 0 ->
                        appContext.getString(R.string.toast_import_batch_success, successCount)
                    successCount > 0 ->
                        appContext.getString(
                            R.string.toast_import_batch_partial,
                            successCount,
                            skippedCount,
                        )
                    duplicateCount > 0 && failCount == 0 ->
                        appContext.getString(R.string.toast_import_already_exists)
                    else -> appContext.getString(R.string.toast_import_batch_failed)
                }
            )
        }
    }

    fun importFromLocalUri(
        context: Context,
        uri: Uri,
        openReaderWhenDone: Boolean = false,
        target: BookImportTarget = BookImportTarget.None,
    ) {
        viewModelScope.launch {
            val outcome = importLocalUriInternal(
                context = context,
                uri = uri,
                notify = true,
                target = target,
            )
            if (openReaderWhenDone &&
                outcome is LocalImportOutcome.Success &&
                outcome.bookId > 0L
            ) {
                readerOpenRequestChannel.trySend(outcome.bookId)
            }
        }
    }

    fun notifyImportInProgress() {
        toastChannel.trySend(appContext.getString(R.string.bookshelf_importing_wait))
    }

    private fun upsertImportJob(job: LocalImportJob) {
        _localImportJobs.update { list ->
            val index = list.indexOfFirst { it.jobId == job.jobId }
            if (index < 0) {
                listOf(job) + list
            } else {
                list.toMutableList().also { it[index] = job }
            }
        }
    }

    private fun removeImportJob(jobId: String) {
        _localImportJobs.update { list -> list.filterNot { it.jobId == jobId } }
    }

    private fun deleteStagedImportDir(stagedAssetsDir: File?) {
        val root = stagedAssetsDir?.let { dir ->
            if (dir.name == ParsedBookStorage.ASSETS_DIR) dir.parentFile else dir
        } ?: return
        if (root.name.startsWith("pdf_import_") || root.name.startsWith("bundle_staging_")) {
            runCatching { root.deleteRecursively() }
        }
    }

    private fun publishStagedBundle(staging: File, dest: File): Boolean {
        if (dest.exists()) dest.deleteRecursively()
        dest.parentFile?.mkdirs()
        if (staging.renameTo(dest) && dest.isDirectory) return true
        return runCatching {
            staging.copyRecursively(dest, overwrite = true)
            true
        }.getOrDefault(false)
    }

    private suspend fun importLocalUriInternal(
        context: Context,
        uri: Uri,
        notify: Boolean,
        target: BookImportTarget,
    ): LocalImportOutcome {
        var jobId: String? = null
        var stagedAssetsDir: File? = null
        return try {
            val filePath = uri.toString()
            if (bookRepository.getBookByFilePath(filePath) != null) {
                if (notify) {
                    toastChannel.trySend(
                        appContext.getString(R.string.toast_import_already_exists)
                    )
                }
                return LocalImportOutcome.Duplicate
            }
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // 部分来源不支持持久权限，仍尝试当前会话内读取
            }
            val fileName = BookImportSupport.displayNameFromUri(context, uri)
                ?: appContext.getString(R.string.book_untitled)
            val mime = context.contentResolver.getType(uri)
            if (BookImportSupport.isRemovedFormat(fileName, mime)) {
                if (notify) {
                    toastChannel.trySend(
                        appContext.getString(R.string.toast_import_unsupported_format)
                    )
                }
                return LocalImportOutcome.Failed
            }
            val format = BookImportSupport.detectFormat(fileName, mime)
            val title = BookImportSupport.stripKnownExtension(fileName)
            if (format.isPdf) {
                jobId = UUID.randomUUID().toString()
                upsertImportJob(
                    LocalImportJob(
                        jobId = jobId,
                        title = title.ifBlank { appContext.getString(R.string.book_untitled) },
                    ),
                )
            }
            val load = withContext(Dispatchers.IO) {
                BookContentLoader.loadForImport(context, uri, format) { done, total, cover ->
                    val id = jobId ?: return@loadForImport
                    upsertImportJob(
                        LocalImportJob(
                            jobId = id,
                            title = title.ifBlank { appContext.getString(R.string.book_untitled) },
                            current = done,
                            total = total,
                            coverJpeg = cover,
                        ),
                    )
                }
            }
            stagedAssetsDir = load.stagedAssetsDir
            val extracted = load.extracted
            if (notify && extracted.body.isBlank() && format.hasBuiltInTextExtract) {
                toastChannel.trySend(appContext.getString(R.string.toast_import_parse_failed))
            }
            jobId?.let(::removeImportJob)
            jobId = null
            val bookId = persistImportedBook(
                importContext = context,
                title = title,
                extracted = extracted,
                filePath = filePath,
                format = format,
                notify = notify,
                target = target,
                stagedAssetsDir = stagedAssetsDir,
            )
            if (bookId > 0L) {
                LocalImportOutcome.Success(bookId)
            } else {
                LocalImportOutcome.Failed
            }
        } catch (e: Exception) {
            if (notify) {
                toastChannel.trySend(
                    appContext.getString(
                        R.string.toast_import_failed,
                        e.message ?: appContext.getString(R.string.error_unknown),
                    )
                )
            }
            LocalImportOutcome.Failed
        } finally {
            jobId?.let(::removeImportJob)
            deleteStagedImportDir(stagedAssetsDir)
        }
    }

    fun importFromUrl(
        urlRaw: String,
        target: BookImportTarget = BookImportTarget.None,
    ) {
        val url = BookImportSupport.normalizeImportUrl(urlRaw)
        if (url == null) {
            toastChannel.trySend(
                if (urlRaw.trim().isEmpty()) {
                    appContext.getString(R.string.toast_import_url_empty)
                } else {
                    appContext.getString(R.string.toast_import_url_invalid_scheme)
                }
            )
            return
        }
        viewModelScope.launch {
            var jobId: String? = null
            var stagedAssetsDir: File? = null
            try {
                if (bookRepository.getBookByFilePath(url) != null) {
                    toastChannel.trySend(
                        appContext.getString(R.string.toast_import_already_exists)
                    )
                    return@launch
                }
                val result = withContext(Dispatchers.IO) { UrlBookDownloader.download(url) }
                val name = result.suggestedFileName ?: url.substringAfterLast('/').substringBefore('?')
                if (BookImportSupport.isRemovedFormat(name, result.contentType)) {
                    toastChannel.trySend(
                        appContext.getString(R.string.toast_import_unsupported_format)
                    )
                    return@launch
                }
                val format = BookImportSupport.detectFormat(name, result.contentType)
                val title = BookImportSupport.stripKnownExtension(
                    name.ifBlank { appContext.getString(R.string.book_from_network) }
                )
                if (format.isPdf) {
                    jobId = UUID.randomUUID().toString()
                    upsertImportJob(
                        LocalImportJob(
                            jobId = jobId,
                            title = title.ifBlank { appContext.getString(R.string.book_untitled) },
                        ),
                    )
                }
                val load = withContext(Dispatchers.IO) {
                    BookContentLoader.loadUrlBytesForImport(
                        appContext,
                        result.bytes,
                        format,
                        result.charsetFromHeader,
                    ) { done, total, cover ->
                        val id = jobId ?: return@loadUrlBytesForImport
                        upsertImportJob(
                            LocalImportJob(
                                jobId = id,
                                title = title.ifBlank { appContext.getString(R.string.book_untitled) },
                                current = done,
                                total = total,
                                coverJpeg = cover,
                            ),
                        )
                    }
                }
                stagedAssetsDir = load.stagedAssetsDir
                val extracted = load.extracted
                if (extracted.body.isBlank() && format.hasBuiltInTextExtract) {
                    toastChannel.trySend(appContext.getString(R.string.toast_import_download_empty))
                }
                jobId?.let(::removeImportJob)
                jobId = null
                persistImportedBook(
                    importContext = appContext,
                    title = title,
                    extracted = extracted,
                    filePath = url,
                    format = format,
                    target = target,
                    stagedAssetsDir = stagedAssetsDir,
                )
            } catch (e: Exception) {
                toastChannel.trySend(
                    appContext.getString(
                        R.string.toast_import_from_url_failed,
                        e.message ?: appContext.getString(R.string.error_network),
                    )
                )
            } finally {
                jobId?.let(::removeImportJob)
                deleteStagedImportDir(stagedAssetsDir)
            }
        }
    }

    private suspend fun persistImportedBook(
        importContext: Context,
        title: String,
        extracted: ExtractedBookText,
        filePath: String,
        format: ImportedBookFormat,
        notify: Boolean = true,
        target: BookImportTarget = BookImportTarget.None,
        stagedAssetsDir: File? = null,
    ): Long {
        val shelfGroup = target.shelfGroup.trim()
        if (shelfGroup.isNotEmpty()) {
            shelfGroupRepository.ensureGroup(shelfGroup)
        }
        val enriched = BookTocEnricher.enrichIfEmpty(format, extracted)
        val enrichedForStore = prepareImportedContent(importContext, format, enriched)
        val content = enrichedForStore.body
        val resolvedTitle = title.ifBlank { appContext.getString(R.string.book_untitled) }
        if (format.isPdf &&
            (stagedAssetsDir == null || !PdfReaderContent.looksLikePdfBody(content))
        ) {
            if (notify) {
                toastChannel.trySend(appContext.getString(R.string.toast_import_parse_failed))
            }
            return 0L
        }
        val contentHash = if (format.isPdf && stagedAssetsDir != null) {
            BookContentHasher.hashPdfFromAssetsDir(content, stagedAssetsDir)
                ?: run {
                    if (notify) {
                        toastChannel.trySend(appContext.getString(R.string.toast_import_parse_failed))
                    }
                    return 0L
                }
        } else {
            BookContentHasher.hashForBook(
                body = content,
                importFormat = format.storedKey,
                title = resolvedTitle,
                filePath = filePath,
                assets = enrichedForStore.assets,
            )
        }
        val existingByHash = bookRepository.getBookByContentHash(contentHash)
        if (existingByHash != null) {
            bookRepository.scheduleUploadBookContent(existingByHash.id)
            if (notify) {
                toastChannel.trySend(appContext.getString(R.string.toast_import_already_exists))
            }
            return existingByHash.id
        }
        val author = BookImportSupport.extractAuthorFromContent(content)
        val stagingBundle = when {
            stagedAssetsDir != null -> {
                val root = if (stagedAssetsDir.name == ParsedBookStorage.ASSETS_DIR) {
                    stagedAssetsDir.parentFile
                } else {
                    stagedAssetsDir
                }
                root ?: File(appContext.cacheDir, "bundle_staging_${System.nanoTime()}").apply { mkdirs() }
            }
            else -> File(appContext.cacheDir, "bundle_staging_${System.nanoTime()}").apply { mkdirs() }
        }
        val stagedOk = withContext(Dispatchers.IO) {
            val ok = ParsedBookStorage.writeBundle(
                dir = stagingBundle,
                extracted = enrichedForStore,
                coverBytes = enriched.coverImageBytes,
            )
            ok && ParsedBookStorage.isCompleteBundle(stagingBundle, format.storedKey)
        }
        if (!stagedOk) {
            runCatching { stagingBundle.deleteRecursively() }
            if (notify) {
                toastChannel.trySend(appContext.getString(R.string.toast_import_write_failed))
            }
            return 0L
        }
        val coverFile = when {
            File(stagingBundle, ParsedBookStorage.COVER_JPG).isFile ->
                File(stagingBundle, ParsedBookStorage.COVER_JPG)
            File(stagingBundle, ParsedBookStorage.COVER_PNG).isFile ->
                File(stagingBundle, ParsedBookStorage.COVER_PNG)
            else -> null
        }
        val book = BookEntity(
            title = resolvedTitle,
            author = author,
            filePath = filePath,
            importFormat = format.storedKey,
            coverColor = Random.nextInt(BOOK_COVER_COLOR_COUNT),
            totalChars = content.length,
            addTime = Date(),
            shelfGroup = shelfGroup,
            isFavorite = target.isFavorite,
            contentHash = contentHash,
            parsedBundlePath = stagingBundle.absolutePath,
            coverImagePath = coverFile?.absolutePath,
        )
        val id = bookRepository.addBook(book)
        if (id <= 0L) {
            runCatching { stagingBundle.deleteRecursively() }
            if (notify) {
                toastChannel.trySend(appContext.getString(R.string.toast_import_write_failed))
            }
            return 0L
        }
        val writeOk = withContext(Dispatchers.IO) {
            val dir = ParsedBookStorage.bundleDir(appContext, id)
            if (!publishStagedBundle(stagingBundle, dir)) {
                ParsedBookStorage.deleteBundleDir(dir.absolutePath)
                return@withContext false
            }
            if (!ParsedBookStorage.isCompleteBundle(dir, format.storedKey)) {
                ParsedBookStorage.deleteBundleDir(dir.absolutePath)
                return@withContext false
            }
            val publishedCover = when {
                File(dir, ParsedBookStorage.COVER_JPG).isFile ->
                    File(dir, ParsedBookStorage.COVER_JPG)
                File(dir, ParsedBookStorage.COVER_PNG).isFile ->
                    File(dir, ParsedBookStorage.COVER_PNG)
                else -> null
            }
            bookRepository.updateBook(
                book.copy(
                    id = id,
                    totalChars = content.length,
                    parsedBundlePath = dir.absolutePath,
                    coverImagePath = publishedCover?.absolutePath,
                    contentHash = contentHash,
                )
            )
            if (stagingBundle.exists() && stagingBundle.absolutePath != dir.absolutePath) {
                runCatching { stagingBundle.deleteRecursively() }
            }
            true
        }
        if (!writeOk) {
            bookRepository.deleteBook(book.copy(id = id), deleteCloudBackup = false)
            if (notify) {
                toastChannel.trySend(appContext.getString(R.string.toast_import_write_failed))
            }
            return 0L
        }
        bookRepository.scheduleUploadBookContent(id)
        if (notify) {
            toastChannel.trySend(appContext.getString(R.string.toast_import_success, book.title))
        }
        return id
    }

    private suspend fun prepareImportedContent(
        importContext: Context,
        format: ImportedBookFormat,
        extracted: ExtractedBookText,
    ): ExtractedBookText {
        if (format.usesReaderPlainBody) {
            return extracted.copy(body = MarkdownPreprocessor.stripLocalRelativeImages(extracted.body))
        }
        if (format.isPdf) {
            return extracted.copy(body = PdfReaderContent.sanitizeStoredBody(extracted.body))
        }
        var body = MarkdownPreprocessor.prepare(extracted.body, appContext)
        withContext(Dispatchers.IO) {
            NetworkImageCache.preloadFromMarkdown(appContext, body)
        }
        body = NetworkImageCache.rewriteCachedUrls(appContext, body)
        DiagramImageLoader.preloadFromMarkdown(importContext, body)
        return extracted.copy(body = body)
    }

    fun toggleFavorite(book: BookEntity) {
        viewModelScope.launch {
            bookRepository.toggleFavorite(book.id, !book.isFavorite)
        }
    }

    fun toggleFavoriteProject(project: GitProjectEntity) {
        viewModelScope.launch {
            gitProjectRepository.toggleFavorite(project.id, !project.isFavorite)
        }
    }

    fun deleteBook(book: BookEntity, deleteCloudBackup: Boolean = false) {
        viewModelScope.launch {
            bookRepository.deleteBook(book, deleteCloudBackup)
        }
    }

    fun deleteBooks(ids: Set<Long>, deleteCloudBackup: Boolean = false) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            bookRepository.deleteBooksByIds(ids, deleteCloudBackup)
        }
    }

    fun deleteSelection(
        bookIds: Set<Long>,
        projectIds: Set<Long>,
        deleteCloudBackup: Boolean = false,
    ) {
        viewModelScope.launch {
            if (projectIds.isNotEmpty()) gitPullCoordinator.cancelAll(projectIds)
            if (bookIds.isNotEmpty()) {
                bookRepository.deleteBooksByIds(bookIds, deleteCloudBackup)
            }
            if (projectIds.isNotEmpty()) gitProjectRepository.deleteProjectsByIds(projectIds)
            if (projectIds.isNotEmpty()) {
                val backupError = withContext(Dispatchers.IO) {
                    backupManager.backupIfConfigured().exceptionOrNull()
                }
                if (backupError != null) {
                    toastChannel.trySend(
                        appContext.getString(
                            R.string.backup_toast_backup_failed,
                            backupError.message ?: appContext.getString(R.string.error_unknown),
                        ),
                    )
                }
            }
        }
    }

    fun togglePinForSelection(bookIds: Set<Long>, projectIds: Set<Long> = emptySet()) {
        viewModelScope.launch {
            val books = bookIds.mapNotNull { bookRepository.getBookById(it) }
            val projects = projectIds.mapNotNull { gitProjectRepository.getById(it) }
            if (books.isEmpty() && projects.isEmpty()) return@launch
            val allPinned = books.all { it.isPinned } && projects.all { it.isPinned } &&
                (books.isNotEmpty() || projects.isNotEmpty())
            val pinOrder = System.currentTimeMillis()
            if (allPinned) {
                if (bookIds.isNotEmpty()) bookRepository.unpinBooksByIds(bookIds)
                if (projectIds.isNotEmpty()) gitProjectRepository.unpinByIds(projectIds)
            } else {
                if (bookIds.isNotEmpty()) bookRepository.pinBooksByIds(bookIds, pinOrder)
                if (projectIds.isNotEmpty()) gitProjectRepository.pinByIds(projectIds, pinOrder)
            }
        }
    }

    fun toggleFavoriteForSelection(bookIds: Set<Long>, projectIds: Set<Long>) {
        viewModelScope.launch {
            val books = bookIds.mapNotNull { bookRepository.getBookById(it) }
            val projects = projectIds.mapNotNull { gitProjectRepository.getById(it) }
            val allFavorite = books.all { it.isFavorite } && projects.all { it.isFavorite } &&
                (books.isNotEmpty() || projects.isNotEmpty())
            val target = !allFavorite
            for (book in books) {
                bookRepository.toggleFavorite(book.id, target)
            }
            if (projectIds.isNotEmpty()) {
                gitProjectRepository.updateFavoriteByIds(projectIds, target)
            }
        }
    }

    fun moveSelectedToGroup(
        bookIds: Set<Long>,
        projectIds: Set<Long>,
        groupName: String,
    ) {
        viewModelScope.launch {
            val name = groupName.trim()
            if (name.isNotEmpty()) {
                shelfGroupRepository.ensureGroup(name)
            }
            if (bookIds.isNotEmpty()) bookRepository.updateShelfGroupByIds(bookIds, name)
            if (projectIds.isNotEmpty()) {
                gitProjectRepository.updateShelfGroupByIds(projectIds, name)
            }
        }
    }

    private sealed class LocalImportOutcome {
        data class Success(val bookId: Long) : LocalImportOutcome()
        data object Duplicate : LocalImportOutcome()
        data object Failed : LocalImportOutcome()
    }

    companion object {
        /** 封面颜色数量，与 [space.liushenme.markdownreader.ui.theme.BookCoverColors] 保持同步 */
        const val BOOK_COVER_COLOR_COUNT = 16
    }
}

/** 书架上正在导入的本地任务（仅内存，不写 Room）。 */
data class LocalImportJob(
    val jobId: String,
    val title: String,
    val current: Int = 0,
    val total: Int = 0,
    val coverJpeg: ByteArray? = null,
)

/** 导入时的归属：当前书架标签对应的分组 / 收藏。 */
data class BookImportTarget(
    val shelfGroup: String = "",
    val isFavorite: Boolean = false,
) {
    companion object {
        val None = BookImportTarget()

        fun fromSelectedGroup(selectedGroup: String?): BookImportTarget = when (selectedGroup) {
            null -> None
            ShelfGroups.FAVORITES_SENTINEL -> BookImportTarget(isFavorite = true)
            else -> BookImportTarget(shelfGroup = selectedGroup)
        }
    }
}
