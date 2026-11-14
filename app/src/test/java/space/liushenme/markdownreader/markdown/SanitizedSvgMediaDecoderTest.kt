package space.liushenme.markdownreader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SanitizedSvgMediaDecoderTest {

    @Test
    fun replacesSvg2MarkerOrientation() {
        // Mermaid/绘图工具导出的箭头标记：AndroidSVG 会抛 Invalid float value
        val source = """<svg xmlns="http://www.w3.org/2000/svg">
            <marker id="a" orient="auto-start-reverse"><path d="M0,0 L5,5"/></marker>
        </svg>"""
        val sanitized = SanitizedSvgMediaDecoder.sanitize(source)
        assertFalse(sanitized.contains("auto-start-reverse"))
        assertEquals(1, Regex("orient=\"auto\"").findAll(sanitized).count())
    }

    @Test
    fun keepsRegularMarkerOrientation() {
        val source = """<marker id="a" orient="auto"/>"""
        assertEquals(source, SanitizedSvgMediaDecoder.sanitize(source))
    }

    @Test
    fun handlesEmptyInput() {
        assertEquals("", SanitizedSvgMediaDecoder.sanitize(""))
    }

    @Test
    fun supportedTypeIsSvgMime() {
        assertEquals(listOf("image/svg+xml"), SanitizedSvgMediaDecoder.CONTENT_TYPE.let { listOf(it) })
    }
}
