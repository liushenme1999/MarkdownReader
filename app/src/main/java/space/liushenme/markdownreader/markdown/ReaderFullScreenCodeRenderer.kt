package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned

/**
 * Renders a standalone source file without wrapping it in a Markdown fence.
 *
 * Large files deliberately keep syntax colors as lightweight ranges. The code block span paints
 * only visible ranges, avoiding Markwon parsing and thousands of Android text spans on first open.
 */
internal object ReaderFullScreenCodeRenderer {

    data class Prepared(
        val text: Spanned,
        val span: ReaderScrollableCodeBlockSpan,
    )

    /**
     * Builds the single replacement span and optionally pre-lays the source off the main thread.
     * Syntax ranges are intentionally omitted here so a large file can become visible before the
     * comparatively expensive regex pass finishes.
     */
    fun prepare(
        context: Context,
        content: String,
        language: String,
        codeHighlightTheme: CodeHighlightTheme,
        layoutPaint: Paint? = null,
        viewportWidthPx: Int = 0,
        showLineNumbers: Boolean = false,
    ): Prepared {
        val normalized = normalizeReaderCodeContent(content).toString().ifEmpty { " " }
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 0f
            setColor(codeHighlightTheme.palette.background)
        }
        val span = ReaderScrollableCodeBlockSpan(
            theme = io.noties.markwon.core.MarkwonTheme.create(context.applicationContext),
            background = background,
            codeHighlightTheme = codeHighlightTheme,
            fullScreenCodeDocument = true,
            padH = (12f * density + 0.5f).toInt(),
            padV = (8f * density + 0.5f).toInt(),
            content = normalized,
            rawCode = normalized,
            languageInfo = language,
            wrapEnabled = ReaderCodeBlockSettings.wrapEnabled,
            density = density,
            showLineNumbers = showLineNumbers,
        )
        if (layoutPaint != null && viewportWidthPx > 1) {
            span.prewarmLayout(layoutPaint, viewportWidthPx)
        }
        span.renderThroughHostView()
        val text = SpannableString("\uFFFC").apply {
            setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return Prepared(text = text, span = span)
    }

    fun highlight(
        span: ReaderScrollableCodeBlockSpan,
        language: String,
        codeHighlightTheme: CodeHighlightTheme,
    ): List<CodeSyntaxRange> =
        ReaderSyntaxHighlight(codeHighlightTheme).highlightRanges(language, span.rawCode)

    /** Synchronous convenience path used by tests and callers that do not need progressive paint. */
    fun render(
        context: Context,
        content: String,
        language: String,
        codeHighlightTheme: CodeHighlightTheme,
        showLineNumbers: Boolean = false,
    ): Spanned {
        val prepared = prepare(
            context = context,
            content = content,
            language = language,
            codeHighlightTheme = codeHighlightTheme,
            showLineNumbers = showLineNumbers,
        )
        prepared.span.setSyntaxHighlightRanges(
            highlight(prepared.span, language, codeHighlightTheme),
        )
        return prepared.text
    }
}
