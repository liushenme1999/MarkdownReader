package com.example.markdownreader.ui.screens.bookshelf

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.markdownreader.data.local.entity.BookEntity
import com.example.markdownreader.ui.theme.BookCoverColors
import com.example.markdownreader.ui.theme.BookshelfPageBackground
import com.example.markdownreader.ui.theme.BookshelfPageBackgroundDark
import com.example.markdownreader.ui.theme.MainNavigationBarBackground
import java.text.SimpleDateFormat
import java.util.*

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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importMarkdownFile(context, it) }
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

    DisposableEffect(Unit) {
        onDispose { onSelectionModeChange(false) }
    }

    val shelfBg =
        if (isSystemInDarkTheme()) BookshelfPageBackgroundDark
        else BookshelfPageBackground
    val barBg = MainNavigationBarBackground
    val managementVisible = selectionMode && selectedIds.isNotEmpty()
    val gridBottomPadding = 16.dp + if (managementVisible) 80.dp else 0.dp

    LaunchedEffect(selectionMode) {
        onSelectionModeChange(selectionMode)
    }

    Scaffold(
        containerColor = shelfBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionMode) "已选 ${selectedIds.size} 本" else "我的书架",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
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
                    containerColor = shelfBg,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(shelfBg)
                .padding(paddingValues)
        ) {
            if (books.isEmpty()) {
                EmptyBookshelf(
                    onImportClick = {
                        filePickerLauncher.launch(
                            arrayOf("text/markdown", "text/plain", "text/x-markdown")
                        )
                    }
                )
            } else {
                val gridCount = if (selectionMode) books.size else books.size + 1
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
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
                        count = gridCount,
                        key = { index ->
                            if (index < books.size) books[index].id else "import_card"
                        }
                    ) { index ->
                        if (index < books.size) {
                            val book = books[index]
                            val selected = book.id in selectedIds
                            BookCard(
                                book = book,
                                selected = selected,
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) {
                                        selectedIds =
                                            if (selected) selectedIds - book.id else selectedIds + book.id
                                    } else {
                                        navController.navigate("reader/${book.id}")
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
                        } else {
                            ImportBookCard(
                                onClick = {
                                    filePickerLauncher.launch(
                                        arrayOf(
                                            "text/markdown",
                                            "text/plain",
                                            "text/x-markdown"
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (managementVisible) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    color = barBg,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("移出书架") },
            text = { Text("确定将选中的 ${selectedIds.size} 本书从书架移除吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBooks(selectedIds)
                        showRemoveConfirm = false
                        exitSelection()
                    }
                ) {
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showGroupDialog) {
        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text("设置分组") },
            text = {
                OutlinedTextField(
                    value = groupInput,
                    onValueChange = { groupInput = it },
                    label = { Text("分组名称") },
                    supportingText = { Text("留空则取消分组") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.moveSelectedToGroup(selectedIds, groupInput)
                        showGroupDialog = false
                        exitSelection()
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGroupDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ManagementBarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EmptyBookshelf(
    onImportClick: () -> Unit
) {
    val shelfBg =
        if (isSystemInDarkTheme()) BookshelfPageBackgroundDark
        else BookshelfPageBackground
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(shelfBg)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "书架空空如也",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击导入你的第一本 Markdown 书籍",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        ImportBookCard(onClick = onImportClick)
    }
}

@Composable
private fun ImportBookCard(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val dashSurface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .shadow(2.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, outline, RoundedCornerShape(8.dp))
                .background(dashSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "导入书籍",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Spacer(modifier = Modifier.height(36.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    book: BookEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val coverColor = BookCoverColors[book.coverColor % BookCoverColors.size]
    val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
    val subtitle = when {
        book.shelfGroup.isNotBlank() -> book.shelfGroup
        book.author != null -> book.author
        book.lastReadTime != null -> dateFormat.format(book.lastReadTime)
        else -> "未读"
    }
    val borderColor = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .width(100.dp)
            .then(
                if (selected) Modifier.border(2.dp, borderColor, shape) else Modifier
            )
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .shadow(4.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(coverColor)
                .padding(8.dp)
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 12.sp
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart)
            )

            if (book.readingProgress > 0) {
                Column(
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    LinearProgressIndicator(
                        progress = book.readingProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(book.readingProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            if (book.isFavorite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                )
            }

            if (book.isPinned && !selectionMode) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "已置顶",
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(14.dp)
                )
            }

            if (selectionMode && selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = book.title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
