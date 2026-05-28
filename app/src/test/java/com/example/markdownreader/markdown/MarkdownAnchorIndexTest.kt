package com.example.markdownreader.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownAnchorIndexTest {

    @Test
    fun slugify_matchesGfmStyleForChineseHeading() {
        assertEquals("一标题层级", MarkdownAnchorIndex.slugify("一、标题层级"))
    }
}
