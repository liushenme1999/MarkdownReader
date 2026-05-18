package com.example.markdownreader.ui.screens.bookshelf

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.markdownreader.ui.components.AppSearchField
import com.example.markdownreader.importing.BookImportSupport
import com.example.markdownreader.ui.components.ShelfStyleTopBarBackground
import com.example.markdownreader.ui.components.shelfStylePageBackground
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.markdownreader.data.local.entity.BookEntity
import com.example.markdownreader.ui.theme.BookCoverColors
import com.example.markdownreader.navigation.AppRoutes
import com.example.markdownreader.ui.theme.MarkdownReaderTheme
import java.text.SimpleDateFormat
import java.io.File
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BookshelfScreen(
    navController: NavController,
    onSelectionModeChange: (Boolean) -> Unit = {},
    viewModel: BookshelfViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsState()
    val context = LocalContext.current
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var groupInput by remember { mutableStateOf("") }
    var showImportMethodDialog by remember { mutableStateOf(false) }
    var showUrlImportDialog by remember { mutableStateOf(false) }
    var urlImportText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }
    val coverImageCache = remember { LruCache<String, ImageBitmap>(20) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importFromLocalUri(context, it) }
    }

    fun openImportChooser() {
        showImportMethodDialog = true
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessages.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
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

    // 搜索过滤逻辑
    val filteredBooks = remember(books, searchQuery) {
        val query = searchQuery.trim().lowercase(Locale.getDefault())
        if (query.isEmpty()) {
            books
        } else {
            books.filter { book ->
                book.title.lowercase(Locale.getDefault()).contains(query) ||
                    (book.author?.lowercase(Locale.getDefault())?.contains(query) == true)
            }
        }
    }

    Scaffold(
        containerColor = shelfBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ShelfStyleTopBarBackground(shelfBg) {
                TopAppBar(
                    // Scaffold 已对 topBar 施加状态栏区域；避免与 TopAppBar 默认 windowInsets 叠加成双倍顶距
                    windowInsets = WindowInsets(),
                    title = {
                        Text(
                            if (selectionMode) "已选 ${selectedIds.size} 本"
                            else "书架 (${books.size})",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Black
                            )
                        )
                    },
                    navigationIcon = {
                        if (selectionMode) {
                            IconButton(onClick = { exitSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "退出管理")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                // 搜索栏
                if (!selectionMode) {
                    AppSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholder = "搜索书名或作者...",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 4.dp)
                    )
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
            } else if (filteredBooks.isEmpty()) {
                // 搜索无结果
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "未找到匹配的书籍",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "尝试其他关键词",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = gridBottomPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = filteredBooks,
                        key = { it.id }
                    ) { book ->
                        val selected = book.id in selectedIds
                        BookCard(
                            book = book,
                            selected = selected,
                            selectionMode = selectionMode,
                            coverImageCache = coverImageCache,
                            onClick = {
                                if (selectionMode) {
                                    selectedIds =
                                        if (selected) selectedIds - book.id else selectedIds + book.id
                                } else {
                                    navController.navigate(AppRoutes.reader(book.id))
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    selectedIds = setOf(book.id)
                                } else {
                                    selectedIds =
                                        if (book.id in selectedIds) selectedIds - book.id
                                        else selectedIds + book.id
                                }
                            }
                        )
                    }
                    if (!selectionMode && searchQuery.isEmpty()) {
                        item(key = "import_card") {
                            ImportBookCard(onClick = { openImportChooser() })
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
                            label = "移出书架",
                            onClick = { showRemoveConfirm = true }
                        )
                        ManagementBarButton(
                            icon = Icons.Default.PushPin,
                            label = "置顶",
                            onClick = { viewModel.togglePinForSelection(selectedIds) }
                        )
                        ManagementBarButton(
                            icon = Icons.Default.FavoriteBorder,
                            label = "收藏",
                            onClick = {
                                filteredBooks
                                    .filter { it.id in selectedIds }
                                    .forEach { viewModel.toggleFavorite(it) }
                            }
                        )
                        ManagementBarButton(
                            icon = Icons.Default.FolderSpecial,
                            label = "分组",
                            onClick = {
                                groupInput = ""
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
                    viewModel.importFromUrl(url)
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

