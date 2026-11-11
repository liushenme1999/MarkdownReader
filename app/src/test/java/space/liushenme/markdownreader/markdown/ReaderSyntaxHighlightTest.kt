package space.liushenme.markdownreader.markdown

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReaderSyntaxHighlightTest {

    @Test
    fun python_coversCommentsStringsFunctionsTypesNumbersAndKeywords() {
        val source = """
            # return "not a keyword"
            def greet(name: str) -> int:
                value = "return # still a string"
                return 42
        """.trimIndent()
        val rendered = highlight("python", source)
        val palette = CodeHighlightTheme.DEFAULT.palette

        assertTokenColor(rendered, "# return \"not a keyword\"", palette.comment)
        assertTokenColor(rendered, "\"return # still a string\"", palette.string)
        assertTokenColor(rendered, "def", palette.keyword)
        assertTokenColor(rendered, "greet", palette.function)
        assertTokenColor(rendered, "str", palette.type)
        assertTokenColor(rendered, "return", palette.keyword, occurrence = 2)
        assertTokenColor(rendered, "42", palette.number)
        assertNoNestedSpan(rendered, "return", occurrence = 1, enclosingColor = palette.string)
    }

    @Test
    fun jvm_coversKeywordsTypesFunctionsStringsCommentsAndNumbers() {
        val source = """
            public class Demo {
                private String name = "class return";
                // public void fake() { return; }
                public static void main(String[] args) {
                    int count = 42;
                }
            }
        """.trimIndent()
        val rendered = highlight("java", source)
        val palette = CodeHighlightTheme.DEFAULT.palette

        assertTokenColor(rendered, "class", palette.keyword)
        assertTokenColor(rendered, "String", palette.type)
        assertTokenColor(rendered, "main", palette.function)
        assertTokenColor(rendered, "\"class return\"", palette.string)
        assertTokenColor(rendered, "// public void fake() { return; }", palette.comment)
        assertTokenColor(rendered, "42", palette.number)
        assertNoNestedSpan(rendered, "public", occurrence = 1, enclosingColor = palette.comment)
    }

    @Test
    fun javascriptAndTypeScript_keepProtectedRangesAndHighlightTypes() {
        val source = """
            const message: string = "return 42"; // const seven
            function run(count) { return count + 7; }
            const answer = 99;
        """.trimIndent()
        val rendered = highlight("ts", source)
        val palette = CodeHighlightTheme.DEFAULT.palette

        assertTokenColor(rendered, "const", palette.keyword)
        assertTokenColor(rendered, "string", palette.type)
        assertTokenColor(rendered, "run", palette.function)
        assertTokenColor(rendered, "\"return 42\"", palette.string)
        assertTokenColor(rendered, "// const seven", palette.comment)
        assertTokenColor(rendered, "99", palette.number)
        assertNoNestedSpan(rendered, "42", enclosingColor = palette.string)
        assertNoNestedSpan(rendered, "return", occurrence = 0, enclosingColor = palette.string)
        assertNoNestedSpan(rendered, "const", occurrence = 1, enclosingColor = palette.comment)
    }

    @Test
    fun vue_usesHtmlAndJavaScriptTokensWithoutLosingTemplateMarkup() {
        val source = """
            <template>
              <div class="app">{{ label }}</div>
            </template>
            <script setup>
            const message = "return"
            // return class
            </script>
        """.trimIndent()
        val rendered = highlight("vue", source)
        val palette = CodeHighlightTheme.DEFAULT.palette

        assertTokenColor(rendered, "<template", palette.tag)
        assertTokenColor(rendered, "class", palette.attribute)
        assertTokenColor(rendered, "\"app\"", palette.string)
        assertTokenColor(rendered, "const", palette.keyword)
        assertTokenColor(rendered, "// return class", palette.comment)
        assertNoNestedSpan(rendered, "return", occurrence = 0, enclosingColor = palette.string)
    }

    @Test
    fun htmlXmlAndCss_coverTagsAttributesCommentsAndValues() {
        val html = """
            <!DOCTYPE html>
            <section class="hero" data-id="item">
              <h1>Title</h1>
              <p>42</p>
            </section>
        """.trimIndent()
        val htmlRendered = highlight("html", html)
        val palette = CodeHighlightTheme.DEFAULT.palette
        assertTokenColor(htmlRendered, "<section", palette.tag)
        assertTokenColor(htmlRendered, "class", palette.attribute)
        assertTokenColor(htmlRendered, "\"hero\"", palette.string)
        assertTokenColor(htmlRendered, "42", palette.number)

        val css = """
            /* public class */
            .card {
              color: #ff0000;
              margin: 8px;
            }
        """.trimIndent()
        val cssRendered = highlight("css", css)
        assertTokenColor(cssRendered, "/* public class */", palette.comment)
        assertTokenColor(cssRendered, ".card", palette.tag)
        assertTokenColor(cssRendered, "color", palette.attribute)
        assertTokenColor(cssRendered, "#ff0000", palette.number)
        assertTokenColor(cssRendered, "8px", palette.number)
        assertNoNestedSpan(cssRendered, "class", enclosingColor = palette.comment)
    }

    @Test
    fun configurationFamilies_distinguishKeysStringsCommentsAndLiterals() {
        val palette = CodeHighlightTheme.DEFAULT.palette

        val json = """{"name": "return", "enabled": true, "count": 42}"""
        val jsonRendered = highlight("json", json)
        assertTokenColor(jsonRendered, "\"name\"", palette.attribute)
        assertTokenColor(jsonRendered, "\"return\"", palette.string)
        assertTokenColor(jsonRendered, "true", palette.keyword)
        assertTokenColor(jsonRendered, "42", palette.number)

        val yaml = """
            name: "return"
            # class
            enabled: true
            count: 42
        """.trimIndent()
        val yamlRendered = highlight("yaml", yaml)
        assertTokenColor(yamlRendered, "name", palette.attribute)
        assertTokenColor(yamlRendered, "\"return\"", palette.string)
        assertTokenColor(yamlRendered, "# class", palette.comment)
        assertTokenColor(yamlRendered, "true", palette.keyword)
        assertTokenColor(yamlRendered, "42", palette.number)

        val toml = """
            name = "return"
            # class
            enabled = true
            count = 42
        """.trimIndent()
        val tomlRendered = highlight("toml", toml)
        assertTokenColor(tomlRendered, "name", palette.attribute)
        assertTokenColor(tomlRendered, "\"return\"", palette.string)
        assertTokenColor(tomlRendered, "# class", palette.comment)
        assertTokenColor(tomlRendered, "true", palette.keyword)
        assertTokenColor(tomlRendered, "42", palette.number)

        val ini = """
            name = "return"
            ; class
            enabled = true
            count = 42
        """.trimIndent()
        val iniRendered = highlight("ini", ini)
        assertTokenColor(iniRendered, "name", palette.attribute)
        assertTokenColor(iniRendered, "\"return\"", palette.string)
        assertTokenColor(iniRendered, "; class", palette.comment)
        assertTokenColor(iniRendered, "true", palette.keyword)
        assertTokenColor(iniRendered, "42", palette.number)
    }

    @Test
    fun shellPowerShellAndSql_coverTheirCommonTokenFamilies() {
        val palette = CodeHighlightTheme.DEFAULT.palette

        val shell = """
            # return class
            name="run"
            touch ${'$'}target
            if [ "${'$'}name" = "run" ]; then
              echo 42
            fi
        """.trimIndent()
        val shellRendered = highlight("sh", shell)
        assertTokenColor(shellRendered, "# return class", palette.comment)
        assertTokenColor(shellRendered, "\"run\"", palette.string)
        assertTokenColor(shellRendered, "if", palette.keyword)
        assertTokenColor(shellRendered, "${'$'}target", palette.variable)
        assertNoNestedSpan(shellRendered, "${'$'}name", enclosingColor = palette.string)
        assertTokenColor(shellRendered, "42", palette.number)

        val powerShell = """
            # return class
            ${'$'}name = "run"
            function Invoke-Run { return 42 }
        """.trimIndent()
        val powerShellRendered = highlight("powershell", powerShell)
        assertTokenColor(powerShellRendered, "# return class", palette.comment)
        assertTokenColor(powerShellRendered, "${'$'}name", palette.variable)
        assertTokenColor(powerShellRendered, "\"run\"", palette.string)
        assertTokenColor(powerShellRendered, "Invoke-Run", palette.function)
        assertTokenColor(powerShellRendered, "return", palette.keyword, occurrence = 1)
        assertTokenColor(powerShellRendered, "42", palette.number)

        val sql = """
            -- return class
            SELECT name, COUNT(*) AS total
            FROM users
            WHERE id = 42 AND name <> 'return';
        """.trimIndent()
        val sqlRendered = highlight("sql", sql)
        assertTokenColor(sqlRendered, "-- return class", palette.comment)
        assertTokenColor(sqlRendered, "SELECT", palette.keyword)
        assertTokenColor(sqlRendered, "COUNT", palette.function)
        assertTokenColor(sqlRendered, "42", palette.number)
        assertTokenColor(sqlRendered, "'return'", palette.string)
    }

    @Test
    fun eachThemeUsesItsOwnPaletteAndUnknownLanguageRemainsUncolored() {
        val source = "return 42 // comment"
        CodeHighlightTheme.all.forEach { theme ->
            val rendered = ReaderSyntaxHighlight(theme).highlight(
                "javascript",
                source,
            ) as Spanned
            assertTokenColor(rendered, "return", theme.palette.keyword)
            assertTokenColor(rendered, "42", theme.palette.number)
            assertTokenColor(rendered, "// comment", theme.palette.comment)
        }

        val unknown = ReaderSyntaxHighlight().highlight("unknown-language", source)
        assertTrue(unknown is String)
        assertEquals(source, unknown.toString())
    }

    @Test
    fun uppercaseAndDecoratedFenceLanguage_areNormalized() {
        val rendered = highlight("  VUE {.line-numbers}  ", "const value = 1")
        assertTokenColor(
            rendered,
            "const",
            CodeHighlightTheme.DEFAULT.palette.keyword,
        )
    }

    private fun highlight(language: String, source: String): Spanned =
        ReaderSyntaxHighlight().highlight(language, source) as Spanned

    private fun assertTokenColor(
        rendered: Spanned,
        token: String,
        expectedColor: Int,
        occurrence: Int = 0,
    ) {
        val range = tokenRange(rendered, token, occurrence)
        val spans = spansCovering(rendered, range)
        assertEquals("spans covering '$token': $spans", 1, spans.size)
        assertEquals("color for '$token'", expectedColor, spans.single().foregroundColor)
    }

    private fun assertNoNestedSpan(
        rendered: Spanned,
        token: String,
        enclosingColor: Int,
        occurrence: Int = 0,
    ) {
        val range = tokenRange(rendered, token, occurrence)
        val spans = spansCovering(rendered, range)
        assertEquals("spans covering '$token': $spans", 1, spans.size)
        assertEquals(enclosingColor, spans.single().foregroundColor)
    }

    private fun tokenRange(rendered: Spanned, token: String, occurrence: Int): IntRange {
        val source = rendered.toString()
        var start = -1
        repeat(occurrence + 1) {
            start = source.indexOf(token, start + 1)
        }
        assertTrue("token '$token' occurrence $occurrence not found in: $source", start >= 0)
        return start until start + token.length
    }

    private fun spansCovering(
        rendered: Spanned,
        range: IntRange,
    ): List<ForegroundColorSpan> =
        rendered.getSpans(0, rendered.length, ForegroundColorSpan::class.java)
            .filter { span ->
                rendered.getSpanStart(span) <= range.first &&
                    rendered.getSpanEnd(span) >= range.last + 1
            }
            .sortedBy { rendered.getSpanStart(it) }
}
