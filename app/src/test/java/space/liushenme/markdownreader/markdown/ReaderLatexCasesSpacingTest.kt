package space.liushenme.markdownreader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLatexCasesSpacingTest {

    @Test
    fun `cases columns get explicit spacing`() {
        val input = "\\begin{cases}\n1 & \\text{if } x \\\\\n0 & \\text{otherwise}\n\\end{cases}"
        val expected = "\\begin{cases}\n1 &\\quad \\text{if } x \\\\\n0 &\\quad \\text{otherwise}\n\\end{cases}"

        assertEquals(expected, ReaderLatexPreprocessor.preprocess(input))
    }

    @Test
    fun `existing quad spacing is not doubled`() {
        val input = "\\begin{cases}1 &\\quad a\\\\0 & b\\end{cases}"
        val expected = "\\begin{cases}1 &\\quad a\\\\0 &\\quad b\\end{cases}"

        assertEquals(expected, ReaderLatexPreprocessor.preprocess(input))
    }

    @Test
    fun `multi line block cases columns get explicit spacing`() {
        val input = "$$\n\\begin{cases}\n1 & \\text{if } x \\\\\n0 & \\text{otherwise}\n\\end{cases}\n$$"

        val result = ReaderLatexPreprocessor.preprocessBlockLatexInMarkdown(input)

        assertTrue(result.contains("1 &\\quad \\text{if } x"))
        assertTrue(result.contains("0 &\\quad \\text{otherwise}"))
        assertFalse(result.contains("& \\text"))
    }
}
