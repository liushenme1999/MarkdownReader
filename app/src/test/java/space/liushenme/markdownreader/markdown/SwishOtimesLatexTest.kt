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
class SwishOtimesLatexTest {

    @Before fun init() { JLatexMathAndroid.init(RuntimeEnvironment.getApplication()) }

    @Test fun swishFormula_rawOtimes_hasBounds() = assertW("Swish(A) \\otimes B")
    @Test fun swishFormula_unicodeOtimes_hasBounds() = assertW("Swish(A) \u2297 B")
    @Test fun swishFormula_textOtimes_hasBounds() = assertW("Swish(A) \\text{\u2297} B")

    private fun assertW(latex: String) {
        val c: Context = RuntimeEnvironment.getApplication()
        val d = c.resources.displayMetrics.density
        val b = JLatexMathDrawable.builder(latex).textSize(14f * d).build().bounds
        assertTrue("$latex w=${b.width()} h=${b.height()}", b.width() > 0 && b.height() > 0)
    }
}
