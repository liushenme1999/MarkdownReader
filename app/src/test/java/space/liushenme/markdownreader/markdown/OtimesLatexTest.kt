package space.liushenme.markdownreader.markdown

import android.content.Context
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.noties.jlatexmath.JLatexMathAndroid
import ru.noties.jlatexmath.JLatexMathDrawable

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OtimesLatexTest {

    @Before
    fun initJlatex() {
        JLatexMathAndroid.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun otimesDrawable_hasNonZeroBounds() {
        assertNonZero("\\otimes")
    }

    @Test
    fun wrappedOtimesDrawable_hasNonZeroBounds() {
        assertNonZero("{\\otimes}")
    }

    @Test
    fun otimesWithInlineTheme_hasNonZeroBounds() {
        val context: Context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        val bg = ReaderLatexBlockStyle.inlineLatexBackground(context)
        val padH = (6f * density + 0.5f).toInt()
        val padV = (4f * density + 0.5f).toInt()
        val d = JLatexMathDrawable.builder("{\\otimes}")
            .textSize(14f * density)
            .background(bg)
            .padding(padH, padV, padH, padV)
            .build()
        assertTrue(d.bounds.width() > 0 && d.bounds.height() > 0)
    }

    private fun assertNonZero(latex: String) {
        val context: Context = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        val d = JLatexMathDrawable.builder(latex).textSize(14f * density).build()
        val b = d.bounds
        assertTrue("$latex width=${b.width()} height=${b.height()}", b.width() > 0 && b.height() > 0)
    }
}
