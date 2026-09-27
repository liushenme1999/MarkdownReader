package space.liushenme.markdownreader.ui.screens.reader

import android.text.SpannableString
import android.text.Spanned
import android.widget.TextView
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.spans.HeadingSpan
import space.liushenme.markdownreader.markdown.ReaderMarkwonFactory
import space.liushenme.markdownreader.markdown.ReaderScrollableCodeBlockSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.model.HighlightStyle
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ApplyHighlightsToRenderedTextTest {

    @Test
    fun applyHighlights_restoresCodeBlockOnFreshRenderedTextView() {
        val context = RuntimeEnvironment.getApplication()
        val source = "前文\n\n```kotlin\nval value = 1\n```\n\n后文"
        val rendered = ReaderMarkwonFactory.create(context).toMarkdown(source) as Spanned
        val start = source.indexOf("val value")
        val textView = TextView(context).apply {
            setText(rendered, TextView.BufferType.SPANNABLE)
        }

        applyHighlightsToRenderedText(
            textView = textView,
            highlights = listOf(
                HighlightEntity(
                    id = 17L,
                    bookId = 1L,
                    startPosition = start,
                    endPosition = start + "val value = 1".length,
                    highlightedText = "val value = 1",
                    color = 0xFFFFFF00.toInt(),
                    createTime = Date(),
                ),
            ),
            highlightColorArgb = 0xFFFFFF00.toInt(),
            sourceContentLength = source.length,
            sourceText = source,
        )

        val codeSpan = (textView.text as Spanned)
            .getSpans(0, textView.text.length, ReaderScrollableCodeBlockSpan::class.java)
            .single()
        assertEquals(1, codeSpan.highlightRangesForTest().size)
        assertEquals(17L, codeSpan.highlightRangesForTest().single().highlightId)
        assertEquals("val value = 1", codeSpan.contentSliceForTest(
            codeSpan.highlightRangesForTest().single().start,
            codeSpan.highlightRangesForTest().single().end,
        ))
    }

    @Test
    fun applyHighlights_usesPerHighlightColor() {
        val context = RuntimeEnvironment.getApplication()
        val textView = TextView(context).apply {
            setText(SpannableString("前面选中后面"), TextView.BufferType.SPANNABLE)
        }
        val green = 0xFF00FF00.toInt()
        val themeYellow = 0xFFFFFF00.toInt()
        val highlights = listOf(
            HighlightEntity(
                bookId = 1L,
                startPosition = 2,
                endPosition = 4,
                highlightedText = "选中",
                color = green,
                createTime = Date(),
            ),
        )
        applyHighlightsToRenderedText(textView, highlights, themeYellow, sourceContentLength = 6)
        val spanned = textView.text as SpannableString
        val spans = spanned.getSpans(0, spanned.length, HighlightBackgroundSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(green, spans[0].backgroundColor)
        assertEquals(2, spanned.getSpanStart(spans[0]))
        assertEquals(4, spanned.getSpanEnd(spans[0]))
    }

    @Test
    fun applyHighlights_fallsBackToThemeColorWhenAlphaTooLow() {
        val context = RuntimeEnvironment.getApplication()
        val textView = TextView(context).apply {
            setText(SpannableString("前面选中后面"), TextView.BufferType.SPANNABLE)
        }
        val themeYellow = 0xFFFFFF00.toInt()
        val highlights = listOf(
            HighlightEntity(
                bookId = 1L,
                startPosition = 2,
                endPosition = 4,
                highlightedText = "选中",
                color = 0x00000000,
                createTime = Date(),
            ),
        )
        applyHighlightsToRenderedText(textView, highlights, themeYellow, sourceContentLength = 6)
        val spanned = textView.text as SpannableString
        val spans = spanned.getSpans(0, spanned.length, HighlightBackgroundSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(themeYellow, spans[0].backgroundColor)
    }

    @Test
    fun applyHighlights_onlyMarksOccurrenceNearSourcePosition() {
        val context = RuntimeEnvironment.getApplication()
        // 同文两次「选中」，只应标第二处（源码相对位置 4..6）
        val body = "选中一二选中"
        val textView = TextView(context).apply {
            setText(SpannableString(body), TextView.BufferType.SPANNABLE)
        }
        val yellow = 0xFFFFFF00.toInt()
        val highlights = listOf(
            HighlightEntity(
                bookId = 1L,
                startPosition = 4,
                endPosition = 6,
                highlightedText = "选中",
                color = yellow,
                createTime = Date(),
            ),
        )
        applyHighlightsToRenderedText(
            textView = textView,
            highlights = highlights,
            highlightColorArgb = yellow,
            sourceContentLength = body.length,
        )
        val spanned = textView.text as SpannableString
        val spans = spanned.getSpans(0, spanned.length, HighlightBackgroundSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(4, spanned.getSpanStart(spans[0]))
        assertEquals(6, spanned.getSpanEnd(spans[0]))
    }

    @Test
    fun applyHighlights_underlineStyle_usesUnderlineSpan() {
        val context = RuntimeEnvironment.getApplication()
        val textView = TextView(context).apply {
            setText(SpannableString("前面选中后面"), TextView.BufferType.SPANNABLE)
        }
        val yellow = 0xFFFFFF00.toInt()
        applyHighlightsToRenderedText(
            textView = textView,
            highlights = listOf(
                HighlightEntity(
                    bookId = 1L,
                    startPosition = 2,
                    endPosition = 4,
                    highlightedText = "选中",
                    color = yellow,
                    style = HighlightStyle.Underline.storageKey,
                    createTime = Date(),
                ),
            ),
            highlightColorArgb = yellow,
            sourceContentLength = 6,
        )
        val spanned = textView.text as SpannableString
        val underlines = spanned.getSpans(0, spanned.length, HighlightUnderlineSpan::class.java)
        val backgrounds = spanned.getSpans(0, spanned.length, HighlightBackgroundSpan::class.java)
        assertEquals(1, underlines.size)
        assertEquals(0, backgrounds.size)
    }

    @Test
    fun resolveHighlightDisplayedRange_picksClosestMatch() {
        // 三处「选中」：0、4、8；hint 靠近末尾时应命中第三处
        val displayed = "选中AA选中BB选中"
        val highlight = HighlightEntity(
            bookId = 1L,
            startPosition = 9,
            endPosition = 11,
            highlightedText = "选中",
            color = 0xFFFFFF00.toInt(),
            createTime = Date(),
        )
        val range = resolveHighlightDisplayedRange(
            displayed = displayed,
            highlight = highlight,
            sourceContentLength = displayed.length,
        )
        assertEquals(8 until 10, range)
    }

    @Test
    fun applyHighlights_attachesSnippetToCodeBlockSpan() {
        val context = RuntimeEnvironment.getApplication()
        space.liushenme.markdownreader.markdown.ReaderCodeBlockSettings.wrapEnabled = true
        space.liushenme.markdownreader.markdown.ReaderCodeBlockSettings.viewportWidthPx = 400
        val markwon = space.liushenme.markdownreader.markdown.ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("```kotlin\nval hello = 1\n```")
        val textView = TextView(context).apply {
            setText(rendered, TextView.BufferType.SPANNABLE)
        }
        val yellow = 0xFFFFFF00.toInt()
        applyHighlightsToRenderedText(
            textView,
            listOf(
                HighlightEntity(
                    bookId = 1L,
                    startPosition = 0,
                    endPosition = 5,
                    highlightedText = "hello",
                    color = yellow,
                    createTime = Date(),
                ),
            ),
            highlightColorArgb = yellow,
            sourceContentLength = 40,
        )
        val span = (textView.text as android.text.Spanned).getSpans(
            0,
            textView.text.length,
            space.liushenme.markdownreader.markdown.ReaderScrollableCodeBlockSpan::class.java,
        ).single()
        val ranges = span.highlightRangesForTest()
        assertEquals(1, ranges.size)
        assertEquals("hello", span.rawCode.substring(ranges[0].start, ranges[0].end))
    }

    @Test
    fun persistedHighlight_insideCodeBlockSurvivesLargeMarkdownCompression() {
        val context = RuntimeEnvironment.getApplication()
        space.liushenme.markdownreader.markdown.ReaderCodeBlockSettings.wrapEnabled = true
        space.liushenme.markdownreader.markdown.ReaderCodeBlockSettings.viewportWidthPx = 400
        val markwon = space.liushenme.markdownreader.markdown.ReaderMarkwonFactory.create(context)
        val snippet = "准确率奖励"
        val prefix = "前文段落。".repeat(400)
        val compressedMarkup = "<div data-padding=\"${"x".repeat(4_000)}\"></div>"
        val code = """
            # 准确率奖励
            print("准确率奖励:", result)
        """.trimIndent()
        val source = "$prefix\n\n$compressedMarkup\n\n```python\n$code\n```\n${"后文。".repeat(400)}"
        val rendered = markwon.toMarkdown(source)
        val textView = TextView(context).apply {
            setText(rendered, TextView.BufferType.SPANNABLE)
        }
        val sourceStart = source.indexOf(snippet, source.indexOf("```python"))
        val highlight = HighlightEntity(
            id = 91L,
            bookId = 1L,
            startPosition = sourceStart,
            endPosition = sourceStart + snippet.length,
            highlightedText = snippet,
            color = 0xFFFFFF00.toInt(),
            style = HighlightStyle.Wavy.storageKey,
            createTime = Date(),
        )

        applyHighlightsToRenderedText(
            textView = textView,
            highlights = listOf(highlight),
            highlightColorArgb = 0xFFFFFF00.toInt(),
            sourceContentLength = source.length,
            sourceText = source,
        )

        val codeSpans = (textView.text as Spanned).getSpans(
            0,
            textView.text.length,
            space.liushenme.markdownreader.markdown.ReaderScrollableCodeBlockSpan::class.java,
        )
        assertEquals("rendered code block count", 1, codeSpans.size)
        val codeSpan = codeSpans.single()
        val ranges = codeSpan.highlightRangesForTest()
        assertEquals(1, ranges.size)
        assertEquals(91L, ranges.single().highlightId)
        assertEquals(snippet, codeSpan.rawCode.substring(ranges.single().start, ranges.single().end))
    }

    @Test
    fun persistedHighlight_insideCodeBlockRestoresWhenWindowStartsInsideFence() {
        val context = RuntimeEnvironment.getApplication()
        space.liushenme.markdownreader.markdown.ReaderCodeBlockSettings.wrapEnabled = true
        space.liushenme.markdownreader.markdown.ReaderCodeBlockSettings.viewportWidthPx = 400
        val markwon = space.liushenme.markdownreader.markdown.ReaderMarkwonFactory.create(context)
        val snippet = "准确率奖励"
        val rendered = markwon.toMarkdown("```python\nprint(\"准确率奖励:\", result)\n```")
        val textView = TextView(context).apply {
            setText(rendered, TextView.BufferType.SPANNABLE)
        }
        // 模拟窗口切在围栏内部：只有代码正文，看不到 ``` 开栏。
        val sourceWindow = "print(\"准确率奖励:\", result)\n"
        val start = sourceWindow.indexOf(snippet)
        val highlight = HighlightEntity(
            id = 92L,
            bookId = 1L,
            startPosition = start,
            endPosition = start + snippet.length,
            highlightedText = snippet,
            color = 0xFFFFFF00.toInt(),
            createTime = Date(),
        )

        applyHighlightsToRenderedText(
            textView = textView,
            highlights = listOf(highlight),
            highlightColorArgb = 0xFFFFFF00.toInt(),
            sourceContentLength = sourceWindow.length,
            sourceText = sourceWindow,
        )

        val codeSpan = (textView.text as Spanned).getSpans(
            0,
            textView.text.length,
            space.liushenme.markdownreader.markdown.ReaderScrollableCodeBlockSpan::class.java,
        ).single()
        assertEquals(listOf(92L), codeSpan.highlightRangesForTest().map { it.highlightId })
    }

    @Test
    fun resolveHighlightDisplayedRange_doesNotJumpToDistantDuplicate() {
        val displayed = "选中" + "甲".repeat(500)
        val highlight = HighlightEntity(
            bookId = 1L,
            startPosition = displayed.length - 1,
            endPosition = displayed.length + 1,
            highlightedText = "选中",
            color = 0xFFFFFF00.toInt(),
            createTime = Date(),
        )
        assertNull(
            resolveHighlightDisplayedRange(
                displayed = displayed,
                highlight = highlight,
                sourceContentLength = displayed.length,
            ),
        )
    }

    @Test
    fun resolveHighlightDisplayedRange_secondOccurrenceFollowsSourceContext() {
        val source = "**选中**后面独特甲" + "z".repeat(400) + "**选中**后面独特乙"
        val displayed = "选中后面独特甲" + "z".repeat(400) + "选中后面独特乙"
        val sourceStart = source.lastIndexOf("选中")
        val displayedStart = displayed.lastIndexOf("选中")
        val highlight = HighlightEntity(
            bookId = 1L,
            startPosition = sourceStart,
            endPosition = sourceStart + 2,
            highlightedText = "选中",
            color = 0xFFFFFF00.toInt(),
            createTime = Date(),
        )
        assertEquals(
            displayedStart until displayedStart + 2,
            resolveHighlightDisplayedRange(
                displayed = displayed,
                highlight = highlight,
                sourceContentLength = source.length,
                sourceText = source,
            ),
        )
    }

    @Test
    fun resolveHighlightDisplayedRange_sourceAnchoredMatchSurvivesRenderShift() {
        val snippet = "正文划线"
        val source = "a".repeat(800) + snippet
        val displayed = snippet + "b".repeat(800)
        val start = source.indexOf(snippet)
        val highlight = HighlightEntity(
            bookId = 1L,
            startPosition = start,
            endPosition = start + snippet.length,
            highlightedText = snippet,
            color = 0xFFFFFF00.toInt(),
            createTime = Date(),
        )
        assertEquals(
            0 until snippet.length,
            resolveHighlightDisplayedRange(
                displayed = displayed,
                highlight = highlight,
                sourceContentLength = source.length,
                sourceText = source,
            ),
        )
    }

    @Test
    fun persistedHighlight_restoresAfterLargeHtmlCompressionWithDuplicates() {
        val snippet = "准确率奖励"
        val hiddenHtml = "<div data-padding=\"${"x".repeat(3_000)}\"></div>"
        val tail = "后续正文".repeat(500)
        val source = "$snippet 早期说明\n$hiddenHtml\n<strong>（1）$snippet</strong>\n\n" +
            "$snippet(AccuracyReward)是最基础的奖励函数。$tail"
        val displayed = "$snippet 早期说明\n（1）$snippet\n\n" +
            "$snippet(AccuracyReward)是最基础的奖励函数。$tail"
        val sourceStart = source.lastIndexOf(snippet)
        val displayedStart = displayed.lastIndexOf(snippet, displayed.indexOf("AccuracyReward"))
        val highlight = HighlightEntity(
            id = 17L,
            bookId = 1L,
            startPosition = sourceStart,
            endPosition = sourceStart + snippet.length,
            highlightedText = snippet,
            color = 0xFFFFFF00.toInt(),
            createTime = Date(),
        )

        assertEquals(
            displayedStart until displayedStart + snippet.length,
            resolveHighlightDisplayedRange(
                displayed = displayed,
                highlight = highlight,
                sourceContentLength = source.length,
                sourceText = source,
            ),
        )

        val context = RuntimeEnvironment.getApplication()
        val textView = TextView(context).apply {
            setText(SpannableString(displayed), TextView.BufferType.SPANNABLE)
        }
        applyHighlightsToRenderedText(
            textView = textView,
            highlights = listOf(highlight),
            highlightColorArgb = 0xFFFFFF00.toInt(),
            sourceContentLength = source.length,
            sourceText = source,
        )
        val painted = (textView.text as Spanned).getSpans(
            0,
            textView.text.length,
            HighlightBackgroundSpan::class.java,
        )
        assertEquals(1, painted.size)
        assertEquals(17L, painted.single().highlightId)
        assertEquals(displayedStart, (textView.text as Spanned).getSpanStart(painted.single()))
    }

    @Test
    fun closestSnippetIndexNear_ignoresTextOutsideRadius() {
        val text = "选中" + "a".repeat(500) + "选中"
        val second = text.lastIndexOf("选中")
        assertEquals(second, closestSnippetIndexNear(text, "选中", second + 1, 80))
        assertNull(closestSnippetIndexNear(text, "选中", 250, 40))
    }

    @Test
    fun refresh_keepsVisibleHighlightWhenSourceProjectionMisses() {
        val context = RuntimeEnvironment.getApplication()
        val textView = SafeReaderTextView(context).apply {
            setText(SpannableString("前面选中后面"), TextView.BufferType.SPANNABLE)
        }
        val yellow = 0xFFFFFF00.toInt()
        applyHighlightDecorationAtRange(
            textView = textView,
            start = 2,
            end = 4,
            colorArgb = yellow,
            style = HighlightStyle.Background,
            highlightId = 5L,
        )
        val highlight = HighlightEntity(
            id = 5L,
            bookId = 1L,
            startPosition = 800,
            endPosition = 802,
            highlightedText = "选中",
            color = yellow,
            createTime = Date(),
        )
        textView.retainedHighlights = listOf(highlight)
        refreshReaderHighlightSpans(
            textView = textView,
            highlights = emptyList(),
            highlightColorArgb = yellow,
            sourceContentLength = 20,
            highlightSig = "miss",
            sourceText = "别处",
        )
        val spanned = textView.text as android.text.Spanned
        val spans = spanned.getSpans(0, spanned.length, HighlightBackgroundSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(2, spanned.getSpanStart(spans[0]))
        assertEquals(4, spanned.getSpanEnd(spans[0]))
        assertEquals(5L, spans[0].highlightId)
    }

    @Test
    fun refresh_keepsActiveNegativeDraftWhenDatabaseListIsEmpty() {
        val context = RuntimeEnvironment.getApplication()
        val textView = SafeReaderTextView(context).apply {
            setText(SpannableString("前面选中后面"), TextView.BufferType.SPANNABLE)
            registerDraftHighlight(-1L)
        }
        val yellow = 0xFFFFFF00.toInt()
        applyHighlightDecorationAtRange(
            textView = textView,
            start = 2,
            end = 4,
            colorArgb = yellow,
            style = HighlightStyle.Background,
            highlightId = -1L,
        )

        refreshReaderHighlightSpans(
            textView = textView,
            highlights = emptyList(),
            highlightColorArgb = yellow,
            sourceContentLength = 6,
            highlightSig = "draft",
        )

        val spans = (textView.text as Spanned).getSpans(
            0,
            textView.text.length,
            HighlightBackgroundSpan::class.java,
        )
        assertEquals(1, spans.size)
        assertEquals(-1L, spans.single().highlightId)
        assertEquals(2, (textView.text as Spanned).getSpanStart(spans.single()))
    }

    @Test
    fun draftRebindsToDatabaseIdAndCanBeLocatedById() {
        val context = RuntimeEnvironment.getApplication()
        val textView = SafeReaderTextView(context).apply {
            setText(SpannableString("相同文字和相同文字"), TextView.BufferType.SPANNABLE)
            registerDraftHighlight(-2L)
        }
        applyHighlightDecorationAtRange(
            textView = textView,
            start = 5,
            end = 9,
            colorArgb = 0xFFFFFF00.toInt(),
            style = HighlightStyle.Underline,
            highlightId = -2L,
        )

        rebindReaderHighlightDecorationId(textView, -2L, 42L)
        textView.unregisterDraftHighlight(-2L)

        assertEquals(5, displayedOffsetForHighlightId(textView, 42L))
        val spans = (textView.text as Spanned).getSpans(
            0,
            textView.text.length,
            HighlightUnderlineSpan::class.java,
        )
        assertEquals(listOf(42L), spans.map { it.highlightId })
    }

    @Test
    fun failedDraftRemovalDoesNotRemoveOverlappingPersistedHighlight() {
        val context = RuntimeEnvironment.getApplication()
        val textView = SafeReaderTextView(context).apply {
            setText(SpannableString("重叠划线范围"), TextView.BufferType.SPANNABLE)
            registerDraftHighlight(-3L)
        }
        val yellow = 0xFFFFFF00.toInt()
        applyHighlightDecorationAtRange(
            textView,
            start = 0,
            end = 4,
            colorArgb = yellow,
            style = HighlightStyle.Background,
            highlightId = 7L,
        )
        applyHighlightDecorationAtRange(
            textView,
            start = 2,
            end = 6,
            colorArgb = yellow,
            style = HighlightStyle.Wavy,
            highlightId = -3L,
        )

        removeReaderHighlightDecorationById(textView, -3L)
        textView.unregisterDraftHighlight(-3L)

        val text = textView.text as Spanned
        assertEquals(1, text.getSpans(0, text.length, HighlightBackgroundSpan::class.java).size)
        assertEquals(0, text.getSpans(0, text.length, HighlightUnderlineSpan::class.java).size)
        assertEquals(0, displayedOffsetForHighlightId(textView, 7L))
    }

    @Test
    fun cancelledDraftStateSurvivesSelectionUiCleanupUntilRoomReturns() {
        val context = RuntimeEnvironment.getApplication()
        val textView = SafeReaderTextView(context)
        textView.registerDraftHighlight(-4L)

        textView.cancelDraftHighlight(-4L)

        assertEquals(false, textView.isDraftHighlightActive(-4L))
        assertEquals(true, textView.consumeDraftHighlightCancellation(-4L))
        assertEquals(false, textView.consumeDraftHighlightCancellation(-4L))
    }

    @Test
    fun displayedOffsetForHighlightId_selectsRequestedDuplicate() {
        val context = RuntimeEnvironment.getApplication()
        val textView = TextView(context).apply {
            setText(SpannableString("重复文字--重复文字"), TextView.BufferType.SPANNABLE)
        }
        applyHighlightDecorationAtRange(
            textView,
            start = 0,
            end = 4,
            colorArgb = 0xFFFFFF00.toInt(),
            style = HighlightStyle.Background,
            highlightId = 10L,
        )
        applyHighlightDecorationAtRange(
            textView,
            start = 6,
            end = 10,
            colorArgb = 0xFFFFFF00.toInt(),
            style = HighlightStyle.Background,
            highlightId = 11L,
        )

        assertEquals(0, displayedOffsetForHighlightId(textView, 10L))
        assertEquals(6, displayedOffsetForHighlightId(textView, 11L))
    }

    @Test
    fun refresh_dropsDecorationWhenHighlightWasDeleted() {
        val context = RuntimeEnvironment.getApplication()
        val textView = SafeReaderTextView(context).apply {
            setText(SpannableString("前面选中后面"), TextView.BufferType.SPANNABLE)
        }
        val yellow = 0xFFFFFF00.toInt()
        applyHighlightDecorationAtRange(
            textView = textView,
            start = 2,
            end = 4,
            colorArgb = yellow,
            style = HighlightStyle.Background,
        )
        textView.retainedHighlights = emptyList()
        refreshReaderHighlightSpans(
            textView = textView,
            highlights = emptyList(),
            highlightColorArgb = yellow,
            sourceContentLength = 6,
            highlightSig = "gone",
        )
        val spanned = textView.text as android.text.Spanned
        val spans = spanned.getSpans(0, spanned.length, HighlightBackgroundSpan::class.java)
        assertEquals(0, spans.size)
    }

    @Test
    fun readerHighlightRangeCovering_returnsSpanThatContainsOffset() {
        val text = SpannableString("前面选中后面")
        text.setSpan(HighlightBackgroundSpan(0xFFFFFF00.toInt(), highlightId = 7L), 2, 4, 0)
        assertEquals(2 until 4, readerHighlightRangeCovering(text, 3))
        assertNull(readerHighlightRangeCovering(text, 0))
        assertEquals(7L, readerHighlightIdCovering(text, 2, 4))
    }

    @Test
    fun headingHighlight_paintsHeadingNotBodyDuplicate() {
        val context = RuntimeEnvironment.getApplication()
        val title = "准确率奖励"
        val displayed = SpannableString("$title\n\n正文里的$title")
        displayed.setSpan(
            HeadingSpan(MarkwonTheme.create(context), 2),
            0,
            title.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        val textView = TextView(context).apply {
            setText(displayed, TextView.BufferType.SPANNABLE)
        }
        val source = "## $title\n\n正文里的$title。\n"
        val titleStart = source.indexOf(title)
        applyHighlightsToRenderedText(
            textView = textView,
            highlights = listOf(
                HighlightEntity(
                    bookId = 1L,
                    startPosition = titleStart,
                    endPosition = titleStart + title.length,
                    highlightedText = title,
                    color = 0xFFFFFF00.toInt(),
                    createTime = Date(),
                ),
            ),
            highlightColorArgb = 0xFFFFFF00.toInt(),
            sourceContentLength = source.length,
            sourceText = source,
        )
        val spanned = textView.text as SpannableString
        val spans = spanned.getSpans(0, spanned.length, HighlightBackgroundSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(0, spanned.getSpanStart(spans[0]))
        assertEquals(title.length, spanned.getSpanEnd(spans[0]))
    }

    @Test
    fun bodyDuplicate_doesNotPaintTheHeading() {
        val context = RuntimeEnvironment.getApplication()
        val title = "准确率奖励"
        val displayed = SpannableString("$title\n\n正文里的$title")
        displayed.setSpan(
            HeadingSpan(MarkwonTheme.create(context), 2),
            0,
            title.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        val textView = TextView(context).apply {
            setText(displayed, TextView.BufferType.SPANNABLE)
        }
        val source = "## $title\n\n正文里的$title。\n"
        val bodyStart = source.lastIndexOf(title)
        applyHighlightsToRenderedText(
            textView = textView,
            highlights = listOf(
                HighlightEntity(
                    bookId = 1L,
                    startPosition = bodyStart,
                    endPosition = bodyStart + title.length,
                    highlightedText = title,
                    color = 0xFFFFFF00.toInt(),
                    createTime = Date(),
                ),
            ),
            highlightColorArgb = 0xFFFFFF00.toInt(),
            sourceContentLength = source.length,
            sourceText = source,
        )
        val spanned = textView.text as SpannableString
        val spans = spanned.getSpans(0, spanned.length, HighlightBackgroundSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals(displayed.lastIndexOf(title), spanned.getSpanStart(spans[0]))
    }

    @Test
    fun displayedHeadingSelection_savesHeadingLineNotBody() {
        val context = RuntimeEnvironment.getApplication()
        val title = "准确率奖励"
        val displayed = SpannableString("$title\n\n正文里的$title")
        displayed.setSpan(
            HeadingSpan(MarkwonTheme.create(context), 2),
            0,
            title.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        val source = "## $title\n\n正文里的$title。\n"
        val toc = listOf(MarkdownTocEntry(level = 2, title = title, sourceOffset = 0))
        val span = sourceSpanForDisplayedHeading(
            displayed = displayed,
            displayedStart = 0,
            displayedEnd = title.length,
            selectedText = title,
            source = source,
            windowStart = 0,
            windowEnd = source.length,
            toc = toc,
        )
        assertEquals(source.indexOf(title), span?.first)
        assertEquals(source.indexOf(title) + title.length, span?.second)
        assertNull(atxHeadingCovering(source, source.lastIndexOf(title)))
    }

    @Test
    fun headingSource_doesNotPaintBodyWhenHeadingSpanIsMissing() {
        val context = RuntimeEnvironment.getApplication()
        val title = "准确率奖励"
        val displayed = SpannableString("前文\n正文里的$title")
        val textView = TextView(context).apply {
            setText(displayed, TextView.BufferType.SPANNABLE)
        }
        val source = "## $title\n\n前文\n正文里的$title"
        val titleStart = source.indexOf(title)
        applyHighlightsToRenderedText(
            textView = textView,
            highlights = listOf(
                HighlightEntity(
                    bookId = 1L,
                    startPosition = titleStart,
                    endPosition = titleStart + title.length,
                    highlightedText = title,
                    color = 0xFFFFFF00.toInt(),
                    createTime = Date(),
                ),
            ),
            highlightColorArgb = 0xFFFFFF00.toInt(),
            sourceContentLength = source.length,
            sourceText = source,
        )
        val spanned = textView.text as SpannableString
        val spans = spanned.getSpans(0, spanned.length, HighlightBackgroundSpan::class.java)
        assertEquals(0, spans.size)
    }

    @Test
    fun headingWithoutHeadingSpan_paintsOnlyExactRenderedHeadingLine() {
        val context = RuntimeEnvironment.getApplication()
        val title = "小标题"
        val displayed = SpannableString("$title\n\n正文里的$title")
        val textView = TextView(context).apply {
            setText(displayed, TextView.BufferType.SPANNABLE)
        }
        val source = "## $title\n\n正文里的$title"
        val titleStart = source.indexOf(title)
        applyHighlightsToRenderedText(
            textView = textView,
            highlights = listOf(
                HighlightEntity(
                    id = 18L,
                    bookId = 1L,
                    startPosition = titleStart,
                    endPosition = titleStart + title.length,
                    highlightedText = title,
                    color = 0xFFFFFF00.toInt(),
                    createTime = Date(),
                ),
            ),
            highlightColorArgb = 0xFFFFFF00.toInt(),
            sourceContentLength = source.length,
            sourceText = source,
        )
        val spans = (textView.text as Spanned).getSpans(
            0,
            textView.text.length,
            HighlightBackgroundSpan::class.java,
        )
        assertEquals(1, spans.size)
        assertEquals(0, (textView.text as Spanned).getSpanStart(spans.single()))
        assertEquals(title.length, (textView.text as Spanned).getSpanEnd(spans.single()))
    }

    @Test
    fun duplicateHeading_rankUsesCurrentWindowNotEarlierBookTitles() {
        val context = RuntimeEnvironment.getApplication()
        val title = "准确率奖励"
        val before = "## $title\n\n书首的同名标题。\n"
        val window = "## $title\n\n窗口里的第一处。\n\n## $title\n\n窗口里的第二处。\n"
        val source = before + window
        val windowStart = before.length
        val firstInWindow = source.indexOf(title, windowStart)
        val displayed = SpannableString("$title\n\n窗口里的第一处。\n\n$title\n\n窗口里的第二处。")
        val theme = MarkwonTheme.create(context)
        displayed.setSpan(HeadingSpan(theme, 2), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val secondSpan = displayed.indexOf(title, title.length)
        displayed.setSpan(
            HeadingSpan(theme, 2),
            secondSpan,
            secondSpan + title.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        val range = displayedRangeForSourceHeading(
            displayed = displayed,
            source = source,
            sourceStart = firstInWindow,
            snippet = title,
            sourceWindowStart = windowStart,
        )
        assertEquals(0, range?.first)
        assertEquals(title.length, range?.last?.plus(1))
    }

    @Test
    fun savedHeading_jumpsToHeading_savedBody_jumpsToBody() {
        val context = RuntimeEnvironment.getApplication()
        val title = "准确率奖励"
        val source = "## $title\n\n前面也有$title。\n\n" + "垫".repeat(40) + "\n\n后面才是$title。\n"
        val displayed = SpannableString("$title\n\n前面也有$title。\n\n" + "垫".repeat(40) + "\n\n后面才是$title。")
        displayed.setSpan(
            HeadingSpan(MarkwonTheme.create(context), 2),
            0,
            title.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        val headingPos = source.indexOf(title)
        val bodyPos = source.lastIndexOf(title)
        val toc = listOf(MarkdownTocEntry(level = 2, title = title, sourceOffset = 0))
        val headingJump = resolveDisplayedCharOffsetForSavedPosition(
            sourceContent = source,
            sourceOffset = headingPos,
            displayedText = displayed,
            renderPlainText = false,
            windowStart = 0,
            windowEnd = source.length,
            tocEntries = toc,
            preferredText = title,
        )
        val bodyJump = resolveDisplayedCharOffsetForSavedPosition(
            sourceContent = source,
            sourceOffset = bodyPos,
            displayedText = displayed,
            renderPlainText = false,
            windowStart = 0,
            windowEnd = source.length,
            tocEntries = toc,
            preferredText = title,
        )
        assertEquals(0, headingJump)
        assertEquals(displayed.lastIndexOf(title), bodyJump)
    }

    @Test
    fun headingJump_ignoresSameTitleBeforeWindow() {
        val context = RuntimeEnvironment.getApplication()
        val title = "准确率奖励"
        val before = "## $title\n\n书首正文。\n"
        val source = before + "## $title\n\n窗口正文里的$title。\n"
        val windowStart = before.length
        val displayed = SpannableString("$title\n\n窗口正文里的$title。")
        displayed.setSpan(
            HeadingSpan(MarkwonTheme.create(context), 2),
            0,
            title.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        val headingPos = source.indexOf(title, windowStart)
        val offset = resolveDisplayedCharOffsetForSavedPosition(
            sourceContent = source,
            sourceOffset = headingPos,
            displayedText = displayed,
            renderPlainText = false,
            windowStart = windowStart,
            windowEnd = source.length,
            tocEntries = listOf(
                MarkdownTocEntry(2, title, 0),
                MarkdownTocEntry(2, title, windowStart),
            ),
            preferredText = title,
        )
        assertEquals(0, offset)
    }
}
