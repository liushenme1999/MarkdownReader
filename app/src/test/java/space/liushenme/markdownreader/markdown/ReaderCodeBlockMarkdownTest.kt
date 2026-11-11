package space.liushenme.markdownreader.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderCodeBlockMarkdownTest {

    @Test
    fun wrapsSourceWithMinimumFenceAndKeepsContentVerbatim() {
        val source = "# heading\n| a | b |\n<template>{{ value }}</template>"

        val wrapped = ReaderCodeBlockMarkdown.wrap(source, "vue")

        assertEquals("```vue\n$source\n```", wrapped)
    }

    @Test
    fun fenceIsLongerThanEveryBacktickRunInSource() {
        val source = "const a = ```\nconst b = ````\nconst c = ``"

        val wrapped = ReaderCodeBlockMarkdown.wrap(source, "javascript")

        assertTrue(wrapped.startsWith("`````javascript\n"))
        assertTrue(wrapped.endsWith("\n`````"))
        assertTrue(wrapped.contains(source))
    }

    @Test
    fun sanitizesFenceInfoWithoutChangingBody() {
        val source = "plain text"

        val wrapped = ReaderCodeBlockMarkdown.wrap(source, "js {.line-numbers}\n`")

        assertEquals("```js\n$source\n```", wrapped)
    }
}
