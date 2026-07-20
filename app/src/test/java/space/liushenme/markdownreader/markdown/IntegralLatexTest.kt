package space.liushenme.markdownreader.markdown

import android.content.Context
import org.junit.Assert.assertFalse
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
class IntegralLatexTest {
    @Before fun init() { JLatexMathAndroid.init(RuntimeEnvironment.getApplication()) }

    @Test
    fun gaussAndStokesBlock_rendersAfterPreprocess() {
        val raw = """
            \iiint\limits_{V} \left( \nabla \cdot \mathbf{F} \right) \mathrm{d}V = \oiint\limits_{\partial V} \mathbf{F} \cdot \mathrm{d}\mathbf{S}, \quad
            \oint\limits_{C} P\,\mathrm{d}x + Q\,\mathrm{d}y = \iint\limits_{D} \left( \frac{\partial Q}{\partial x} - \frac{\partial P}{\partial y} \right) \mathrm{d}x\,\mathrm{d}y
        """.trimIndent()
        val preprocessed = ReaderLatexPreprocessor.preprocess(raw)
        assertFalse(preprocessed.contains("\\oiint"))
        render(preprocessed)
    }

    @Test
    fun oiintAlias_renders() {
        render(ReaderLatexPreprocessor.preprocess("\\oiint\\limits_{\\partial V}"))
    }

    private fun render(latex: String) {
        val c: Context = RuntimeEnvironment.getApplication()
        val d = c.resources.displayMetrics.density
        JLatexMathDrawable.builder(latex).textSize(14f * d).build()
    }
}
