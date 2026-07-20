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
class JlatexFragmentTest {
    @Before fun init() { JLatexMathAndroid.init(RuntimeEnvironment.getApplication()) }

    @Test fun frag1() = build("Swish(A)")
    @Test fun frag2() = build("Swish(A) ")
    @Test fun frag3() = build(" B")
    @Test fun frag4() = build("B")

    private fun build(latex: String) {
        val c: Context = RuntimeEnvironment.getApplication()
        val d = c.resources.displayMetrics.density
        JLatexMathDrawable.builder(latex).textSize(14f * d).build()
    }
}
