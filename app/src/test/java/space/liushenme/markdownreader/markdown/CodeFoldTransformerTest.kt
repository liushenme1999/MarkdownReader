package space.liushenme.markdownreader.markdown

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeFoldTransformerTest {

    private val sample = """
        fun first() {
            val value = 1
            println(value)
        }

        fun second() {
            println(2)
        }
    """.trimIndent()

    private val classSample = """
        class Holder {
            fun compute(value: Int): Int {
                return value * 2
            }
        }
    """.trimIndent()

    private val chineseSample = """
        fun 你好() {
            世界()
        }

        fun 再见() {
            回头见()
        }
    """.trimIndent()

    @Test
    fun placeholderPrefixUsesChineseLabel() {
        assertEquals("⋯ 已折叠", CodeFoldTransformer.FOLD_PLACEHOLDER_PREFIX)
    }

    @Test
    fun emptyCollapsedSetReturnsSourceAndIdentityMapping() {
        val result = transform(sample, emptySet())

        assertEquals(sample, result.text)
        assertArrayEquals(IntArray(8) { it + 1 }, result.displayLineToSourceLine)
        assertArrayEquals(IntArray(8) { it + 1 }, result.sourceLineToDisplayLine)
        assertArrayEquals(expectedLineStarts(sample), result.displayLineStarts)
        assertArrayEquals(expectedLineStarts(sample), result.sourceLineStarts)
        assertInvariants(result, sample)
    }

    @Test
    fun collapsedStartLinesWithoutMatchingDefinitionIsIdentity() {
        val result = transform(sample, setOf(0, 5, 99, -3))

        assertEquals(sample, result.text)
        assertArrayEquals(IntArray(8) { it + 1 }, result.displayLineToSourceLine)
        assertArrayEquals(IntArray(8) { it + 1 }, result.sourceLineToDisplayLine)
        assertInvariants(result, sample)
    }

    @Test
    fun functionBodyBecomesPlaceholderAndKeepsDeclarationLine() {
        val result = transform(sample, setOf(1))

        assertEquals(
            listOf(
                "fun first() {",
                "⋯ 已折叠 3 行",
                "",
                "fun second() {",
                "    println(2)",
                "}",
            ),
            displayLines(result.text),
        )
        assertArrayEquals(intArrayOf(1, 1, 5, 6, 7, 8), result.displayLineToSourceLine)
        assertArrayEquals(intArrayOf(1, 2, 2, 2, 3, 4, 5, 6), result.sourceLineToDisplayLine)
        assertInvariants(result, sample)
    }

    @Test
    fun foldingLastFunctionDropsBodyThroughEndOfFile() {
        val result = transform(sample, setOf(6))

        assertEquals(
            listOf(
                "fun first() {",
                "    val value = 1",
                "    println(value)",
                "}",
                "",
                "fun second() {",
                "⋯ 已折叠 2 行",
            ),
            displayLines(result.text),
        )
        assertFalse(result.text.endsWith("\n"))
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 5, 6, 6), result.displayLineToSourceLine)
        assertArrayEquals(intArrayOf(1, 2, 3, 4, 5, 6, 7, 7), result.sourceLineToDisplayLine)
        assertInvariants(result, sample)
    }

    @Test
    fun foldingLastFunctionKeepsTrailingNewline() {
        val source = "$sample\n"
        val result = transform(source, setOf(6))

        assertTrue(result.text.endsWith("⋯ 已折叠 2 行\n"))
        assertEquals(7, displayLines(result.text).size)
        assertEquals(result.text.length, result.displayLineStarts.last())
        assertInvariants(result, source)
    }

    @Test
    fun nestedInnerFoldIsIgnoredWhenOuterFoldCoversIt() {
        val result = transform(classSample, setOf(1, 2))

        assertEquals(listOf("class Holder {", "⋯ 已折叠 4 行"), displayLines(result.text))
        assertArrayEquals(intArrayOf(1, 1), result.displayLineToSourceLine)
        assertArrayEquals(intArrayOf(1, 2, 2, 2, 2), result.sourceLineToDisplayLine)
        assertFalse(result.text.contains("fun compute"))
        assertInvariants(result, classSample)
    }

    @Test
    fun foldingOnlyInnerFunctionKeepsSurroundingClassLines() {
        val result = transform(classSample, setOf(2))

        assertEquals(
            listOf(
                "class Holder {",
                "    fun compute(value: Int): Int {",
                "    ⋯ 已折叠 2 行",
                "}",
            ),
            displayLines(result.text),
        )
        assertArrayEquals(intArrayOf(1, 2, 2, 5), result.displayLineToSourceLine)
        assertArrayEquals(intArrayOf(1, 2, 3, 3, 4), result.sourceLineToDisplayLine)
        assertInvariants(result, classSample)
    }

    @Test
    fun tabIndentedDeclarationKeepsTabIndentInPlaceholder() {
        val source = "class A {\n\tfun b() {\n\t\tx()\n\t}\n}"
        val result = transform(source, setOf(2))

        assertEquals(
            listOf("class A {", "\tfun b() {", "\t⋯ 已折叠 2 行", "}"),
            displayLines(result.text),
        )
        assertInvariants(result, source)
    }

    @Test
    fun crlfSourceKeepsCarriageReturnsAndTrailingNewline() {
        val source = "fun first() {\r\n    val value = 1\r\n    println(value)\r\n}\r\n" +
            "\r\nfun second() {\r\n    println(2)\r\n}\r\n"
        val result = transform(source, setOf(1))

        assertEquals(
            listOf(
                "fun first() {\r",
                "⋯ 已折叠 3 行",
                "\r",
                "fun second() {\r",
                "    println(2)\r",
                "}\r",
            ),
            displayLines(result.text),
        )
        assertEquals(8, result.sourceLineToDisplayLine.size)
        assertArrayEquals(intArrayOf(1, 1, 5, 6, 7, 8), result.displayLineToSourceLine)
        assertArrayEquals(intArrayOf(1, 2, 2, 2, 3, 4, 5, 6), result.sourceLineToDisplayLine)
        assertTrue(result.text.endsWith("}\r\n"))
        assertTrue(result.text.contains("fun second() {\r\n    println(2)\r\n"))
        assertEquals(
            result.text.indexOf("⋯ 已折叠"),
            result.sourceOffsetToDisplayOffset(source.indexOf("val value")),
        )
        assertInvariants(result, source)
    }

    @Test
    fun crlfSourceFoldingThroughEndOfFileKeepsTrailingNewline() {
        val source = "fun crlf() {\r\n    work()\r\n}\r\n"
        val result = transform(source, setOf(1))

        assertEquals(
            listOf("fun crlf() {\r", "⋯ 已折叠 2 行", ""),
            result.text.split("\n"),
        )
        assertArrayEquals(intArrayOf(1, 1), result.displayLineToSourceLine)
        assertArrayEquals(intArrayOf(1, 2, 2), result.sourceLineToDisplayLine)
        assertInvariants(result, source)
    }

    @Test
    fun emptySourceYieldsEmptyTextAndSingleLogicalLine() {
        val result = transform("", setOf(1))

        assertEquals("", result.text)
        assertArrayEquals(intArrayOf(1), result.displayLineToSourceLine)
        assertArrayEquals(intArrayOf(1), result.sourceLineToDisplayLine)
        assertArrayEquals(intArrayOf(0, 0), result.displayLineStarts)
        assertArrayEquals(intArrayOf(0, 0), result.sourceLineStarts)
        assertEquals(0, result.sourceOffsetToDisplayOffset(7))
        assertEquals(0, result.displayOffsetToSourceOffset(-7))
        assertInvariants(result, "")
    }

    @Test
    fun singleLineSourceWithoutBodyIsIdentity() {
        val source = "fun noop() {}"
        val result = transform(source, setOf(1))

        assertEquals(source, result.text)
        assertArrayEquals(intArrayOf(1), result.displayLineToSourceLine)
        assertArrayEquals(intArrayOf(0, source.length), result.displayLineStarts)
        assertInvariants(result, source)

        val withNewline = "$source\n"
        val newlineResult = transform(withNewline, setOf(1))

        assertEquals(withNewline, newlineResult.text)
        assertArrayEquals(expectedLineStarts(withNewline), newlineResult.displayLineStarts)
        assertInvariants(newlineResult, withNewline)
    }

    @Test
    fun chineseTextKeepsPerCharOffsets() {
        val result = transform(chineseSample, setOf(5))

        assertEquals(
            listOf(
                "fun 你好() {",
                "    世界()",
                "}",
                "",
                "fun 再见() {",
                "⋯ 已折叠 2 行",
            ),
            displayLines(result.text),
        )
        val retained = chineseSample.indexOf("世界")
        assertEquals(retained, result.sourceOffsetToDisplayOffset(retained))
        assertEquals(retained, result.displayOffsetToSourceOffset(retained))
        assertEquals(retained + 3, result.sourceOffsetToDisplayOffset(retained + 3))
        val folded = chineseSample.indexOf("回头见")
        assertEquals(result.text.indexOf("⋯ 已折叠"), result.sourceOffsetToDisplayOffset(folded))
        assertEquals(result.text.indexOf("⋯ 已折叠"), result.sourceOffsetToDisplayOffset(folded + 1))
        assertEquals(
            chineseSample.indexOf("fun 再见"),
            result.displayOffsetToSourceOffset(result.text.indexOf("⋯ 已折叠")),
        )
        assertInvariants(result, chineseSample)
    }

    @Test
    fun foldedBodyOffsetsMapToPlaceholderLineStart() {
        val result = transform(sample, setOf(1))
        val placeholderStart = result.text.indexOf("⋯ 已折叠")

        assertEquals(placeholderStart, result.sourceOffsetToDisplayOffset(sample.indexOf("val value")))
        assertEquals(
            placeholderStart,
            result.sourceOffsetToDisplayOffset(sample.indexOf("val value") + 6),
        )
        assertEquals(
            placeholderStart,
            result.sourceOffsetToDisplayOffset(sample.indexOf("println(value)")),
        )
    }

    @Test
    fun placeholderLineOffsetsMapToDefinitionStartLine() {
        val result = transform(sample, setOf(1))
        val placeholderStart = result.text.indexOf("⋯ 已折叠")

        assertEquals(0, result.displayOffsetToSourceOffset(placeholderStart))
        assertEquals(0, result.displayOffsetToSourceOffset(placeholderStart + 4))
        assertEquals(0, result.displayOffsetToSourceOffset(placeholderStart + "⋯ 已折叠 3 行".length))
    }

    @Test
    fun retainedLinesKeepTheirSourceOffsets() {
        val result = transform(sample, setOf(1))

        assertEquals(0, result.sourceOffsetToDisplayOffset(0))
        assertEquals(0, result.displayOffsetToSourceOffset(0))
        assertEquals(
            result.text.indexOf("println(2)"),
            result.sourceOffsetToDisplayOffset(sample.indexOf("println(2)")),
        )
        assertEquals(
            sample.indexOf("    println(2)"),
            result.displayOffsetToSourceOffset(result.text.indexOf("    println(2)")),
        )
        assertEquals(
            sample.indexOf("fun second"),
            result.displayOffsetToSourceOffset(result.text.indexOf("fun second")),
        )
    }

    @Test
    fun offsetsOutsideTextAreClamped() {
        val result = transform(sample, setOf(1))

        assertEquals(0, result.sourceOffsetToDisplayOffset(-9))
        assertEquals(result.text.length, result.sourceOffsetToDisplayOffset(sample.length + 99))
        assertEquals(0, result.displayOffsetToSourceOffset(-9))
        assertEquals(sample.length, result.displayOffsetToSourceOffset(result.text.length + 99))
    }

    @Test
    fun sourceEndOffsetMapsPastAFoldedTail() {
        val result = transform(sample, setOf(6))

        assertEquals(result.text.indexOf("⋯ 已折叠"), result.sourceOffsetToDisplayOffset(sample.length))
        assertEquals(sample.indexOf("fun second"), result.displayOffsetToSourceOffset(result.text.length))
    }

    @Test
    fun identityMappingRoundTripsEveryOffset() {
        val result = transform(sample, emptySet())

        for (offset in 0..sample.length) {
            assertEquals(offset, result.sourceOffsetToDisplayOffset(offset))
            assertEquals(offset, result.displayOffsetToSourceOffset(offset))
        }
    }

    @Test
    fun multipleFoldsAreIndependentOfCollapsedSetOrder() {
        val first = transform(sample, setOf(1, 6))
        val second = transform(sample, setOf(6, 1))

        assertEquals(
            listOf(
                "fun first() {",
                "⋯ 已折叠 3 行",
                "",
                "fun second() {",
                "⋯ 已折叠 2 行",
            ),
            displayLines(first.text),
        )
        assertArrayEquals(first.displayLineToSourceLine, second.displayLineToSourceLine)
        assertArrayEquals(first.sourceLineToDisplayLine, second.sourceLineToDisplayLine)
        assertEquals(first.text, second.text)
        assertInvariants(first, sample)
    }

    @Test
    fun foldingBothFunctionsMapsEachBodyToItsOwnPlaceholder() {
        val result = transform(sample, setOf(1, 6))

        assertEquals(2, result.text.split("⋯ 已折叠").size - 1)
        assertEquals(2, result.sourceLineToDisplayLine[1])
        assertEquals(2, result.sourceLineToDisplayLine[3])
        assertEquals(4, result.sourceLineToDisplayLine[5])
        assertEquals(5, result.sourceLineToDisplayLine[6])
        assertEquals(5, result.sourceLineToDisplayLine[7])
    }

    private fun transform(source: String, collapsedStartLines: Set<Int>): FoldedCodeSource {
        val outline = CodeOutlineParser.parse(source, "kotlin")
        return CodeFoldTransformer.transform(source, outline, collapsedStartLines)
    }

    /**
     * 与实现无关地重算行首偏移：每行行首 + 末尾 text.length 哨兵。
     * 文本以换行结尾时不会多出一个空行（与 CodeDefinition 的行号计数一致）。
     */
    private fun expectedLineStarts(text: String): IntArray {
        val starts = ArrayList<Int>()
        starts += 0
        text.forEachIndexed { index, char ->
            if (char == '\n' && index + 1 < text.length) starts += index + 1
        }
        starts += text.length
        return starts.toIntArray()
    }

    private fun displayLines(text: String): List<String> =
        if (text.endsWith('\n')) text.dropLast(1).split("\n") else text.split("\n")

    private fun assertInvariants(result: FoldedCodeSource, source: String) {
        val sourceLines = displayLines(source)
        val displayLineList = displayLines(result.text)

        assertArrayEquals(expectedLineStarts(source), result.sourceLineStarts)
        assertEquals(source.length, result.sourceLineStarts.last())
        assertEquals(sourceLines.size, result.sourceLineToDisplayLine.size)

        assertArrayEquals(expectedLineStarts(result.text), result.displayLineStarts)
        assertEquals(result.text.length, result.displayLineStarts.last())
        assertEquals(displayLineList.size + 1, result.displayLineStarts.size)
        assertEquals(displayLineList.size, result.displayLineToSourceLine.size)

        for (sourceLine in result.displayLineToSourceLine) {
            assertTrue(sourceLine in 1..sourceLines.size)
        }
        for (displayLine in result.sourceLineToDisplayLine) {
            assertTrue(displayLine in 1..displayLineList.size)
        }
        for (index in sourceLines.indices) {
            val displayLine = result.sourceLineToDisplayLine[index]
            assertEquals(
                result.displayLineStarts[displayLine - 1],
                result.sourceOffsetToDisplayOffset(result.sourceLineStarts[index]),
            )
        }
    }
}
