package space.liushenme.markdownreader.markdown

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Spanned
import io.noties.markwon.image.AsyncDrawableSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderLatexBlockStyleTest {

    @Test
    fun inlineLatexHorizontalPadding_isHalfOfPreviousSixDp() {
        val context: Context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        assertEquals((3f * density + 0.5f).toInt(), ReaderLatexBlockStyle.inlinePadHPx(density))
        assertEquals(3f, ReaderLatexBlockStyle.INLINE_LATEX_PAD_H_DP, 0f)
    }

    @Test
    fun inlineCodeHorizontalPadding_usesOneThirdOfSideGap() {
        val context: Context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        assertEquals((2f * density + 0.5f).toInt(), ReaderLatexBlockStyle.inlineCodePadHPx(density))
        assertEquals(2f, ReaderLatexBlockStyle.INLINE_CODE_PAD_H_DP, 0f)
        assertEquals(1f / 3f, ReaderLatexBlockStyle.INLINE_CODE_SIDE_GAP_FRACTION, 0f)
        assertEquals(3f, ReaderLatexBlockStyle.INLINE_CODE_CORNER_RADIUS_DP, 0f)
    }

    @Test
    fun expandInlineLatexFontMetrics_keepsTallerPeerOnSameLine() {
        val paint = android.graphics.Paint().apply { textSize = 40f }
        val fm = android.graphics.Paint.FontMetricsInt().also { paint.getFontMetricsInt(it) }
        val firstAscent = fm.ascent
        ReaderLatexBlockStyle.expandInlineLatexFontMetrics(fm, paint, contentHeightPx = 80, padVPx = 4)
        val tallAscent = fm.ascent
        assertTrue(tallAscent < firstAscent)
        ReaderLatexBlockStyle.expandInlineLatexFontMetrics(fm, paint, contentHeightPx = 20, padVPx = 4)
        assertEquals(tallAscent, fm.ascent)
    }

    @Test
    fun blockBackground_isRoundedDrawable() {
        val context: Context = RuntimeEnvironment.getApplication()
        val drawable = ReaderLatexBlockStyle.blockBackground(context)
        assertTrue(drawable is GradientDrawable)
    }

    @Test
    fun centeredDivFormula_rendersWithoutRawDelimiters() {
        val context: Context = RuntimeEnvironment.getApplication()
        val markwon = ReaderMarkwonFactory.create(context)
        val md = """
            text
            <div align="center">
            ${'$'}${'$'}\\text{Score}(Q_i, K_j) = Q_i \\cdot K_j${'$'}${'$'}
            </div>
        """.trimIndent()
        val prepared = ReaderMarkwonFactory.prepareMarkdown(md, context)
        assertFalse(
            "single-line block should be expanded",
            prepared.text.contains("${'$'}${'$'}\\text{Score}"),
        )
        val rendered = markwon.toMarkdown(prepared.text)
        assertTrue(rendered.contains("Score"))
        assertFalse(rendered.contains("$$"))
        val spanned = rendered as Spanned
        val span = spanned.getSpans(0, spanned.length, AsyncDrawableSpan::class.java).single()
        assertEquals("JLatexAsyncDrawableSpan", span.javaClass.simpleName)
    }
}
