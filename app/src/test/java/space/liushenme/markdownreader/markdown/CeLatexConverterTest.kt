package space.liushenme.markdownreader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CeLatexConverterTest {

    @Test
    fun waterFormation() {
        val out = CeLatexConverter.convert("2H2 + O2 ->[点燃] 2H2O")
        assertEquals(
            "2H_{2} + O_{2} \\xrightarrow{\\text{点燃}} 2H_{2}O",
            out,
        )
    }

    @Test
    fun reversibleWithCondition() {
        val out = CeLatexConverter.convert("CO2 + C <=>[高温] 2CO")
        assertEquals(
            "CO_{2} + C \\stackrel{\\text{高温}}{\\rightleftharpoons} 2CO",
            out,
        )
    }

    @Test
    fun complexIon() {
        val out = CeLatexConverter.convert("[Cu(NH3)4]^{2+}")
        assertEquals("[Cu(NH_{3})_{4}]^{2+}", out)
    }

    @Test
    fun dehydrationWithTwoConditions() {
        val out = CeLatexConverter.convert(
            "CH3CH2OH ->[浓H2SO4][170^\\circ C] CH2=CH2 ^ + H2O",
        )
        assertEquals(
            "CH_{3}CH_{2}OH \\xrightarrow[{170^\\circ C}]{\\text{浓H_{2}SO_{4}}} CH_{2}=CH_{2}^{\\uparrow} + H_{2}O",
            out,
        )
    }
}

class ReaderLatexPreprocessorTest {

    @Test
    fun cancelBecomesStrike() {
        val out = ReaderLatexPreprocessor.preprocess("\\cancel{\\frac{a}{b}}")
        assertEquals("\\st{\\frac{a}{b}}", out)
    }

    @Test
    fun ceCommandIsConverted() {
        val out = ReaderLatexPreprocessor.preprocess("\\ce{2H2 + O2 -> 2H2O}")
        assertFalse(out.contains("\\ce"))
        assertEquals("2H_{2} + O_{2} \\rightarrow 2H_{2}O", out)
    }

    @Test
    fun blockMarkdownIsRewritten() {
        val md = """
            $$
            \ce{2H2 + O2 ->[点燃] 2H2O}
            $$
        """.trimIndent()
        val out = ReaderLatexPreprocessor.preprocessBlockLatexInMarkdown(md)
        assertFalse(out.contains("\\ce"))
        assertEquals(
            """
            $$
            2H_{2} + O_{2} \xrightarrow{\text{点燃}} 2H_{2}O
            $$
            """.trimIndent(),
            out,
        )
    }

    @Test
    fun combinedAdvancedBlock() {
        val raw = """\stackrel{\longrightarrow}{ABC} , \quad \xleftarrow[\text{below}]{\text{above}} , \quad \not{=} , \quad \cancel{\frac{a}{b}} , \quad \bcancel{\frac{a}{b}}"""
        val out = ReaderLatexPreprocessor.preprocess(raw)
        assertEquals(
            """\stackrel{\longrightarrow}{ABC} , \quad \xleftarrow[\text{below}]{\text{above}} , \quad \not{=} , \quad \st{\frac{a}{b}} , \quad \st{\frac{a}{b}}""",
            out,
        )
    }

    @Test
    fun oiintBecomesCompositeOperator() {
        val out = ReaderLatexPreprocessor.preprocess("\\oiint\\limits_{S}")
        assertEquals("\\mathop{\\oint\\!\\!\\iint}\\limits_{S}", out)
    }
}
