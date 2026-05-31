package space.liushenme.markdownreader.ui.screens.notes

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.ui.components.AppSearchField
import space.liushenme.markdownreader.ui.components.ShelfStyleTopBarBackground
import space.liushenme.markdownreader.ui.components.ShelfStyleTopAppBar
import space.liushenme.markdownreader.ui.components.iconTintForDeleteStrip
import space.liushenme.markdownreader.ui.components.shelfStylePageBackground
import space.liushenme.markdownreader.ui.theme.MarkdownReaderTheme
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    navController: NavController,
    viewModel: NotesViewModel = hiltViewModel()
) {
    val highlights by viewModel.allHighlights.collectAsState()
    val bookmarks by viewModel.allBookmarks.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showExportDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val pageBg = shelfStylePageBackground()

    // 根据搜索关键词过滤数据
    val filteredHighlights = remember(highlights, searchQuery) {
        if (searchQuery.isBlank()) highlights
        else highlights.filter {
            it.highlight.highlightedText.contains(searchQuery, ignoreCase = true) ||
                    it.bookTitle.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredBookmarks = remember(bookmarks, searchQuery) {
        if (searchQuery.isBlank()) bookmarks
        else bookmarks.filter {
            it.bookmark.previewText.contains(searchQuery, ignoreCase = true) ||
                    it.bookTitle.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = pageBg,
        topBar = {
            ShelfStyleTopBarBackground(pageBg) {
                ShelfStyleTopAppBar(
                    title = "笔记管理",
                    onNavigateBack = { navController.navigateUp() },
                    actions = {
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.Share, contentDescription = "导出")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(pageBg)
        ) {
            AppSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = if (selectedTab == 0) {
                    "搜索划线内容或书名..."
                } else {
                    "搜索书签内容或书名..."
                },
                clearContentDescription = "清除",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 标签页切换
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("划线笔记 (${filteredHighlights.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("书签 (${filteredBookmarks.size})") }
                )
            }

            // 内容列表
            when (selectedTab) {
                0 -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    HighlightsList(
                        highlights = filteredHighlights,
                        onDelete = { viewModel.deleteHighlight(it) }
                    )
                }
                1 -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    BookmarksList(
                        bookmarks = filteredBookmarks,
                        onDelete = { viewModel.deleteBookmark(it) }
                    )
                }
            }
        }
    }

    // 导出对话框
    if (showExportDialog) {
        ExportDialog(
            onExportMarkdown = { viewModel.exportToMarkdown() },
            onExportJson = { viewModel.exportToJson() },
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
private fun HighlightsList(
    highlights: List<HighlightWithBook>,
    onDelete: (HighlightEntity) -> Unit
) {
    if (highlights.isEmpty()) {
        EmptyState(
            message = "还没有划线笔记",
            modifier = Modifier.fillMaxSize()
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(highlights, key = { it.highlight.id }) { item ->
                HighlightCard(
                    highlight = item,
                    onDelete = { onDelete(item.highlight) }
                )
            }
        }
    }
}

@Composable
private fun BookmarksList(
    bookmarks: List<BookmarkWithBook>,
    onDelete: (space.liushenme.markdownreader.data.local.entity.BookmarkEntity) -> Unit
) {
    var revealedBookmarkId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(bookmarks) {
        revealedBookmarkId = null
    }

    if (bookmarks.isEmpty()) {
        EmptyState(
            message = "还没有书签",
            modifier = Modifier.fillMaxSize()
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(bookmarks, key = { it.bookmark.id }) { item ->
                BookmarkCard(
                    bookmark = item,
                    revealedBookmarkId = revealedBookmarkId,
                    onRevealChange = { revealedBookmarkId = it },
                    onDelete = {
                        onDelete(item.bookmark)
                        if (revealedBookmarkId == item.bookmark.id) {
                            revealedBookmarkId = null
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HighlightCard(
    highlight: HighlightWithBook,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val highlightColor = Color(highlight.highlight.color)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxHeight()
        ) {
            // 左侧彩色竖条装饰
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(
                        color = highlightColor,
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
            )

            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                // 书籍信息
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(highlightColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = highlight.bookTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = dateFormat.format(highlight.highlight.createTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 高亮内容
                Text(
                    text = highlight.highlight.highlightedText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = highlightColor.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                )

                // 笔记
                if (!highlight.highlight.note.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "笔记: ${highlight.highlight.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 操作按钮
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除")
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(
    bookmark: BookmarkWithBook,
    revealedBookmarkId: Long?,
    onRevealChange: (Long?) -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { 72.dp.toPx() }
    var offsetPx by remember(bookmark.bookmark.id) { mutableFloatStateOf(0f) }
    val revealedIdSnapshot by rememberUpdatedState(revealedBookmarkId)

    LaunchedEffect(revealedBookmarkId, bookmark.bookmark.id) {
        if (revealedBookmarkId != bookmark.bookmark.id) {
            offsetPx = 0f
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center
            ) {
                val err = MaterialTheme.colorScheme.error
                val deleteIconTint = iconTintForDeleteStrip(err)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            onDelete()
                            offsetPx = 0f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(28.dp),
                        tint = deleteIconTint
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offsetPx }
                .pointerInput(bookmark.bookmark.id, deleteWidthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetPx = (offsetPx + dragAmount).coerceIn(-deleteWidthPx, 0f)
                        },
                        onDragEnd = {
                            val threshold = -deleteWidthPx * 0.35f
                            if (offsetPx < threshold) {
                                offsetPx = -deleteWidthPx
                                onRevealChange(bookmark.bookmark.id)
                            } else {
                                offsetPx = 0f
                                if (revealedIdSnapshot == bookmark.bookmark.id) {
                                    onRevealChange(null)
                                }
                            }
                        }
                    )
                },
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = bookmark.bookTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = dateFormat.format(bookmark.bookmark.createTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = bookmark.bookmark.previewText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (!bookmark.bookmark.note.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "笔记: ${bookmark.bookmark.note}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String, modifier: Modifier = Modifier) {
    // 呼吸动画：alpha 脉冲
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingAlpha"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.NoteAlt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ExportDialog(
    onExportMarkdown: () -> Unit,
    onExportJson: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出笔记") },
        text = {
            Column {
                Text("选择导出格式:")
                Spacer(modifier = Modifier.height(16.dp))
                ListItem(
                    headlineContent = { Text("导出为 Markdown") },
                    supportingContent = { Text("适合导入其他笔记软件") },
                    leadingContent = {
                        Icon(Icons.Default.Description, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        onExportMarkdown()
                        onDismiss()
                    }
                )
                ListItem(
                    headlineContent = { Text("导出为 JSON") },
                    supportingContent = { Text("包含完整数据信息") },
                    leadingContent = {
                        Icon(Icons.Default.Code, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        onExportJson()
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// 数据类
data class HighlightWithBook(
    val highlight: HighlightEntity,
    val bookTitle: String
)

data class BookmarkWithBook(
    val bookmark: space.liushenme.markdownreader.data.local.entity.BookmarkEntity,
    val bookTitle: String
)

private fun notesPreviewSampleHighlights(): List<HighlightWithBook> {
    val h = HighlightEntity(
        id = 1L,
        bookId = 1L,
        startPosition = 0,
        endPosition = 12,
        highlightedText = "这是预览中的划线示例文本，用于查看卡片布局与多行换行效果。",
        color = 0xFFFFFF00.toInt(),
        note = "示例笔记一行",
    )
    return listOf(
        HighlightWithBook(h, "示例书籍 A"),
        HighlightWithBook(
            highlight = h.copy(
                id = 2L,
                highlightedText = "第二条划线，颜色不同。",
                color = 0xFF90CAF9.toInt(),
                note = null
            ),
            bookTitle = "另一本书"
        ),
    )
}

private fun notesPreviewSampleBookmarks(): List<BookmarkWithBook> {
    val b = BookmarkEntity(
        id = 1L,
        bookId = 1L,
        position = 1200,
        previewText = "书签位置附近的正文预览内容，用于观察书签卡片在列表中的展示。",
        note = "书签备注（可选）",
    )
    return listOf(
        BookmarkWithBook(b, "示例书籍 B"),
        BookmarkWithBook(
            bookmark = b.copy(
                id = 2L,
                position = 3400,
                previewText = "第二条书签预览文本。",
                note = null
            ),
            bookTitle = "Markdown 测试"
        ),
    )
}

/**
 * 仅用于 @Preview：不依赖 Hilt / Activity 窗口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesScreenPreviewImpl(navController: NavController) {
    val highlights = remember { notesPreviewSampleHighlights() }
    val bookmarks = remember { notesPreviewSampleBookmarks() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showExportDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredHighlights = remember(highlights, searchQuery) {
        if (searchQuery.isBlank()) highlights
        else highlights.filter {
            it.highlight.highlightedText.contains(searchQuery, ignoreCase = true) ||
                it.bookTitle.contains(searchQuery, ignoreCase = true)
        }
    }
    val filteredBookmarks = remember(bookmarks, searchQuery) {
        if (searchQuery.isBlank()) bookmarks
        else bookmarks.filter {
            it.bookmark.previewText.contains(searchQuery, ignoreCase = true) ||
                it.bookTitle.contains(searchQuery, ignoreCase = true)
        }
    }

    val pageBg = shelfStylePageBackground()

    Scaffold(
        containerColor = pageBg,
        topBar = {
            ShelfStyleTopBarBackground(pageBg) {
                ShelfStyleTopAppBar(
                    title = "笔记管理",
                    onNavigateBack = { navController.navigateUp() },
                    actions = {
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.Share, contentDescription = "导出")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(pageBg)
        ) {
            AppSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = if (selectedTab == 0) {
                    "搜索划线内容或书名..."
                } else {
                    "搜索书签内容或书名..."
                },
                clearContentDescription = "清除",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("划线笔记 (${filteredHighlights.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("书签 (${filteredBookmarks.size})") }
                )
            }

            when (selectedTab) {
                0 -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    HighlightsList(
                        highlights = filteredHighlights,
                        onDelete = { }
                    )
                }
                1 -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    BookmarksList(
                        bookmarks = filteredBookmarks,
                        onDelete = { }
                    )
                }
            }
        }
    }

    if (showExportDialog) {
        ExportDialog(
            onExportMarkdown = { showExportDialog = false },
            onExportJson = { showExportDialog = false },
            onDismiss = { showExportDialog = false }
        )
    }
}

@Preview(showBackground = true, name = "笔记管理")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesScreenPreview() {
    MarkdownReaderTheme {
        NotesScreenPreviewImpl(navController = rememberNavController())
    }
}
