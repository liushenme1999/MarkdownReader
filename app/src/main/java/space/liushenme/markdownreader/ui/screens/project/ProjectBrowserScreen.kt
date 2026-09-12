package space.liushenme.markdownreader.ui.screens.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.local.entity.isFinishedReading
import space.liushenme.markdownreader.git.GitPathUtils
import space.liushenme.markdownreader.git.GitTreeNode
import space.liushenme.markdownreader.navigation.AppRoutes
import space.liushenme.markdownreader.ui.components.ShelfStyleTopAppBar
import space.liushenme.markdownreader.ui.components.ShelfStyleTopBarBackground
import space.liushenme.markdownreader.ui.components.shelfStylePageBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectBrowserScreen(
    navController: NavController,
    viewModel: ProjectBrowserViewModel = hiltViewModel(),
) {
    val project by viewModel.projectState.collectAsState()
    val tree by viewModel.tree.collectAsState()
    val expanded by viewModel.expandedPaths.collectAsState()
    val lastOpened by viewModel.lastOpenedRelativePath.collectAsState()
    val recentReadEntries by viewModel.recentReadEntries.collectAsState()
    val recentReadDisplayCount by viewModel.recentReadDisplayCount.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val busy by viewModel.busy.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val busyStatusRes by viewModel.busyStatusRes.collectAsState()
    val remoteBranches by viewModel.remoteBranches.collectAsState()
    val loadingBranches by viewModel.loadingBranches.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showBranchDialog by remember { mutableStateOf(false) }
    var selectedBranch by remember { mutableStateOf<String?>(null) }
    var recentReadingExpanded by remember { mutableStateOf(false) }
    val shelfBg = shelfStylePageBackground()
    val listState = rememberLazyListState()
    var didInitialScroll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.toastMessages.collectLatest { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.gitPullToasts.collectLatest { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.openReaderRequests.collectLatest { bookId ->
            navController.navigate(AppRoutes.reader(bookId)) {
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.deletedEvents.collectLatest {
            navController.popBackStack()
        }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshRecentReadProgress()
        }
    }

    val flatRows = remember(tree, expanded) { flattenTree(tree, expanded) }

    LaunchedEffect(lastOpened, flatRows) {
        if (didInitialScroll) return@LaunchedEffect
        val target = lastOpened?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (flatRows.isEmpty()) return@LaunchedEffect
        val index = flatRows.indexOfFirst { !it.isFolder && it.path == target }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            didInitialScroll = true
        }
    }

    Scaffold(
        containerColor = shelfBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ShelfStyleTopBarBackground(shelfBg) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ShelfStyleTopAppBar(
                        title = project?.title
                            ?: stringResource(R.string.git_project_browser_title),
                        onNavigateBack = { navController.popBackStack() },
                        actions = {
                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    enabled = !busy,
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (project?.hasRemoteUpdate == true) {
                                                Badge(containerColor = Color(0xFFE53935))
                                            }
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = stringResource(
                                                if (project?.hasRemoteUpdate == true) {
                                                    R.string.bookshelf_git_update_available_cd
                                                } else {
                                                    R.string.git_project_more_cd
                                                },
                                            ),
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.git_project_change_branch))
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.AccountTree,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            selectedBranch = project?.defaultBranch
                                            showBranchDialog = true
                                            viewModel.loadRemoteBranches()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(stringResource(R.string.git_project_pull_cd))
                                                if (project?.hasRemoteUpdate == true) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Box(
                                                        Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFFE53935)),
                                                    )
                                                }
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Sync, contentDescription = null)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.pull()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.git_project_delete_cd))
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            showDeleteConfirm = true
                                        },
                                    )
                                }
                            }
                        },
                    )
                    if (progress != null && busy) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                stringResource(
                                    busyStatusRes ?: R.string.git_project_pulling_title,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(6.dp))
                            val pct = progress?.percent ?: -1
                            if (pct in 0..100) {
                                LinearProgressIndicator(
                                    progress = { pct / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    if (recentReadEntries.isNotEmpty()) {
                        ContinueReadingSection(
                            entries = recentReadEntries,
                            collapsedCount = recentReadDisplayCount,
                            expanded = recentReadingExpanded,
                            enabled = !busy,
                            onToggleExpand = {
                                recentReadingExpanded = !recentReadingExpanded
                            },
                            onOpen = { viewModel.openDocument(it) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(shelfBg),
        ) {
            when {
                project == null && !busy -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.error_unknown))
                    }
                }
                flatRows.isEmpty() && !busy -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.git_project_empty_docs),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = 24.dp,
                        ),
                    ) {
                        items(flatRows, key = { it.path }) { row ->
                            TreeRow(
                                row = row,
                                highlighted = !row.isFolder && row.path == lastOpened,
                                onFolderClick = { viewModel.toggleFolder(row.path) },
                                onFileClick = { viewModel.openDocument(row.path) },
                                enabled = !busy,
                            )
                        }
                    }
                }
            }

            if (busy && progress == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.git_project_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.git_project_delete_message,
                        project?.title.orEmpty(),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteProject()
                    },
                ) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showBranchDialog) {
        val currentBranch = project?.defaultBranch.orEmpty()
        AlertDialog(
            onDismissRequest = {
                if (!busy) showBranchDialog = false
            },
            title = { Text(stringResource(R.string.git_project_change_branch_title)) },
            text = {
                when {
                    loadingBranches -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.git_project_loading_branches),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    remoteBranches.isEmpty() -> {
                        Text(
                            stringResource(R.string.git_project_branch_list_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.git_project_current_branch,
                                    currentBranch.ifBlank { "—" },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            remoteBranches.forEach { branch ->
                                val selected = (selectedBranch ?: currentBranch) == branch
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = selected,
                                            onClick = { selectedBranch = branch },
                                            role = Role.RadioButton,
                                        )
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { selectedBranch = branch },
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = branch,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = selectedBranch ?: currentBranch
                        showBranchDialog = false
                        if (target.isNotBlank()) {
                            viewModel.checkoutBranch(target)
                        }
                    },
                    enabled = !loadingBranches &&
                        !busy &&
                        (selectedBranch ?: currentBranch).isNotBlank() &&
                        (selectedBranch ?: currentBranch) != currentBranch,
                ) {
                    Text(stringResource(R.string.git_project_switch_branch))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBranchDialog = false },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ContinueReadingSection(
    entries: List<RecentReadEntry>,
    collapsedCount: Int,
    expanded: Boolean,
    enabled: Boolean,
    onToggleExpand: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val canExpand = entries.size > collapsedCount
    val visibleEntries = if (expanded) entries else entries.take(collapsedCount)
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topAppBarHeight = TopAppBarDefaults.TopAppBarExpandedHeight
    val contentAreaHeight =
        LocalConfiguration.current.screenHeightDp.dp - statusBarHeight - topAppBarHeight
    val maxExpandedListHeight = (contentAreaHeight * 0.4f).coerceAtLeast(96.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.git_project_continue_reading),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
            )

            val listModifier = if (expanded) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxExpandedListHeight)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            }

            Column(modifier = listModifier) {
                visibleEntries.forEachIndexed { index, entry ->
                    ContinueReadingItem(
                        entry = entry,
                        enabled = enabled,
                        onClick = { onOpen(entry.relativePath) },
                        showDivider = index < visibleEntries.lastIndex,
                    )
                }
            }

            if (canExpand || expanded) {
                IconButton(
                    onClick = onToggleExpand,
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = stringResource(
                            if (expanded) {
                                R.string.git_project_collapse_recent
                            } else {
                                R.string.git_project_expand_recent
                            },
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ContinueReadingItem(
    entry: RecentReadEntry,
    enabled: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    val relativePath = entry.relativePath
    val fileName = GitPathUtils.fileName(relativePath)
    val progressText = if (entry.readingProgress.isFinishedReading()) {
        stringResource(R.string.bookshelf_finished)
    } else {
        "${(entry.readingProgress.coerceIn(0f, 1f) * 100).toInt()}%"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = stringResource(R.string.git_project_last_opened_cd),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (relativePath != fileName) {
                    Text(
                        text = relativePath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (showDivider) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(1.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    ),
            )
        }
    }
}


private data class FlatTreeRow(
    val path: String,
    val name: String,
    val depth: Int,
    val isFolder: Boolean,
    val expanded: Boolean,
)

private fun flattenTree(
    nodes: List<GitTreeNode>,
    expanded: Set<String>,
    depth: Int = 0,
): List<FlatTreeRow> {
    val out = ArrayList<FlatTreeRow>()
    for (node in nodes) {
        when (node) {
            is GitTreeNode.Folder -> {
                val isExpanded = node.relativePath in expanded
                out += FlatTreeRow(
                    path = node.relativePath,
                    name = node.name,
                    depth = depth,
                    isFolder = true,
                    expanded = isExpanded,
                )
                if (isExpanded) {
                    out += flattenTree(node.children, expanded, depth + 1)
                }
            }
            is GitTreeNode.Document -> {
                out += FlatTreeRow(
                    path = node.relativePath,
                    name = node.name,
                    depth = depth,
                    isFolder = false,
                    expanded = false,
                )
            }
        }
    }
    return out
}

@Composable
private fun TreeRow(
    row: FlatTreeRow,
    highlighted: Boolean,
    onFolderClick: () -> Unit,
    onFileClick: () -> Unit,
    enabled: Boolean,
) {
    val bg = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(shape)
            .background(bg)
            .clickable(enabled = enabled) {
                if (row.isFolder) onFolderClick() else onFileClick()
            }
            .padding(start = (8 + row.depth * 16).dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (highlighted) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (row.isFolder) {
            Icon(
                imageVector = if (row.expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.Folder,
                contentDescription = stringResource(R.string.git_project_folder_cd),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Spacer(Modifier.width(24.dp))
            Icon(
                Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = stringResource(R.string.git_project_file_cd),
                tint = if (highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = row.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
