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

internal fun applyReaderTextContent(
    textView: TextView,
    content: String,
    renderPlainText: Boolean,
    renderSig: String,
    markwon: Markwon,
    highlights: List<HighlightEntity>,
    highlightColorArgb: Int
) {
    textView.setTag(TAG_READER_RENDER_SIG, renderSig)
    if (!renderPlainText) {
        applyMarkdownContent(textView, content, renderSig, markwon, highlights, highlightColorArgb)
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
    highlightColorArgb: Int
) {
    if (content.length <= MARKWON_BACKGROUND_RENDER_THRESHOLD) {
        // 短文本同步走完，UI 首帧响应更直接
        markwon.setMarkdown(textView, content)
        applyHighlightsToRenderedText(textView, highlights, highlightColorArgb)
        return
    }
    // 长 Markdown：在后台线程做 CommonMark parse + Markwon render（产 Spanned），
    // 这一步通常 200~500ms（取决于内容长度与图片数）。主线程只剩 setText + measure。
    readerMarkwonRenderExecutor.execute {
        val rendered: CharSequence = runCatching { markwon.toMarkdown(content) }
            .getOrNull() ?: content
        textView.post {
            if (textView.getTag(TAG_READER_RENDER_SIG) != renderSig) return@post
            // setParsedMarkdown 会在主线程上把 Spanned 应用到 TextView，并执行
            // Markwon 各插件的 `afterSetText`（如启动 AsyncDrawable 图片加载）。
            markwon.setParsedMarkdown(textView, rendered as? android.text.Spanned ?: android.text.SpannableString(rendered))
            applyHighlightsToRenderedText(textView, highlights, highlightColorArgb)
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
    onCenterTap: () -> Unit
) {
    val context = LocalContext.current
    val markwon = remember { createMarkwon(context) }
    val touchState = remember { ReaderTouchState() }
    val slop = ViewConfiguration.get(context).scaledTouchSlop

    AndroidView(
        factory = { ctx ->
            SafeReaderTextView(ctx).apply {
                this.allowVerticalScroll = allowVerticalScroll
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(theme.textColor.toArgb())
                setBackgroundColor(theme.backgroundColor.toArgb())
                textSize = fontSize.toFloat()
                val density = resources.displayMetrics.density
                val padPx = (readerPaddingDp * density).toInt().coerceAtLeast(0)
                val padTopPx = (readerPaddingTopDp * density).toInt().coerceAtLeast(0)
                setPadding(padPx, padTopPx, padPx, padPx)
                setLineSpacing(0f, readerLineSpacingMultiplier)

                val hlKey0 = highlights.joinToString("|") { "${it.id}_${it.startPosition}_${it.endPosition}" }
                val sig0 =
                    "${renderPlainText}_${content.length}_${content.hashCode()}_${theme::class.java.name}_${fontSize}_$hlKey0"
                applyReaderTextContent(
                    textView = this,
                    content = content,
                    renderPlainText = renderPlainText,
                    renderSig = sig0,
                    markwon = markwon,
                    highlights = highlights,
                    highlightColorArgb = theme.highlightColor.toArgb()
                )

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
                        val start = selectionStart
                        val end = selectionEnd
                        val len = text.length
                        if (start < 0 || end < 0 || len == 0) {
                            onTextSelected("")
                            return
                        }
                        val from = start.coerceAtMost(end).coerceIn(0, len)
                        val to = start.coerceAtLeast(end).coerceIn(0, len)
                        if (from >= to) {
                            onTextSelected("")
                            return
                        }
                        onTextSelected(text.substring(from, to))
                    }
                }

                bindReaderGesturesAndScroll(
                    textView = this,
                    touchState = touchState,
                    slop = slop,
                    allowVerticalScroll = allowVerticalScroll,
                    onScroll = onScroll,
                    onReadingVerticalScroll = onReadingVerticalScroll,
                    onSwipeRightBookmark = onSwipeRightBookmark,
                    onSwipeDownBookmark = onSwipeDownBookmark,
                    onCenterTap = onCenterTap
                )
                post { onViewReady(this) }
            }
        },
        update = { textView ->
            (textView as? SafeReaderTextView)?.allowVerticalScroll = allowVerticalScroll
            textView.movementMethod = LinkMovementMethod.getInstance()
            textView.setTextColor(theme.textColor.toArgb())
            textView.setBackgroundColor(theme.backgroundColor.toArgb())
            textView.textSize = fontSize.toFloat()
            val density = textView.resources.displayMetrics.density
            val padPx = (readerPaddingDp * density).toInt().coerceAtLeast(0)
            val padTopPx = (readerPaddingTopDp * density).toInt().coerceAtLeast(0)
            textView.setPadding(padPx, padTopPx, padPx, padPx)
            textView.setLineSpacing(0f, readerLineSpacingMultiplier)

            val hlKey = highlights.joinToString("|") { "${it.id}_${it.startPosition}_${it.endPosition}" }
            val renderSig =
                "${renderPlainText}_${content.length}_${content.hashCode()}_${theme::class.java.name}_${fontSize}_$hlKey"
            val prevSig = textView.getTag(TAG_READER_RENDER_SIG) as? String
            if (prevSig != renderSig) {
                applyReaderTextContent(
                    textView = textView,
                    content = content,
                    renderPlainText = renderPlainText,
                    renderSig = renderSig,
                    markwon = markwon,
                    highlights = highlights,
                    highlightColorArgb = theme.highlightColor.toArgb()
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
                onCenterTap = onCenterTap
            )
            if (!allowVerticalScroll) {
                textView.scrollTo(0, 0)
            }
            textView.post { onViewReady(textView) }
        },
        modifier = modifier
    )
}

internal class ReaderTouchState(
    var downX: Float = 0f,
    var downY: Float = 0f,
    var scrollYOnDown: Int = 0
)

internal fun bindReaderGesturesAndScroll(
    textView: TextView,
    touchState: ReaderTouchState,
    slop: Int,
    allowVerticalScroll: Boolean,
    onScroll: (Float) -> Unit,
    onReadingVerticalScroll: (verticalScrollDeltaPx: Int) -> Unit,
    onSwipeRightBookmark: () -> Unit,
    onSwipeDownBookmark: (() -> Unit)? = null,
    onCenterTap: () -> Unit
) {
    textView.setOnScrollChangeListener { v, _, scrollY, _, oldScrollY ->
        if (!allowVerticalScroll) return@setOnScrollChangeListener
        if (scrollY != oldScrollY) {
            onReadingVerticalScroll(kotlin.math.abs(scrollY - oldScrollY))
        }
        val tv = v as? TextView ?: return@setOnScrollChangeListener
        val layout = tv.layout ?: return@setOnScrollChangeListener
        val innerH = tv.height - tv.paddingTop - tv.paddingBottom
        if (innerH <= 0) return@setOnScrollChangeListener
        val total = layout.height
        if (total <= innerH) {
            onScroll(0f)
            return@setOnScrollChangeListener
        }
        val maxScroll = (total - innerH).coerceAtLeast(1)
        val safeY = scrollY.coerceIn(0, maxScroll)
        onScroll((safeY / maxScroll.toFloat()).coerceIn(0f, 1f))
    }

    textView.setOnTouchListener { v, e ->
        val tv = v as? TextView
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchState.downX = e.x
                touchState.downY = e.y
                touchState.scrollYOnDown = tv?.scrollY ?: 0
                if (!allowVerticalScroll) {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            MotionEvent.ACTION_MOVE -> {
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
                if (e.actionMasked != MotionEvent.ACTION_UP) return@setOnTouchListener false
                val dx = e.x - touchState.downX
                val dy = e.y - touchState.downY
                val adx = kotlin.math.abs(dx)
                val ady = kotlin.math.abs(dy)
                if (adx < slop && ady < slop) {
                    val w = v.width.toFloat()
                    val h = v.height.toFloat()
                    if (w > 0f && h > 0f &&
                        e.x in (w * 0.32f)..(w * 0.68f) &&
                        e.y in (h * 0.36f)..(h * 0.64f)
                    ) {
                        onCenterTap()
                    }
                } else if (allowVerticalScroll && dx > 120f && dx > ady * 2f) {
                    onSwipeRightBookmark()
                } else if (onSwipeDownBookmark != null &&
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
 * 阅读器用的 TextView 子类，吞掉两类已知 Android 框架 bug：
 *
 * 1. `Editor.performLongClick` 里访问尚未初始化的
 *    `SelectionModifierCursorController` 抛 `NullPointerException`
 *    （https://issuetracker.google.com/issues/37095917 起就存在，
 *    MIUI / Android 11/12 上仍可复现）。
 *
 * 2. `ArrowKeyMovementMethod.onTouchEvent` 在长文 selection 边界
 *    偶发 `IndexOutOfBoundsException`。
 *
 * 这些都是 framework 内部状态问题，无法在应用层根治；包一层 catch
 * 让长按时退化为「不进入文本选择」即可，比直接 crash 体验好得多。
 */
internal class SafeReaderTextView(context: Context) : TextView(context) {
    /** 分页翻页模式下为 false：禁止上下滚动，仅由 HorizontalPager 横向翻页 */
    var allowVerticalScroll: Boolean = true

    init {
        // 必须显式开启 textIsSelectable，否则 Editor.startSelectionActionMode() 会走
        // textCanBeSelected() 检查直接取消选择，日志表现为
        //   "TextView does not support text selection. Selection cancelled."
        // setTextIsSelectable(true) 会顺带把 movementMethod 重置为 ArrowKeyMovementMethod，
        // 立刻覆盖回 LinkMovementMethod 以保留 url 点击行为；mTextIsSelectable=true
        // 不受影响，长按选词仍能进入 ActionMode。
        setTextIsSelectable(true)
        movementMethod = LinkMovementMethod.getInstance()
        isVerticalScrollBarEnabled = false
    }

    override fun canScrollVertically(direction: Int): Boolean =
        allowVerticalScroll && super.canScrollVertically(direction)

    override fun scrollTo(x: Int, y: Int) {
        if (allowVerticalScroll) {
            super.scrollTo(x, y)
        } else {
            super.scrollTo(x, 0)
        }
    }

    override fun performLongClick(): Boolean = try {
        super.performLongClick()
    } catch (_: NullPointerException) {
        false
    }

    override fun performLongClick(x: Float, y: Float): Boolean = try {
        super.performLongClick(x, y)
    } catch (_: NullPointerException) {
        false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = try {
        if (!allowVerticalScroll) {
            // 分页模式：禁用纵向滚动，保留链接点击
            val spannable = text as? Spannable
            movementMethod?.onTouchEvent(this, spannable, event) == true
        } else {
            super.onTouchEvent(event)
        }
    } catch (_: NullPointerException) {
        false
    } catch (_: IndexOutOfBoundsException) {
        false
    }
}

internal fun createMarkwon(context: Context): Markwon {
    return Markwon.builder(context)
        .usePlugin(CorePlugin.create())
        // JLatexMathPlugin 启用 inline `$...$` 模式后会 require 这个插件；
        // 同时它的 commonmark inline parser 会替换 CorePlugin 默认的解析器，
        // 让 `$...$` 不再被当作普通文本拆分。
        .usePlugin(MarkwonInlineParserPlugin.create())
        .usePlugin(HtmlPlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(LinkifyPlugin.create())
        .usePlugin(
            ImagesPlugin.create { plugin ->
                // 仅启用 file:// 本地图片：EPUB/MOBI 内嵌图已落盘到 parsed_books/<id>/assets/，
                // ParsedBookStorage.readBundle 读取时把 book-asset:// 占位换成了 file://。
                // 出于隐私 / 流量考虑暂不启用 HTTP 远程图片加载。
                plugin.addSchemeHandler(FileSchemeHandler.create())
            }
        )
        .usePlugin(
            // `$$...$$` 块、`$...$` 内联 LaTeX 公式渲染（基于 jlatexmath）。
            // EPUB/MOBI 中如有 MathML，HtmlToMarkdownConverter 会先把它转换为 $...$ / $$...$$。
            JLatexMathPlugin.create(context.resources.getDimension(android.R.dimen.app_icon_size) / 2f) { builder ->
                builder.inlinesEnabled(true)
            }
        )
        .build()
}

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
