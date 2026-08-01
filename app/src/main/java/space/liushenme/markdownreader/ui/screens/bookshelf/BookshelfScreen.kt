package space.liushenme.markdownreader.ui.screens.bookshelf

import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.data.local.entity.ShelfGroups
import space.liushenme.markdownreader.data.local.entity.isAllGroup
import space.liushenme.markdownreader.data.local.entity.isFavoritesGroup
import space.liushenme.markdownreader.data.local.entity.isSystemGroup
import space.liushenme.markdownreader.importing.BookImportSupport
import space.liushenme.markdownreader.model.BookshelfLayoutMode
import space.liushenme.markdownreader.navigation.AppRoutes
import space.liushenme.markdownreader.ui.components.ShelfStyleTopBarBackground
import space.liushenme.markdownreader.ui.components.shelfStylePageBackground
import space.liushenme.markdownreader.data.local.entity.BookEntity

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BookshelfScreen(
    navController: NavController,
    onSelectionModeChange: (Boolean) -> Unit = {},
    pendingExternalUri: Uri? = null,
    onPendingExternalUriConsumed: () -> Unit = {},
    viewModel: BookshelfViewModel = hiltViewModel(),
) {
    val books by viewModel.books.collectAsState()
    val shelfGroups by viewModel.shelfGroups.collectAsState()
    val layoutMode by viewModel.layoutMode.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val context = LocalContext.current
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var groupInput by remember { mutableStateOf("") }
    var showImportMethodDialog by remember { mutableStateOf(false) }
    var showUrlImportDialog by remember { mutableStateOf(false) }
    var urlImportText by remember { mutableStateOf("") }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    val coverImageCache = remember { LruCache<String, ImageBitmap>(20) }
    val allGroupLabel = stringResource(R.string.bookshelf_group_all)
    val favoritesGroupLabel = stringResource(R.string.bookshelf_group_favorites)

    val visibleTabs = remember(shelfGroups, allGroupLabel, favoritesGroupLabel) {
        shelfGroups.filter { it.isVisible }.map { group ->
            when {
                group.isAllGroup ->
                    ShelfGroupTab(id = group.id, filterKey = null, label = allGroupLabel)
                group.isFavoritesGroup ->
                    ShelfGroupTab(
                        id = group.id,
                        filterKey = ShelfGroups.FAVORITES_SENTINEL,
                        label = favoritesGroupLabel,
                    )
                else ->
                    ShelfGroupTab(id = group.id, filterKey = group.name, label = group.name)
            }
        }
    }
    val dialogGroupNames = remember(shelfGroups) {
        shelfGroups.filter { !it.isSystemGroup }.map { it.name }
    }

    LaunchedEffect(visibleTabs, selectedGroup) {
        val keys = visibleTabs.map { it.filterKey }.toSet()
        if (selectedGroup !in keys) {
            selectedGroup = visibleTabs.firstOrNull()?.filterKey
        }
    }

    val displayedBooks = remember(books, selectedGroup) {
        when (selectedGroup) {
            null -> books
            ShelfGroups.FAVORITES_SENTINEL -> books.filter { it.isFavorite }
            else -> books.filter { it.shelfGroup == selectedGroup }
        }
    }

    val importTarget = remember(selectedGroup) {
        BookImportTarget.fromSelectedGroup(selectedGroup)
    }
    val latestImportTarget = rememberUpdatedState(importTarget)

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importFromLocalUris(context, uris, latestImportTarget.value)
        }
    }

    fun openImportChooser() {
        showImportMethodDialog = true
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessages.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(pendingExternalUri) {
        val uri = pendingExternalUri ?: return@LaunchedEffect
        viewModel.importFromLocalUri(
            context = context,
            uri = uri,
            openReaderWhenDone = true,
        )
        onPendingExternalUriConsumed()
    }

    LaunchedEffect(viewModel) {
        viewModel.readerOpenRequests.collectLatest { bookId ->
            navController.navigate(AppRoutes.reader(bookId)) {
                launchSingleTop = true
            }
        }
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

    val shelfBg = shelfStylePageBackground()

    DisposableEffect(Unit) {
        onDispose { onSelectionModeChange(false) }
    }

    val barBg = MaterialTheme.colorScheme.surface
    val managementVisible = selectionMode && selectedIds.isNotEmpty()
    val gridBottomPadding = 16.dp + if (managementVisible) 80.dp else 0.dp

    LaunchedEffect(selectionMode) {
        onSelectionModeChange(selectionMode)
    }

    Scaffold(
        containerColor = shelfBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ShelfStyleTopBarBackground(shelfBg) {
                if (selectionMode) {
                    TopAppBar(
                        windowInsets = WindowInsets(),
                        title = {
                            Text(
                                stringResource(R.string.bookshelf_selected_count, selectedIds.size),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Black
                                )
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { exitSelection() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.bookshelf_exit_management_cd),
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShelfGroupTabs(
                            tabs = visibleTabs,
                            selectedGroup = selectedGroup,
                            onSelect = { selectedGroup = it },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                navController.navigate(AppRoutes.BOOKSHELF_SEARCH)
                            }
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.bookshelf_search_cd),
                            )
                        }
                        Box {
                            IconButton(onClick = { moreMenuExpanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.bookshelf_more_cd),
                                )
                            }
                            DropdownMenu(
                                expanded = moreMenuExpanded,
                                onDismissRequest = { moreMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.bookshelf_menu_local_import))
                                    },
                                    onClick = {
                                        moreMenuExpanded = false
                                        filePickerLauncher.launch(BookImportSupport.importMimeTypes)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.InsertDriveFile,
                                            contentDescription = null,
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.bookshelf_menu_url_import))
                                    },
                                    onClick = {
                                        moreMenuExpanded = false
                                        showUrlImportDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Link, contentDescription = null)
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.bookshelf_menu_manage))
                                    },
                                    onClick = {
                                        moreMenuExpanded = false
                                        selectionMode = true
                                        selectedIds = emptySet()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.bookshelf_menu_layout))
                                    },
                                    onClick = {
                                        moreMenuExpanded = false
                                        navController.navigate(AppRoutes.BOOKSHELF_LAYOUT)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.DashboardCustomize,
                                            contentDescription = null,
                                        )
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.bookshelf_menu_group_manage))
                                    },
                                    onClick = {
                                        moreMenuExpanded = false
                                        navController.navigate(AppRoutes.BOOKSHELF_GROUPS)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.FolderSpecial,
                                            contentDescription = null,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = PaddingValues(
            start = paddingValues.calculateStartPadding(layoutDirection),
            top = paddingValues.calculateTopPadding(),
            end = paddingValues.calculateEndPadding(layoutDirection),
            bottom = if (managementVisible) 0.dp else paddingValues.calculateBottomPadding(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(shelfBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                if (books.isEmpty()) {
                    EmptyBookshelf(onImportClick = { openImportChooser() })
                } else {
                    fun onBookClick(book: BookEntity, selected: Boolean) {
                        if (selectionMode) {
                            selectedIds =
                                if (selected) selectedIds - book.id else selectedIds + book.id
                        } else {
                            navController.navigate(AppRoutes.reader(book.id))
                        }
                    }

                    fun onBookLongClick(book: BookEntity) {
                        if (!selectionMode) {
                            selectionMode = true
                            selectedIds = setOf(book.id)
                        } else {
                            selectedIds =
                                if (book.id in selectedIds) selectedIds - book.id
                                else selectedIds + book.id
                        }
                    }

                    when (layoutMode) {
                        BookshelfLayoutMode.List -> {
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 12.dp,
                                    bottom = gridBottomPadding,
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (displayedBooks.isEmpty() && !selectionMode) {
                                    item(key = "group_empty_hint") {
                                        BookshelfGroupEmptyHint()
                                    }
                                }
                                items(items = displayedBooks, key = { it.id }) { book ->
                                    val selected = book.id in selectedIds
                                    BookSearchResultCard(
                                        book = book,
                                        coverImageCache = coverImageCache,
                                        selected = selected,
                                        selectionMode = selectionMode,
                                        onClick = { onBookClick(book, selected) },
                                        onLongClick = { onBookLongClick(book) },
                                    )
                                }
                                if (!selectionMode) {
                                    item(key = "import_card") {
                                        ImportListCard(onClick = { openImportChooser() })
                                    }
                                }
                            }
                        }
                        BookshelfLayoutMode.Grid -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(gridColumns),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 12.dp,
                                    bottom = gridBottomPadding,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                if (displayedBooks.isEmpty() && !selectionMode) {
                                    item(
                                        key = "group_empty_hint",
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) {
                                        BookshelfGroupEmptyHint()
                                    }
                                }
                                items(items = displayedBooks, key = { it.id }) { book ->
                                    val selected = book.id in selectedIds
                                    BookCard(
                                        book = book,
                                        selected = selected,
                                        selectionMode = selectionMode,
                                        coverImageCache = coverImageCache,
                                        onClick = { onBookClick(book, selected) },
                                        onLongClick = { onBookLongClick(book) },
                                    )
                                }
                                if (!selectionMode) {
                                    item(key = "import_card") {
                                        ImportBookCard(onClick = { openImportChooser() })
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (managementVisible) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = barBg,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ManagementBarButton(
                            icon = Icons.Default.DeleteOutline,
                            label = stringResource(R.string.bookshelf_action_remove),
                            onClick = { showRemoveConfirm = true }
                        )
                        ManagementBarButton(
                            icon = Icons.Default.PushPin,
                            label = stringResource(R.string.bookshelf_action_pin),
                            onClick = { viewModel.togglePinForSelection(selectedIds) }
                        )
                        ManagementBarButton(
                            icon = Icons.Default.FavoriteBorder,
                            label = stringResource(R.string.bookshelf_action_favorite),
                            onClick = {
                                displayedBooks
                                    .filter { it.id in selectedIds }
                                    .forEach { viewModel.toggleFavorite(it) }
                            }
                        )
                        ManagementBarButton(
                            icon = Icons.Default.FolderSpecial,
                            label = stringResource(R.string.bookshelf_action_group),
                            onClick = {
                                val selectedBooks = books.filter { it.id in selectedIds }
                                val common = selectedBooks.map { it.shelfGroup }.distinct()
                                groupInput = common.singleOrNull().orEmpty()
                                showGroupDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showRemoveConfirm) {
        BookshelfRemoveConfirmDialog(
            selectedCount = selectedIds.size,
            onConfirm = {
                viewModel.deleteBooks(selectedIds)
                showRemoveConfirm = false
                exitSelection()
            },
            onDismiss = { showRemoveConfirm = false }
        )
    }

    if (showGroupDialog) {
        BookshelfGroupDialog(
            groupInput = groupInput,
            existingGroups = dialogGroupNames,
            onGroupInputChange = { groupInput = it },
            onConfirm = {
                viewModel.moveSelectedToGroup(selectedIds, groupInput)
                showGroupDialog = false
                exitSelection()
            },
            onDismiss = { showGroupDialog = false }
        )
    }

    if (showImportMethodDialog) {
        BookshelfImportMethodDialog(
            onLocalImport = {
                showImportMethodDialog = false
                filePickerLauncher.launch(BookImportSupport.importMimeTypes)
            },
            onUrlImport = {
                showImportMethodDialog = false
                showUrlImportDialog = true
            },
            onDismiss = { showImportMethodDialog = false }
        )
    }

    if (showUrlImportDialog) {
        BookshelfUrlImportDialog(
            urlText = urlImportText,
            onUrlTextChange = { urlImportText = it },
            onConfirm = {
                val url = urlImportText.trim()
                if (url.isNotEmpty()) {
                    viewModel.importFromUrl(url, latestImportTarget.value)
                }
                showUrlImportDialog = false
                urlImportText = ""
            },
            onDismiss = {
                showUrlImportDialog = false
                urlImportText = ""
            }
        )
    }
}

@Composable
private fun BookshelfGroupEmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Inbox,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.bookshelf_group_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}
