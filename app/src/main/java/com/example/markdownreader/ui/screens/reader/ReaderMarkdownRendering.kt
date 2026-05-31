package com.example.markdownreader.ui.screens.reader

import android.app.Activity
import android.content.Context
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
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
import com.example.markdownreader.data.local.entity.HighlightEntity
import com.example.markdownreader.importing.ImportedBookFormat
import com.example.markdownreader.model.ReaderPageTurnMode
import com.example.markdownreader.ui.components.iconTintForDeleteStrip
import com.example.markdownreader.ui.theme.MarkdownReaderTheme
import com.example.markdownreader.ui.theme.ReadingTheme
import com.example.markdownreader.R
import com.example.markdownreader.markdown.DiagramImageLoader
import com.example.markdownreader.markdown.NetworkImageCache
import com.example.markdownreader.markdown.ReaderMarkwonFactory
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

/** Markwon 渲染的"小内容"门槛：< 该长度直接主线程同步，避免线程切换开销让首屏更慢。 */
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
        textView.setText(sp, TextView.BufferType.SPANNABLE)
        applyHighlightsToRenderedText(textView, highlights, highlightColorArgb)
        return
    }
    val params = TextViewCompat.getTextMetricsParams(textView)
    readerPlainTextPrecomputeExecutor.execute {
        val pre = PrecomputedTextCompat.create(content, params)
        textView.post {
            if (textView.getTag(TAG_READER_RENDER_SIG) != renderSig) return@post
            TextViewCompat.setPrecomputedText(textView, pre)
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
        val anchorIndex = textView.getTag(R.id.markdown_anchor_index) as? com.example.markdownreader.markdown.MarkdownAnchorIndex
        if (anchorIndex != null) {
            textView.post { com.example.markdownreader.markdown.RenderedAnchorBinder.bind(textView, anchorIndex) }
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
    val hasDiagram = markdown.contains("diagram://")
    if (markdown.length <= MARKWON_BACKGROUND_RENDER_THRESHOLD && !hasDiagram) {
        // 短文本同步走完，UI 首帧响应更直接
        markwon.setMarkdown(textView, markdown)
        finishMarkdownRender()
        return
    }
    // 长 Markdown：在后台线程做 CommonMark parse + Markwon render（产 Spanned），
    // 这一步通常 200~500ms（取决于内容长度与图片数）。主线程只剩 setText + measure。
    readerMarkwonRenderExecutor.execute {
        val rendered: CharSequence = runCatching { markwon.toMarkdown(markdown) }
            .getOrNull() ?: content
        textView.post {
            if (textView.getTag(TAG_READER_RENDER_SIG) != renderSig) return@post
            textView.setTag(R.id.markdown_anchor_index, prepared.anchorIndex)
            // setParsedMarkdown 会在主线程上把 Spanned 应用到 TextView，并执行
            // Markwon 各插件的 `afterSetText`（如启动 AsyncDrawable 图片加载）。
            markwon.setParsedMarkdown(textView, rendered as? android.text.Spanned ?: android.text.SpannableString(rendered))
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
    highlights: List<com.example.markdownreader.data.local.entity.HighlightEntity>,
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
                setLineSpacing(0f, readerLineSpacingMultiplier)

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
            textView.setLineSpacing(0f, readerLineSpacingMultiplier)
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
                    tv.tryMarkVerticalScrollDrag(dx, dy)
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
                val blockBookmarkSwipe = touchState.blockBookmarkSwipeGesture ||
                    tv?.hasActiveReaderTextSelection() == true
                touchState.blockBookmarkSwipeGesture = false
                if (e.actionMasked != MotionEvent.ACTION_UP) return@setOnTouchListener false
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
    /** 划词选区是否已递增 suppressScrollRefCount（配对递减）。 */
    private var selectionIncrementedSuppress = false

    private val linkMovement = LinkMovementMethod.getInstance()
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
        }
    }

    init {
        // Legado MdRead：始终可选中，由系统 Editor 处理长按/句柄/ActionMode
        setTextIsSelectable(true)
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

    /** 本次手势是否从 diagram 区域开始（扩窗应忽略）。 */
    fun isGestureOnDiagram(): Boolean = gestureOnDiagram

    fun diagramBitmapAt(x: Float, y: Float): android.graphics.Bitmap? {
        return DiagramImageLoader.cachedBitmapForDestination(diagramDestinationAt(x, y))
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

    private fun setSelectionActive(active: Boolean) {
        if (selectionActive == active) return
        selectionActive = active
        post { onReaderTextSelectionActiveChange?.invoke(active) }
    }

    private fun resetSelectionUiOnly() {
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

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val preserveScrollY = if (changed && allowVerticalScroll && scrollY > 0) scrollY else null
        super.onLayout(changed, left, top, right, bottom)
        if (preserveScrollY == null) return
        val layout = layout ?: return
        val innerH = height - paddingTop - paddingBottom
        if (innerH <= 0) return
        val maxScroll = (layout.height - innerH).coerceAtLeast(0)
        val target = preserveScrollY.coerceIn(0, maxScroll)
        if (scrollY != target) {
            super.scrollTo(0, target)
        }
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
            dismissSelectionOnOutsideTapIfNeeded()
            isVerticalScrollDrag = false
            gestureOnDiagram = false
            if (!selectionActive) {
                allowReaderScrollSideEffects = false
            }
            if (selectionActive && !hasSelectionRange()) {
                restoreSavedSelectionRange()
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
    highlights: List<com.example.markdownreader.data.local.entity.HighlightEntity>,
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
