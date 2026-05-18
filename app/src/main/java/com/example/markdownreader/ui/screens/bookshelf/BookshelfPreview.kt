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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.markdownreader.ui.components.AppSearchField
import com.example.markdownreader.importing.BookImportSupport
import com.example.markdownreader.ui.components.shelfStylePageBackground
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.markdownreader.data.local.entity.BookEntity
import com.example.markdownreader.ui.theme.BookCoverColors
import com.example.markdownreader.navigation.AppRoutes
import com.example.markdownreader.ui.theme.MainNavigationBarBackground
import com.example.markdownreader.ui.theme.MarkdownReaderTheme
import java.text.SimpleDateFormat
import java.io.File
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext


internal fun bookshelfPreviewSampleBooks(): List<BookEntity> {
    val now = Date()
    return listOf(
        BookEntity(
            id = 1L,
            title = "示例 Markdown 书籍",
            author = "预览作者",
            filePath = "/preview/1.md",
            readingProgress = 0.35f,
            lastReadTime = now,
            coverColor = 0,
            isFavorite = true,
        ),
        BookEntity(
            id = 2L,
            title = "活着",
            author = null,
            filePath = "/preview/2.txt",
            readingProgress = 0.02f,
            lastReadTime = now,
            coverColor = 3,
        ),
        BookEntity(
            id = 3L,
            title = "面试问答精选",
            author = "刘",
            filePath = "/preview/3.md",
            readingProgress = 0f,
            coverColor = 5,
        ),
    )
}

/**
 * 仅用于 Android Studio @Preview：不依赖 Hilt / 数据库 / Activity 窗口。
 * 正式界面请使用 [BookshelfScreen]。
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun BookshelfScreenPreviewImpl(
    navController: NavController,
    onSelectionModeChange: (Boolean) -> Unit,
) {
    val books = remember { bookshelfPreviewSampleBooks() }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var searchQuery by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coverImageCache = remember { LruCache<String, ImageBitmap>(20) }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    val shelfBg = shelfStylePageBackground()

    val gridBottomPadding = 16.dp + if (selectionMode && selectedIds.isNotEmpty()) 80.dp else 0.dp

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

    LaunchedEffect(selectionMode) {
        onSelectionModeChange(selectionMode)
    }

    Scaffold(
        containerColor = shelfBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(shelfBg)
                .padding(paddingValues)
        ) {
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
                    item(key = "import_card_preview") {
                        ImportBookCard(onClick = {})
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Preview(showBackground = true, name = "书架")
@Composable
internal fun BookshelfScreenPreview() {
    MarkdownReaderTheme {
        BookshelfScreenPreviewImpl(
            navController = rememberNavController(),
            onSelectionModeChange = {},
        )
    }
}
