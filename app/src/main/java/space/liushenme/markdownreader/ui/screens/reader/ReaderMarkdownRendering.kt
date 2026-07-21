package space.liushenme.markdownreader.ui.screens.reader

import android.app.Activity
import android.content.Context
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.BackgroundColorSpan
import android.graphics.Rect
import android.os.Build
import android.view.ActionMode
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.textclassifier.TextClassifier
import android.text.method.ArrowKeyMovementMethod
import android.text.method.LinkMovementMethod
import android.text.method.MovementMethod
import android.text.style.ImageSpan
import android.view.View
import android.widget.TextView
import io.noties.markwon.image.AsyncDrawableSpan
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
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.importing.ImportedBookFormat
import space.liushenme.markdownreader.model.ReaderPageTurnMode
import space.liushenme.markdownreader.ui.components.iconTintForDeleteStrip
import space.liushenme.markdownreader.ui.theme.MarkdownReaderTheme
import space.liushenme.markdownreader.ui.theme.ReadingTheme
import space.liushenme.markdownreader.R
import space.liushenme.markdownreader.markdown.DiagramImageLoader
import space.liushenme.markdownreader.markdown.NetworkImageCache
import space.liushenme.markdownreader.markdown.ReaderMarkwonFactory
import space.liushenme.markdownreader.markdown.ReaderTableSpacing
import java.io.File
import io.noties.markwon.Markwon
import io.noties.markwon.core.spans.HeadingSpan
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 长纯文本 PrecomputedText 在后台算布局，减轻主线程测量（MIUI 上易触发 ANR 日志） */
internal val readerPlainTextPrecomputeExecutor =
    java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "reader-plain-precompute").apply { isDaemon = true }
    }

/** Markwon 解析 + 渲染（Markdown → Spanned）也搬到后台线程，主线程只剩 setText + measure。 */
internal val readerMarkwonRenderExecutor =
    java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "reader-markwon-render").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

internal const val PLAIN_TEXT_PRECOMPUTE_THRESHOLD = 6000

/** Markwon 非线程安全（HtmlPlugin 内部状态）；串行化 parse/render，避免与后台 toMarkdown 并发。 */
internal val markwonRenderLock = Any()

/** 曾用于主线程同步渲染；现一律走 [readerMarkwonRenderExecutor] 以避免 HtmlPlugin 并发崩溃。 */
internal const val MARKWON_BACKGROUND_RENDER_THRESHOLD = 4000

internal fun readerRenderSignature(
    content: String,
    renderPlainText: Boolean,
    themeName: String,
    fontSize: Int,
    highlights: List<HighlightEntity>,
): String {
    val hlKey = highlights.joinToString("|") { "${it.id}_${it.startPosition}_${it.endPosition}" }
    return "${renderPlainText}_${content.length}_${content.hashCode()}_${themeName}_${fontSize}_$hlKey"
}

internal fun applyReaderTextContent(
    textView: TextView,
    content: String,
    renderPlainText: Boolean,
    renderSig: String,
    markwon: Markwon,
    highlights: List<HighlightEntity>,
    highlightColorArgb: Int,
    pdfFullWidthImages: Boolean = false,
    pdfCenterImageVertically: Boolean = false,
) {
    textView.setTag(TAG_READER_RENDER_SIG, renderSig)
    if (!renderPlainText) {
        applyMarkdownContent(
            textView = textView,
            content = content,
            renderSig = renderSig,
            markwon = markwon,
            highlights = highlights,
            highlightColorArgb = highlightColorArgb,
            pdfFullWidthImages = pdfFullWidthImages,
            pdfCenterImageVertically = pdfCenterImageVertically,
        )
        return
    }
    if (content.length <= PLAIN_TEXT_PRECOMPUTE_THRESHOLD) {
        val sp = SpannableString(content)
        refreshStashedExpandAnchorBeforeContentSwap(textView)
        textView.setText(sp, TextView.BufferType.SPANNABLE)
        if (!applyReaderScrollToTopIfAny(textView, clearAfter = true)) {
            if (!applyStashedSavedPositionSnapIfAny(textView)) {
                applyStashedSourceScrollRestoreIfAny(textView, renderPlainText = true)
            }
        }
        applyHighlightsToRenderedText(textView, highlights, highlightColorArgb)
        return
    }
    val params = TextViewCompat.getTextMetricsParams(textView)
    readerPlainTextPrecomputeExecutor.execute {
        val pre = PrecomputedTextCompat.create(content, params)
        textView.post {
            if (textView.getTag(TAG_READER_RENDER_SIG) != renderSig) return@post
            refreshStashedExpandAnchorBeforeContentSwap(textView)
            TextViewCompat.setPrecomputedText(textView, pre)
            if (!applyReaderScrollToTopIfAny(textView, clearAfter = true)) {
                if (!applyStashedSavedPositionSnapIfAny(textView)) {
                    applyStashedSourceScrollRestoreIfAny(textView, renderPlainText = true)
                }
            }
            applyHighlightsToRenderedText(textView, highlights, highlightColorArgb)
        }
    }
}

internal fun applyMarkdownContent(
    textView: TextView,
    content: String,
    renderSig: String,
    markwon: Markwon,
    highlights: List<HighlightEntity>,
    highlightColorArgb: Int,
    pdfFullWidthImages: Boolean = false,
    pdfCenterImageVertically: Boolean = false,
) {
    fun finishMarkdownRender() {
        textView.setTag(R.id.reader_markdown_render_complete, renderSig)
        // 目录跳转置顶意图优先：直接 scrollY=0 并清意图，不再走扩窗 stash 恢复。
        // 打开书/书签 snap 次之：同帧滚到目标，避免 scrollY=0 先画一帧「别的章节」再跳回。
        // 扩窗滚动恢复交给 onLayout（首帧 draw 前）。此处若 layout 未就绪不要 post，
        // 否则会在 scrollY=0 先画一帧「开头」再跳回。
        if (!applyReaderScrollToTopIfAny(textView, clearAfter = true)) {
            if (!applyStashedSavedPositionSnapIfAny(textView)) {
                applyStashedSourceScrollRestoreIfAny(textView, renderPlainText = false)
            }
        }
        val anchorIndex = textView.getTag(R.id.markdown_anchor_index) as? space.liushenme.markdownreader.markdown.MarkdownAnchorIndex
        if (anchorIndex != null) {
            textView.post { space.liushenme.markdownreader.markdown.RenderedAnchorBinder.bind(textView, anchorIndex) }
        }
        applyHighlightsToRenderedText(textView, highlights, highlightColorArgb)
        if (pdfFullWidthImages) {
            if (pdfCenterImageVertically) {
                PdfImageLayoutHelper.clearLayoutState(textView)
            }
            PdfImageLayoutHelper.scheduleApplyPdfPageLayout(
                textView = textView,
                centerVertically = pdfCenterImageVertically,
            )
        }
    }

    textView.setTag(R.id.reader_markdown_render_complete, null)
    val prepared = ReaderMarkwonFactory.prepareMarkdown(content)
    textView.setTag(R.id.markdown_anchor_index, prepared.anchorIndex)
    val markdown = NetworkImageCache.rewriteCachedUrls(textView.context, prepared.text)
    // 一律后台 Markwon 渲染：主线程 setMarkdown 与 executor 上 toMarkdown 并发会触发 HtmlPlugin CME。
    readerMarkwonRenderExecutor.execute {
        val rendered: CharSequence = synchronized(markwonRenderLock) {
            runCatching { markwon.toMarkdown(markdown) }.getOrNull() ?: content
        }
        textView.post {
            if (textView.getTag(TAG_READER_RENDER_SIG) != renderSig) return@post
            textView.setTag(R.id.markdown_anchor_index, prepared.anchorIndex)
            // 新内容替换前重抓扩窗锚点：渲染在途时用户可能已滑动旧内容，旧锚点会把视口拉回去。
            refreshStashedExpandAnchorBeforeContentSwap(textView)
            synchronized(markwonRenderLock) {
                markwon.setParsedMarkdown(
                    textView,
                    rendered as? android.text.Spanned ?: android.text.SpannableString(rendered),
                )
            }
            finishMarkdownRender()
        }
    }
}

@Composable
internal fun MarkdownReaderView(
    content: String,
    renderPlainText: Boolean,
    theme: ReadingTheme,
    fontSize: Int,
    readerPaddingDp: Int,
    readerPaddingHorizontalDp: Int = readerPaddingDp,
    readerPaddingTopDp: Int = readerPaddingDp,
    readerLineSpacingMultiplier: Float,
    highlights: List<space.liushenme.markdownreader.data.local.entity.HighlightEntity>,
    modifier: Modifier = Modifier.fillMaxSize(),
    onTextSelected: (String) -> Unit,
    onScroll: (Float) -> Unit,
    onReadingVerticalScroll: (verticalScrollDeltaPx: Int) -> Unit,
    onViewReady: (TextView) -> Unit,
    allowVerticalScroll: Boolean = true,
    onSwipeRightBookmark: () -> Unit = {},
    onSwipeDownBookmark: (() -> Unit)? = null,
    onCenterTap: () -> Unit,
    onDiagramTap: (android.graphics.Bitmap) -> Unit = {},
    onReaderTextSelectionActiveChange: (Boolean) -> Unit = {},
    pdfFullWidthImages: Boolean = false,
) {
    val context = LocalContext.current
    val pdfPagedLayout = pdfFullWidthImages && !allowVerticalScroll
    val markwon = remember(pdfFullWidthImages) {
        ReaderMarkwonFactory.create(context)
    }
    val touchState = remember { ReaderTouchState() }
    val slop = ViewConfiguration.get(context).scaledTouchSlop

    AndroidView(
        factory = { ctx ->
            SafeReaderTextView(ctx).apply {
                this.allowVerticalScroll = allowVerticalScroll
                this.renderPlainTextBody = renderPlainText
                if (pdfPagedLayout) {
                    PdfImageLayoutHelper.applyPagedPdfTextGravity(this, centerVertically = true)
                    includeFontPadding = false
                }
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(theme.textColor.toArgb())
                setBackgroundColor(theme.backgroundColor.toArgb())
                textSize = fontSize.toFloat()
                val density = resources.displayMetrics.density
                val padHPx = (readerPaddingHorizontalDp * density).toInt().coerceAtLeast(0)
                val padBottomPx = (readerPaddingDp * density).toInt().coerceAtLeast(0)
                val padTopPx = (readerPaddingTopDp * density).toInt().coerceAtLeast(0)
                setPadding(padHPx, padTopPx, padHPx, padBottomPx)
                ReaderTableSpacing.lineSpacingMultiplier = readerLineSpacingMultiplier
                setLineSpacing(0f, readerLineSpacingMultiplier)
                setTag(TAG_READER_LINE_SPACING, readerLineSpacingMultiplier)

                val sig0 = readerRenderSignature(
                    content = content,
                    renderPlainText = renderPlainText,
                    themeName = theme::class.java.name,
                    fontSize = fontSize,
                    highlights = highlights,
                )
                applyReaderTextContent(
                    textView = this,
                    content = content,
                    renderPlainText = renderPlainText,
                    renderSig = sig0,
                    markwon = markwon,
                    highlights = highlights,
                    highlightColorArgb = theme.highlightColor.toArgb(),
                    pdfFullWidthImages = pdfFullWidthImages,
                    pdfCenterImageVertically = pdfPagedLayout,
                )

                this.onReaderTextSelectionActiveChange = onReaderTextSelectionActiveChange
                bindReaderGesturesAndScroll(
                    textView = this,
                    touchState = touchState,
                    slop = slop,
                    allowVerticalScroll = allowVerticalScroll,
                    onScroll = onScroll,
                    onReadingVerticalScroll = onReadingVerticalScroll,
                    onSwipeRightBookmark = onSwipeRightBookmark,
                    onSwipeDownBookmark = onSwipeDownBookmark,
                    onCenterTap = onCenterTap,
                    onDiagramTap = onDiagramTap,
                )
                post { onViewReady(this) }
            }
        },
        update = { textView ->
            textView.allowVerticalScroll = allowVerticalScroll
            (textView as? SafeReaderTextView)?.renderPlainTextBody = renderPlainText
            textView.onReaderTextSelectionActiveChange = onReaderTextSelectionActiveChange
            textView.setTextColor(theme.textColor.toArgb())
            textView.setBackgroundColor(theme.backgroundColor.toArgb())
            textView.textSize = fontSize.toFloat()
            val density = textView.resources.displayMetrics.density
            val padHPx = (readerPaddingHorizontalDp * density).toInt().coerceAtLeast(0)
            val padBottomPx = (readerPaddingDp * density).toInt().coerceAtLeast(0)
            val padTopPx = (readerPaddingTopDp * density).toInt().coerceAtLeast(0)
            val renderSig = readerRenderSignature(
                content = content,
                renderPlainText = renderPlainText,
                themeName = theme::class.java.name,
                fontSize = fontSize,
                highlights = highlights,
            )
            val prevSig = textView.getTag(TAG_READER_RENDER_SIG) as? String
            val contentChanged = prevSig != renderSig
            if (contentChanged) {
                if (pdfPagedLayout) {
                    PdfImageLayoutHelper.clearLayoutState(textView)
                }
                textView.setPadding(padHPx, padTopPx, padHPx, padBottomPx)
            } else if (pdfPagedLayout) {
                textView.setPadding(padHPx, textView.paddingTop, padHPx, padBottomPx)
            } else {
                textView.setPadding(padHPx, padTopPx, padHPx, padBottomPx)
            }
            ReaderTableSpacing.lineSpacingMultiplier = readerLineSpacingMultiplier
            textView.setLineSpacing(0f, readerLineSpacingMultiplier)
            val prevLineSpacing = textView.getTag(TAG_READER_LINE_SPACING) as? Float
            if (prevLineSpacing != null && prevLineSpacing != readerLineSpacingMultiplier && !contentChanged) {
                textView.post {
                    val t = textView.text
                    if (!t.isNullOrEmpty()) textView.text = t
                }
            }
            textView.setTag(TAG_READER_LINE_SPACING, readerLineSpacingMultiplier)
            if (pdfPagedLayout) {
                PdfImageLayoutHelper.applyPagedPdfTextGravity(textView, centerVertically = true)
                textView.includeFontPadding = false
            }

            if (contentChanged) {
                applyReaderTextContent(
                    textView = textView,
                    content = content,
                    renderPlainText = renderPlainText,
                    renderSig = renderSig,
                    markwon = markwon,
                    highlights = highlights,
                    highlightColorArgb = theme.highlightColor.toArgb(),
                    pdfFullWidthImages = pdfFullWidthImages,
                    pdfCenterImageVertically = pdfPagedLayout,
                )
            }

            bindReaderGesturesAndScroll(
                textView = textView,
                touchState = touchState,
                slop = slop,
                allowVerticalScroll = allowVerticalScroll,
                onScroll = onScroll,
                onReadingVerticalScroll = onReadingVerticalScroll,
                onSwipeRightBookmark = onSwipeRightBookmark,
                onSwipeDownBookmark = onSwipeDownBookmark,
                onCenterTap = onCenterTap,
                onDiagramTap = onDiagramTap,
            )
            if (pdfPagedLayout && textView.text?.isNotEmpty() == true) {
                PdfImageLayoutHelper.scheduleApplyPdfPageLayout(
                    textView = textView,
                    centerVertically = true,
                )
            }
            if (!allowVerticalScroll) {
                textView.post {
                    textView.scrollTo(0, 0)
                    if (pdfPagedLayout) {
                        PdfImageLayoutHelper.applyPdfPageLayout(textView)
                    }
                }
            }
            textView.post { onViewReady(textView) }
        },
        modifier = modifier
    )
}

internal class ReaderTouchState(
    var downX: Float = 0f,
    var downY: Float = 0f,
    var scrollYOnDown: Int = 0,
    /** 本次触摸开始时已有选区，或划词过程中出现选区 → 禁用滑动加书签 */
    var blockBookmarkSwipeGesture: Boolean = false,
)

/** 当前是否处于文本选区（含 SafeReaderTextView 会话与 Spannable 选区）。 */
internal fun TextView.hasActiveReaderTextSelection(): Boolean {
    (this as? SafeReaderTextView)?.let { return it.isInTextSelection() }
    val spannable = text as? Spannable ?: return false
    val start = Selection.getSelectionStart(spannable)
    val end = Selection.getSelectionEnd(spannable)
    return start >= 0 && end >= 0 && start != end
}

internal fun bindReaderGesturesAndScroll(
    textView: TextView,
    touchState: ReaderTouchState,
    slop: Int,
    allowVerticalScroll: Boolean,
    onScroll: (Float) -> Unit,
    onReadingVerticalScroll: (verticalScrollDeltaPx: Int) -> Unit,
    onSwipeRightBookmark: () -> Unit,
    onSwipeDownBookmark: (() -> Unit)? = null,
    onCenterTap: () -> Unit,
    onDiagramTap: (android.graphics.Bitmap) -> Unit = {},
) {
    textView.setOnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
        if (!allowVerticalScroll) return@setOnScrollChangeListener
        if (scrollY != oldScrollY) {
            onReadingVerticalScroll(kotlin.math.abs(scrollY - oldScrollY))
        }
        val tv = v as? TextView ?: return@setOnScrollChangeListener
        onScroll(localCharProgressAtScrollTop(tv))
    }

    textView.setOnTouchListener { v, e ->
        val tv = v as? TextView
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchState.downX = e.x
                touchState.downY = e.y
                touchState.scrollYOnDown = tv?.scrollY ?: 0
                touchState.blockBookmarkSwipeGesture = tv?.hasActiveReaderTextSelection() == true
                (tv as? SafeReaderTextView)?.prepareForNewTouch(e.x, e.y)
                // 手指按下即解除目录/书签跳转锁定：否则滑走后 diagram 仍按标题 offset 拉回。
                if (allowVerticalScroll) {
                    clearPendingScrollCharOffset(tv)
                }
                if (tv is SafeReaderTextView && tv.isInTextSelection()) {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                } else if (!allowVerticalScroll) {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (tv?.hasActiveReaderTextSelection() == true) {
                    touchState.blockBookmarkSwipeGesture = true
                }
                if (tv is SafeReaderTextView && tv.isInTextSelection()) {
                    return@setOnTouchListener false
                }
                if (allowVerticalScroll && tv is SafeReaderTextView) {
                    val dx = e.x - touchState.downX
                    val dy = e.y - touchState.downY
                    val markedDrag = tv.tryMarkVerticalScrollDrag(dx, dy)
                    if (markedDrag) {
                        // 用户真正开始拖动即放弃目录置顶意图（程序触发的 scrollTo 不经此路径，故安全）。
                        clearReaderScrollToTop(tv)
                        // 纵向拖动一开始就走 onScroll：清除跳转补滚，并在顶部/底部无 scrollY 变化时仍能扩窗。
                        onScroll(localCharProgressAtScrollTop(tv))
                    } else if (tv.isUserVerticalScrollDrag()) {
                        val atTopPullingPrev = tv.scrollY <= 0 && dy > slop
                        val atBottomPullingNext = isReaderTextViewAtScrollBottom(tv) && dy < -slop
                        if (atTopPullingPrev || atBottomPullingNext) {
                            onScroll(localCharProgressAtScrollTop(tv))
                        }
                    }
                }
                if (!allowVerticalScroll && tv != null) {
                    val dx = e.x - touchState.downX
                    val dy = e.y - touchState.downY
                    val adx = kotlin.math.abs(dx)
                    val ady = kotlin.math.abs(dy)
                    when {
                        ady > adx && ady > slop -> {
                            // 纵向手势由 TextView 消费，避免上下滚动；横向交给 HorizontalPager 翻页
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                            return@setOnTouchListener true
                        }
                        adx > ady && adx > slop -> {
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                            return@setOnTouchListener false
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!allowVerticalScroll) {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                val wasVerticalDrag = (tv as? SafeReaderTextView)?.isUserVerticalScrollDrag() == true
                if (allowVerticalScroll && wasVerticalDrag) {
                    clearPendingScrollCharOffset(tv)
                    onScroll(localCharProgressAtScrollTop(tv))
                }
                val blockBookmarkSwipe = touchState.blockBookmarkSwipeGesture ||
                    tv?.hasActiveReaderTextSelection() == true
                touchState.blockBookmarkSwipeGesture = false
                if (e.actionMasked != MotionEvent.ACTION_UP) return@setOnTouchListener false
                if (tv is SafeReaderTextView && tv.dispatchLinkClickIfPresent(e.x, e.y)) {
                    return@setOnTouchListener true
                }
                // 划词/选区期间跳过中心点击与滑动加书签
                if (tv is SafeReaderTextView && tv.isInTextSelection()) {
                    return@setOnTouchListener false
                }
                val dx = e.x - touchState.downX
                val dy = e.y - touchState.downY
                val adx = kotlin.math.abs(dx)
                val ady = kotlin.math.abs(dy)
                if (adx < slop && ady < slop) {
                    val diagramBitmap = (tv as? SafeReaderTextView)
                        ?.diagramBitmapAt(touchState.downX, touchState.downY)
                    if (diagramBitmap != null) {
                        onDiagramTap(diagramBitmap)
                        return@setOnTouchListener true
                    }
                    val w = v.width.toFloat()
                    val h = v.height.toFloat()
                    if (w > 0f && h > 0f &&
                        e.x in (w * 0.32f)..(w * 0.68f) &&
                        e.y in (h * 0.36f)..(h * 0.64f)
                    ) {
                        onCenterTap()
                    }
                } else if (!blockBookmarkSwipe && allowVerticalScroll && dx > 120f && dx > ady * 2f) {
                    onSwipeRightBookmark()
                } else if (!blockBookmarkSwipe && onSwipeDownBookmark != null &&
                    dy > 120f &&
                    dy > adx * 2f &&
                    (!allowVerticalScroll ||
                        (tv != null && touchState.scrollYOnDown == 0 && tv.scrollY == 0))
                ) {
                    onSwipeDownBookmark.invoke()
                    return@setOnTouchListener true
                }
            }
        }
        false
    }
}

internal fun scrollTextViewToProgress(tv: TextView, progress: Float) {
    val layout = tv.layout ?: return
    val innerH = tv.height - tv.paddingTop - tv.paddingBottom
    if (innerH <= 0) return
    val maxScroll = (layout.height - innerH).coerceAtLeast(0)
    val y = (maxScroll * progress.coerceIn(0f, 1f)).toInt()
    tv.scrollTo(0, y)
}

/** 取 TextView 当前视口顶部附近可见的纯文本，用作书签预览。 */
internal fun previewPlainTextFromTextViewTop(tv: TextView): String {
    val layout = tv.layout ?: return ""
    val text = tv.text ?: return ""
    val len = text.length
    if (len == 0) return ""
    val padTop = tv.compoundPaddingTop
    val y = (tv.scrollY + padTop).coerceAtLeast(0)
    val line = layout.getLineForVertical(y).coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))
    val start = layout.getLineStart(line).coerceIn(0, (len - 1).coerceAtLeast(0))
    val end = (start + 160).coerceAtMost(len)
    return text.substring(start, end)
        .replace('\n', ' ')
        .trim()
        .ifEmpty { "书签" }
        .take(100)
}

/**
 * 阅读器 TextView：参考 Legado MdRead，始终 textIsSelectable + 系统 Editor 选词；
 * 仅拦截图表/图片长按，选区期间抑制扩窗与滑动加书签。
 */
internal class SafeReaderTextView(context: Context) : TextView(context) {
    var allowVerticalScroll: Boolean = true
    /** 正文是否按纯文本渲染；扩窗 stash 恢复时需与写入内容一致。 */
    var renderPlainTextBody: Boolean = false
    var onReaderTextSelectionActiveChange: ((Boolean) -> Unit)? = null

    /** 引用计数：多个并发异步渲染各自递增，仅全部完成后才解除抑制。 */
    private var suppressScrollRefCount: Int = 0
    val suppressReaderScrollSideEffects: Boolean
        get() = suppressScrollRefCount > 0
    var allowReaderScrollSideEffects: Boolean = false
        private set

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val scrollDragSlop = touchSlop * 3
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isVerticalScrollDrag = false
    /** 当前手势相对按下点的纵向位移（+ 手指下移=想看上文；- 手指上移=想看下文），用于扩窗方向门控。 */
    private var lastDragDy = 0f
    /** 本次触摸落在 Mermaid/图表 span 上，整段手势内禁止扩窗。 */
    private var gestureOnDiagram = false
    /** 每次手指按下只允许触发一次扩窗，避免连续扩到全书末尾。 */
    private var windowExpandConsumedThisGesture = false
    private var selectionActive = false
    private var savedSelStart = -1
    private var savedSelEnd = -1
    private var readerSelectionActionMode: ActionMode? = null
    /** DOWN 在选区外时先标记，UP 仍为轻微点击且仍在选区外才真正清除（避免句柄 DOWN 被误杀）。 */
    private var pendingOutsideTapDismiss = false
    /** 正在主动清理选区 UI，避免 ActionMode.onDestroy 递归再 dismiss。 */
    private var clearingSelectionUi = false
    /** 划词选区是否已递增 suppressScrollRefCount（配对递减）。 */
    private var selectionIncrementedSuppress = false
    /** DOWN 时命中链接，UP 时优先跳转而非进入 Editor 选词。 */
    private var pendingLinkSpan: ClickableSpan? = null

    private val linkMovement = ReaderLinkMovementMethod.getInstance()
    private val selectionMovement = ArrowKeyMovementMethod.getInstance()

    private val emptySelectionActionMode = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) =
            true.also {
                menu?.clear()
                readerSelectionActionMode = mode
            }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false

        override fun onDestroyActionMode(mode: ActionMode?) {
            if (readerSelectionActionMode == mode) readerSelectionActionMode = null
            // 系统关掉 ActionMode 时首尾句柄会一起消失；若 session 仍在，同步清掉选区阴影，
            // 避免出现「句柄没了但高亮还在」。
            if (!clearingSelectionUi && selectionActive) {
                post {
                    if (!clearingSelectionUi &&
                        selectionActive &&
                        readerSelectionActionMode == null
                    ) {
                        dismissSelection()
                    }
                }
            }
        }
    }

    init {
        // Legado MdRead：始终可选中，由系统 Editor 处理长按/句柄/ActionMode
        setTextIsSelectable(true)
        includeFontPadding = false
        movementMethod = linkMovement
        isVerticalScrollBarEnabled = false
        isLongClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        customSelectionActionModeCallback = emptySelectionActionMode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setTextClassifier(TextClassifier.NO_OP)
        }
    }

    fun shouldSuppressReaderScrollSideEffects(): Boolean =
        suppressReaderScrollSideEffects || selectionActive

    /** 用户手指正在纵向拖动滚动（用于区分布局重排触发的 scroll 变化）。 */
    fun isUserVerticalScrollDrag(): Boolean = isVerticalScrollDrag

    /** 手指下移（想看上文）→ 才允许向上扩窗；避免目录置顶后 scrollY=0 时向下滑也误触发向上扩窗。 */
    fun isDragTowardPrevious(): Boolean = lastDragDy > touchSlop

    /** 手指上移（想看下文）→ 才允许向下扩窗。 */
    fun isDragTowardNext(): Boolean = lastDragDy < -touchSlop

    /** 本次手势是否从 diagram 区域开始（扩窗应忽略）。 */
    fun isGestureOnDiagram(): Boolean = gestureOnDiagram

    fun diagramBitmapAt(x: Float, y: Float): android.graphics.Bitmap? {
        return DiagramImageLoader.cachedBitmapForDestination(diagramDestinationAt(x, y))
    }

    /**
     * 轻触链接时分发跳转（由 [bindReaderGesturesAndScroll] 在 OnTouchListener 中优先调用）。
     * 可选中 TextView 下 Editor 会吞掉 LinkMovementMethod，需显式触发 [ClickableSpan.onClick]。
     */
    fun dispatchLinkClickIfPresent(x: Float, y: Float): Boolean {
        if (selectionActive || gestureOnDiagram) return false
        if (!isTapGesture(x, y)) return false
        val span = ReaderTextLinkTouch.findClickableSpanAt(this, x, y) ?: return false
        pendingLinkSpan = null
        ReaderTextLinkTouch.dispatchClickableSpan(this, span)
        (text as? Spannable)?.let { Selection.removeSelection(it) }
        return true
    }

    private fun isTapGesture(x: Float, y: Float): Boolean {
        val dx = kotlin.math.abs(x - touchDownX)
        val dy = kotlin.math.abs(y - touchDownY)
        return dx <= touchSlop && dy <= touchSlop
    }

    /** 每次 DOWN 仅允许一次扩窗；返回 false 表示本手势已扩过窗。 */
    fun consumeWindowExpandThisGesture(): Boolean {
        if (windowExpandConsumedThisGesture) return false
        windowExpandConsumedThisGesture = true
        return true
    }

    /** diagram 等异步改行高时临时抑制扩窗/进度副作用。 */
    fun runSuppressingScrollSideEffects(block: () -> Unit) {
        suppressScrollRefCount++
        try {
            block()
        } finally {
            suppressScrollRefCount--
        }
    }

    /** 开始异步抑制（跨 requestLayout → post 周期），配对 [endAsyncScrollSuppression]。 */
    fun beginAsyncScrollSuppression() {
        suppressScrollRefCount++
    }

    /** 结束异步抑制。 */
    fun endAsyncScrollSuppression() {
        suppressScrollRefCount = (suppressScrollRefCount - 1).coerceAtLeast(0)
    }

    /** 划词会话进行中（含拖句柄），不要求 Spannable 此刻仍有有效 range。 */
    fun isInTextSelection(): Boolean = selectionActive

    fun hasVisibleTextSelection(): Boolean = selectionActive && hasSelectionRange()

    fun prepareForNewTouch(x: Float, y: Float) {
        isVerticalScrollDrag = false
        lastDragDy = 0f
        windowExpandConsumedThisGesture = false
        gestureOnDiagram = isTouchOnDiagramSpan(x, y)
        allowReaderScrollSideEffects = false
        if (selectionActive) {
            pendingOutsideTapDismiss = !isTouchNearSelection(x, y)
        } else {
            pendingOutsideTapDismiss = false
        }
    }

    fun markVerticalScrollDrag() {
        if (selectionActive) return
        allowReaderScrollSideEffects = true
        isVerticalScrollDrag = true
    }

    internal fun tryMarkVerticalScrollDrag(dx: Float, dy: Float): Boolean {
        if (!allowVerticalScroll || selectionActive) return false
        lastDragDy = dy
        if (kotlin.math.abs(dy) <= scrollDragSlop || kotlin.math.abs(dy) <= kotlin.math.abs(dx)) {
            return false
        }
        markVerticalScrollDrag()
        return true
    }

    fun dismissSelection() {
        if (!selectionActive) {
            resetSelectionUiOnly()
            return
        }
        setSelectionActive(false)
        resetSelectionUiOnly()
    }

    /** 单元测试：同步 Spannable 选区到阅读器划词状态。 */
    internal fun applySelectionRangeForTest(start: Int, end: Int) {
        ensureSelectionInteractionMode()
        (text as? Spannable)?.let { Selection.setSelection(it, start, end) }
        onSelectionChanged(start, end)
    }

    /**
     * 单元测试：模拟系统结束划词 ActionMode（句柄消失），验证选区阴影会同步清除。
     *
     * 生产路径在 [onDestroyActionMode] 里 `post { dismissSelection() }`；测试里改为同步执行，
     * 避免 Robolectric 推进 looper 时被划词句柄动画以 0 delay 反复入队而挂死。
     */
    internal fun simulateSystemActionModeDestroyForTest() {
        val mode = object : ActionMode() {
            override fun setTitle(title: CharSequence?) = Unit
            override fun setTitle(resId: Int) = Unit
            override fun setSubtitle(subtitle: CharSequence?) = Unit
            override fun setSubtitle(resId: Int) = Unit
            override fun setCustomView(view: android.view.View?) = Unit
            override fun invalidate() = Unit
            override fun finish() = Unit
            override fun getMenu(): Menu = throw UnsupportedOperationException()
            override fun getTitle(): CharSequence = ""
            override fun getSubtitle(): CharSequence = ""
            override fun getCustomView(): android.view.View? = null
            override fun getMenuInflater(): android.view.MenuInflater =
                throw UnsupportedOperationException()
        }
        readerSelectionActionMode = mode
        emptySelectionActionMode.onDestroyActionMode(mode)
        if (!clearingSelectionUi && selectionActive && readerSelectionActionMode == null) {
            dismissSelection()
        }
    }

    private fun setSelectionActive(active: Boolean) {
        if (selectionActive == active) return
        selectionActive = active
        post { onReaderTextSelectionActiveChange?.invoke(active) }
    }

    private fun resetSelectionUiOnly() {
        clearingSelectionUi = true
        try {
            savedSelStart = -1
            savedSelEnd = -1
            pendingOutsideTapDismiss = false
            if (selectionIncrementedSuppress) {
                suppressScrollRefCount = (suppressScrollRefCount - 1).coerceAtLeast(0)
                selectionIncrementedSuppress = false
            }
            allowReaderScrollSideEffects = false
            readerSelectionActionMode?.finish()
            readerSelectionActionMode = null
            movementMethod = linkMovement
            (text as? Spannable)?.let { Selection.removeSelection(it) }
            parent?.requestDisallowInterceptTouchEvent(false)
            invalidate()
        } finally {
            clearingSelectionUi = false
        }
    }

    private fun restoreSavedSelectionRange() {
        val spannable = text as? Spannable ?: return
        if (savedSelStart < 0 || savedSelEnd <= savedSelStart) return
        val start = savedSelStart.coerceIn(0, spannable.length)
        val end = savedSelEnd.coerceIn(0, spannable.length)
        if (start < end) {
            Selection.setSelection(spannable, start, end)
        }
    }

    private fun revertToLinkModeIfIdle() {
        if (selectionActive || hasSelectionRange()) return
        movementMethod = linkMovement
    }

    /** 仅拦截图片/图表上的长按，其余完全交给 Editor 原生划词流程（含句柄）。 */
    private fun allowLongPressSelectionAt(x: Float, y: Float): Boolean {
        if (isTouchOnDiagramSpan(x, y)) return false
        val offset = touchOffsetToCharOffset(x, y) ?: return false
        return canSelectAtOffset(offset)
    }

    override fun performLongClick(): Boolean {
        if (gestureOnDiagram || !allowLongPressSelectionAt(lastTouchX, lastTouchY)) return false
        return super.performLongClick()
    }

    override fun performLongClick(x: Float, y: Float): Boolean {
        lastTouchX = x
        lastTouchY = y
        if (gestureOnDiagram || !allowLongPressSelectionAt(x, y)) return false
        return super.performLongClick(x, y)
    }

    private fun hasSelectionRange(): Boolean {
        val spannable = text as? Spannable ?: return false
        val start = Selection.getSelectionStart(spannable)
        val end = Selection.getSelectionEnd(spannable)
        return start >= 0 && end >= 0 && start != end
    }

    private fun isTouchNearSelection(x: Float, y: Float): Boolean =
        ReaderTextSelectionTouch.isTouchNearSelectionOnTextView(this, x, y)

    private fun ensureSelectionInteractionMode() {
        movementMethod = selectionMovement
    }

    override fun canScrollVertically(direction: Int): Boolean =
        allowVerticalScroll && super.canScrollVertically(direction)

    /**
     * 视口顶字符行锚点：随每次滚动更新；文本异步重排（表格测量、图片/图表占位符→实际高度、
     * 字号变化）导致锚点行位移时，在 onLayout（绘制前）按同一字符行重新定位，视口不动。
     * 若只按像素保留 scrollY，上方内容高度变化会把视口顶到更早的内容（表现为「过一会跳回前面」）。
     */
    private var viewportAnchorTextLen = -1
    private var viewportAnchorChar = -1
    private var viewportAnchorInLineOffset = 0

    private fun updateViewportCharAnchor() {
        if (!allowVerticalScroll) {
            viewportAnchorTextLen = -1
            return
        }
        val layout = layout ?: return
        val len = text?.length ?: 0
        if (len == 0 || layout.text.length != len || layout.lineCount <= 0) {
            viewportAnchorTextLen = -1
            return
        }
        val y = (scrollY + paddingTop).coerceAtLeast(0)
        val line = layout.getLineForVertical(y).coerceIn(0, layout.lineCount - 1)
        viewportAnchorChar = layout.getLineStart(line).coerceIn(0, len - 1)
        viewportAnchorInLineOffset = scrollY - layout.getLineTop(line)
        viewportAnchorTextLen = len
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        updateViewportCharAnchor()
    }

    /** 布局重排后按快照锚点维持视口顶字符行；返回 true 表示做了补偿滚动。 */
    private fun maintainViewportCharAnchorAfterLayout(
        snapTextLen: Int,
        snapChar: Int,
        snapInLineOffset: Int,
    ): Boolean {
        if (!allowVerticalScroll) return false
        // 划词期间 Editor 可能自行 bringPointIntoView，不与其争抢滚动。
        if (selectionActive) return false
        val layout = layout ?: return false
        val len = text?.length ?: 0
        if (len == 0 || layout.text.length != len) return false
        if (snapTextLen != len || snapChar !in 0 until len) {
            // 新文本首个布局：只建立锚点，不滚动。
            updateViewportCharAnchor()
            return false
        }
        val innerH = height - paddingTop - paddingBottom
        if (innerH <= 0) return false
        val line = layout.getLineForOffset(snapChar)
            .coerceIn(0, (layout.lineCount - 1).coerceAtLeast(0))
        val newLineTop = layout.getLineTop(line)
        val maxScroll = (layout.height - innerH).coerceAtLeast(0)
        val target = (newLineTop + snapInLineOffset).coerceIn(0, maxScroll)
        if (target != scrollY) {
            // scrollTo 触发 onScrollChanged，锚点随新布局刷新。
            super.scrollTo(scrollX, target)
            return true
        }
        updateViewportCharAnchor()
        return false
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // 目录跳转置顶优先：目标章节即首行，首帧就压在顶部，避免闪一下再跳。
        val scrollToTop = hasReaderScrollToTop(this)
        val hasSnapStash = !scrollToTop && hasPendingSavedPositionSnap(this)
        val hasExpandStash = !scrollToTop && !hasSnapStash && hasPendingSourceScrollRestore(this)
        // 快照必须在 super.onLayout 之前抓：布局过程中的内部 scrollTo 会经 onScrollChanged 污染锚点。
        val snapTextLen = viewportAnchorTextLen
        val snapChar = viewportAnchorChar
        val snapInLineOffset = viewportAnchorInLineOffset
        super.onLayout(changed, left, top, right, bottom)
        if (scrollToTop) {
            // 不清意图：等 finishMarkdownRender 匹配新内容后再清，确保新窗口首帧也在顶部。
            if (scrollY != 0) super.scrollTo(0, 0)
            return
        }
        if (hasSnapStash) {
            applyStashedSavedPositionSnapIfAny(this)
            return
        }
        if (hasExpandStash) {
            applyStashedSourceScrollRestoreIfAny(this, renderPlainText = renderPlainTextBody)
            return
        }
        maintainViewportCharAnchorAfterLayout(snapTextLen, snapChar, snapInLineOffset)
    }

    override fun scrollTo(x: Int, y: Int) {
        if (allowVerticalScroll) super.scrollTo(x, y) else super.scrollTo(x, 0)
    }

    private fun shouldDismissSelectionOnOutsideTap(): Boolean {
        if (!pendingOutsideTapDismiss || !selectionActive) return false
        val dx = kotlin.math.abs(lastTouchX - touchDownX)
        val dy = kotlin.math.abs(lastTouchY - touchDownY)
        if (dx > touchSlop || dy > touchSlop) return false
        return !isTouchNearSelection(lastTouchX, lastTouchY)
    }

    private fun dismissSelectionOnOutsideTapIfNeeded() {
        if (shouldDismissSelectionOnOutsideTap()) {
            dismissSelection()
        }
    }

    private fun touchOffsetToCharOffset(x: Float, y: Float): Int? {
        val len = text?.length ?: 0
        if (len == 0 || layout == null) return null
        return getOffsetForPosition(x, y).coerceIn(0, len)
    }

    private fun canSelectAtOffset(offset: Int): Boolean {
        val spannable = text as? Spanned ?: return true
        if (spannable.isEmpty()) return false
        val check = offset.coerceIn(0, spannable.length - 1)
        return spannable.getSpans(check, check + 1, Any::class.java).none {
            it is AsyncDrawableSpan || it is ImageSpan
        }
    }

    private fun isTouchOnDiagramSpan(x: Float, y: Float): Boolean {
        return diagramDestinationAt(x, y) != null
    }

    private fun diagramDestinationAt(x: Float, y: Float): String? {
        val layout = layout ?: return null
        val spanned = text as? Spanned ?: return null
        if (spanned.isEmpty()) return null
        val contentX = x - totalPaddingLeft
        if (contentX < 0f || contentX > (width - totalPaddingLeft - totalPaddingRight)) return null
        val contentY = (y + scrollY - totalPaddingTop).toInt().coerceAtLeast(0)
        val line = layout.getLineForVertical(contentY).coerceIn(0, layout.lineCount - 1)
        val lineStart = layout.getLineStart(line).coerceIn(0, spanned.length)
        val lineEnd = layout.getLineEnd(line).coerceIn(lineStart, spanned.length)
        val spans = spanned.getSpans(lineStart, lineEnd, AsyncDrawableSpan::class.java)
        for (span in spans) {
            val spanStart = spanned.getSpanStart(span)
            val spanEnd = spanned.getSpanEnd(span)
            if (spanStart < lineEnd && spanEnd > lineStart) {
                val destination = span.drawable.destination
                if (destination.startsWith("diagram://")) return destination
            }
        }
        return null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                isVerticalScrollDrag = false
                windowExpandConsumedThisGesture = false
                gestureOnDiagram = isTouchOnDiagramSpan(event.x, event.y)
                pendingLinkSpan = if (!selectionActive && !gestureOnDiagram) {
                    ReaderTextLinkTouch.findClickableSpanAt(this, event.x, event.y)
                } else {
                    null
                }
                if (pendingLinkSpan != null) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                if (selectionActive) {
                    pendingOutsideTapDismiss = !isTouchNearSelection(event.x, event.y)
                } else {
                    pendingOutsideTapDismiss = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                lastTouchX = event.x
                lastTouchY = event.y
                if (selectionActive && isTouchNearSelection(event.x, event.y)) {
                    pendingOutsideTapDismiss = false
                }
                tryMarkVerticalScrollDrag(event.x - touchDownX, event.y - touchDownY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastTouchX = event.x
                lastTouchY = event.y
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val link = pendingLinkSpan
                    pendingLinkSpan = null
                    if (link != null && isTapGesture(event.x, event.y)) {
                        ReaderTextLinkTouch.dispatchClickableSpan(this, link)
                        (text as? Spannable)?.let { Selection.removeSelection(it) }
                        return true
                    }
                } else {
                    pendingLinkSpan = null
                }
            }
        }
        val handled = try {
            super.onTouchEvent(event)
        } catch (_: NullPointerException) {
            false
        } catch (_: IndexOutOfBoundsException) {
            false
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            // dismiss 会清掉 pending 标记，先记下本次是否区外轻点。
            val outsideTap = pendingOutsideTapDismiss
            dismissSelectionOnOutsideTapIfNeeded()
            isVerticalScrollDrag = false
            gestureOnDiagram = false
            if (!selectionActive) {
                allowReaderScrollSideEffects = false
            }
            if (selectionActive && !hasSelectionRange()) {
                // 区外手势期间系统已清掉 range/句柄时，勿再 restore，否则只剩选区阴影。
                // 选区内/拖句柄时仍恢复，避免 Editor 瞬时丢 range 导致选区闪断。
                if (outsideTap) {
                    dismissSelection()
                } else {
                    restoreSavedSelectionRange()
                }
            }
            if (selectionActive) {
                ensureSelectionInteractionMode()
                parent?.requestDisallowInterceptTouchEvent(true)
            } else {
                parent?.requestDisallowInterceptTouchEvent(false)
                revertToLinkModeIfIdle()
            }
        }
        return handled
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (selStart >= 0 && selEnd >= 0 && selStart != selEnd) {
            savedSelStart = minOf(selStart, selEnd)
            savedSelEnd = maxOf(selStart, selEnd)
            ensureSelectionInteractionMode()
            if (!selectionActive) {
                setSelectionActive(true)
                suppressScrollRefCount++
                selectionIncrementedSuppress = true
                parent?.requestDisallowInterceptTouchEvent(true)
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    private companion object
}

/** @see ReaderMarkwonFactory */
internal fun createMarkwon(context: Context): Markwon = ReaderMarkwonFactory.create(context)

/** PDF 阅读：与默认配置相同，渲染后由 [PdfImageLayoutHelper] 将页图拉满内容区宽度。 */
internal fun createPdfMarkwon(context: Context): Markwon = ReaderMarkwonFactory.create(context)

/**
 * 在 Markwon 渲染后的纯文本上按划线内容做背景高亮（源码下标与渲染后 Spanned 长度不一致，故用文本匹配）。
 */
internal fun applyHighlightsToRenderedText(
    textView: TextView,
    highlights: List<space.liushenme.markdownreader.data.local.entity.HighlightEntity>,
    highlightColorArgb: Int
) {
    val text = textView.text
    if (text !is Spannable || highlights.isEmpty()) return
    val full = text.toString()
    highlights.forEach { highlight ->
        val snippet = highlight.highlightedText
        if (snippet.isEmpty()) return@forEach
        var searchFrom = 0
        while (searchFrom < full.length) {
            val idx = full.indexOf(snippet, searchFrom)
            if (idx < 0) break
            val end = idx + snippet.length
            text.setSpan(
                BackgroundColorSpan(highlightColorArgb),
                idx,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            searchFrom = end
        }
    }
}
