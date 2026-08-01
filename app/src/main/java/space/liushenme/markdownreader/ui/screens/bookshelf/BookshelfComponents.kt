package space.liushenme.markdownreader.ui.screens.bookshelf

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
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.ui.components.AppSearchField
import space.liushenme.markdownreader.importing.BookImportSupport
import space.liushenme.markdownreader.ui.components.shelfStylePageBackground
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import space.liushenme.markdownreader.data.local.entity.BookEntity
import space.liushenme.markdownreader.ui.theme.BookCoverColors
import space.liushenme.markdownreader.navigation.AppRoutes
import space.liushenme.markdownreader.ui.theme.MainNavigationBarBackground
import space.liushenme.markdownreader.ui.theme.MarkdownReaderTheme
import java.text.SimpleDateFormat
import java.io.File
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext


@Composable
internal fun ManagementBarButton(
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
internal fun EmptyBookshelf(
    onImportClick: () -> Unit
) {
    val shelfBg = shelfStylePageBackground()

    // 呼吸动画 - alpha 脉冲
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(shelfBg)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.bookshelf_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.bookshelf_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        ImportBookCard(
            onClick = onImportClick,
            modifier = Modifier.width(100.dp)
        )
    }
}

@Composable
internal fun ImportBookCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val dashSurface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val primaryColor = MaterialTheme.colorScheme.primary

    val infiniteTransition = rememberInfiniteTransition(label = "dash_offset")
    val dashOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dash_offset"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .shadow(2.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(dashSurface)
                .drawBehind {
                    val strokeWidth = 2.dp.toPx()
                    val cornerRadius = 8.dp.toPx()
                    drawRoundRect(
                        color = outline,
                        style = Stroke(
                            width = strokeWidth,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(10f, 6f),
                                dashOffset
                            )
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.bookshelf_import_cd),
                    modifier = Modifier.size(28.dp),
                    tint = primaryColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.bookshelf_import),
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryColor.copy(alpha = 0.8f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 与 BookCard 标题 + 副标题占位对齐，保持网格行高一致
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun BookCard(
    book: BookEntity,
    selected: Boolean,
    selectionMode: Boolean,
    coverImageCache: LruCache<String, ImageBitmap>,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val coverColor = BookCoverColors[book.coverColor % BookCoverColors.size]
    var coverImage by remember(book.id, book.coverImagePath) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(book.id, book.coverImagePath) {
        val cacheKey = book.coverImagePath ?: return@LaunchedEffect
        val cached = coverImageCache.get(cacheKey)
        if (cached != null) {
            coverImage = cached
        } else {
            val decoded = withContext(Dispatchers.IO) {
                if (!File(cacheKey).exists()) return@withContext null
                BitmapFactory.decodeFile(cacheKey)?.asImageBitmap()
            }
            if (decoded != null) {
                coverImageCache.put(cacheKey, decoded)
            }
            coverImage = decoded
        }
    }
    val dateFormat = SimpleDateFormat("MM-dd", Locale.getDefault())
    val unreadLabel = stringResource(R.string.bookshelf_unread)
    val subtitle = when {
        book.shelfGroup.isNotBlank() -> book.shelfGroup
        book.author != null -> book.author
        book.lastReadTime != null -> dateFormat.format(book.lastReadTime)
        else -> unreadLabel
    }
    val borderColor = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(10.dp)

    // 点击缩放动画
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            stiffness = 400f,
            dampingRatio = 0.6f
        ),
        label = "card_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            androidx.compose.ui.input.pointer.PointerEventType.Press -> {
                                isPressed = true
                            }
                            androidx.compose.ui.input.pointer.PointerEventType.Release -> {
                                isPressed = false
                            }
                        }
                    }
                }
            }
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
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(10.dp),
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.16f)
                )
                .clip(RoundedCornerShape(10.dp))
        ) {
            val bmp = coverImage
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // 封面底部渐变遮罩，让标题文字更清晰
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f)
                                ),
                                startY = 0f,
                                endY = 40f
                            )
                        )
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
                // 无封面时也添加底部渐变遮罩
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f)
                                ),
                                startY = 0f,
                                endY = 40f
                            )
                        )
                )
            }

            if (book.readingProgress > 0) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { book.readingProgress },
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
                    contentDescription = stringResource(R.string.bookshelf_pinned_cd),
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
