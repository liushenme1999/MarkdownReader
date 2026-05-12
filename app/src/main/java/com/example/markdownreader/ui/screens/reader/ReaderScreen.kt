package com.example.markdownreader.ui.screens.reader

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.markdownreader.ui.theme.ReadingTheme
import io.noties.markwon.Markwon
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    navController: NavController,
    bookId: Long,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val book by viewModel.book.collectAsState()
    val content by viewModel.content.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val highlights by viewModel.highlights.collectAsState()
    val readingProgress by viewModel.readingProgress.collectAsState()
    val currentTheme by viewModel.currentTheme.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf("") }
    var showHighlightMenu by remember { mutableStateOf(false) }

    LaunchedEffect(bookId) {
        viewModel.loadBook(context, bookId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            book?.title ?: "阅读中",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "${(readingProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showBookmarks = true }) {
                        Icon(Icons.Default.Bookmark, contentDescription = "书签")
                    }
                    IconButton(onClick = { showToc = true }) {
                        Icon(Icons.Default.Toc, contentDescription = "目录")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(currentTheme.backgroundColor)
        ) {
            if (content.isNotEmpty()) {
                MarkdownReaderView(
                    content = content,
                    theme = currentTheme,
                    fontSize = fontSize,
                    highlights = highlights,
                    onTextSelected = { text ->
                        selectedText = text
                        showHighlightMenu = text.isNotEmpty()
                    },
                    onScroll = { progress ->
                        viewModel.updateReadingProgress(progress)
                    }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // 底部进度条
            LinearProgressIndicator(
                progress = readingProgress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp),
                color = currentTheme.textColor.copy(alpha = 0.5f),
                trackColor = Color.Transparent
            )
        }
    }

    // 设置面板
    if (showSettings) {
        ReaderSettingsSheet(
            currentTheme = currentTheme,
            fontSize = fontSize,
            onThemeChange = { viewModel.setTheme(it) },
            onFontSizeChange = { viewModel.setFontSize(it) },
            onDismiss = { showSettings = false }
        )
    }

    // 书签列表
    if (showBookmarks) {
        BookmarksSheet(
            bookmarks = bookmarks,
            onBookmarkClick = { position ->
                // 跳转到书签位置
                showBookmarks = false
            },
            onDeleteBookmark = { bookmark ->
                viewModel.deleteBookmark(bookmark)
            },
            onDismiss = { showBookmarks = false }
        )
    }

    // 高亮菜单
    if (showHighlightMenu) {
        HighlightActionSheet(
            selectedText = selectedText,
            onHighlight = { color ->
                viewModel.addHighlight(selectedText, color)
                showHighlightMenu = false
            },
            onAddBookmark = { note ->
                viewModel.addBookmark(selectedText, note)
                showHighlightMenu = false
            },
            onDismiss = { showHighlightMenu = false }
        )
    }
}

@Composable
private fun MarkdownReaderView(
    content: String,
    theme: ReadingTheme,
    fontSize: Int,
    highlights: List<com.example.markdownreader.data.local.entity.HighlightEntity>,
    onTextSelected: (String) -> Unit,
    onScroll: (Float) -> Unit
) {
    val context = LocalContext.current
    val markwon = remember { createMarkwon(context) }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                setTextColor(theme.textColor.hashCode())
                textSize = fontSize.toFloat()
                setLineSpacing(0f, 1.5f)
                setPadding(32, 32, 32, 32)

                // 应用高亮
                val spannable = applyHighlights(content, highlights, theme.highlightColor.hashCode())
                markwon.setMarkdown(this, spannable.toString())

                // 文本选择监听
                customSelectionActionModeCallback = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        return true
                    }

                    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                        return false
                    }

                    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                        return false
                    }

                    override fun onDestroyActionMode(mode: ActionMode?) {
                        val selected = text.substring(selectionStart, selectionEnd)
                        onTextSelected(selected)
                    }
                }
            }
        },
        update = { textView ->
            textView.setTextColor(theme.textColor.hashCode())
            textView.textSize = fontSize.toFloat()

            val spannable = applyHighlights(content, highlights, theme.highlightColor.hashCode())
            markwon.setMarkdown(textView, spannable.toString())
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun createMarkwon(context: Context): Markwon {
    return Markwon.builder(context)
        .usePlugin(HtmlPlugin.create())
        .usePlugin(LinkifyPlugin.create())
        .build()
}

private fun applyHighlights(
    content: String,
    highlights: List<com.example.markdownreader.data.local.entity.HighlightEntity>,
    highlightColor: Int
): Spannable {
    val spannable = SpannableStringBuilder(content)

    highlights.forEach { highlight ->
        val start = highlight.startPosition.coerceIn(0, content.length)
        val end = highlight.endPosition.coerceIn(start, content.length)

        if (start < end) {
            spannable.setSpan(
                BackgroundColorSpan(highlightColor),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    return spannable
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    currentTheme: ReadingTheme,
    fontSize: Int,
    onThemeChange: (ReadingTheme) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                "阅读设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 字体大小调节
            Text("字体大小", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { onFontSizeChange((fontSize - 2).coerceAtLeast(12)) }) {
                    Icon(Icons.Default.Remove, contentDescription = "减小")
                }
                Text("${fontSize}px", style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { onFontSizeChange((fontSize + 2).coerceAtMost(32)) }) {
                    Icon(Icons.Default.Add, contentDescription = "增大")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 主题选择
            Text("阅读主题", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ReadingTheme.allThemes().forEach { theme ->
                    ThemeOption(
                        theme = theme,
                        isSelected = theme == currentTheme,
                        onClick = { onThemeChange(theme) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeOption(
    theme: ReadingTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(theme.backgroundColor)
                .then(
                    if (isSelected) {
                        Modifier.padding(2.dp)
                    } else Modifier
                )
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = theme.textColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            theme.name,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarksSheet(
    bookmarks: List<com.example.markdownreader.data.local.entity.BookmarkEntity>,
    onBookmarkClick: (Int) -> Unit,
    onDeleteBookmark: (com.example.markdownreader.data.local.entity.BookmarkEntity) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                "我的书签",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无书签",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                bookmarks.forEach { bookmark ->
                    BookmarkItem(
                        bookmark = bookmark,
                        onClick = { onBookmarkClick(bookmark.position) },
                        onDelete = { onDeleteBookmark(bookmark) }
                    )
                    Divider()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BookmarkItem(
    bookmark: com.example.markdownreader.data.local.entity.BookmarkEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Bookmark,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                bookmark.previewText.take(50),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
            if (!bookmark.note.isNullOrEmpty()) {
                Text(
                    bookmark.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighlightActionSheet(
    selectedText: String,
    onHighlight: (Color) -> Unit,
    onAddBookmark: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                "划线与笔记",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                selectedText.take(100) + if (selectedText.length > 100) "..." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 高亮颜色选择
            Text("选择高亮颜色", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val colors = listOf(
                    Color(0xFFFFFF00) to "黄色",
                    Color(0xFF00FF00) to "绿色",
                    Color(0xFF00FFFF) to "青色",
                    Color(0xFFFF00FF) to "粉色",
                    Color(0xFFFFA500) to "橙色"
                )
                colors.forEach { (color, name) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { onHighlight(color) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 添加书签按钮
            Button(
                onClick = { showNoteDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加书签笔记")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // 笔记输入对话框
    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("添加笔记") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("笔记内容（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddBookmark(noteText.takeIf { it.isNotEmpty() })
                        showNoteDialog = false
                    }
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
