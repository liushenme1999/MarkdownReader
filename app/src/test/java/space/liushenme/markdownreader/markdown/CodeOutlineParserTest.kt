package space.liushenme.markdownreader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeOutlineParserTest {

    @Test
    fun java_classAndMethod_areReturnedAsEnclosingPath() {
        val source = """
            public final class VehicleUtil {
                public static int convert(int value) {
                    int grids = value / 2;
                    return grids;
                }
            }
        """.trimIndent()

        val outline = CodeOutlineParser.parse(source, "java")

        val path = outline.pathAtLine(3)
        assertEquals(2, path.size)
        assertTrue(path[0].text.contains("class VehicleUtil"))
        assertTrue(path[1].text.contains("convert(int value)"))
        assertEquals(listOf(0, 1), path.map { it.depth })
        assertTrue(outline.pathAtLine(1).single().text.startsWith("public final class"))
    }

    @Test
    fun kotlin_nestedClassAndFunction_keepSourceDeclarationText() {
        val source = """
            class Outer {
                private class Inner {
                    fun render(name: String): String {
                        return name
                    }
                }
            }
        """.trimIndent()

        val outline = CodeOutlineParser.parse(source, "kotlin")

        val path = outline.pathAtLine(4)
        assertEquals(3, path.size)
        assertEquals(listOf(0, 1, 2), path.map { it.depth })
        assertEquals("fun render(name: String): String {", path.last().text)
    }

    @Test
    fun kotlin_composableCallWithTrailingLambda_isNotTreatedAsFunction() {
        val source = """
            @Composable
            fun Screen() {
                Column {
                    Button(onClick = {}) {
                        Text("Hello")
                    }
                }
            }
        """.trimIndent()

        val outline = CodeOutlineParser.parse(source, "kotlin")

        assertEquals(listOf("fun Screen() {"), outline.definitions.map { it.text })
    }

    @Test
    fun python_nestedClassAndFunctions_followIndentation() {
        val source = """
            class Service:
                def handle(self, value):
                    def normalize(item):
                        return item.strip()
                    return normalize(value)

            def outside():
                pass
        """.trimIndent()

        val outline = CodeOutlineParser.parse(source, "python")

        val path = outline.pathAtLine(4)
        assertEquals(listOf("class Service:", "def handle(self, value):", "def normalize(item):"), path.map { it.text })
        assertEquals(listOf(0, 1, 2), path.map { it.depth })
        assertEquals(listOf("def outside():") , outline.pathAtLine(8).map { it.text })
    }

    @Test
    fun vueScriptSetup_functionsUseFullFileLineNumbers() {
        val source = """
            <template>
              <button>{{ count }}</button>
            </template>
            <script setup>
            function increment(step) {
              count += step
            }
            </script>
        """.trimIndent()

        val outline = CodeOutlineParser.parse(source, "vue")

        val definition = outline.pathAtLine(6).single()
        assertEquals(5, definition.startLine)
        assertEquals(7, definition.endLine)
        assertEquals("function increment(step) {", definition.text)
    }

    @Test
    fun bracesInsideStringsAndComments_doNotCloseEnclosingScope() {
        val source = """
            class Demo {
                // } fake close
                String value = "} still a string";
                /*
                 } block comment
                 */
                void run() {
                    call();
                }
            }
        """.trimIndent()

        val outline = CodeOutlineParser.parse(source, "java")

        val method = outline.definitions.single { it.text.contains("void run") }
        assertEquals(7, method.startLine)
        assertEquals(9, method.endLine)
        assertEquals(listOf("class Demo {", "void run() {"), outline.pathAtLine(7).map { it.text })
    }

    @Test
    fun unsupportedMarkupAndDataLanguages_haveNoStickyDefinitions() {
        val source = """
            <template>
              <section>Hello</section>
            </template>
            {"enabled": true}
        """.trimIndent()

        assertTrue(CodeOutlineParser.parse(source, "html").definitions.isEmpty())
        assertTrue(CodeOutlineParser.parse(source, "json").definitions.isEmpty())
    }
}
