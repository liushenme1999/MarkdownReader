package space.liushenme.markdownreader.ui.screens.reader

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.BackgroundColorSpan
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.view.ActionMode
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
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
import space.liushenme.markdownreader.markdown.MarkdownAnchorIndex
import space.liushenme.markdownreader.markdown.NetworkImageCache
import space.liushenme.markdownreader.markdown.PreparedMarkdown
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

internal fun readerContentSignature(
    content: String,
    renderPlainText: Boolean,
    themeName: String,
    fontSize: Int,
): String = "${renderPlainText}_${content.length}_${content.hashCode()}_${themeName}_$fontSize"

internal fun readerHighlightSignature(
    highlights: List<HighlightEntity>,
): String = highlights.joinToString("|") {
    "${it.id}_${it.startPosition}_${it.endPosition}_${it.color}_${it.style}"
}

/** 兼容旧调用：内容签名 + 划线签名。 */
internal fun readerRenderSignature(
    content: String,
    renderPlainText: Boolean,
    themeName: String,
    fontSize: Int,
    highlights: List<HighlightEntity>,
): String = "${readerContentSignature(content, renderPlainText, themeName, fontSize)}|${readerHighlightSignature(highlights)}"

/** 把最新划线状态挂到 TextView，供异步 Markdown/纯文本渲染完成时读取（避免闭包捕获空列表）。 */
internal fun syncReaderPendingHighlights(
    textView: TextView,
    highlights: List<HighlightEntity>,
    highlightColorArgb: Int,
    sourceContentLength: Int,
) {
    val safe = textView as? SafeReaderTextView ?: return
    safe.pendingHighlights = highlights
    safe.pendingHighlightColorArgb = highlightColorArgb
    safe.pendingHighlightSourceLength = sourceContentLength
}

/** 用 TextView 上挂着的最新划线刷新 span。 */
internal fun refreshReaderHighlightSpansFromPending(
    textView: TextView,
    fallbackSourceLength: Int = -1,
) {
    val safe = textView as? SafeReaderTextView
    val highlights = safe?.pendingHighlights ?: emptyList()
    val color = safe?.pendingHighlightColorArgb ?: 0
    val sourceLen = when {
        safe != null && safe.pendingHighlightSourceLength >= 0 -> safe.pendingHighlightSourceLength
        fallbackSourceLength >= 0 -> fallbackSourceLength
        else -> textView.text?.length ?: 0
    }
    refreshReaderHighlightSpans(
        textView = textView,
        highlights = highlights,
        highlightColorArgb = color,
        sourceContentLength = sourceLen,
        highlightSig = readerHighlightSignature(highlights),
    )
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
    syncReaderPendingHighlights(
        textView = textView,
        highlights = highlights,
        highlightColorArgb = highlightColorArgb,
        sourceContentLength = content.length,
    )
    val prevSig = textView.getTag(TAG_READER_RENDER_SIG) as? String
    val prevComplete = textView.getTag(R.id.reader_markdown_render_complete) as? String
    // 同签名已在渲染中或已完成：跳过，避免 AndroidView 误重建 / 周期重组触发全量 Markwon。
    if (prevSig == renderSig) {
        readerOpenDbg(
            "markwonQueue skip sameSig complete=${prevComplete == renderSig} " +
                "contentLen=${content.length}",
        )
        return
    }
    textView.setTag(TAG_READER_RENDER_SIG, renderSig)
    // 正文异步渲染期间可能先刷过划线签名；清掉以免完成后被「已同步」挡住二次刷新。
    textView.setTag(TAG_READER_HIGHLIGHT_SIG, null)
    if (!renderPlainText) {
        applyMarkdownContent(
            textView = textView,
            content = content,
            renderSig = renderSig,
            markwon = markwon,
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
        refreshReaderHighlightSpansFromPending(textView, fallbackSourceLength = content.length)
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
            refreshReaderHighlightSpansFromPending(textView, fallbackSourceLength = content.length)
        }
    }
}

internal fun applyMarkdownContent(
    textView: TextView,
    content: String,
    renderSig: String,
    markwon: Markwon,
    pdfFullWidthImages: Boolean = false,
    pdfCenterImageVertically: Boolean = false,
) {
    fun finishMarkdownRender() {
        textView.setTag(R.id.reader_markdown_render_complete, renderSig)
        readerOpenDbg(
            "finishMarkdown completeTagTail=${renderSig.takeLast(48)} " +
                "textLen=${textView.text?.length} layout=${textView.layout != null}",
        )
        // 目录跳转置顶意图优先：直接 scrollY=0 并清意图，不再走扩窗 stash 恢复。
        // 打开书/书签 snap 次之：同帧滚到目标，避免 scrollY=0 先画一帧「别的章节」再跳回。
        // 扩窗滚动恢复交给 onLayout（首帧 draw 前）。此处若 layout 未就绪不要 post，
        // 否则会在 scrollY=0 先画一帧「开头」再跳回。
        val positioned = when {
            applyReaderScrollToTopIfAny(textView, clearAfter = true) -> {
                (textView as? SafeReaderTextView)?.signalOpenPositionReady("finishScrollToTop")
                true
            }
            applyStashedSavedPositionSnapIfAny(textView) -> {
                (textView as? SafeReaderTextView)?.signalOpenPositionReady("finishSnap")
                true
            }
            else -> {
                applyStashedSourceScrollRestoreIfAny(textView, renderPlainText = false)
                false
            }
        }
        if (!positioned && !isReaderTextViewLayoutReady(textView)) {
            // 促发首帧 layout；onLayout 里会再 snap 并 signalOpenPositionReady。
            textView.requestLayout()
        }
        val anchorIndex = textView.getTag(R.id.markdown_anchor_index) as? space.liushenme.markdownreader.markdown.MarkdownAnchorIndex
        if (anchorIndex != null) {
            textView.post { space.liushenme.markdownreader.markdown.RenderedAnchorBinder.bind(textView, anchorIndex) }
        }
        // 必须读 pending：闭包里的 highlights 可能是启动渲染时的空列表，二次进页时
        // Room 热缓存会在 Markdown 完成前把划线刷上来，若此处用旧空列表会清掉并写死空签名。
        refreshReaderHighlightSpansFromPending(textView, fallbackSourceLength = content.length)
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
    (textView as? SafeReaderTextView)?.clearOpenPositionReadySignal()
    // prepareMarkdown / rewriteCachedUrls / toMarkdown 全部进后台，避免进页主线程卡顿。
    val appContext = textView.context.applicationContext
    val renderT0 = android.os.SystemClock.uptimeMillis()
    readerOpenDbg("markwonQueue contentLen=${content.length} sigTail=${renderSig.takeLast(48)}")
    readerMarkwonRenderExecutor.execute {
        val tPrep0 = android.os.SystemClock.uptimeMillis()
        val prepared = runCatching { ReaderMarkwonFactory.prepareMarkdown(content, appContext) }
            .getOrElse { PreparedMarkdown(text = content, anchorIndex = MarkdownAnchorIndex()) }
        val markdown = NetworkImageCache.rewriteCachedUrls(appContext, prepared.text)
        val tPrep1 = android.os.SystemClock.uptimeMillis()
        val rendered: CharSequence = synchronized(markwonRenderLock) {
            runCatching { markwon.toMarkdown(markdown) }.getOrNull() ?: content
        }
        val tMd1 = android.os.SystemClock.uptimeMillis()
        readerOpenDbg(
            "markwonBg prepare=${tPrep1 - tPrep0}ms toMarkdown=${tMd1 - tPrep1}ms " +
                "totalBg=${tMd1 - renderT0}ms outLen=${rendered.length}",
        )
        textView.post {
            if (textView.getTag(TAG_READER_RENDER_SIG) != renderSig) {
                readerOpenDbg("markwonPost skip staleSig")
                return@post
            }
            textView.setTag(R.id.markdown_anchor_index, prepared.anchorIndex)
            // 新内容替换前重抓扩窗锚点：渲染在途时用户可能已滑动旧内容，旧锚点会把视口拉回去。
            refreshStashedExpandAnchorBeforeContentSwap(textView)
            val tSet0 = android.os.SystemClock.uptimeMillis()
            synchronized(markwonRenderLock) {
                markwon.setParsedMarkdown(
                    textView,
                    rendered as? android.text.Spanned ?: android.text.SpannableString(rendered),
                )
            }
            readerOpenDbg("setParsedMarkdown +${android.os.SystemClock.uptimeMillis() - tSet0}ms")
            finishMarkdownRender()
            readerOpenDbg("markwonDone wall=${android.os.SystemClock.uptimeMillis() - renderT0}ms")
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
    onHighlightMenuClick: (
        text: String,
        displayedStart: Int,
        displayedEnd: Int,
        selectionBoundsInWindow: android.graphics.Rect,
    ) -> Unit = { _, _, _, _ -> },
    resolveExistingHighlightId: (text: String, displayedStart: Int, displayedEnd: Int) -> Long? =
        { _, _, _ -> null },
    onRemoveHighlightClick: (highlightId: Long) -> Unit = {},
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
    onOpenPositionReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val pdfPagedLayout = pdfFullWidthImages && !allowVerticalScroll
    val markwon = remember(pdfFullWidthImages) {
        ReaderMarkwonFactory.create(context)
    }
    val touchState = remember { ReaderTouchState() }
    val slop = ViewConfiguration.get(context).scaledTouchSlop
    val latestOpenPositionReady by rememberUpdatedState(onOpenPositionReady)

    AndroidView(
        factory = { ctx ->
            readerOpenDbg("androidView factory contentLen=${content.length}")
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
                this.onOpenPositionReady = { latestOpenPositionReady() }

                val sig0 = readerContentSignature(
                    content = content,
                    renderPlainText = renderPlainText,
                    themeName = theme::class.java.name,
                    fontSize = fontSize,
                )
                syncReaderPendingHighlights(
                    textView = this,
                    highlights = highlights,
                    highlightColorArgb = theme.highlightColor.toArgb(),
                    sourceContentLength = content.length,
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
                this.onHighlightMenuClick = onHighlightMenuClick
                this.resolveExistingHighlightId = resolveExistingHighlightId
                this.onRemoveHighlightClick = onRemoveHighlightClick
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
            textView.renderPlainTextBody = renderPlainText
            textView.onOpenPositionReady = { latestOpenPositionReady() }
            textView.onReaderTextSelectionActiveChange = onReaderTextSelectionActiveChange
            textView.onHighlightMenuClick = onHighlightMenuClick
            textView.resolveExistingHighlightId = resolveExistingHighlightId
            textView.onRemoveHighlightClick = onRemoveHighlightClick
            textView.setTextColor(theme.textColor.toArgb())
            textView.setBackgroundColor(theme.backgroundColor.toArgb())
            textView.textSize = fontSize.toFloat()
            val density = textView.resources.displayMetrics.density
            val padHPx = (readerPaddingHorizontalDp * density).toInt().coerceAtLeast(0)
            val padBottomPx = (readerPaddingDp * density).toInt().coerceAtLeast(0)
            val padTopPx = (readerPaddingTopDp * density).toInt().coerceAtLeast(0)
            val contentSig = readerContentSignature(
                content = content,
                renderPlainText = renderPlainText,
                themeName = theme::class.java.name,
                fontSize = fontSize,
            )
            val highlightSig = readerHighlightSignature(highlights)
            val highlightColorArgb = theme.highlightColor.toArgb()
            // 无论正文是否变化，都同步最新划线，供异步渲染完成时读取。
            syncReaderPendingHighlights(
                textView = textView,
                highlights = highlights,
                highlightColorArgb = highlightColorArgb,
                sourceContentLength = content.length,
            )
            val prevContentSig = textView.getTag(TAG_READER_RENDER_SIG) as? String
            val prevHighlightSig = textView.getTag(TAG_READER_HIGHLIGHT_SIG) as? String
            // TAG_READER_RENDER_SIG 仅存内容签名，改划线颜色/样式时只刷新 span
            val contentChanged = prevContentSig != contentSig
            val highlightsChanged = prevHighlightSig != highlightSig
            if (contentChanged) {
                readerOpenDbg(
                    "contentChanged prevNull=${prevContentSig == null} " +
                        "len=${content.length} hash=${content.hashCode()} " +
                        "prevTail=${prevContentSig?.takeLast(40)}",
                )
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
                    renderSig = contentSig,
                    markwon = markwon,
                    highlights = highlights,
                    highlightColorArgb = highlightColorArgb,
                    pdfFullWidthImages = pdfFullWidthImages,
                    pdfCenterImageVertically = pdfPagedLayout,
                )
            } else if (highlightsChanged) {
                val renderComplete = textView.getTag(R.id.reader_markdown_render_complete) as? String
                val markdownStillRendering =
                    !renderPlainText && renderComplete != contentSig
                if (markdownStillRendering) {
                    // 正文尚未落地：只更新 pending，等 finishMarkdownRender 再刷，
                    // 避免在空/旧文本上写死 HIGHLIGHT_SIG 后被空闭包覆盖且不再重组。
                    textView.setTag(TAG_READER_HIGHLIGHT_SIG, null)
                } else {
                    refreshReaderHighlightSpans(
                        textView = textView,
                        highlights = highlights,
                        highlightColorArgb = highlightColorArgb,
                        sourceContentLength = content.length,
                        highlightSig = highlightSig,
                    )
                }
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
        .ifEmpty { tv.context.getString(R.string.bookmark_default_preview) }
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
    /** 选区菜单点「划线」时回调选中文本、展示层起止与窗口坐标选区矩形（保持选区）。 */
    var onHighlightMenuClick: (
        (
            text: String,
            displayedStart: Int,
            displayedEnd: Int,
            selectionBoundsInWindow: android.graphics.Rect,
        ) -> Unit
    )? = null
    /** 当前选区若已有划线则返回其 id，用于菜单显示「取消划线」。 */
    var resolveExistingHighlightId: ((text: String, displayedStart: Int, displayedEnd: Int) -> Long?)? =
        null
    /** 选区菜单点「取消划线」。 */
    var onRemoveHighlightClick: ((highlightId: Long) -> Unit)? = null
    /**
     * 本次选区刚创建的划线 id。Markdown 展示坐标与源码坐标不一致时，
     * [resolveExistingHighlightId] 可能暂时匹配不到，靠此把菜单切成「取消划线」。
     */
    var selectionBoundHighlightId: Long? = null
        private set
    private var selectionBoundStart: Int = -1
    private var selectionBoundEnd: Int = -1
    /** 已点「划线」、落库尚未完成：菜单先显示「取消划线」。 */
    private var selectionHighlightPending: Boolean = false
    /** 落库完成前用户已点「取消划线」：收到 id 后立刻删掉。 */
    private var selectionHighlightAddCancelled: Boolean = false
    /**
     * 本地刚点「取消划线」：删除尚未从 Flow 反映时 [resolveExistingHighlightId] 仍可能命中，
     * 此期间菜单强制显示「划线」，直到选区变化或再次点划线。
     */
    private var suppressCancelTitleAfterLocalRemove: Boolean = false
    /**
     * 划线样式浮窗是否正在显示。为 true 时 ActionMode 被系统销毁不连带清选区，
     * 并尝试重新拉起浮动菜单（避免可聚焦窗口/ invalidate 导致「复制/划线」消失）。
     */
    var highlightStylePickerShowing: Boolean = false
    /**
     * Compose update 写入的最新划线；异步 Markdown/PrecomputedText 完成时从此读取，
     * 避免启动渲染时闭包捕获的空列表在完成后把 span 清掉。
     */
    var pendingHighlights: List<HighlightEntity> = emptyList()
    var pendingHighlightColorArgb: Int = 0
    var pendingHighlightSourceLength: Int = -1
    /**
     * 打开书/书签定位已落到正确 scroll 后回调（主线程）。
     * 用于尽早揭开进页遮罩，不必再等 Compose await 轮询。
     */
    var onOpenPositionReady: (() -> Unit)? = null
    /** 同一轮 Markdown 渲染只通知一次揭罩，避免 onLayout 重复 bump restore。 */
    private var openPositionSignaledForRender: Any? = null

    fun clearOpenPositionReadySignal() {
        openPositionSignaledForRender = null
    }

    fun signalOpenPositionReady(reason: String) {
        val complete = getTag(R.id.reader_markdown_render_complete) ?: return
        if (openPositionSignaledForRender == complete) return
        openPositionSignaledForRender = complete
        readerOpenDbg("openPositionReady reason=$reason scrollY=$scrollY")
        onOpenPositionReady?.invoke()
    }

    override fun onDraw(canvas: Canvas) {
        // 纯色底画在文字下；下划线画在文字上。不用 LineBackgroundSpan，避免 ParagraphStyle 卡死布局。
        drawReaderHighlightDecorations(this, canvas, underText = true)
        super.onDraw(canvas)
        drawReaderHighlightDecorations(this, canvas, underText = false)
    }

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
    /**
     * 正在为刷新「划线/取消划线」文案而重建 Floating ActionMode。
     * MIUI FloatingToolbar 按 itemId 缓存按钮文字，invalidate/setTitle 都不会改文案，必须 finish 再拉起。
     */
    private var recreatingSelectionActionMode = false
    /** 已应用到浮动菜单的文案状态；同状态不再 finish/start，避免连闪两次。 */
    private var appliedMenuShowsCancel: Boolean? = null
    /** [refreshHighlightMenuTitle] 已 post，合并多次调用为一次重建。 */
    private var menuRefreshQueued = false
    /** 划词选区是否已递增 suppressScrollRefCount（配对递减）。 */
    private var selectionIncrementedSuppress = false
    /** DOWN 时命中链接，UP 时优先跳转而非进入 Editor 选词。 */
    private var pendingLinkSpan: ClickableSpan? = null

    private val linkMovement = ReaderLinkMovementMethod.getInstance()
    private val selectionMovement = ArrowKeyMovementMethod.getInstance()

    /**
     * 必须用 [ActionMode.Callback2]：手动 [startActionMode] 重建菜单时，
     * 若无 [ActionMode.Callback2.onGetContentRect]，浮动条会落到屏幕顶部。
     */
    private val selectionActionModeCallback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            readerSelectionActionMode = mode
            populateSelectionMenu(menu)
            // MIUI 常在 Menu 之外再注入「搜索」芯片，延迟隐藏
            scheduleHideSelectionSearchAction()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            populateSelectionMenu(menu)
            scheduleHideSelectionSearchAction()
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item?.itemId) {
                MENU_ID_COPY -> copySelectionToClipboard()
                MENU_ID_HIGHLIGHT, MENU_ID_CANCEL_HIGHLIGHT -> handleHighlightMenuClick(mode)
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            if (readerSelectionActionMode == mode) readerSelectionActionMode = null
            // 主动重建菜单：保留选区，由 recreate 的 post 重新拉起 ActionMode。
            if (recreatingSelectionActionMode) return
            // 样式浮窗期间系统可能因焦点/ invalidate 拆掉 ActionMode：保留选区并尝试恢复菜单。
            if (highlightStylePickerShowing && selectionActive && !clearingSelectionUi) {
                post {
                    if (highlightStylePickerShowing &&
                        selectionActive &&
                        !clearingSelectionUi &&
                        readerSelectionActionMode == null &&
                        hasSelectionRange()
                    ) {
                        restoreSelectionActionMode()
                    }
                }
                return
            }
            // 系统关掉 ActionMode 时首尾句柄会一起消失；若 session 仍在，同步清掉选区阴影，
            // 避免出现「句柄没了但高亮还在」。
            if (!clearingSelectionUi && selectionActive) {
                post {
                    if (!clearingSelectionUi &&
                        selectionActive &&
                        readerSelectionActionMode == null &&
                        !highlightStylePickerShowing &&
                        !recreatingSelectionActionMode
                    ) {
                        dismissSelection()
                    }
                }
            }
        }

        override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: Rect) {
            val range = currentSelectionRange()
            val local = if (range != null) {
                selectionContentRectInView(range.first, range.last + 1)
            } else {
                null
            }
            if (local != null) {
                outRect.set(local)
            } else {
                // 避免空矩形被框架当成 (0,0) 贴顶
                outRect.set(
                    paddingLeft,
                    paddingTop,
                    (width - paddingRight).coerceAtLeast(paddingLeft + 1),
                    paddingTop + 1,
                )
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
        customSelectionActionModeCallback = selectionActionModeCallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setTextClassifier(TextClassifier.NO_OP)
        }
    }

    private fun populateSelectionMenu(menu: Menu?) {
        menu ?: return
        menu.clear()
        menu.add(Menu.NONE, MENU_ID_COPY, 0, context.getString(R.string.selection_menu_copy))
        // 划线 / 取消划线必须用不同 itemId：MIUI FloatingToolbar 按 id 缓存芯片文案，
        // 同 id 只改 title 时界面仍显示「划线」。
        val showCancel = shouldShowCancelHighlightTitle()
        if (showCancel) {
            menu.add(
                Menu.NONE,
                MENU_ID_CANCEL_HIGHLIGHT,
                1,
                context.getString(R.string.selection_menu_cancel_highlight),
            )
        } else {
            menu.add(
                Menu.NONE,
                MENU_ID_HIGHLIGHT,
                1,
                context.getString(R.string.selection_menu_highlight),
            )
        }
        appliedMenuShowsCancel = showCancel
        // 若系统/ROM 在 clear 之后又塞回「搜索」，立刻剔除
        stripSearchMenuItems(menu)
    }

    /** 去掉划词菜单里的「搜索」（含 WEB_SEARCH / 标题匹配）。 */
    private fun stripSearchMenuItems(menu: Menu?) {
        menu ?: return
        val webSearchId = resources.getIdentifier("websearch", "id", "android")
        if (webSearchId != 0) {
            menu.removeItem(webSearchId)
        }
        val removeIds = ArrayList<Int>(4)
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i) ?: continue
            if (isSelectionSearchMenuItem(item)) {
                removeIds.add(item.itemId)
            }
        }
        for (id in removeIds) {
            menu.removeItem(id)
        }
    }

    private fun isSelectionSearchMenuItem(item: MenuItem): Boolean {
        val title = item.title?.toString()?.trim().orEmpty()
        if (title == "搜索" ||
            title == "網頁搜尋" ||
            title == "网页搜索" ||
            title.equals("Search", ignoreCase = true) ||
            title.equals("Web search", ignoreCase = true)
        ) {
            return true
        }
        val action = item.intent?.action
        return action == Intent.ACTION_WEB_SEARCH || action == Intent.ACTION_SEARCH
    }

    /**
     * HyperOS/MIUI 的「搜索」常作为 TextAction 芯片画在 PopupWindow 里，不在 [Menu] 中；
     * 通过遍历窗口视图隐藏文案为「搜索」的按钮。
     */
    private fun scheduleHideSelectionSearchAction() {
        val hide = Runnable { hideSelectionSearchActionChips() }
        post(hide)
        postDelayed(hide, 32L)
        postDelayed(hide, 120L)
    }

    private fun hideSelectionSearchActionChips() {
        stripSearchMenuItems(readerSelectionActionMode?.menu)
        for (root in currentWindowDecorRoots()) {
            hideSearchLabeledViews(root)
        }
    }

    private fun currentWindowDecorRoots(): List<View> {
        val roots = ArrayList<View>(4)
        // 只扫 PopupWindow（浮动划词条），避免误藏主界面里的「搜索」入口
        val activityDecor = (context as? Activity)?.window?.decorView
        try {
            val wmgClass = Class.forName("android.view.WindowManagerGlobal")
            val instance = wmgClass.getMethod("getInstance").invoke(null)
            val viewsField = wmgClass.getDeclaredField("mViews").apply { isAccessible = true }
            val views = viewsField.get(instance)
            if (views is List<*>) {
                for (v in views) {
                    if (v is View && v !== activityDecor) roots.add(v)
                }
            }
        } catch (_: Throwable) {
            // 反射失败时不做全树扫描，仅依赖 Menu 剔除
        }
        return roots
    }

    private fun hideSearchLabeledViews(view: View) {
        if (view is TextView && view !is android.widget.EditText) {
            val label = view.text?.toString()?.trim().orEmpty()
            if (label == "搜索" || label.equals("Search", ignoreCase = true)) {
                // 隐藏芯片容器（上溯几层），避免只藏文字留下空白可点区域
                var target: View = view
                repeat(3) {
                    val parent = target.parent as? ViewGroup ?: return@repeat
                    if (parent.childCount <= 4) {
                        target = parent
                    }
                }
                target.visibility = View.GONE
                return
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                hideSearchLabeledViews(view.getChildAt(i))
            }
        }
    }

    private fun shouldShowCancelHighlightTitle(): Boolean {
        if (suppressCancelTitleAfterLocalRemove) return false
        return selectionHighlightPending || currentSelectionExistingHighlightId() != null
    }

    private fun currentSelectedText(): String {
        val range = currentSelectionRange() ?: return ""
        val body = text ?: return ""
        return extractReaderSelectionText(body, range.first, range.last + 1, context)
    }

    private fun currentSelectionRange(): IntRange? {
        val body = text ?: return null
        val selStart = Selection.getSelectionStart(body)
        val selEnd = Selection.getSelectionEnd(body)
        if (selStart < 0 || selEnd < 0 || selStart == selEnd) return null
        val start = minOf(selStart, selEnd)
        val end = maxOf(selStart, selEnd).coerceAtMost(body.length)
        if (start >= end) return null
        return start until end
    }

    private fun currentSelectionExistingHighlightId(): Long? {
        val range = currentSelectionRange() ?: return null
        val start = range.first
        val endExclusive = range.last + 1
        val boundId = selectionBoundHighlightId
        if (boundId != null &&
            selectionBoundStart >= 0 &&
            selectionBoundEnd > selectionBoundStart &&
            kotlin.math.abs(start - selectionBoundStart) <= 2 &&
            kotlin.math.abs(endExclusive - selectionBoundEnd) <= 2
        ) {
            return boundId
        }
        val selected = currentSelectedText()
        if (selected.isBlank()) return null
        return resolveExistingHighlightId?.invoke(selected, start, endExclusive)
    }

    /** 划线落库成功后绑定到当前选区，并把菜单标题改为「取消划线」。 */
    fun bindSelectionHighlightId(highlightId: Long?, displayedStart: Int, displayedEnd: Int) {
        if (highlightId == null || highlightId <= 0L || displayedEnd <= displayedStart) {
            clearSelectionBoundHighlight()
            refreshHighlightMenuTitle()
            return
        }
        if (selectionHighlightAddCancelled) {
            selectionHighlightAddCancelled = false
            clearSelectionBoundHighlight()
            onRemoveHighlightClick?.invoke(highlightId)
            refreshHighlightMenuTitle()
            return
        }
        // 点「划线」时已因 pending 排队切到「取消划线」；落库再 refresh 会二次 finish/start 闪一下
        val menuRefreshAlreadyInFlight =
            selectionHighlightPending ||
                menuRefreshQueued ||
                recreatingSelectionActionMode ||
                appliedMenuShowsCancel == true
        selectionHighlightPending = false
        selectionBoundHighlightId = highlightId
        selectionBoundStart = displayedStart
        selectionBoundEnd = displayedEnd
        if (!menuRefreshAlreadyInFlight) {
            refreshHighlightMenuTitle()
        }
    }

    private fun clearSelectionBoundHighlight() {
        selectionBoundHighlightId = null
        selectionBoundStart = -1
        selectionBoundEnd = -1
        selectionHighlightPending = false
    }

    /**
     * 样式浮窗关闭且尚未落库时调用：取消 pending，菜单恢复「划线」。
     * 若已 [bindSelectionHighlightId]，则保持「取消划线」。
     */
    fun clearPendingHighlightMenuIfUnbound() {
        if (selectionBoundHighlightId != null) {
            selectionHighlightPending = false
            return
        }
        if (!selectionHighlightPending && !selectionHighlightAddCancelled) return
        selectionHighlightPending = false
        selectionBoundStart = -1
        selectionBoundEnd = -1
        // 保留 selectionHighlightAddCancelled，等 bind 时删掉刚写入的划线
        refreshHighlightMenuTitle()
    }

    /** 复制选区到剪贴板并结束选区。 */
    private fun copySelectionToClipboard(): Boolean {
        val selected = currentSelectedText().trim()
        if (selected.isEmpty()) return false
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("selection", selected))
        dismissSelection()
        return true
    }

    /**
     * 选区菜单「划线 / 取消划线」：
     * - 已划线 → 删除该划线，刷新菜单标题，保持选区与 ActionMode
     * - 未划线 → 打开样式浮窗，保持选区与 ActionMode（复制/划线按钮不消失）
     */
    private fun handleHighlightMenuClick(mode: ActionMode?): Boolean {
        val range = currentSelectionRange() ?: return false
        val selected = currentSelectedText()
        if (selected.isBlank()) return false
        val existingId = currentSelectionExistingHighlightId()
        if (existingId != null || selectionHighlightPending) {
            // 先压住「取消划线」文案，再删库：否则 Flow 未更新时 resolve 仍命中，菜单切不回去
            suppressCancelTitleAfterLocalRemove = true
            selectionHighlightPending = false
            if (existingId == null) {
                selectionHighlightAddCancelled = true
            }
            clearSelectionBoundHighlight()
            // 立刻清掉选区上的装饰，避免等 Flow 重组前仍显示划线
            clearReaderHighlightSpansAtRange(this, range.first, range.last + 1)
            if (existingId != null) {
                onRemoveHighlightClick?.invoke(existingId)
            } else {
                onRemoveHighlightClick?.invoke(-1L)
            }
            // 强制重建为「划线」（忽略 applied 同态短路：取消后必切）
            forceRefreshHighlightMenuTitle()
            return true
        }
        return requestHighlightFromSelection()
    }

    /** 打开划线浮窗；不结束选区，保留系统复制/划线菜单。 */
    private fun requestHighlightFromSelection(): Boolean {
        val range = currentSelectionRange() ?: return false
        val selected = currentSelectedText()
        if (selected.isBlank()) return false
        val bounds = selectionBoundsInWindow(range.first, range.last + 1) ?: return false
        highlightStylePickerShowing = true
        selectionHighlightAddCancelled = false
        suppressCancelTitleAfterLocalRemove = false
        selectionHighlightPending = true
        selectionBoundStart = range.first
        selectionBoundEnd = range.last + 1
        // 先切成「取消划线」，无需等 Room 回调（MIUI 靠 invalidate 刷新文案）
        refreshHighlightMenuTitle()
        onHighlightMenuClick?.invoke(selected, range.first, range.last + 1, bounds)
        return true
    }

    /**
     * 刷新选区浮动菜单「划线/取消划线」文案。
     * HyperOS/MIUI 的 FloatingToolbar 会缓存按钮，[ActionMode.invalidate] 与 setTitle 均无效，
     * 需 finish 后按当前状态重新 [startActionMode]（itemId 也已区分）。
     * 同文案状态会跳过；多次调用合并为一次重建，避免连闪。
     */
    fun refreshHighlightMenuTitle() {
        queueHighlightMenuRefresh(force = false)
    }

    /** 取消划线后必须切回「划线」，即使 applied 状态尚未同步也重建。 */
    private fun forceRefreshHighlightMenuTitle() {
        appliedMenuShowsCancel = null
        queueHighlightMenuRefresh(force = true)
    }

    private fun queueHighlightMenuRefresh(force: Boolean) {
        val wantCancel = shouldShowCancelHighlightTitle()
        if (!force &&
            appliedMenuShowsCancel == wantCancel &&
            readerSelectionActionMode != null &&
            !recreatingSelectionActionMode
        ) {
            return
        }
        if (menuRefreshQueued) {
            if (force) {
                // 已排队的刷新在 post 里会再读 shouldShow；强制时清掉 applied 即可
                appliedMenuShowsCancel = null
            }
            return
        }
        menuRefreshQueued = true
        // 勿在 onActionItemClicked 同步 finish，等点击回调返回后再重建
        post {
            menuRefreshQueued = false
            val showCancel = shouldShowCancelHighlightTitle()
            if (!force &&
                appliedMenuShowsCancel == showCancel &&
                readerSelectionActionMode != null &&
                !recreatingSelectionActionMode
            ) {
                return@post
            }
            recreateSelectionActionModeForMenuRefresh()
        }
    }

    /** @deprecated 使用 [refreshHighlightMenuTitle]。 */
    fun invalidateSelectionActionModeMenu() {
        refreshHighlightMenuTitle()
    }

    private fun recreateSelectionActionModeForMenuRefresh() {
        if (!selectionActive || !hasSelectionRange()) return
        val body = text
        val selStart = if (body != null) Selection.getSelectionStart(body) else -1
        val selEnd = if (body != null) Selection.getSelectionEnd(body) else -1
        val mode = readerSelectionActionMode
        if (mode == null) {
            restoreSelectionActionMode()
            return
        }
        recreatingSelectionActionMode = true
        try {
            mode.finish()
        } catch (_: Throwable) {
            recreatingSelectionActionMode = false
            try {
                mode.menu?.let { populateSelectionMenu(it) }
                mode.invalidate()
            } catch (_: Throwable) {
                // ignore
            }
            return
        }
        post {
            recreatingSelectionActionMode = false
            if (body is android.text.Spannable &&
                selStart >= 0 &&
                selEnd > selStart &&
                selEnd <= body.length
            ) {
                val curStart = Selection.getSelectionStart(body)
                val curEnd = Selection.getSelectionEnd(body)
                if (curStart < 0 || curStart == curEnd) {
                    Selection.setSelection(body, selStart, selEnd)
                }
            }
            if (selectionActive && hasSelectionRange() && readerSelectionActionMode == null) {
                restoreSelectionActionMode()
            }
        }
    }

    private fun restoreSelectionActionMode() {
        if (readerSelectionActionMode != null) return
        if (!hasSelectionRange()) return
        try {
            val mode = startActionMode(selectionActionModeCallback, ActionMode.TYPE_FLOATING)
            // 拉起后立刻按选区校正锚点（部分 ROM 首帧会用默认 0,0）
            mode?.invalidateContentRect()
            scheduleHideSelectionSearchAction()
        } catch (_: Throwable) {
            try {
                startActionMode(selectionActionModeCallback)?.invalidateContentRect()
                scheduleHideSelectionSearchAction()
            } catch (_: Throwable) {
                // 部分机型无法手动拉起，至少保留选区高亮与句柄
            }
        }
    }

    /** 选区在 TextView 坐标系中的包围盒（Floating ActionMode 锚点）。 */
    private fun selectionContentRectInView(selStart: Int, selEnd: Int): android.graphics.Rect? {
        val layout = layout ?: return null
        val len = text?.length ?: return null
        if (len == 0) return null
        val start = selStart.coerceIn(0, len)
        val end = selEnd.coerceIn(0, len)
        if (start >= end) return null
        val startLine = layout.getLineForOffset(start)
        val endLine = layout.getLineForOffset(end)
        // 与 Editor 一致：含 padding，并扣除 scroll，相对本 View
        val top = totalPaddingTop + layout.getLineTop(startLine) - scrollY
        val bottom = totalPaddingTop + layout.getLineBottom(endLine) - scrollY
        val left: Int
        val right: Int
        if (startLine == endLine) {
            val x0 = layout.getPrimaryHorizontal(start)
            val x1 = layout.getPrimaryHorizontal(end)
            left = totalPaddingLeft + minOf(x0, x1).toInt() - scrollX
            right = totalPaddingLeft + maxOf(x0, x1).toInt() - scrollX
        } else {
            left = totalPaddingLeft
            right = width - totalPaddingRight
        }
        return android.graphics.Rect(
            left,
            top,
            right.coerceAtLeast(left + 1),
            bottom.coerceAtLeast(top + 1),
        )
    }

    /** 选区在窗口坐标系中的包围盒（用于划线浮窗定位）。 */
    internal fun selectionBoundsInWindow(selStart: Int, selEnd: Int): android.graphics.Rect? {
        val local = selectionContentRectInView(selStart, selEnd) ?: return null
        val loc = IntArray(2)
        getLocationInWindow(loc)
        return android.graphics.Rect(
            local.left + loc[0],
            local.top + loc[1],
            local.right + loc[0],
            local.bottom + loc[1],
        )
    }

    /** 单元测试：填充选区菜单项。 */
    internal fun populateSelectionMenuForTest(menu: Menu) {
        populateSelectionMenu(menu)
    }

    /** 单元测试：模拟点击选区菜单项。 */
    internal fun performSelectionMenuActionForTest(itemId: Int): Boolean {
        return when (itemId) {
            MENU_ID_COPY -> copySelectionToClipboard()
            MENU_ID_HIGHLIGHT, MENU_ID_CANCEL_HIGHLIGHT ->
                handleHighlightMenuClick(readerSelectionActionMode)
            else -> false
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
        selectionActionModeCallback.onDestroyActionMode(mode)
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
            clearSelectionBoundHighlight()
            appliedMenuShowsCancel = null
            menuRefreshQueued = false
            recreatingSelectionActionMode = false
            selectionHighlightAddCancelled = false
            suppressCancelTitleAfterLocalRemove = false
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
     *
     * 手指拖动 / 惯性滑动期间禁止程序化 scrollTo，否则与 TouchScroller 争抢会抖。
     */
    private var viewportAnchorTextLen = -1
    private var viewportAnchorChar = -1
    private var viewportAnchorInLineOffset = 0
    private var pointerDown = false
    private var lastInteractiveScrollUptimeMs = 0L
    private val settleViewportAfterScrollRunnable = Runnable {
        if (pointerDown || isVerticalScrollDrag) return@Runnable
        if (shouldSkipProgrammaticScrollCompensation()) return@Runnable
        maintainViewportCharAnchorAfterLayout(
            snapTextLen = viewportAnchorTextLen,
            snapChar = viewportAnchorChar,
            snapInLineOffset = viewportAnchorInLineOffset,
        )
    }

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

    /**
     * 用户正在拖动、手指仍按下，或松手后惯性窗口内：不要程序化改 scrollY。
     * DiagramImageLoader 等异步纠偏也应遵守此门控。
     */
    fun shouldSkipProgrammaticScrollCompensation(): Boolean {
        if (pointerDown || isVerticalScrollDrag) return true
        return android.os.SystemClock.uptimeMillis() - lastInteractiveScrollUptimeMs < 160L
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        val now = android.os.SystemClock.uptimeMillis()
        // 拖动或惯性滚动期间延续抑制窗口，避免松手后 fling 仍与 maintain scrollTo 争抢。
        val inInteractiveWindow = pointerDown || isVerticalScrollDrag ||
            now - lastInteractiveScrollUptimeMs < 160L
        if (inInteractiveWindow && t != oldt) {
            lastInteractiveScrollUptimeMs = now
            removeCallbacks(settleViewportAfterScrollRunnable)
            postDelayed(settleViewportAfterScrollRunnable, 180L)
        }
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
        // 拖动/惯性中只刷新锚点，不 scrollTo，避免与手势叠加抖动。
        if (shouldSkipProgrammaticScrollCompensation()) {
            updateViewportCharAnchor()
            return false
        }
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
            signalOpenPositionReady("onLayoutScrollToTop")
            return
        }
        if (hasSnapStash) {
            if (applyStashedSavedPositionSnapIfAny(this)) {
                signalOpenPositionReady("onLayoutSnap")
            }
            return
        }
        if (hasExpandStash) {
            // 内容尚未替换时 restore 会失败并保留 stash；此时仍走 maintain，避免高度变化无补偿。
            if (!applyStashedSourceScrollRestoreIfAny(this, renderPlainText = renderPlainTextBody)) {
                maintainViewportCharAnchorAfterLayout(snapTextLen, snapChar, snapInLineOffset)
            }
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
                removeCallbacks(settleViewportAfterScrollRunnable)
                pointerDown = true
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
                pointerDown = false
                lastInteractiveScrollUptimeMs = android.os.SystemClock.uptimeMillis()
                // 松手后等惯性结束，再补一次视口锚点补偿（拖动中跳过的异步重排）。
                removeCallbacks(settleViewportAfterScrollRunnable)
                postDelayed(settleViewportAfterScrollRunnable, 180L)
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
            val newStart = minOf(selStart, selEnd)
            val newEnd = maxOf(selStart, selEnd)
            // 拖动句柄改变选区后，允许重新按真实划线状态显示「取消划线」
            if (suppressCancelTitleAfterLocalRemove &&
                (newStart != savedSelStart || newEnd != savedSelEnd)
            ) {
                suppressCancelTitleAfterLocalRemove = false
            }
            savedSelStart = newStart
            savedSelEnd = newEnd
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

    companion object {
        internal const val MENU_ID_COPY = 0x4D520001
        internal const val MENU_ID_HIGHLIGHT = 0x4D520002
        /** 与 [MENU_ID_HIGHLIGHT] 分离，避免 MIUI 浮动条按 id 缓存「划线」文案。 */
        internal const val MENU_ID_CANCEL_HIGHLIGHT = 0x4D520003
    }
}

/** @see ReaderMarkwonFactory */
internal fun createMarkwon(context: Context): Markwon = ReaderMarkwonFactory.create(context)

/** PDF 阅读：与默认配置相同，渲染后由 [PdfImageLayoutHelper] 将页图拉满内容区宽度。 */
internal fun createPdfMarkwon(context: Context): Markwon = ReaderMarkwonFactory.create(context)

/**
 * 在渲染后的文本上为划线上色。优先使用页/窗内相对 [HighlightEntity.startPosition]/[endPosition]；
 * 源码与 Spanned 长度不一致时，在估算位置附近只匹配一次，避免同文全局全标。
 */
internal fun applyHighlightsToRenderedText(
    textView: TextView,
    highlights: List<space.liushenme.markdownreader.data.local.entity.HighlightEntity>,
    highlightColorArgb: Int,
    sourceContentLength: Int = -1,
) {
    if (highlights.isEmpty()) return
    val text = ensureReaderSpannable(textView) ?: return
    val full = text.toString()
    highlights.forEach { highlight ->
        val range = resolveHighlightDisplayedRange(
            displayed = full,
            highlight = highlight,
            sourceContentLength = sourceContentLength,
        ) ?: return@forEach
        val spanColor = if (android.graphics.Color.alpha(highlight.color) < 16) {
            highlightColorArgb
        } else {
            highlight.color
        }
        val style = space.liushenme.markdownreader.model.HighlightStyle.fromStorageKey(highlight.style)
        val spanStart = range.first
        val spanEnd = range.last + 1
        val span: Any = when (style) {
            space.liushenme.markdownreader.model.HighlightStyle.Background ->
                HighlightBackgroundSpan(spanColor)
            space.liushenme.markdownreader.model.HighlightStyle.Underline ->
                HighlightUnderlineSpan(spanColor, wavy = false)
            space.liushenme.markdownreader.model.HighlightStyle.Wavy ->
                HighlightUnderlineSpan(spanColor, wavy = true)
        }
        text.setSpan(
            span,
            spanStart,
            spanEnd,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    textView.invalidate()
}

/**
 * 在展示层选区上立即打上划线样式（不依赖源码坐标映射），用于点「划线」瞬间出默认效果。
 */
internal fun applyHighlightDecorationAtRange(
    textView: TextView,
    start: Int,
    end: Int,
    colorArgb: Int,
    style: space.liushenme.markdownreader.model.HighlightStyle,
) {
    val text = ensureReaderSpannable(textView) ?: return
    if (start < 0 || end > text.length || start >= end) return
    text.getSpans(start, end, HighlightBackgroundSpan::class.java).forEach { text.removeSpan(it) }
    text.getSpans(start, end, HighlightUnderlineSpan::class.java).forEach { text.removeSpan(it) }
    val span: Any = when (style) {
        space.liushenme.markdownreader.model.HighlightStyle.Background ->
            HighlightBackgroundSpan(colorArgb)
        space.liushenme.markdownreader.model.HighlightStyle.Underline ->
            HighlightUnderlineSpan(colorArgb, wavy = false)
        space.liushenme.markdownreader.model.HighlightStyle.Wavy ->
            HighlightUnderlineSpan(colorArgb, wavy = true)
    }
    text.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    textView.invalidate()
}

/** 清除由阅读划线写入的 span，保留 Markdown 自身背景色等。 */
internal fun clearReaderHighlightSpans(textView: TextView) {
    val text = textView.text
    if (text !is Spannable || text.isEmpty()) return
    text.getSpans(0, text.length, HighlightBackgroundSpan::class.java).forEach { text.removeSpan(it) }
    text.getSpans(0, text.length, HighlightUnderlineSpan::class.java).forEach { text.removeSpan(it) }
    textView.invalidate()
}

/** 清除指定展示区间上的划线装饰（点「取消划线」时立刻去掉预览 span）。 */
internal fun clearReaderHighlightSpansAtRange(textView: TextView, start: Int, end: Int) {
    val text = ensureReaderSpannable(textView) ?: return
    if (start < 0 || end > text.length || start >= end) return
    text.getSpans(start, end, HighlightBackgroundSpan::class.java).forEach { text.removeSpan(it) }
    text.getSpans(start, end, HighlightUnderlineSpan::class.java).forEach { text.removeSpan(it) }
    textView.invalidate()
}

/** Markwon 偶发给出只读 Spanned；划线需要可变 [Spannable]。 */
internal fun ensureReaderSpannable(textView: TextView): Spannable? {
    val raw = textView.text ?: return null
    if (raw is Spannable) return raw
    if (raw.isEmpty()) return null
    val mutable = SpannableString(raw)
    textView.setText(mutable, TextView.BufferType.SPANNABLE)
    return textView.text as? Spannable
}

internal fun refreshReaderHighlightSpans(
    textView: TextView,
    highlights: List<HighlightEntity>,
    highlightColorArgb: Int,
    sourceContentLength: Int,
    highlightSig: String,
) {
    clearReaderHighlightSpans(textView)
    applyHighlightsToRenderedText(
        textView = textView,
        highlights = highlights,
        highlightColorArgb = highlightColorArgb,
        sourceContentLength = sourceContentLength,
    )
    textView.setTag(TAG_READER_HIGHLIGHT_SIG, highlightSig)
}

/**
 * 将划线映射到展示层 [start, end) 区间；每条划线至多一处。
 */
internal fun resolveHighlightDisplayedRange(
    displayed: String,
    highlight: space.liushenme.markdownreader.data.local.entity.HighlightEntity,
    sourceContentLength: Int,
): IntRange? {
    val snippet = highlight.highlightedText
    if (snippet.isEmpty() || displayed.isEmpty()) return null
    val s = highlight.startPosition
    val e = highlight.endPosition
    if (s in 0..displayed.length && e in (s + 1)..displayed.length &&
        displayed.substring(s, e) == snippet
    ) {
        return s until e
    }
    if (s in 0 until displayed.length &&
        s + snippet.length <= displayed.length &&
        displayed.regionMatches(s, snippet, 0, snippet.length)
    ) {
        return s until (s + snippet.length)
    }
    val estimate = when {
        sourceContentLength > 0 ->
            ((s.toLong() * displayed.length) / sourceContentLength)
                .toInt()
                .coerceIn(0, displayed.length)
        else -> s.coerceIn(0, displayed.length)
    }
    var bestIdx = -1
    var bestDist = Int.MAX_VALUE
    var from = 0
    while (from <= displayed.length - snippet.length) {
        val idx = displayed.indexOf(snippet, from)
        if (idx < 0) break
        val dist = kotlin.math.abs(idx - estimate)
        if (dist < bestDist) {
            bestDist = dist
            bestIdx = idx
        }
        from = idx + 1
    }
    if (bestIdx < 0) return null
    return bestIdx until (bestIdx + snippet.length)
}
