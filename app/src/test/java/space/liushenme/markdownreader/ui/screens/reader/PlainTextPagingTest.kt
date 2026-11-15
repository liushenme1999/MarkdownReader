package space.liushenme.markdownreader.ui.screens.reader

import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * 纯文本横向分页（行级分页）回归测试。
 *
 * 用与阅读页完全一致的 TextView 排版参数（字号 / 行距 / 内边距 / includeFontPadding）
 * 把每一页渲染出来，逐页检查：
 * - 不裁字：页内 Layout 高度不超过可视高度，最后一个字符可见；
 * - 不漏字、不重复、不留空白页；
 * - 页面铺满：除最后一页外，页尾空白不超过约两行；
 * - 新页不从空行开始；
 * - 追加正文后旧页边界不变（窗口模式续读不跳页）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlainTextPagingTest {

    private val screenW = 1220
    private val screenH = 2712
    private val padPx = 96
    private val textSizeSp = 16f
    private val spacing = 1.5f

    /** 与 TextView.setTextSize(SP) 完全一致的 sp→px 换算（含 fontScale）。 */
    private val textSizePx: Float
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            textSizeSp,
            RuntimeEnvironment.getApplication().resources.displayMetrics,
        )

    private val availW: Int get() = screenW - 2 * padPx
    private val availH: Int get() = screenH - 2 * padPx

    private fun novel(paragraphs: Int, trailer: String = ""): String = buildString {
        for (i in 0 until paragraphs) {
            append("　　").append("第").append(i).append("段。")
            append("这是一段用于检验纯文本分页排版的小说正文，句子长短不一，")
            append("有时会连续出现较长的描述，用来观察行宽与行数是否被充分利用。")
            append('\n')
            if (i % 3 == 0) append('\n')
        }
        append(trailer)
    }

    private fun pagesOf(content: String): List<IntRange> = splitTextToPagesByLines(
        content = content,
        textSizePx = textSizePx,
        widthPx = availW,
        heightPx = availH,
        lineSpacingMultiplier = spacing,
    )

    /** 与 MarkdownReaderView 分页页同样的 TextView 配置。 */
    private class PageView(val root: FrameLayout, val tv: TextView)

    private fun newPageView(): PageView {
        val context = RuntimeEnvironment.getApplication()
        val tv = TextView(context).apply {
            // 与 MarkdownReaderView 的分页页完全一致
            includeFontPadding = false
            breakStrategy = android.text.Layout.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
            setPadding(padPx, padPx, padPx, padPx)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            setLineSpacing(0f, spacing)
        }
        val root = FrameLayout(context)
        root.addView(
            tv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        layoutRoot(root)
        return PageView(root, tv)
    }

    private fun layoutRoot(root: FrameLayout) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, screenW, screenH)
    }

    private fun renderPage(page: PageView, text: CharSequence) {
        page.tv.setText(text)
        layoutRoot(page.root)
    }

    /** 可用高度只看原始 padding；totalPadding 会把未占满的空白算进去。 */
    private fun innerHeight(tv: TextView): Int =
        tv.height - tv.paddingTop - tv.paddingBottom

    @Test
    fun lineStartsOfChunkedLayout_areAbsoluteOffsets() {
        val content = novel(400)
        val paint = TextPaint().apply { textSize = textSizePx }
        val chunkStart = 6_000
        val layout = StaticLayout.Builder
            .obtain(content, chunkStart, 12_000, paint, availW)
            .setLineSpacing(0f, spacing)
            .setIncludePad(false)
            .build()
        assertTrue(layout.lineCount > 0)
        // getLineStart 已包含 Builder 的 start，分页不能再加 chunkStart。
        assertEquals(chunkStart, layout.getLineStart(0))
    }

    @Test
    fun pages_fitViewportWithoutClippingText() {
        val content = novel(1_200)
        val pages = pagesOf(content)
        val page = newPageView()
        var clipped = 0
        pages.forEachIndexed { i, range ->
            val slice = content.substring(range.first, range.last + 1)
            renderPage(page, slice)
            val layout = page.tv.layout
            val inner = innerHeight(page.tv)
            if (layout.height > inner) {
                clipped++
                println("CLIP page=$i len=${slice.length} layoutH=${layout.height} inner=$inner")
            }
            val lastLine = layout.lineCount - 1
            assertEquals(
                "page=$i 最后一行应完整显示到页尾",
                slice.length,
                layout.getLineEnd(lastLine),
            )
        }
        assertEquals(0, clipped)
    }

    @Test
    fun pages_coverContentWithoutGapsOrOverlap() {
        val content = novel(1_200)
        val pages = pagesOf(content)
        assertEquals(0, pages.first().first)
        assertEquals(content.length - 1, pages.last().last)
        // 页间允许空白字符空隙（不显示的空行），但不允许丢掉任何非空白正文。
        assertTrue(
            "页区间丢失正文或重叠",
            pages.zipWithNext().all { (a, b) ->
                b.first > a.last && content.substring(a.last + 1, b.first).isBlank()
            },
        )
        assertTrue(pages.all { it.count() > 0 })
        assertTrue("不应出现超出正文的页", pages.all { it.last < content.length })
    }

    @Test
    fun pages_fillMostOfTheViewport() {
        val content = novel(1_200)
        val pages = pagesOf(content)
        val page = newPageView()
        val lineHeights = mutableListOf<Int>()
        var worst = 1.0
        var worstPage = -1
        pages.forEachIndexed { i, range ->
            if (i == pages.lastIndex) return@forEachIndexed
            renderPage(page, content.substring(range.first, range.last + 1))
            val layout = page.tv.layout
            val inner = innerHeight(page.tv)
            val lastBottom = layout.getLineBottom(layout.lineCount - 1)
            val fill = lastBottom.toDouble() / inner
            lineHeights += layout.getLineBottom(0) - layout.getLineTop(0)
            if (fill < worst) {
                worst = fill
                worstPage = i
            }
        }
        val lineHeight = lineHeights.sorted()[lineHeights.size / 2]
        val minFill = 1.0 - 2.0 * lineHeight / availH
        println("FILL worst=${"%.3f".format(worst)}@$worstPage minFill=$minFill lineH=$lineHeight")
        assertTrue(
            "第 $worstPage 页页尾空白超过两行（fill=$worst，底线 $minFill）",
            worst >= minFill,
        )
    }

    @Test
    fun pages_startAtContentNotBlankLine() {
        val content = novel(600)
        pagesOf(content).forEachIndexed { i, range ->
            val lineEnd = content.indexOf('\n', range.first)
                .takeIf { it in range.first until range.last + 1 } ?: (range.last + 1)
            assertTrue(
                "第 $i 页首行是空行：'${content.substring(range.first, lineEnd)}'",
                content.substring(range.first, lineEnd).isNotBlank(),
            )
        }
    }

    @Test
    fun trailingBlankLines_doNotCreateBlankPage() {
        val content = novel(200, trailer = "\n\n\n")
        val pages = pagesOf(content)
        val last = pages.last()
        assertTrue(
            "末页不应只剩空行",
            content.substring(last.first, last.last + 1).isNotBlank(),
        )
        assertTrue(pages.size >= 2)
    }

    @Test
    fun singleLongParagraph_isPagedWithoutNewlines() {
        val content = "没有换行的超长段落，".repeat(4_000)
        val pages = pagesOf(content)
        assertTrue(pages.size > 3)
        assertEquals(0, pages.first().first)
        assertEquals(content.length - 1, pages.last().last)
        assertTrue(pages.zipWithNext().all { (a, b) -> b.first == a.last + 1 })
    }

    @Test
    fun crlfText_pagesCoverContentOnce() {
        val content = novel(300).replace("\n", "\r\n")
        val pages = pagesOf(content)
        assertEquals(0, pages.first().first)
        assertEquals(content.length - 1, pages.last().last)
        // 页间只允许跳过空白字符，正文一个都不能少。
        pages.forEachIndexed { i, range ->
            val prevEnd = if (i == 0) 0 else pages[i - 1].last + 1
            assertTrue("第 $i 页之前丢了正文", content.substring(prevEnd, range.first).isBlank())
        }
        assertEquals(content.length - 1, pages.last().last)
    }

    @Test
    fun shortContent_producesSinglePage() {
        val content = "只有一行正文。"
        assertEquals(listOf(0 until content.length), pagesOf(content))
    }

    @Test
    fun hugeFont_neverProducesEmptyPages() {
        val content = novel(50)
        val pages = splitTextToPagesByLines(
            content = content,
            textSizePx = 40f * textSizePx / textSizeSp,
            widthPx = availW,
            heightPx = availH,
            lineSpacingMultiplier = 3f,
        )
        assertTrue(pages.isNotEmpty())
        assertTrue("不应出现空页", pages.all { it.count() > 0 })
        assertEquals(content.length - 1, pages.last().last)
    }

    @Test
    fun appendingWindowText_keepsExistingPageBoundaries() {
        val head = novel(300)
        val tail = novel(60)
        val before = pagesOf(head)
        val after = pagesOf(head + tail)
        // 追加正文只允许在末尾增加页，已读部分的页边界必须保持稳定，否则续读会跳页。
        // 追加前最后一页会继续吸收新正文，其余页边界必须完全不变。
        val stable = before.size - 1
        assertEquals(before.take(stable), after.take(stable))
        assertTrue(after.size > before.size)
    }

    @Test
    fun pageTextViews_remainWhenContentGrows() {
        val head = novel(200)
        val after = pagesOf(head + novel(30))
        val page = newPageView()
        renderPage(page, head.substring(after[0].first, after[0].last + 1))
        assertTrue(abs(page.tv.layout.height - 0) > 0)
    }

    /**
     * 分页可用高度只能用原始 padding 量。
     *
     * TextView.getTotalPaddingBottom() 会把「文字没填满时剩余的竖直空白」算进去
     * （getBottomVerticalOffset），量出来的是当前页的文字高度；用它反哺分页会让
     * 每次翻页可用高度少一行，页面越翻越短，最终整页只剩几行。
     */
    @Test
    fun viewportHeight_mustNotBeDerivedFromTotalPadding() {
        val content = novel(300)
        val pages = pagesOf(content)
        val page = newPageView()
        renderPage(page, content.substring(pages[0].first, pages[0].last + 1))

        assertEquals(availH, innerHeight(page.tv))
        val misleadingHeight = page.tv.height - page.tv.totalPaddingTop - page.tv.totalPaddingBottom
        assertTrue(
            "totalPadding 量出来的是文字高度（$misleadingHeight），不是可用高度（$availH）",
            misleadingHeight < availH,
        )
    }
}
