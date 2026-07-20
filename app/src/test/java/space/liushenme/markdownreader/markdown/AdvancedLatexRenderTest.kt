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
class AdvancedLatexRenderTest {
    @Before fun init() { JLatexMathAndroid.init(RuntimeEnvironment.getApplication()) }

    @Test fun preprocessedAdvancedBlock() = render(
        ReaderLatexPreprocessor.preprocess(
            """\stackrel{\longrightarrow}{ABC} , \quad \xleftarrow[\text{below}]{\text{above}} , \quad \not{=} , \quad \cancel{\frac{a}{b}} , \quad \bcancel{\frac{a}{b}}""",
        ),
    )

    @Test fun preprocessedChemistryBlock() = render(
        ReaderLatexPreprocessor.preprocess(
            """\ce{2H2 + O2 ->[点燃] 2H2O}, \quad \ce{CO2 + C <=>[高温] 2CO}, \quad \ce{[Cu(NH3)4]^{2+}}, \quad \ce{CH3CH2OH ->[浓H2SO4][170^\circ C] CH2=CH2 ^ + H2O}""",
        ),
    )

    @Test fun stStrikeFraction() = render("\\st{\\frac{a}{b}}")

    @Test fun xrightarrow() = render("\\xrightarrow{\\text{点燃}}")

    @Test fun stackrelOnHarpoons() = render("\\stackrel{\\text{高温}}{\\rightleftharpoons}")

    private fun render(latex: String) {
        val c: Context = RuntimeEnvironment.getApplication()
        val d = c.resources.displayMetrics.density
        JLatexMathDrawable.builder(latex).textSize(14f * d).build()
    }
}
