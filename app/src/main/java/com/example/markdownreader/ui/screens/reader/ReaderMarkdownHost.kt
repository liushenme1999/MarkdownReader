package com.example.markdownreader.ui.screens.reader

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
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
import com.example.markdownreader.data.local.entity.HighlightEntity
import com.example.markdownreader.importing.ImportedBookFormat
import com.example.markdownreader.model.ReaderPageTurnMode
import com.example.markdownreader.ui.components.iconTintForDeleteStrip
import com.example.markdownreader.ui.theme.MarkdownReaderTheme
import com.example.markdownreader.ui.theme.ReadingTheme
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReaderPagedMarkdownHost(
    pages: List<Pair<String, Int>>,
    pageTurnMode: ReaderPageTurnMode,
    pagerState: PagerState,
    theme: ReadingTheme,
    fontSize: Int,
    readerPaddingDp: Int,
    readerPaddingHorizontalDp: Int = readerPaddingDp,
    readerPaddingTopDp: Int = readerPaddingDp,
    readerLineSpacingMultiplier: Float,
    highlights: List<HighlightEntity>,
    pageTextViews: MutableMap<Int, TextView>,
    renderPlainText: Boolean,
    modifier: Modifier = Modifier,
    onTextSelected: (String) -> Unit,
    onReadingVerticalScroll: (Int) -> Unit,
    onSwipeDownBookmark: () -> Unit,
    onCenterTap: () -> Unit,
    onPageTextViewReady: (Int, TextView) -> Unit,
    pdfFullWidthImages: Boolean = false,
) {
    val layoutDirection = LocalLayoutDirection.current
    val cameraDistancePx = with(LocalDensity.current) { 12f * density * 80f }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { pageIndex ->
        val outOfCenter = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
        val pageModifier = when (pageTurnMode) {
            ReaderPageTurnMode.HorizontalSwipe ->
                Modifier.fillMaxSize()
            ReaderPageTurnMode.SimulationPageTurn ->
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.cameraDistance = cameraDistancePx
                        transformOrigin = TransformOrigin(
                            pivotFractionX = if (layoutDirection == LayoutDirection.Ltr) 0f else 1f,
                            pivotFractionY = 0.5f
                        )
                        rotationY = (-outOfCenter * 62f).coerceIn(-82f, 82f)
                        alpha = 1f - 0.12f * abs(outOfCenter).coerceIn(0f, 1.5f)
                    }
            ReaderPageTurnMode.CoverPageTurn ->
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = size.width * outOfCenter * 0.92f
                    }
            else -> Modifier.fillMaxSize()
        }

        val (slice, globalStart) = pages[pageIndex]
        val globalEndExclusive = if (pageIndex + 1 < pages.size) {
            pages[pageIndex + 1].second
        } else {
            Int.MAX_VALUE
        }
        val pageHighlights = remember(highlights, slice, globalStart, globalEndExclusive) {
            highlightsForPageSlice(globalStart, globalEndExclusive, highlights, slice)
        }

        Box(modifier = pageModifier) {
            MarkdownReaderView(
                content = slice,
                renderPlainText = renderPlainText,
                theme = theme,
                fontSize = fontSize,
                readerPaddingDp = readerPaddingDp,
                readerPaddingHorizontalDp = readerPaddingHorizontalDp,
                readerPaddingTopDp = readerPaddingTopDp,
                readerLineSpacingMultiplier = readerLineSpacingMultiplier,
                highlights = pageHighlights,
                modifier = Modifier.fillMaxSize(),
                onTextSelected = onTextSelected,
                onScroll = { },
                onReadingVerticalScroll = onReadingVerticalScroll,
                onViewReady = { tv ->
                    pageTextViews[pageIndex] = tv
                    onPageTextViewReady(pageIndex, tv)
                },
                allowVerticalScroll = false,
                onSwipeDownBookmark = onSwipeDownBookmark,
                onSwipeRightBookmark = {},
                onCenterTap = onCenterTap,
                pdfFullWidthImages = pdfFullWidthImages,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderPageTurnSheet(
    currentMode: ReaderPageTurnMode,
    onModeSelected: (ReaderPageTurnMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "翻页方式",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "横向模式按段落估算分页，复杂排版可能与上下滚动略有差异。左右滑动/仿真/覆盖翻页时：仅可左右翻页，向下滑动添加书签，再次滑动取消书签。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            ReaderPageTurnMode.values().forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onModeSelected(mode)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mode == currentMode,
                        onClick = {
                            onModeSelected(mode)
                            onDismiss()
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
