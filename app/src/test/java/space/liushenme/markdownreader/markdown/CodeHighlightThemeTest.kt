package space.liushenme.markdownreader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHighlightThemeTest {

    @Test
    fun builtInThemes_haveStableIdsAndExpectedAppearance() {
        assertEquals(
            listOf("VS_LIGHT_PLUS", "VS_DARK_PLUS", "MONOKAI", "GITHUB_LIGHT"),
            CodeHighlightTheme.all.map { it.stableId },
        )
        assertEquals(
            listOf("VS Light+", "VS Dark+", "Monokai", "GitHub Light"),
            CodeHighlightTheme.all.map { it.displayName },
        )
        assertEquals(
            listOf(false, true, true, false),
            CodeHighlightTheme.all.map { it.isDark },
        )
        assertEquals(CodeHighlightTheme.VS_LIGHT_PLUS, CodeHighlightTheme.DEFAULT)
        assertEquals(CodeHighlightTheme.all.size, CodeHighlightTheme.all.map { it.stableId }.toSet().size)
    }

    @Test
    fun fromStored_acceptsStableIdsAndFallsBackForMissingOrUnknownValues() {
        CodeHighlightTheme.all.forEach { theme ->
            assertEquals(theme, CodeHighlightTheme.fromStored(theme.stableId))
        }

        assertEquals(CodeHighlightTheme.VS_LIGHT_PLUS, CodeHighlightTheme.fromStored(null))
        assertEquals(CodeHighlightTheme.VS_LIGHT_PLUS, CodeHighlightTheme.fromStored(""))
        assertEquals(CodeHighlightTheme.VS_LIGHT_PLUS, CodeHighlightTheme.fromStored("unknown"))
        assertEquals(
            CodeHighlightTheme.MONOKAI,
            CodeHighlightTheme.fromStored("  MONOKAI  "),
        )
    }

    @Test
    fun palettes_matchClassicVsCodeStyleValues() {
        assertPalette(
            theme = CodeHighlightTheme.VS_LIGHT_PLUS,
            background = 0xFFFFFFFF.toInt(),
            foreground = 0xFF000000.toInt(),
            comment = 0xFF008000.toInt(),
            string = 0xFFA31515.toInt(),
            keyword = 0xFF0000FF.toInt(),
            number = 0xFF098658.toInt(),
            function = 0xFF795E26.toInt(),
            type = 0xFF267F99.toInt(),
            tag = 0xFF800000.toInt(),
            attribute = 0xFF001080.toInt(),
        )
        assertPalette(
            theme = CodeHighlightTheme.VS_DARK_PLUS,
            background = 0xFF1E1E1E.toInt(),
            foreground = 0xFFD4D4D4.toInt(),
            comment = 0xFF6A9955.toInt(),
            string = 0xFFCE9178.toInt(),
            keyword = 0xFFC586C0.toInt(),
            number = 0xFFB5CEA8.toInt(),
            function = 0xFFDCDCAA.toInt(),
            type = 0xFF4EC9B0.toInt(),
            tag = 0xFF569CD6.toInt(),
            attribute = 0xFF9CDCFE.toInt(),
        )
        assertPalette(
            theme = CodeHighlightTheme.MONOKAI,
            background = 0xFF272822.toInt(),
            foreground = 0xFFF8F8F2.toInt(),
            comment = 0xFF75715E.toInt(),
            string = 0xFFE6DB74.toInt(),
            keyword = 0xFFF92672.toInt(),
            number = 0xFFAE81FF.toInt(),
            function = 0xFFA6E22E.toInt(),
            type = 0xFF66D9EF.toInt(),
            tag = 0xFFF92672.toInt(),
            attribute = 0xFFA6E22E.toInt(),
        )
        assertPalette(
            theme = CodeHighlightTheme.GITHUB_LIGHT,
            background = 0xFFFFFFFF.toInt(),
            foreground = 0xFF24292F.toInt(),
            comment = 0xFF6E7781.toInt(),
            string = 0xFF0A3069.toInt(),
            keyword = 0xFFCF222E.toInt(),
            number = 0xFF0550AE.toInt(),
            function = 0xFF8250DF.toInt(),
            type = 0xFF953800.toInt(),
            tag = 0xFF116329.toInt(),
            attribute = 0xFF0550AE.toInt(),
        )
    }

    @Test
    fun everyPalette_hasReadableForegroundAndDistinctSemanticColors() {
        CodeHighlightTheme.all.forEach { theme ->
            val palette = theme.palette
            assertTrue(
                "${theme.displayName} contrast",
                contrastRatio(palette.background, palette.foreground) >= 4.5,
            )
            assertFalse(
                "${theme.displayName} selection should be translucent",
                alphaOf(palette.selection) == 0xFF,
            )
            assertTrue(
                "${theme.displayName} token palette",
                setOf(
                    palette.comment,
                    palette.string,
                    palette.keyword,
                    palette.number,
                    palette.function,
                    palette.type,
                ).size >= 5,
            )
        }
    }

    private fun assertPalette(
        theme: CodeHighlightTheme,
        background: Int,
        foreground: Int,
        comment: Int,
        string: Int,
        keyword: Int,
        number: Int,
        function: Int,
        type: Int,
        tag: Int,
        attribute: Int,
    ) {
        val palette = theme.palette
        assertEquals(background, palette.background)
        assertEquals(foreground, palette.foreground)
        assertEquals(comment, palette.comment)
        assertEquals(string, palette.string)
        assertEquals(keyword, palette.keyword)
        assertEquals(number, palette.number)
        assertEquals(function, palette.function)
        assertEquals(type, palette.type)
        assertEquals(tag, palette.tag)
        assertEquals(attribute, palette.attribute)
        assertEquals(attribute, palette.variable)
    }

    private fun contrastRatio(background: Int, foreground: Int): Double {
        val light = maxOf(relativeLuminance(background), relativeLuminance(foreground))
        val dark = minOf(relativeLuminance(background), relativeLuminance(foreground))
        return (light + 0.05) / (dark + 0.05)
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val srgb = value / 255.0
            return if (srgb <= 0.04045) {
                srgb / 12.92
            } else {
                Math.pow((srgb + 0.055) / 1.055, 2.4)
            }
        }
        val red = channel((color shr 16) and 0xFF)
        val green = channel((color shr 8) and 0xFF)
        val blue = channel(color and 0xFF)
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }

    private fun alphaOf(color: Int): Int = color ushr 24
}
