package space.liushenme.markdownreader.ui.screens.reader

import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PendingScrollReapplyTest {

    @Test
    fun cancelPendingScrollReapply_invalidatesScheduledCallbacks() {
        val context = RuntimeEnvironment.getApplication()
        val tv = TextView(context)
        applyPendingScrollToCharOffset(tv, 42)
        val genBefore = tv.getTag(R.id.reader_pending_scroll_reapply_gen) as? Int ?: 0
        schedulePendingScrollReapply(tv, longArrayOf(500))
        val genScheduled = tv.getTag(R.id.reader_pending_scroll_reapply_gen) as? Int
        assertTrue(genScheduled != null && genScheduled > genBefore)
        cancelPendingScrollReapply(tv)
        val genAfterCancel = tv.getTag(R.id.reader_pending_scroll_reapply_gen) as? Int
        assertTrue(genAfterCancel != null && genAfterCancel > genScheduled!!)
        clearPendingScrollCharOffset(tv)
        assertEquals(null, tv.getTag(R.id.reader_pending_scroll_char_offset))
    }

    @Test
    fun shouldTriggerReaderExpandUp_whenScrollYIsZero() {
        val context = RuntimeEnvironment.getApplication()
        val tv = TextView(context).apply {
            text = "hello\nworld\n"
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 200)
            scrollTo(0, 0)
        }
        assertTrue(shouldTriggerReaderExpandUp(tv))
    }

    @Test
    fun requestReaderScrollToTop_scrollsToZeroAndClearsIntent() {
        val context = RuntimeEnvironment.getApplication()
        val tv = TextView(context).apply {
            text = (1..200).joinToString("\n") { "line $it" }
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 300)
            scrollTo(0, 200)
        }
        requestReaderScrollToTop(tv)
        assertTrue(hasReaderScrollToTop(tv))
        val handled = applyReaderScrollToTopIfAny(tv, clearAfter = true)
        assertTrue(handled)
        assertEquals(0, tv.scrollY)
        assertFalse(hasReaderScrollToTop(tv))
    }

    @Test
    fun applyReaderScrollToTop_noopWhenNoIntent() {
        val context = RuntimeEnvironment.getApplication()
        val tv = TextView(context)
        assertFalse(applyReaderScrollToTopIfAny(tv, clearAfter = true))
    }

    @Test
    fun clearReaderScrollToTop_dropsIntentWithoutScrolling() {
        val context = RuntimeEnvironment.getApplication()
        val tv = TextView(context).apply {
            text = (1..200).joinToString("\n") { "line $it" }
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(400, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, 400, 300)
            scrollTo(0, 200)
        }
        requestReaderScrollToTop(tv)
        clearReaderScrollToTop(tv)
        assertFalse(hasReaderScrollToTop(tv))
        assertFalse(applyReaderScrollToTopIfAny(tv, clearAfter = true))
        assertEquals(200, tv.scrollY)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExpandScrollRestoreTest {

    private fun layoutTextView(tv: TextView, width: Int = 400, height: Int = 300) {
        if (tv.layoutParams == null) {
            tv.layoutParams = android.view.ViewGroup.LayoutParams(width, height)
        }
        tv.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
        )
        tv.layout(0, 0, width, height)
    }

    @Test
    fun applyStashedRestore_prepend_usesFingerprintWhenLengthDiffIsWrong() {
        val context = RuntimeEnvironment.getApplication()
        val oldSuffix = (1..60).joinToString("\n") { "suffix line $it content" }
        val tv = TextView(context).apply { text = oldSuffix }
        layoutTextView(tv)
        tv.scrollTo(0, 0)

        val anchor = captureTextViewScrollAnchor(tv, windowStart = 5_000)
        stashPendingSourceScrollRestore(
            tv = tv,
            sourceOffset = 5_000,
            windowStart = 0,
            windowEnd = 9_000,
            anchor = anchor,
        )

        // 整窗重渲染后：前置段 + 原后缀 + 额外尾部（模拟后缀渲染长度变化，使 length-diff 边界失真）。
        val prefix = (1..40).joinToString("\n") { "prefix line $it" }
        val newText = prefix + "\n" + oldSuffix + "\n\nextra rendered tail block that changes total length"
        tv.text = newText
        layoutTextView(tv)

        assertTrue(applyStashedSourceScrollRestoreIfAny(tv, renderPlainText = false))

        val suffixIdx = newText.indexOf("suffix line 1 content")
        val expectedScrollY = tv.layout.getLineTop(tv.layout.getLineForOffset(suffixIdx))
        assertEquals(expectedScrollY, tv.scrollY)
        assertFalse(hasPendingSourceScrollRestore(tv))
    }

    @Test
    fun asyncReflow_keepsViewportTopCharStable() {
        val context = RuntimeEnvironment.getApplication()
        val tv = SafeReaderTextView(context)
        tv.text = (1..120).joinToString("\n") { "行内容 $it 一些正文文字用于占位" }
        layoutTextView(tv)
        val anchorLine = 40
        val anchorChar = tv.layout.getLineStart(anchorLine)
        tv.scrollTo(0, tv.layout.getLineTop(anchorLine))
        assertEquals(anchorChar, charOffsetAtScrollTop(tv))

        // 模拟表格测量/图片图表落地等异步重排：行高整体变化，若按像素保留 scrollY 视口会漂移。
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, tv.textSize * 1.5f)
        layoutTextView(tv)

        assertEquals(anchorChar, charOffsetAtScrollTop(tv))
    }

    @Test
    fun refreshStashedExpandAnchor_capturesScrollDuringInFlightRender() {
        val context = RuntimeEnvironment.getApplication()
        val oldText = (1..80).joinToString("\n") { "chapter line $it body" }
        val tv = TextView(context).apply { text = oldText }
        layoutTextView(tv)
        tv.scrollTo(0, 0)

        val anchor = captureTextViewScrollAnchor(tv, windowStart = 3_000)
        stashPendingSourceScrollRestore(
            tv = tv,
            sourceOffset = 3_000,
            windowStart = 0,
            windowEnd = 6_000,
            anchor = anchor,
        )

        // 渲染在途时用户继续下滑。
        val scrolledY = tv.layout.getLineTop(10)
        tv.scrollTo(0, scrolledY)
        refreshStashedExpandAnchorBeforeContentSwap(tv)

        assertEquals(scrolledY, tv.getTag(R.id.reader_pending_expand_anchor_scroll_y) as? Int)
        val fp = tv.getTag(R.id.reader_pending_expand_fingerprint) as? String
        assertTrue(fp != null && fp.startsWith("chapter line 11"))
    }
}

class ReaderPagingEngineTest {

    @Test
    fun locateExpandFingerprint_matchesAcrossLineBreaks() {
        val text = "前文段落\n\n第三章 风暴\n\n正文开始了 这是内容"
        val fingerprint = "第三章 风暴 正文开始了 这是内容"
        val idx = locateExpandFingerprintNear(text, fingerprint, expectedIndex = 0)
        assertEquals(text.indexOf("第三章"), idx)
    }

    @Test
    fun locateExpandFingerprint_picksMatchNearestExpected() {
        val block = "重复标题 后续正文内容一样"
        val text = block + "\n" + "x".repeat(500) + "\n" + block
        val secondIdx = text.lastIndexOf("重复标题")
        val idx = locateExpandFingerprintNear(text, block, expectedIndex = secondIdx - 10)
        assertEquals(secondIdx, idx)
    }

    @Test
    fun locateExpandFingerprint_rejectsBlankOrTooShort() {
        assertEquals(null, locateExpandFingerprintNear("abc", null, 0))
        assertEquals(null, locateExpandFingerprintNear("abc", "ab", 0))
        assertEquals(null, locateExpandFingerprintNear("", "长度足够的指纹文本", 0))
    }

    @Test
    fun splitMarkdownToPages_splitsAtParagraphBreak() {
        val content = "a\n\n" + "b".repeat(500)
        val pages = splitMarkdownToPages(content, targetChars = 200)
        assertTrue(pages.size >= 2)
        assertEquals(0, pages.first().second)
        val joined = pages.joinToString("") { it.first }
        assertEquals(content, joined)
    }

    @Test
    fun computeReadingWindow_includesLookbehindBeforeJumpTarget() {
        val content = "x".repeat(200_000)
        val boundaries = intArrayOf(0, 150_000, 200_000)
        val (start, end) = computeReadingWindow(boundaries, charPos = 155_000, contentLen = content.length)
        assertTrue(start < 150_000)
        assertTrue(end > 155_000)
    }

    @Test
    fun previousWindowStart_alignsToEarlierChapter() {
        val boundaries = intArrayOf(0, 50_000, 100_000, 200_000)
        assertEquals(50_000, previousWindowStart(boundaries, 100_000))
        assertEquals(0, previousWindowStart(boundaries, 50_000))
    }

    @Test
    fun readingProgressForCharPos_mapsLinearly() {
        assertEquals(0.5f, readingProgressForCharPos(500, 1000), 0.001f)
    }

    @Test
    fun pdfPageIndexForTocOrBookmark_prefersTocEntryIndex() {
        val toc = listOf(
            MarkdownTocEntry(2, "第 1 页", 0),
            MarkdownTocEntry(2, "第 2 页", 100),
        )
        val idx = pdfPageIndexForTocOrBookmark(
            isPdfBook = true,
            tocEntries = toc,
            readerContent = "",
            charPos = 0,
            tocEntry = toc[1],
        )
        assertEquals(1, idx)
    }

    @Test
    fun resolveStoredCharPos_prefersCurrentPositionOverProgress() {
        val pos = resolveStoredCharPos(
            currentPosition = 800,
            readingProgress = 0.1f,
            contentLength = 1000,
            totalChars = 1000,
        )
        assertEquals(800, pos)
    }

    @Test
    fun proportionalSourceOffset_mapsRenderedOffsetLinearly() {
        val source = proportionalSourceOffset(
            windowStart = 10_000,
            windowEnd = 42_000,
            displayedLen = 5_000,
            renderedOffset = 2_500,
        )
        assertTrue(kotlin.math.abs(source - 26_000) <= 500)
    }

    @Test
    fun progressAnchorMapping_roundTripsAcrossCompressedWindow() {
        val windowStart = 10_000
        val windowEnd = 42_000
        val displayedLen = 5_000
        val sourceOffset = 26_000
        val rendered = resolveDisplayedCharOffsetForProgressRestore(
            sourceOffset = sourceOffset,
            windowStart = windowStart,
            windowEnd = windowEnd,
            displayedLen = displayedLen,
            renderPlainText = false,
        )
        val back = resolveSourceCharOffset(
            sourceContent = "x".repeat(windowEnd),
            windowStart = windowStart,
            windowEnd = windowEnd,
            displayedText = "y".repeat(displayedLen),
            renderedOffset = rendered,
            renderPlainText = false,
            tocEntries = emptyList(),
        )
        assertTrue(kotlin.math.abs(back - sourceOffset) <= 32)
    }

    @Test
    fun resolveSourceCharOffset_plainText_matchesDisplayedOffset() {
        val prefix = "intro\n\n"
        val chapter = "第三章"
        val body = "y".repeat(200)
        val full = prefix + chapter + "\n" + body
        val winStart = 0
        val displayed = full
        val sourceOffset = full.indexOf(chapter)
        val rendered = resolveDisplayedCharOffset(
            sourceContent = full,
            sourceOffset = sourceOffset,
            displayedText = displayed,
            renderPlainText = true,
            windowStart = winStart,
            tocEntries = emptyList(),
            preferredEntry = null,
        )
        val back = resolveSourceCharOffset(
            sourceContent = full,
            windowStart = winStart,
            windowEnd = full.length,
            displayedText = displayed,
            renderedOffset = rendered,
            renderPlainText = true,
            tocEntries = emptyList(),
        )
        assertEquals(sourceOffset, back)
    }

    @Test
    fun resolveDisplayedCharOffset_plainText_prefersTitleLineInWindow() {
        val prefix = "intro\n\n"
        val chapter = "第三章 风暴"
        val body = "y".repeat(200)
        val full = prefix + chapter + "\n" + body
        val winStart = 0
        val displayed = full.substring(winStart)
        val sourceOffset = full.indexOf(chapter)
        val offset = resolveDisplayedCharOffset(
            sourceContent = full,
            sourceOffset = sourceOffset,
            displayedText = displayed,
            renderPlainText = true,
            windowStart = winStart,
            tocEntries = listOf(
                MarkdownTocEntry(1, chapter, sourceOffset)
            ),
            preferredEntry = MarkdownTocEntry(1, chapter, sourceOffset),
        )
        assertEquals(sourceOffset, offset)
    }

    @Test
    fun bookOpenRestore_midChapter_landsOnReadLine_notChapterHeading() {
        val chapter1 = "# 第一章\n" + "开头段落。\n".repeat(20)
        val bodyLine = "这里是用户上次阅读到的具体段落内容，位于章节中间偏后的位置。"
        val chapter2 = "# 第二章\n" + "x\n".repeat(30) + bodyLine + "\n" + "y\n".repeat(30)
        val full = chapter1 + chapter2
        val sourceOffset = full.indexOf(bodyLine) + 8
        val rendered = "第一章\n" + "开头段落。\n".repeat(20) +
            "第二章\n" + "x\n".repeat(30) + bodyLine + "\n" + "y\n".repeat(30)
        val toc = listOf(
            MarkdownTocEntry(1, "第一章", 0),
            MarkdownTocEntry(1, "第二章", full.indexOf("# 第二章")),
        )
        val offset = resolveDisplayedCharOffsetForBookOpenRestore(
            sourceContent = full,
            sourceOffset = sourceOffset,
            displayedText = rendered,
            renderPlainText = false,
            windowStart = 0,
            windowEnd = full.length,
            tocEntries = toc,
        )
        // 精确到存储的字符位置本身（段内 +8），而非该行行首。
        assertEquals(rendered.indexOf(bodyLine) + 8, offset)
    }

    @Test
    fun resolveSourceCharOffset_markdown_locatesRenderedSnippetInSource() {
        // 前置大量「渲染后大幅缩短」的内容（长 URL 链接），使比例映射严重偏差。
        val junk = "[链接](https://example.com/very/long/url/path/that/renders/short/12345)\n".repeat(20)
        val body = "这里是视口顶部正在阅读的一段具体正文内容，应当精确映射回源码坐标。"
        val source = junk + body + "\n" + "尾部内容行。\n".repeat(10)
        val rendered = "链接\n".repeat(20) + body + "\n" + "尾部内容行。\n".repeat(10)
        val result = resolveSourceCharOffset(
            sourceContent = source,
            windowStart = 0,
            windowEnd = source.length,
            displayedText = rendered,
            renderedOffset = rendered.indexOf(body),
            renderPlainText = false,
            tocEntries = emptyList(),
        )
        assertEquals(source.indexOf(body), result)
        // 对照：纯比例映射会偏出很远。
        val proportional = proportionalSourceOffset(0, source.length, rendered.length, rendered.indexOf(body))
        assertTrue(kotlin.math.abs(proportional - source.indexOf(body)) > 200)
    }

    @Test
    fun progressSaveThenRestore_roundTripsToSameRenderedLine() {
        val junk = "[链接](https://example.com/very/long/url/path/that/renders/short/12345)\n".repeat(20)
        val body = "往返测试的正文内容，保存后重新进入应当回到同一行。"
        val source = junk + body + "\n" + "尾部内容行。\n".repeat(10)
        val rendered = "链接\n".repeat(20) + body + "\n" + "尾部内容行。\n".repeat(10)
        val renderedTop = rendered.indexOf(body)
        val savedSourcePos = resolveSourceCharOffset(
            sourceContent = source,
            windowStart = 0,
            windowEnd = source.length,
            displayedText = rendered,
            renderedOffset = renderedTop,
            renderPlainText = false,
            tocEntries = emptyList(),
        )
        val restored = resolveDisplayedCharOffsetForBookOpenRestore(
            sourceContent = source,
            sourceOffset = savedSourcePos,
            displayedText = rendered,
            renderPlainText = false,
            windowStart = 0,
            windowEnd = source.length,
            tocEntries = emptyList(),
        )
        assertEquals(renderedTop, restored)
    }

    @Test
    fun savedPosition_withPreview_matchesBookmarkAndOpenBookPath() {
        val junk = "[链接](https://example.com/very/long/url/path/that/renders/short/12345)\n".repeat(20)
        val body = "书签与进度共用预览定位，应当命中同一视口顶字符。"
        val source = junk + body + "\n" + "尾部内容行。\n".repeat(10)
        val rendered = "链接\n".repeat(20) + body + "\n" + "尾部内容行。\n".repeat(10)
        val renderedTop = rendered.indexOf(body)
        val preview = body.take(48)
        val sourcePos = source.indexOf(body) + 5
        val openBook = resolveDisplayedCharOffsetForSavedPosition(
            sourceContent = source,
            sourceOffset = sourcePos,
            displayedText = rendered,
            renderPlainText = false,
            windowStart = 0,
            windowEnd = source.length,
            tocEntries = emptyList(),
            preferredText = preview,
        )
        val bookmark = resolveDisplayedCharOffsetForSavedPosition(
            sourceContent = source,
            sourceOffset = sourcePos,
            displayedText = rendered,
            renderPlainText = false,
            windowStart = 0,
            windowEnd = source.length,
            tocEntries = emptyList(),
            preferredText = preview,
        )
        assertEquals(openBook, bookmark)
        assertEquals(renderedTop, openBook)
    }

    @Test
    fun bookOpenRestore_atChapterHeading_snapsToHeading() {
        val chapter1 = "# 第一章\n" + "内容行。\n".repeat(10)
        val chapter2Title = "# 第二章 进阶"
        val full = chapter1 + chapter2Title + "\n正文开始"
        val sourceOffset = full.indexOf(chapter2Title)
        val rendered = "第一章\n" + "内容行。\n".repeat(10) + "第二章 进阶\n正文开始"
        val toc = listOf(
            MarkdownTocEntry(1, "第一章", 0),
            MarkdownTocEntry(1, "第二章 进阶", sourceOffset),
        )
        val offset = resolveDisplayedCharOffsetForBookOpenRestore(
            sourceContent = full,
            sourceOffset = sourceOffset,
            displayedText = rendered,
            renderPlainText = false,
            windowStart = 0,
            windowEnd = full.length,
            tocEntries = toc,
        )
        assertEquals(rendered.indexOf("第二章 进阶"), offset)
    }

    @Test
    fun bookOpenRestore_plainText_isExact() {
        val full = "第一行\n第二行\n第三行\n"
        val sourceOffset = full.indexOf("第三行")
        val offset = resolveDisplayedCharOffsetForBookOpenRestore(
            sourceContent = full,
            sourceOffset = sourceOffset,
            displayedText = full,
            renderPlainText = true,
            windowStart = 0,
            windowEnd = full.length,
            tocEntries = emptyList(),
        )
        assertEquals(sourceOffset, offset)
    }

    @Test
    fun resolveDisplayedCharOffset_markdown_tocJumpPrefersTitleOverProportional() {
        val section1 = "# 一、标题\n" + "x\n".repeat(40)
        val latexBlock = "上标：\$x^2\$" + " ".repeat(120) + "\n下标：\$y_1\$" + " ".repeat(120) + "\n"
        val section2Title = "# 二、基础文本样式"
        val full = section1 + latexBlock + section2Title + "\n正文"
        val sourceOffset = full.indexOf(section2Title)
        val rendered = "一、标题\n" + "x\n".repeat(40) +
            "上标：x²\n下标：y₁\n" +
            "二、基础文本样式\n正文"
        val toc = listOf(
            MarkdownTocEntry(1, "一、标题", 0),
            MarkdownTocEntry(1, "二、基础文本样式", sourceOffset),
        )
        val expected = rendered.indexOf("二、基础文本样式")
        val headingOffset = resolveDisplayedCharOffset(
            sourceContent = full,
            sourceOffset = sourceOffset,
            displayedText = rendered,
            renderPlainText = false,
            windowStart = 0,
            windowEnd = full.length,
            tocEntries = toc,
            preferredEntry = toc[1],
        )
        val proportional = resolveDisplayedCharOffsetForProgressRestore(
            sourceOffset = sourceOffset,
            windowStart = 0,
            windowEnd = full.length,
            displayedLen = rendered.length,
            renderPlainText = false,
        )
        assertEquals(expected, headingOffset)
        assertTrue(proportional != expected)
    }
}
