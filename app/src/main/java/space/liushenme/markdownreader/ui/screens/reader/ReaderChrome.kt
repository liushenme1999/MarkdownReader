package space.liushenme.markdownreader.ui.screens.reader

import android.app.Activity
import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import space.liushenme.markdownreader.R
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.graphics.ColorUtils
import androidx.core.text.PrecomputedTextCompat
import androidx.core.view.WindowCompat
import androidx.core.widget.TextViewCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.importing.ImportedBookFormat
import space.liushenme.markdownreader.model.ReaderPageTurnMode
import space.liushenme.markdownreader.ui.components.MarkdownInlineHtmlText
import space.liushenme.markdownreader.ui.components.iconTintForDeleteStrip
import space.liushenme.markdownreader.ui.theme.MarkdownReaderTheme
import space.liushenme.markdownreader.ui.theme.ReadingTheme
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.file.FileSchemeHandler
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun ReaderImmersiveChapterTitleBar(
    viewModel: ReaderViewModel,
    tocEntries: List<MarkdownTocEntry>,
    totalChars: Int,
    fallbackTitle: String,
    theme: ReadingTheme,
    modifier: Modifier = Modifier,
    reserveStatusBarInset: Boolean = true,
) {
    val readingProgress by viewModel.readingProgress.collectAsState()
    val chapterEntry = remember(tocEntries, readingProgress, totalChars) {
        currentChapterEntryForProgress(tocEntries, readingProgress, totalChars)
    }
    ReaderImmersiveChapterTitleBar(
        title = chapterEntry?.rawTitle ?: chapterEntry?.title ?: fallbackTitle,
        theme = theme,
        progressPercent = (readingProgress * 100).toInt(),
        modifier = modifier,
        reserveStatusBarInset = reserveStatusBarInset,
    )
}

@Composable
internal fun rememberChapterTitleForProgress(
    viewModel: ReaderViewModel,
    tocEntries: List<MarkdownTocEntry>,
    totalChars: Int,
): String? {
    val readingProgress by viewModel.readingProgress.collectAsState()
    val chapterEntry = remember(tocEntries, readingProgress, totalChars) {
        currentChapterEntryForProgress(tocEntries, readingProgress, totalChars)
    }
    return chapterEntry?.rawTitle ?: chapterEntry?.title
}

@Composable
internal fun ReaderImmersiveChapterTitleBar(
    title: String,
    theme: ReadingTheme,
    progressPercent: Int,
    modifier: Modifier = Modifier,
    reserveStatusBarInset: Boolean = true,
) {
    val percent = progressPercent.coerceIn(0, 100)
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (reserveStatusBarInset) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
            )
        }
        val sidePadding = if (reserveStatusBarInset) 16.dp else 28.dp
        val topPadding = if (reserveStatusBarInset) 2.dp else 8.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = sidePadding, end = sidePadding, top = topPadding, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarkdownInlineHtmlText(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = theme.textColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelMedium,
                color = theme.textColor.copy(alpha = 0.7f),
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
internal fun ReaderImmersiveBottomBar(
    modifier: Modifier = Modifier,
    theme: ReadingTheme,
    chromeBackground: Color,
    onToc: () -> Unit,
    onBookmarks: () -> Unit,
    onReadingSettings: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = chromeBackground
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ReaderImmersiveBottomBarHeight),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
            val iconTint = theme.textColor
            IconButton(onClick = onToc) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Toc,
                    contentDescription = stringResource(R.string.reader_chrome_toc_cd),
                    tint = iconTint
                )
            }
            IconButton(onClick = onBookmarks) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = stringResource(R.string.reader_chrome_bookmark_cd),
                    tint = iconTint
                )
            }
            IconButton(onClick = onReadingSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.reader_chrome_settings_cd),
                    tint = iconTint
                )
            }
            }
            // 底栏背景延伸到屏幕底，图标区在导航条/小白条之上
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopAppBar(
    title: String,
    chapterTitle: String?,
    onNavigateBack: () -> Unit,
    theme: ReadingTheme,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        // Scaffold topBar 或外层 statusBarsPadding 已处理顶 inset，避免与 TopAppBar 默认 insets 叠加
        windowInsets = WindowInsets(),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = theme.textColor,
            navigationIconContentColor = theme.textColor,
            actionIconContentColor = theme.textColor
        ),
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (chapterTitle != null) {
                    MarkdownInlineHtmlText(
                        text = chapterTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back_cd),
                )
            }
        }
    )
}

internal suspend fun SnackbarHostState.showBriefSnackbar(message: String, visibleMs: Long = 1400L) {
    coroutineScope {
        val snackJob = launch {
            showSnackbar(message = message, duration = SnackbarDuration.Indefinite)
        }
        delay(visibleMs)
        currentSnackbarData?.dismiss()
        snackJob.join()
    }
}
