package com.example.markdownreader.ui.screens.bookshelf

import android.app.Activity
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.markdownreader.data.local.entity.BookEntity
import com.example.markdownreader.ui.theme.BookCoverColors
import com.example.markdownreader.ui.theme.BookshelfPageBackground
import com.example.markdownreader.ui.theme.BookshelfPageBackgroundDark
import com.example.markdownreader.ui.theme.MainNavigationBarBackground
import java.text.SimpleDateFormat
import java.io.File
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

private val BookshelfImportMimeTypes = arrayOf(
    "text/markdown",
    "text/x-markdown",
    "text/plain",
    "application/epub+zip",
    "application/pdf",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/msword",
    "application/x-mobipocket-ebook",
    "application/vnd.amazon.mobi8-ebook",
    "application/vnd.amazon.ebook",
    "application/octet-stream"
)

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

    val view = LocalView.current
    val systemInDarkTheme = isSystemInDarkTheme()
    val shelfBg =
        if (systemInDarkTheme) BookshelfPageBackgroundDark
        else BookshelfPageBackground

    DisposableEffect(shelfBg) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val prevColor = window.statusBarColor
        val prevLightStatusBars = controller.isAppearanceLightStatusBars
        window.statusBarColor = shelfBg.toArgb()
        controller.isAppearanceLightStatusBars = systemInDarkTheme
        onDispose {
            window.statusBarColor = prevColor
            controller.isAppearanceLightStatusBars = prevLightStatusBars
        }
    }

    DisposableEffect(Unit) {
        onDispose { onSelectionModeChange(false) }
    }

    val barBg = MainNavigationBarBackground
    val managementVisible = selectionMode && selectedIds.isNotEmpty()
    val gridBottomPadding = 16.dp + if (managementVisible) 80.dp else 0.dp

    LaunchedEffect(selectionMode) {
        onSelectionModeChange(selectionMode)
    }

    Scaffold(
        containerColor = shelfBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionMode) "已选 ${selectedIds.size} 本" else "书架",
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
                EmptyBookshelf(onImportClick = { openImportChooser() })
            } else {
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
                    // 用 items(list, key={it.id}) + 末尾 item 写法，避免按 count+index 提供
                    // key 时，gridCount 与 key lambda 跨 snapshot 读到不同 books.size，
                    // 导致多个尾部 index 同时映射到 "import_card" 触发重复 key 崩溃。
                    items(
                        items = books,
                        key = { it.id }
                    ) { book ->
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
                    }
                    if (!selectionMode) {
                        item(key = "import_card") {
                            ImportBookCard(onClick = { openImportChooser() })
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

    if (showImportMethodDialog) {
        AlertDialog(
            onDismissRequest = { showImportMethodDialog = false },
            title = { Text("导入书籍") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "支持 Markdown、TXT、EPUB、PDF、DOC、DOCX、MOBI、AZW3",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            showImportMethodDialog = false
                            filePickerLauncher.launch(BookshelfImportMimeTypes)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("从本地导入", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = {
                            showImportMethodDialog = false
                            showUrlImportDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("从网址导入", modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportMethodDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showUrlImportDialog) {
        AlertDialog(
            onDismissRequest = {
                showUrlImportDialog = false
                urlImportText = ""
            },
            title = { Text("从网址导入") },
            text = {
                OutlinedTextField(
                    value = urlImportText,
                    onValueChange = { urlImportText = it },
                    label = { Text("文件下载地址（https://…）") },
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val url = urlImportText.trim()
                        if (url.isNotEmpty()) {
                            viewModel.importFromUrl(url)
                        }
                        showUrlImportDialog = false
                        urlImportText = ""
                    }
                ) {
                    Text("导入")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUrlImportDialog = false
                        urlImportText = ""
                    }
                ) {
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
            text = "支持 Markdown、TXT、EPUB 等多种格式，点击下方导入",
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
    var coverImage by remember(book.id, book.coverImagePath) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(book.id, book.coverImagePath) {
        coverImage = withContext(Dispatchers.IO) {
            val p = book.coverImagePath ?: return@withContext null
            if (!File(p).exists()) return@withContext null
            BitmapFactory.decodeFile(p)?.asImageBitmap()
        }
    }
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
        ) {
            val bmp = coverImage
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize().background(coverColor))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 12.sp
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
            }

            if (book.readingProgress > 0) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
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
                        .padding(6.dp)
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
