package space.liushenme.markdownreader.markdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import space.liushenme.markdownreader.ui.screens.reader.readerContentSignature
import space.liushenme.markdownreader.ui.screens.reader.SafeReaderTextView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderScrollableCodeBlockTest {

    @Before
    fun setUp() {
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 0
        ReaderCodeBlockSettings.viewportTopPx = 0
        ReaderCodeBlockSettings.viewportHeightPx = 0
    }

    @After
    fun tearDown() {
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 0
        ReaderCodeBlockSettings.viewportTopPx = 0
        ReaderCodeBlockSettings.viewportHeightPx = 0
    }

    @Test
    fun wrapOn_usesWindowSpanWithLanguage() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        val markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("```javascript\nfunction foo() {\n  return 1;\n}\n```")
        val spans = rendered.getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals("JavaScript", spans[0].languageLabel)
        assertTrue(spans[0].rawCode.contains("function foo()"))
        assertTrue(rendered.contains('\uFFFC'))
    }

    @Test
    fun wrapOff_usesSingleObjectReplacement() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = false
        ReaderCodeBlockSettings.viewportWidthPx = 360
        val markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("```javascript\nfunction foo() {\n  return 1;\n}\n```")
        val spans = rendered.getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java)
        assertEquals(1, spans.size)
        assertTrue(rendered.contains('\uFFFC'))
        val start = rendered.getSpanStart(spans[0])
        val end = rendered.getSpanEnd(spans[0])
        assertEquals(1, end - start)

        val paint = Paint().apply { textSize = 42f }
        val fm = Paint.FontMetricsInt()
        val width = spans[0].getSize(paint, rendered, start, end, fm)
        assertEquals(360, width)
        assertTrue(fm.descent - fm.ascent > paint.textSize)
        assertTrue(spans[0] is ReplacementSpan)
        assertEquals("JavaScript", spans[0].languageLabel)
    }

    @Test
    fun wrapOff_longLine_canScrollHorizontally() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = false
        ReaderCodeBlockSettings.viewportWidthPx = 240
        val markwon = ReaderMarkwonFactory.create(context)
        val longLine = "val x = " + "abcdefghij".repeat(40)
        val rendered = markwon.toMarkdown("```kotlin\n$longLine\n```")
        val span = rendered.getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java).single()
        val paint = Paint().apply { textSize = 40f }
        span.getSize(paint, rendered, 0, 1, Paint.FontMetricsInt())
        assertTrue("expected overflow, maxScrollX=${span.maxScrollX()}", span.maxScrollX() > 50)
        val before = span.scrollX
        assertTrue(span.scrollBy(80f))
        assertTrue(span.scrollX > before)
        assertEquals("Kotlin", span.languageLabel)

        ReaderCodeBlockSettings.viewportWidthPx = 0
        span.prepareForTouch(paint, 240)
        assertTrue(span.canScrollHorizontally())
    }

    @Test
    fun wrapOff_horizontalScrollBeyondOneTile_keepsEveryVisibleColumnPainted() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = false
        ReaderCodeBlockSettings.viewportWidthPx = 240
        val source = "MMMMMMMMMMMMMMMMMM".repeat(20)
        val rendered = ReaderFullScreenCodeRenderer.render(
            context = context,
            content = source,
            language = "text",
            codeHighlightTheme = CodeHighlightTheme.VS_DARK_PLUS,
        )
        val span = rendered.getSpans(
            0,
            rendered.length,
            ReaderScrollableCodeBlockSpan::class.java,
        ).single()
        val paint = Paint().apply {
            textSize = 24f
            isAntiAlias = true
        }
        span.prewarmLayout(paint, 240)
        val ranges = ReaderFullScreenCodeRenderer.highlight(
            span = span,
            language = "text",
            codeHighlightTheme = CodeHighlightTheme.VS_DARK_PLUS,
        )
        span.setSyntaxHighlightRanges(
            ranges,
            span.prepareStyledSyntaxLayout(paint, 240, ranges),
        )
        span.prepareForTouch(paint, 240)
        assertTrue(span.scrollBy(180f))

        val bitmap = Bitmap.createBitmap(240, 120, Bitmap.Config.ARGB_8888)
        span.drawFullScreenByHost(
            canvas = Canvas(bitmap),
            x = 0f,
            top = 0,
            bottom = 120,
            paint = paint,
            viewportPx = 240,
        )
        val background = CodeHighlightTheme.VS_DARK_PLUS.palette.background
        for (bucketStart in 0 until 240 step 20) {
            var painted = 0
            for (x in bucketStart until bucketStart + 20) {
                for (y in 0 until 120) {
                    if (bitmap.getPixel(x, y) != background) painted++
                }
            }
            assertTrue(
                "visible column bucket starting at $bucketStart should not be blank",
                painted > 10,
            )
        }
    }

    @Test
    fun wrappedFullScreenCode_chunksStartAndEndOnCompleteSourceLines() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 360
        val source = buildString {
            repeat(80) { index ->
                appendLine(
                    "    public int process$index(int input) { " +
                        "return input + $index + anotherLargeExpression$index; }",
                )
            }
            appendLine("}")
        }.trimEnd()
        val rendered = ReaderFullScreenCodeRenderer.render(
            context = context,
            content = source,
            language = "java",
            codeHighlightTheme = CodeHighlightTheme.VS_LIGHT_PLUS,
        )
        val span = rendered.getSpans(
            0,
            rendered.length,
            ReaderScrollableCodeBlockSpan::class.java,
        ).single()
        val paint = Paint().apply { textSize = 42f }
        span.prewarmLayout(paint, 360)
        val ranges = ReaderFullScreenCodeRenderer.highlight(
            span = span,
            language = "java",
            codeHighlightTheme = CodeHighlightTheme.VS_LIGHT_PLUS,
        )
        val styled = span.prepareStyledSyntaxLayout(paint, 360, ranges)

        assertTrue("fixture must produce multiple chunks", styled.chunks.size > 1)
        assertEquals(0, styled.chunks.first().firstLayoutLine)
        styled.chunks.zipWithNext().forEach { (current, next) ->
            assertEquals(
                "chunks must cover the layout without a gap or overlap",
                current.lastLayoutLine + 1,
                next.firstLayoutLine,
            )
        }
        styled.chunks.forEach { chunk ->
            assertTrue(
                "chunk must begin at a complete source line",
                chunk.sourceStart == 0 || source[chunk.sourceStart - 1] == '\n',
            )
            val sourceEnd = if (chunk === styled.chunks.last()) {
                source.length
            } else {
                styled.chunks[styled.chunks.indexOf(chunk) + 1].sourceStart
            }
            assertTrue(
                "chunk must end at a complete source line boundary",
                sourceEnd == source.length || source[sourceEnd - 1] == '\n',
            )
            assertEquals(
                "chunk layout must cover exactly its mapped full-layout lines",
                chunk.lastLayoutLine - chunk.firstLayoutLine + 1,
                chunk.layout.lineCount,
            )
        }
    }

    @Test
    fun fullScreenCode_scrollingAcrossChunks_keepsVisibleRowsPainted() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 360
        val source = buildString {
            appendLine("public final class LargeService {")
            repeat(500) { index ->
                appendLine("    public static final class Worker$index {")
                appendLine("        public int process$index(int input) {")
                appendLine("            return input + $index;")
                appendLine("        }")
                appendLine("    }")
            }
            appendLine("}")
        }.trimEnd()
        val rendered = ReaderFullScreenCodeRenderer.render(
            context = context,
            content = source,
            language = "java",
            codeHighlightTheme = CodeHighlightTheme.VS_LIGHT_PLUS,
        )
        val span = rendered.getSpans(
            0,
            rendered.length,
            ReaderScrollableCodeBlockSpan::class.java,
        ).single()
        val paint = Paint().apply {
            textSize = 24f
            isAntiAlias = true
        }
        span.prewarmLayout(paint, 360)
        val ranges = ReaderFullScreenCodeRenderer.highlight(
            span = span,
            language = "java",
            codeHighlightTheme = CodeHighlightTheme.VS_LIGHT_PLUS,
        )
        val styled = span.prepareStyledSyntaxLayout(paint, 360, ranges)
        span.setSyntaxHighlightRanges(ranges, styled)

        val background = CodeHighlightTheme.VS_LIGHT_PLUS.palette.background
        val width = 360
        val height = 600
        ReaderCodeBlockSettings.viewportHeightPx = height
        listOf(0, 20_000, 60_000, 120_000).forEach { viewportTop ->
            ReaderCodeBlockSettings.viewportTopPx = viewportTop
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.translate(0f, -viewportTop.toFloat())
                span.drawFullScreenByHost(
                    canvas = canvas,
                    x = 0f,
                    top = 0,
                    bottom = span.requiredHeightForTest(paint),
                    paint = paint,
                    viewportPx = width,
                )
                var painted = 0
                for (x in 0 until width step 8) {
                    for (y in 0 until height step 8) {
                        if (bitmap.getPixel(x, y) != background) painted++
                    }
                }
                assertTrue("viewportTop=$viewportTop should contain painted source", painted > 20)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test
    fun safeReaderTextView_scrolledFullScreenCode_paintsTheVisibleViewport() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 360
        val source = buildString {
            appendLine("public final class ScrolledLargeService {")
            repeat(400) { index ->
                appendLine("    public static final class Worker$index {")
                appendLine("        public int process$index(int input) {")
                appendLine("            return input + $index;")
                appendLine("        }")
                appendLine("    }")
            }
            appendLine("}")
        }.trimEnd()
        val rendered = ReaderFullScreenCodeRenderer.render(
            context = context,
            content = source,
            language = "java",
            codeHighlightTheme = CodeHighlightTheme.VS_DARK_PLUS,
        )
        val width = 360
        val height = 600
        val textView = ScrolledSafeReaderTextView(context).apply {
            setText(rendered, TextView.BufferType.SPANNABLE)
            textSize = 24f
            setPadding(0, 0, 0, 0)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(
                    width,
                    android.view.View.MeasureSpec.EXACTLY,
                ),
                android.view.View.MeasureSpec.makeMeasureSpec(
                    height,
                    android.view.View.MeasureSpec.EXACTLY,
                ),
            )
            layout(0, 0, width, height)
            scrollTo(0, 24_000)
        }
        assertTrue("fixture must scroll through source", textView.scrollY > 0)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.MAGENTA)
            textView.drawAtCurrentScrollForTest(Canvas(bitmap))
            val background = CodeHighlightTheme.VS_DARK_PLUS.palette.background
            var backgroundPixels = 0
            var sourcePixels = 0
            var sampledPixels = 0
            for (x in 0 until width step 2) {
                for (y in 0 until height step 2) {
                    sampledPixels++
                    if (bitmap.getPixel(x, y) == background) {
                        backgroundPixels++
                    } else if (bitmap.getPixel(x, y) != Color.MAGENTA) {
                        sourcePixels++
                    }
                }
            }

            assertTrue(
                "scrolled code should paint its themed surface, background=$backgroundPixels " +
                    "of $sampledPixels sampled pixels",
                backgroundPixels > sampledPixels * 0.6,
            )
            assertTrue(
                "scrolled code should paint visible source glyphs, got $sourcePixels sampled pixels",
                sourcePixels > 200,
            )
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Android delivers TextView.onDraw a canvas already translated by its scroll offset. Calling
     * View.draw under Robolectric does not reproduce that contract, so mirror only the translation
     * needed to exercise the host-drawn full-screen code clip.
     */
    private class ScrolledSafeReaderTextView(context: android.content.Context) :
        SafeReaderTextView(context) {
        fun drawAtCurrentScrollForTest(canvas: Canvas) {
            val save = canvas.save()
            try {
                canvas.translate(-scrollX.toFloat(), -scrollY.toFloat())
                onDraw(canvas)
            } finally {
                canvas.restoreToCount(save)
            }
        }
    }

    @Test
    fun selectionEdgeScrollDelta_rightAndLeftEdges() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = false
        ReaderCodeBlockSettings.viewportWidthPx = 240
        val markwon = ReaderMarkwonFactory.create(context)
        val longLine = "val x = " + "abcdefghij".repeat(40)
        val rendered = markwon.toMarkdown("```kotlin\n$longLine\n```")
        val span = rendered.getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java).single()
        val paint = Paint().apply { textSize = 40f }
        span.getSize(paint, rendered, 0, 1, Paint.FontMetricsInt())
        span.prepareForTouch(paint, 240)
        assertTrue(span.canScrollHorizontally())

        val right = span.selectionEdgeScrollDelta(
            contentX = 240f,
            originLeft = 0f,
            viewportWidth = 240,
        )
        val center = span.selectionEdgeScrollDelta(
            contentX = 120f,
            originLeft = 0f,
            viewportWidth = 240,
        )
        val left = span.selectionEdgeScrollDelta(
            contentX = 0f,
            originLeft = 0f,
            viewportWidth = 240,
        )
        assertTrue("right edge should scroll forward, delta=$right", right > 0f)
        assertEquals(0f, center, 0.01f)
        assertTrue("left edge should scroll backward, delta=$left", left < 0f)
        assertTrue(span.scrollBy(right))
        assertTrue(span.scrollX > 0)
    }

    @Test
    fun wrapOn_longLine_doesNotScroll() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 240
        val markwon = ReaderMarkwonFactory.create(context)
        val longLine = "val x = " + "abcdefghij".repeat(40)
        val rendered = markwon.toMarkdown("```kotlin\n$longLine\n```")
        val span = rendered.getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java).single()
        val paint = Paint().apply { textSize = 40f }
        span.getSize(paint, rendered, 0, 1, Paint.FontMetricsInt())
        assertFalse(span.canScrollHorizontally())
        assertEquals(0, span.maxScrollX())
    }

    @Test
    fun languageLabel_parsesFenceInfo() {
        assertEquals("Python", ReaderCodeBlockLanguage.label("python"))
        assertEquals("JavaScript", ReaderCodeBlockLanguage.label("js {.line-numbers}"))
        assertEquals("Java", ReaderCodeBlockLanguage.label("java"))
        assertEquals("Vue", ReaderCodeBlockLanguage.label("vue"))
        assertEquals("HTML", ReaderCodeBlockLanguage.label("html"))
        assertEquals("CSS", ReaderCodeBlockLanguage.label("css"))
        assertEquals("", ReaderCodeBlockLanguage.label(null))
        assertEquals("", ReaderCodeBlockLanguage.label("  "))
    }

    @Test
    fun allThemes_renderTheirOwnCodeBlockPalette() {
        val context = RuntimeEnvironment.getApplication()
        val source = "```kotlin\nfun greet(name: String) = \"Hello, \${'$'}name\"\n```"
        val sampledBackgrounds = mutableSetOf<Int>()

        CodeHighlightTheme.all.forEach { theme ->
            val markwon = ReaderMarkwonFactory.create(context, codeHighlightTheme = theme)
            val rendered = markwon.toMarkdown(source)
            val span = rendered.getSpans(
                0,
                rendered.length,
                ReaderScrollableCodeBlockSpan::class.java,
            ).single()

            assertEquals(theme, span.codeHighlightTheme)
            assertEquals(theme.palette.foreground, span.codeHighlightTheme.palette.foreground)

            val background = span.background as GradientDrawable
            val sampled = background.color?.defaultColor
            assertEquals(theme.palette.background, sampled)
            sampledBackgrounds += sampled ?: error("Missing code block background color")
        }

        assertEquals(3, sampledBackgrounds.size)
    }

    @Test
    fun codeDocument_usesSeamlessFullScreenSurfaceWhileMarkdownKeepsCardChrome() {
        val context = RuntimeEnvironment.getApplication()
        val markdown = "```python\nprint('hello')\n```"

        val cardRendered = ReaderMarkwonFactory.create(context).toMarkdown(markdown)
        val cardSpan = cardRendered.getSpans(
            0,
            cardRendered.length,
            ReaderScrollableCodeBlockSpan::class.java,
        ).single()
        val documentRendered = ReaderMarkwonFactory.create(
            context,
            fullScreenCodeDocument = true,
        ).toMarkdown(markdown)
        val documentSpan = documentRendered.getSpans(
            0,
            documentRendered.length,
            ReaderScrollableCodeBlockSpan::class.java,
        ).single()

        assertFalse(cardSpan.fullScreenCodeDocument)
        assertTrue(documentSpan.fullScreenCodeDocument)
        assertTrue((cardSpan.background as GradientDrawable).cornerRadius > 0f)
        assertEquals(0f, (documentSpan.background as GradientDrawable).cornerRadius)
    }

    @Test
    fun codeDocument_hidesLanguageAndCopyHeaderWhileMarkdownKeepsIt() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 480
        val markdown = "```python\nprint('hello')\n```"
        val paint = Paint().apply { textSize = 40f }

        val cardRendered = ReaderMarkwonFactory.create(context).toMarkdown(markdown)
        val cardSpan = cardRendered.getSpans(
            0,
            cardRendered.length,
            ReaderScrollableCodeBlockSpan::class.java,
        ).single()
        val documentRendered = ReaderMarkwonFactory.create(
            context,
            fullScreenCodeDocument = true,
        ).toMarkdown(markdown)
        val documentSpan = documentRendered.getSpans(
            0,
            documentRendered.length,
            ReaderScrollableCodeBlockSpan::class.java,
        ).single()

        assertTrue(cardSpan.headerHeightPx(paint) > 0)
        assertEquals(0, documentSpan.headerHeightPx(paint))
        assertFalse(documentSpan.isOnCopyButton(0f, 0f))
    }

    @Test
    fun fullScreenCodeDocument_mapsViewportYToSourceLineStart() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 480
        val source = """
            fun first() {
                val a = 1
            }
            fun second() {
                val b = 2
            }
        """.trimIndent()
        val rendered = ReaderMarkwonFactory.create(
            context,
            fullScreenCodeDocument = true,
        ).toMarkdown(ReaderCodeBlockMarkdown.wrap(source, "kotlin"))
        val span = rendered.getSpans(
            0,
            rendered.length,
            ReaderScrollableCodeBlockSpan::class.java,
        ).single()
        val paint = Paint().apply { textSize = 40f }
        span.prepareForTouch(paint, 480)
        span.getSize(paint, rendered, 0, 1, Paint.FontMetricsInt())

        val targetLineStart = source.indexOf("fun second")
        val targetLineTop = span.offsetTopInLine(targetLineStart, paint).toFloat()

        assertEquals(
            targetLineStart,
            span.sourceOffsetAtLayoutY(
                contentY = targetLineTop + 1f,
                originTop = 0f,
                paint = paint,
            ),
        )
        assertEquals(
            0,
            span.sourceOffsetAtLayoutY(
                contentY = -100f,
                originTop = 0f,
                paint = paint,
            ),
        )
        val viewport = span.sourceViewportAtLayout(
            contentTopY = targetLineTop + 1f,
            contentBottomY = targetLineTop + 1000f,
            originTop = 0f,
            viewportPx = 480,
            paint = paint,
        )
        assertEquals(targetLineStart, viewport?.top)
        assertEquals(source.length, viewport?.bottom)
    }

    @Test
    fun fullScreenCodeDocument_measuredLineFitsAllCodeAtDefaultReaderLineSpacing() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.viewportWidthPx = 1000
        val source = """
            {
              "name": "full-screen code",
              "enabled": true,
              "count": 42,
              "nested": { "last": "line" }
            }
        """.trimIndent()
        val rendered = ReaderMarkwonFactory.create(
            context,
            fullScreenCodeDocument = true,
        ).toMarkdown(ReaderCodeBlockMarkdown.wrap(source, "json"))
        val span = rendered.getSpans(
            0,
            rendered.length,
            ReaderScrollableCodeBlockSpan::class.java,
        ).single()
        val textView = SafeReaderTextView(context).apply {
            setText(rendered, android.widget.TextView.BufferType.SPANNABLE)
            textSize = 16f
            setPadding(0, 0, 0, 0)
            setLineSpacing(0f, 1.5f)
        }

        textView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(
                1000,
                android.view.View.MeasureSpec.EXACTLY,
            ),
            android.view.View.MeasureSpec.makeMeasureSpec(
                4000,
                android.view.View.MeasureSpec.AT_MOST,
            ),
        )
        textView.layout(0, 0, 1000, 4000)

        val layout = textView.layout ?: error("layout missing")
        val measuredLineHeight = layout.getLineBottom(0) - layout.getLineTop(0)
        val desiredHeight = span.requiredHeightForTest(textView.paint)

        assertTrue(
            "measured line=$measuredLineHeight, desired=$desiredHeight",
            measuredLineHeight >= desiredHeight,
        )
    }

    @Test
    fun markdownCodeBlock_outerReaderLineSpacingDoesNotExpandCardHeight() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = false
        ReaderCodeBlockSettings.viewportWidthPx = 1000
        val markdown = """
            ```python
            from hello_agents.evaluation import GAIADataset, GAIAEvaluator

            dataset = GAIADataset(level=1)
            items = dataset.load()
            evaluator = GAIAEvaluator(dataset=dataset, level=1)
            ```

            普通段落。
        """.trimIndent()
        val rendered = ReaderMarkwonFactory.create(context).toMarkdown(markdown)
        val span = rendered.getSpans(
            0,
            rendered.length,
            ReaderScrollableCodeBlockSpan::class.java,
        ).single()
        val textView = SafeReaderTextView(context).apply {
            setText(rendered, TextView.BufferType.SPANNABLE)
            textSize = 16f
            setPadding(0, 0, 0, 0)
            ReaderTableSpacing.lineSpacingMultiplier = 1.5f
            setLineSpacing(0f, 1.5f)
        }

        textView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(
                1000,
                android.view.View.MeasureSpec.EXACTLY,
            ),
            android.view.View.MeasureSpec.makeMeasureSpec(
                4000,
                android.view.View.MeasureSpec.AT_MOST,
            ),
        )
        textView.layout(0, 0, 1000, 4000)

        val layout = textView.layout ?: error("layout missing")
        val line = layout.getLineForOffset(rendered.getSpanStart(span))
        val measuredLineHeight = layout.getLineBottom(line) - layout.getLineTop(line)
        val desiredHeight = span.requiredHeightForTest(textView.paint)
        val bitmap = Bitmap.createBitmap(
            1000,
            layout.getLineBottom(line).coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        try {
            span.draw(
                canvas = Canvas(bitmap),
                text = rendered,
                start = rendered.getSpanStart(span),
                end = rendered.getSpanEnd(span),
                x = 0f,
                top = layout.getLineTop(line),
                y = layout.getLineBaseline(line),
                bottom = layout.getLineBottom(line),
                paint = textView.paint,
            )
        } finally {
            bitmap.recycle()
        }

        assertTrue(
            "outer 1.5x line spacing must not expand code card: " +
                "measured=$measuredLineHeight, desired=$desiredHeight",
            kotlin.math.abs(measuredLineHeight - desiredHeight) <= 2,
        )
        assertEquals(
            "the painted card must use the measured row bounds",
            measuredLineHeight,
            span.lastDrawnHeightForTest,
        )
        val trailingLineHeights = (line + 1 until layout.lineCount).map { index ->
            layout.getLineBottom(index) - layout.getLineTop(index)
        }
        assertTrue(
            "a blank line after the code card must not inherit its height: " +
                "desired=$desiredHeight, trailing=$trailingLineHeights",
            trailingLineHeights.all { it < desiredHeight },
        )
    }

    @Test
    fun fullScreenRenderer_bypassesMarkdownAndKeepsLightweightSyntaxRanges() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 480
        val source = """
            # not a heading
            | not | a table |
            <template>
              <button class="primary">{{ label }}</button>
            </template>
            <script setup>
            const fence = ```````
            function greet(name) { return `hello ${'$'}{name}` }
            </script>
        """.trimIndent()

        val rendered = ReaderFullScreenCodeRenderer.render(
            context = context,
            content = source,
            language = "vue",
            codeHighlightTheme = CodeHighlightTheme.VS_DARK_PLUS,
        )
        val span = rendered.getSpans(
            0,
            rendered.length,
            ReaderScrollableCodeBlockSpan::class.java,
        ).single()

        assertEquals(1, rendered.length)
        assertTrue(rendered is Spanned)
        assertEquals(0, rendered.getSpans(0, rendered.length, ForegroundColorSpan::class.java).size)
        assertTrue(span.fullScreenCodeDocument)
        assertEquals("Vue", span.languageLabel)
        assertEquals(source, span.rawCode)
        assertEquals(CodeHighlightTheme.VS_DARK_PLUS, span.codeHighlightTheme)
        assertEquals(
            CodeHighlightTheme.VS_DARK_PLUS.palette.background,
            (span.background as GradientDrawable).color?.defaultColor,
        )
    }

    @Test
    fun themeChange_rebuildsMarkwonAndRenderSignature() {
        val context = RuntimeEnvironment.getApplication()
        val markdown = "```json\n{\"enabled\": true, \"count\": 2}\n```"
        val lightRendered = ReaderMarkwonFactory.create(
            context,
            codeHighlightTheme = CodeHighlightTheme.VS_LIGHT_PLUS,
        ).toMarkdown(markdown)
        val lightSpan = lightRendered.getSpans(
            0,
            lightRendered.length,
            ReaderScrollableCodeBlockSpan::class.java,
        ).single()
        val darkRendered = ReaderMarkwonFactory.create(
            context,
            codeHighlightTheme = CodeHighlightTheme.VS_DARK_PLUS,
        ).toMarkdown(markdown)
        val darkSpan = darkRendered.getSpans(
            0,
            darkRendered.length,
            ReaderScrollableCodeBlockSpan::class.java,
        ).single()

        assertEquals(CodeHighlightTheme.VS_LIGHT_PLUS, lightSpan.codeHighlightTheme)
        assertEquals(CodeHighlightTheme.VS_DARK_PLUS, darkSpan.codeHighlightTheme)
        assertNotEquals(
            lightSpan.codeHighlightTheme.palette.background,
            darkSpan.codeHighlightTheme.palette.background,
        )

        val lightSignature = readerContentSignature(
            content = markdown,
            renderPlainText = false,
            themeName = "Paper",
            fontSize = 16,
            codeHighlightTheme = CodeHighlightTheme.VS_LIGHT_PLUS,
        )
        val darkSignature = readerContentSignature(
            content = markdown,
            renderPlainText = false,
            themeName = "Paper",
            fontSize = 16,
            codeHighlightTheme = CodeHighlightTheme.VS_DARK_PLUS,
        )
        assertNotEquals(lightSignature, darkSignature)
        assertTrue(lightSignature.contains("_syntax=VS_LIGHT_PLUS"))
        assertTrue(darkSignature.contains("_syntax=VS_DARK_PLUS"))
    }

    @Test
    fun wrappedVueSource_rendersAsOneCodeBlockWithoutMarkdownInterpretation() {
        val context = RuntimeEnvironment.getApplication()
        val source = """
            # not a heading
            | not | a table |
            <template>
              <button>{{ label }}</button>
            </template>
            <script setup>
            const fence = ```````
            </script>
        """.trimIndent()
        val rendered = ReaderMarkwonFactory.create(context).toMarkdown(
            ReaderCodeBlockMarkdown.wrap(source, "vue"),
        )
        val spans = rendered.getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java)

        assertEquals(1, spans.size)
        assertEquals("Vue", spans.single().languageLabel)
        assertTrue(spans.single().rawCode.contains("# not a heading"))
        assertTrue(spans.single().rawCode.contains("| not | a table |"))
        assertTrue(spans.single().rawCode.contains("<template>"))
        assertTrue(spans.single().rawCode.contains("```````"))
    }

    @Test
    fun offsetAt_andSelectedText_useInnerLayout() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 400
        val markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("```kotlin\nval hello = 1\n```")
        val span = rendered.getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java).single()
        val paint = Paint().apply { textSize = 40f }
        span.prepareForTouch(paint, 400)
        span.getSize(paint, rendered, 0, 1, Paint.FontMetricsInt())
        val y = span.headerHeightPx(paint) + 20f
        val offset = span.offsetAt(24f, y, paint, originLeft = 0f, originTop = 0f)
        assertTrue("offsetAt should hit code, got $offset", offset != null)
        val start = offset!!
        val end = (start + 1).coerceAtMost(span.codeLength())
        span.setSelection(start, end)
        assertTrue(span.hasSelection())
        val selected = span.selectedText()
        assertTrue(selected.isNotEmpty())
        assertTrue(span.rawCode.contains(selected))

        span.setSelection(0, 3)
        assertEquals(span.rawCode.take(3), span.selectedText())

        val idx = span.indexOfSnippet("hello")
        assertTrue(idx >= 0)
        span.addHighlightRange(
            CodeBlockHighlightRange(idx, idx + 5, 0xFFFFFF00.toInt()),
        )
        assertEquals(1, span.highlightRangesForTest().size)
        span.removeHighlightRangeMatching("hello")
        assertTrue(span.highlightRangesForTest().isEmpty())
    }

    @Test
    fun selectionBoundsInLayout_sitsOnInnerTextNotBlockTop() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 400
        val markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown(
            "```kotlin\nline one\nline two\nline three selected\nline four\n```",
        )
        val span = rendered.getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java).single()
        val paint = Paint().apply { textSize = 40f }
        span.prepareForTouch(paint, 400)
        span.getSize(paint, rendered, 0, 1, Paint.FontMetricsInt())
        val idx = span.indexOfSnippet("selected")
        assertTrue(idx >= 0)
        span.setSelection(idx, idx + "selected".length)
        val header = span.headerHeightPx(paint)
        val bounds = span.selectionBoundsInLayout(paint, originLeft = 0f, originTop = 0f)
        assertTrue(bounds != null)
        assertTrue(
            "inner selection should be below the language header, top=${bounds!!.top} header=$header",
            bounds.top > header,
        )
        assertTrue(
            "anchor height should be one text line, h=${bounds.height()}",
            bounds.height() < paint.textSize * 3,
        )
    }

    @Test
    fun indexOfSnippet_toleratesWhitespaceDifferencesAndReturnsDisplayedRange() {
        val context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val rendered = markwon.toMarkdown("```kotlin\nval  value = 1\n```")
        val span = rendered.getSpans(0, rendered.length, ReaderScrollableCodeBlockSpan::class.java).single()

        val range = span.rangeOfSnippet("val value = 1")

        assertTrue("normalized code should still be found", range != null)
        assertEquals("val  value = 1", span.contentSliceForTest(range!!.first, range.last + 1))
    }

    @Test
    fun trailingBlankCodeLines_doNotAddVisibleRowsBelowSource() {
        val context = RuntimeEnvironment.getApplication()
        ReaderCodeBlockSettings.wrapEnabled = true
        ReaderCodeBlockSettings.viewportWidthPx = 400
        val markwon = ReaderMarkwonFactory.create(context)
        val paint = Paint().apply { textSize = 40f }

        val withoutTrailingBlanks = markwon.toMarkdown("```kotlin\nval value = 1\n```")
        val withoutTrailingSpan = withoutTrailingBlanks.getSpans(
            0,
            withoutTrailingBlanks.length,
            ReaderScrollableCodeBlockSpan::class.java,
        ).single()
        val expectedHeight = withoutTrailingSpan.requiredHeightForTest(paint)

        listOf(
            "```kotlin\nval value = 1\n\n```",
            "```kotlin\nval value = 1\n   \n\t\n```",
        ).forEach { markdown ->
            val rendered = markwon.toMarkdown(markdown)
            val span = rendered.getSpans(
                0,
                rendered.length,
                ReaderScrollableCodeBlockSpan::class.java,
            ).single()
            assertEquals("val value = 1", span.contentSliceForTest(0, span.codeLength()))
            assertEquals("val value = 1", span.rawCode)
            assertEquals(expectedHeight, span.requiredHeightForTest(paint))
        }
    }
}
