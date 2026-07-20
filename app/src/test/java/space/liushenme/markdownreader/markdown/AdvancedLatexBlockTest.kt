package space.liushenme.markdownreader.markdown

import android.content.Context
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
class AdvancedLatexBlockTest {
    @Before fun init() { JLatexMathAndroid.init(RuntimeEnvironment.getApplication()) }

    @Test fun jlatexDirect_stackrel() = render("\\stackrel{\\longrightarrow}{ABC}")
    @Test fun jlatexDirect_xleftarrow() = render("\\xleftarrow[\\text{below}]{\\text{above}}")
    @Test fun jlatexDirect_not() = render("\\not{=}")
    @Test fun jlatexDirect_stCancel() = render("\\st{\\frac{a}{b}}")
    @Test fun jlatexDirect_combined() = render("\\stackrel{\\longrightarrow}{ABC} , \\not{=}")

    @Test fun jlatexDirect_fullAdvancedWithoutCancel() =
        render("\\stackrel{\\longrightarrow}{ABC} , \\quad \\xleftarrow[\\text{below}]{\\text{above}} , \\quad \\not{=}")

    private fun render(latex: String) {
        val c: Context = RuntimeEnvironment.getApplication()
        val d = c.resources.displayMetrics.density
        JLatexMathDrawable.builder(latex).textSize(14f * d).build()
    }
}
