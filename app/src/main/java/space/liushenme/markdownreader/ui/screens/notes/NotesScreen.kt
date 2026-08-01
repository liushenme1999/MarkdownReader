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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import space.liushenme.markdownreader.R
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
                    title = stringResource(R.string.notes_title),
                    onNavigateBack = { navController.navigateUp() },
                    actions = {
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.notes_export_cd),
                            )
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
                    stringResource(R.string.notes_search_highlights_placeholder)
                } else {
                    stringResource(R.string.notes_search_bookmarks_placeholder)
                },
                clearContentDescription = stringResource(R.string.notes_clear_cd),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 标签页切换
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            stringResource(R.string.notes_tab_highlights, filteredHighlights.size)
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            stringResource(R.string.notes_tab_bookmarks, filteredBookmarks.size)
                        )
                    }
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
    var revealedHighlightId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(highlights) {
        revealedHighlightId = null
    }

    if (highlights.isEmpty()) {
        EmptyState(
            message = stringResource(R.string.notes_empty_highlights),
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
                    revealedHighlightId = revealedHighlightId,
                    onRevealChange = { revealedHighlightId = it },
                    onDelete = {
                        onDelete(item.highlight)
                        if (revealedHighlightId == item.highlight.id) {
                            revealedHighlightId = null
                        }
                    }
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
            message = stringResource(R.string.notes_empty_bookmarks),
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
    revealedHighlightId: Long?,
    onRevealChange: (Long?) -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val highlightColor = Color(highlight.highlight.color)
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { 72.dp.toPx() }
    var offsetPx by remember(highlight.highlight.id) { mutableFloatStateOf(0f) }
    val revealedIdSnapshot by rememberUpdatedState(revealedHighlightId)

    LaunchedEffect(revealedHighlightId, highlight.highlight.id) {
        if (revealedHighlightId != highlight.highlight.id) {
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.notes_delete_cd),
                            modifier = Modifier.size(28.dp),
                            tint = deleteIconTint
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.action_delete),
                            style = MaterialTheme.typography.labelSmall,
                            color = deleteIconTint
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offsetPx }
                .pointerInput(highlight.highlight.id, deleteWidthPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetPx = (offsetPx + dragAmount).coerceIn(-deleteWidthPx, 0f)
                        },
                        onDragEnd = {
                            val threshold = -deleteWidthPx * 0.35f
                            if (offsetPx < threshold) {
                                offsetPx = -deleteWidthPx
                                onRevealChange(highlight.highlight.id)
                            } else {
                                offsetPx = 0f
                                if (revealedIdSnapshot == highlight.highlight.id) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
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
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 18.dp, top = 18.dp, bottom = 18.dp, end = 8.dp)
                ) {
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

                    if (!highlight.highlight.note.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                R.string.notes_note_prefix,
                                highlight.highlight.note.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                SwipeDeleteHintArrow(visible = offsetPx > -8f)
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.notes_delete_cd),
                            modifier = Modifier.size(28.dp),
                            tint = deleteIconTint
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.action_delete),
                            style = MaterialTheme.typography.labelSmall,
                            color = deleteIconTint
                        )
                    }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 18.dp, top = 18.dp, bottom = 18.dp, end = 8.dp)
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
                            text = stringResource(
                                R.string.notes_note_prefix,
                                bookmark.bookmark.note.orEmpty(),
                            ),
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

                SwipeDeleteHintArrow(visible = offsetPx > -8f)
            }
        }
    }
}

/** 列表项右侧「《」向左轻移 + 呼吸透明度，引导左滑删除。 */
@Composable
private fun SwipeDeleteHintArrow(visible: Boolean) {
    val hintWidth = 28.dp
    if (!visible) {
        Spacer(modifier = Modifier.width(hintWidth))
        return
    }
    val infiniteTransition = rememberInfiniteTransition(label = "swipeDeleteHint")
    val shiftX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "swipeDeleteHintShift",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "swipeDeleteHintAlpha",
    )
    Box(
        modifier = Modifier
            .width(hintWidth)
            .padding(end = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "《",
            modifier = Modifier.graphicsLayer {
                translationX = shiftX
                this.alpha = alpha
            },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
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
        title = { Text(stringResource(R.string.export_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.export_dialog_choose_format))
                Spacer(modifier = Modifier.height(16.dp))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.export_as_markdown)) },
                    supportingContent = { Text(stringResource(R.string.export_markdown_hint)) },
                    leadingContent = {
                        Icon(Icons.Default.Description, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        onExportMarkdown()
                        onDismiss()
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.export_as_json)) },
                    supportingContent = { Text(stringResource(R.string.export_json_hint)) },
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
                Text(stringResource(R.string.action_cancel))
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
                    title = stringResource(R.string.notes_title),
                    onNavigateBack = { navController.navigateUp() },
                    actions = {
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.notes_export_cd),
                            )
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
                    stringResource(R.string.notes_search_highlights_placeholder)
                } else {
                    stringResource(R.string.notes_search_bookmarks_placeholder)
                },
                clearContentDescription = stringResource(R.string.notes_clear_cd),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            stringResource(R.string.notes_tab_highlights, filteredHighlights.size)
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            stringResource(R.string.notes_tab_bookmarks, filteredBookmarks.size)
                        )
                    }
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
