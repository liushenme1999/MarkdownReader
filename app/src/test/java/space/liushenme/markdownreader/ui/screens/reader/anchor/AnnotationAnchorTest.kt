package space.liushenme.markdownreader.ui.screens.reader.anchor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import space.liushenme.markdownreader.data.local.entity.BookmarkEntity
import space.liushenme.markdownreader.data.local.entity.HighlightEntity
import space.liushenme.markdownreader.ui.screens.reader.MarkdownTocEntry

class AnnotationAnchorTest {

    @Test
    fun markdownBlocks_keepFenceWhole_andSplitListItems() {
        val markdown = "# 标题\n\n段落一\n\n- 列表甲\n- 列表乙\n\n```\ncode\nline\n```\n\n段落二\n"
        val blocks = TextBlockIndex.build(markdown, plainText = false).blocks.map { it.text }
        assertEquals(
            listOf(
                "# 标题",
                "段落一",
                "- 列表甲",
                "- 列表乙",
                "```\ncode\nline\n```",
                "段落二",
            ),
            blocks,
        )
    }

    @Test
    fun plainText_splitsOnBlankLines_andSubdividesLongBlocks() {
        val paragraphs = TextBlockIndex.build("甲段。\n\n乙段。\n", plainText = true)
        assertEquals(listOf("甲段。", "乙段。"), paragraphs.blocks.map { it.text })

        val lines = TextBlockIndex.build("甲行\n乙行\n", plainText = true)
        assertEquals(listOf("甲行", "乙行"), lines.blocks.map { it.text })

        val longLine = "字".repeat(2500)
        val split = TextBlockIndex.build(longLine, plainText = true)
        assertEquals(3, split.blocks.size)
        assertTrue(split.blocks.all { it.text.length <= TextBlockIndex.SUBBLOCK_CHARS })
        assertEquals(longLine, split.blocks.joinToString("") { it.text })
    }

    @Test
    fun normalize_collapsesHorizontalSpace_andKeepsCase() {
        assertEquals("A B", TextBlockIndex.normalize("A \t B"))
        assertEquals("Ab", TextBlockIndex.normalize("Ab"))
        assertEquals("Ａ", TextBlockIndex.normalize("Ａ"))
    }

    @Test
    fun headingPath_keepsLastFourLevels() {
        val toc = listOf(
            MarkdownTocEntry(level = 1, title = "章", sourceOffset = 0),
            MarkdownTocEntry(level = 2, title = "节", sourceOffset = 10),
            MarkdownTocEntry(level = 3, title = "小节", sourceOffset = 20),
            MarkdownTocEntry(level = 4, title = "四级", sourceOffset = 30),
            MarkdownTocEntry(level = 5, title = "五级", sourceOffset = 40),
            MarkdownTocEntry(level = 2, title = "另一节", sourceOffset = 50),
        )
        assertEquals(
            "节${TextBlockIndex.HEADING_PATH_SEPARATOR}小节" +
                "${TextBlockIndex.HEADING_PATH_SEPARATOR}四级" +
                "${TextBlockIndex.HEADING_PATH_SEPARATOR}五级",
            TextBlockIndex.headingPath(toc, 45),
        )
        assertEquals(
            "章${TextBlockIndex.HEADING_PATH_SEPARATOR}另一节",
            TextBlockIndex.headingPath(toc, 50),
        )
        val section = TextBlockIndex.sectionRange(80, toc, TextBlockIndex.headingPath(toc, 45), 45)
        assertEquals(40 until 50, section)
    }

    @Test
    fun fastPath_keepsSavedOffsetWhenDocumentUnchanged() {
        val document = "同样句子甲\n\n同样句子乙"
        val exact = "同样句子"
        val start = document.lastIndexOf(exact)
        val anchor = captureTextAnchor(document, plainText = true, start, start + exact.length, emptyList(), "same")
        val result = resolve(document, anchor, exact, start, start + exact.length, currentHash = "same")
        val unique = result as AnchorResolveResult.Unique
        assertEquals(start, unique.start)
        assertEquals(start + exact.length, unique.end)
    }

    @Test
    fun duplicateQuote_isDisambiguatedByContext() {
        val pad = "垫".repeat(60)
        val original = "${pad}甲前同样句子甲后\n\n${pad}乙前同样句子乙后\n"
        val edited = "改${pad}甲前同样句子甲后\n\n改${pad}乙前同样句子乙后\n"
        val exact = "同样句子"
        val savedStart = original.lastIndexOf(exact)
        val anchor = captureTextAnchor(
            original,
            plainText = true,
            savedStart,
            savedStart + exact.length,
            emptyList(),
            "old",
        )
        val result = resolve(edited, anchor, exact, savedStart, savedStart + exact.length, currentHash = "new")
        val unique = result as AnchorResolveResult.Unique
        assertEquals(edited.lastIndexOf(exact), unique.start)
    }

    @Test
    fun identicalBlocks_useSurroundingContext() {
        val block = "块内前缀同样句子块内后缀"
        val original = "章节甲\n\n$block\n\n章节乙\n\n$block\n"
        val exact = "同样句子"
        val start = original.lastIndexOf(exact)
        val anchor = captureTextAnchor(original, plainText = true, start, start + exact.length, emptyList(), "old")
        val result = resolve(original, anchor, exact, start, start + exact.length, currentHash = "new")
        val unique = result as AnchorResolveResult.Unique
        assertEquals(start, unique.start)
    }

    @Test
    fun movedBlock_stillHitsByHash() {
        val block = "甲段独特正文在这里不会重复。"
        val original = "前言\n\n$block\n\n乙段。\n"
        val moved = "乙段。\n\n$block\n\n结尾。\n"
        val exact = "独特正文"
        val start = original.indexOf(exact)
        val anchor = captureTextAnchor(original, plainText = true, start, start + exact.length, emptyList(), "old")
        val result = resolve(moved, anchor, exact, start, start + exact.length, currentHash = "new")
        val unique = result as AnchorResolveResult.Unique
        assertEquals(moved.indexOf(exact), unique.start)
        assertTrue(unique.canPaint)
    }

    @Test
    fun lowScoreFuzzy_isNotPaintable() {
        val document = "zzzzXbcdefghijyyyy"
        val query = AnchorQuery(
            exact = "abcdefghij",
            docStart = 0,
            docEnd = 10,
            quotePrefix = "QQQQ",
            quoteSuffix = "RRRR",
            blockIndex = 0,
            blockOffsetStart = 0,
            blockOffsetEnd = 10,
            endBlockIndex = null,
            endBlockHash = null,
            blockHash = "missing",
            docHash = "old",
            headingPath = null,
            point = false,
        )
        val result = AnnotationAnchorResolver.resolve(
            query = query,
            document = document,
            blocks = TextBlockIndex.build(document, plainText = true),
            currentDocHash = "new",
            toc = emptyList(),
        )
        assertFalse(result.canPaint)
        assertTrue(result !is AnchorResolveResult.Unique)
    }

    @Test
    fun ambiguousContext_doesNotReturnUniqueRange() {
        val unit = "前缀同样句子后缀"
        val document = "$unit\n\n$unit"
        val exact = "同样句子"
        val start = document.lastIndexOf(exact)
        val highlight = HighlightEntity(
            id = 7L,
            bookId = 1L,
            startPosition = start,
            endPosition = start + exact.length,
            highlightedText = exact,
            quotePrefix = "前缀",
            quoteSuffix = "后缀",
            blockHash = "missing",
            docHash = "old",
        )
        val plan = planAnnotationAnchors(
            document = document,
            plainText = true,
            docHash = "new",
            toc = emptyList(),
            highlights = listOf(highlight),
            bookmarks = emptyList(),
        )
        assertTrue(plan.paintHighlights.isEmpty())
        assertTrue(7L in plan.pendingHighlightIds)
        assertEquals(start, plan.highlightJump[7L])
    }

    @Test
    fun legacyOffset_usesExactSlice_andIgnoresDistantDuplicate() {
        val document = "选中" + "a".repeat(800) + "选中"
        val exact = "选中"
        val second = document.lastIndexOf(exact)
        val atHint = resolveLegacy(document, exact, second, second + exact.length)
        val kept = atHint as AnchorResolveResult.Unique
        assertEquals(second, kept.start)

        val middle = resolveLegacy(document, exact, 400, 402)
        assertTrue(middle is AnchorResolveResult.Unresolved)
        assertNull(middle.jumpStart)
    }

    @Test
    fun legacyMarkdownHighlight_recoversAcrossBoldLinkAndLineBreak() {
        val document = "开头\n\n这是**重要**内容，[链接文本](https://example.com)\n下一行继续。\n\n结尾"
        val rendered = "这是重要内容，链接文本 下一行继续。"
        val hint = document.indexOf("这是")

        val result = resolveLegacy(document, rendered, hint, hint + rendered.length)

        val unique = result as AnchorResolveResult.Unique
        assertEquals(document.indexOf("这是"), unique.start)
        assertEquals(document.indexOf("下一行继续。") + "下一行继续。".length, unique.end)
    }

    @Test
    fun legacyBookmarkPreview_matchesCollapsedWhitespaceNearSavedPosition() {
        val document = "前文\n\n这里   有\t空白\n以及下一行内容\n\n后文"
        val preview = "这里 有 空白 以及下一行内容"
        val start = document.indexOf("这里")
        val result = AnnotationAnchorResolver.resolve(
            query = AnchorQuery(
                exact = preview,
                docStart = start,
                docEnd = start,
                quotePrefix = null,
                quoteSuffix = null,
                blockIndex = null,
                blockOffsetStart = null,
                blockOffsetEnd = null,
                endBlockIndex = null,
                endBlockHash = null,
                blockHash = null,
                docHash = null,
                headingPath = null,
                point = true,
            ),
            document = document,
            blocks = TextBlockIndex.build(document, plainText = false),
            currentDocHash = "current",
            toc = emptyList(),
        )

        val unique = result as AnchorResolveResult.Unique
        assertEquals(start, unique.start)
        assertEquals(start, unique.end)
    }

    @Test
    fun blockCovering_usesHalfOpenRangesAtParagraphGapAndEof() {
        val document = "甲段\n\n乙段\n"
        val index = TextBlockIndex.build(document, plainText = false)
        val first = index.blocks.first()
        val second = index.blocks.last()

        assertEquals(first, index.blockCovering(0))
        assertNull(index.blockCovering(first.end))
        assertNull(index.blockCovering(first.end + 1))
        assertEquals(second, index.blockCovering(second.start))
        assertNull(index.blockCovering(second.end))
        assertNull(index.blockCovering(document.length))
    }

    @Test
    fun crossBlockHighlight_requiresBothBlocks() {
        val document = "甲块里的起点文字\n\n乙块里的终点文字\n"
        val start = document.indexOf("起点")
        val end = document.indexOf("终点") + "终点".length
        val exact = document.substring(start, end)
        val anchor = captureTextAnchor(document, plainText = true, start, end, emptyList(), "old")
        assertTrue(anchor.endBlockHash != null)
        val hit = resolve(document, anchor, exact, start, end, currentHash = "new")
        val unique = hit as AnchorResolveResult.Unique
        assertEquals(start, unique.start)
        assertEquals(end, unique.end)

        val broken = document.replace("乙块里的终点文字", "乙块已经改写")
        val missed = resolve(broken, anchor, exact, start, end, currentHash = "new")
        assertFalse(missed.canPaint)
    }

    @Test
    fun bookmarkPoint_followsMovedBlock() {
        val block = "这里是一段足够长的书签段落，用来把锚点留在块内。"
        val original = "前言\n\n$block\n\n结尾\n"
        val moved = "另一段\n\n$block\n\n结尾\n"
        val position = original.indexOf("书签段落")
        val anchor = captureTextAnchor(original, plainText = true, position, position, emptyList(), "old")
        val bookmark = BookmarkEntity(
            id = 3L,
            bookId = 1L,
            position = position,
            previewText = "书签段落",
        ).withCapturedAnchor(anchor)
        val blocks = TextBlockIndex.build(moved, plainText = true)
        val result = AnnotationAnchorResolver.resolveBookmark(
            bookmark = bookmark,
            document = moved,
            blocks = blocks,
            currentDocHash = "new",
            toc = emptyList(),
        )
        val unique = result as AnchorResolveResult.Unique
        assertEquals(moved.indexOf("书签段落"), unique.start)
    }

    @Test
    fun renderedQuote_mapsThroughBoldAndLink() {
        val bold = "前文\n\n这是**重要**的句子，后面还有。\n"
        val boldSpan = sourceSpanForRenderedQuote(bold, "这是重要的句子", bold.indexOf("重"))
        assertEquals(bold.indexOf("这是"), boldSpan?.first)
        assertEquals(bold.indexOf("句子") + "句子".length, boldSpan?.second)

        val link = "请看[点击这里](https://example.com/a)了解详情"
        val linkSpan = sourceSpanForRenderedQuote(link, "请看点击这里了解详情", 0)
        assertEquals(0, linkSpan?.first)
        assertEquals(link.length, linkSpan?.second)

        val plain = "这是重要的句子"
        val mixed = "$plain\n\n这是**重要**的句子\n"
        val hint = mixed.lastIndexOf("重要")
        val kept = sourceSpanForRenderedQuote(mixed, plain, hint)
        assertEquals(mixed.lastIndexOf("这是"), kept?.first)
        assertEquals(mixed.lastIndexOf("这是") + "这是**重要**的句子".length, kept?.second)
    }

    @Test
    fun freshMarkdownHighlight_isNotPending() {
        val sentence = "这是**重要**的句子"
        val left = "甲".repeat(40)
        val right = "乙".repeat(40)
        val document = "$left\n\n$sentence\n\n$right\n\n$sentence\n"
        val rendered = "这是重要的句子"
        val start = document.lastIndexOf("这是")
        val end = start + sentence.length
        val anchor = captureTextAnchor(document, plainText = false, start, end, emptyList(), "same")
        val highlight = HighlightEntity(
            id = 9L,
            bookId = 1L,
            startPosition = start,
            endPosition = end,
            highlightedText = rendered,
        ).withCapturedAnchor(anchor)
        val plan = planAnnotationAnchors(
            document = document,
            plainText = false,
            docHash = "same",
            toc = emptyList(),
            highlights = listOf(highlight),
            bookmarks = emptyList(),
        )
        assertFalse(9L in plan.pendingHighlightIds)
        assertEquals(start, plan.paintHighlights.single().startPosition)
        assertEquals(end, plan.paintHighlights.single().endPosition)
    }

    @Test
    fun movedMarkdownBlock_followsHashWithoutLiteralQuote() {
        val sentence = "这是**重要**的句子"
        val original = "前言\n\n$sentence\n\n结尾\n"
        val moved = "别的开头\n\n$sentence\n\n结尾\n"
        val rendered = "这是重要的句子"
        val start = original.indexOf("这是")
        val end = start + sentence.length
        val anchor = captureTextAnchor(original, plainText = false, start, end, emptyList(), "old")
        val result = resolveMarkdown(moved, anchor, rendered, start, end, "new")
        val unique = result as AnchorResolveResult.Unique
        assertEquals(moved.indexOf("这是"), unique.start)
        assertEquals(moved.indexOf("这是") + sentence.length, unique.end)
    }

    @Test
    fun duplicateMarkdown_afterEdit_usesContext() {
        val sentence = "这是**重要**的句子"
        val left = "甲".repeat(40)
        val right = "乙".repeat(40)
        val original = "$left\n\n$sentence\n\n$right\n\n$sentence\n"
        val edited = "新增的开头。\n\n$original"
        val rendered = "这是重要的句子"
        val start = original.lastIndexOf("这是")
        val end = start + sentence.length
        val anchor = captureTextAnchor(original, plainText = false, start, end, emptyList(), "old")
        val result = resolveMarkdown(edited, anchor, rendered, start, end, "new")
        val unique = result as AnchorResolveResult.Unique
        assertEquals(edited.lastIndexOf("这是"), unique.start)
    }

    @Test
    fun sameSentenceInHeadingAndBody_staysOnHeadingAfterInsertion() {
        val title = "准确率奖励"
        val original = "## $title\n\n这里是正文，其中也有${title}两个字。\n"
        val toc = listOf(MarkdownTocEntry(level = 2, title = title, sourceOffset = 0))
        val start = original.indexOf(title)
        val end = start + title.length
        val anchor = captureTextAnchor(original, plainText = false, start, end, toc, "old")
        val edited = "新增一段开场。\n\n## $title\n\n这里是正文，其中也有${title}两个字。\n"
        val editedToc = listOf(
            MarkdownTocEntry(level = 2, title = title, sourceOffset = edited.indexOf("## ")),
        )
        val result = AnnotationAnchorResolver.resolve(
            query = AnchorQuery(
                exact = title,
                docStart = start,
                docEnd = end,
                quotePrefix = anchor.quotePrefix,
                quoteSuffix = anchor.quoteSuffix,
                blockIndex = anchor.blockIndex,
                blockOffsetStart = anchor.blockOffsetStart,
                blockOffsetEnd = anchor.blockOffsetEnd,
                endBlockIndex = anchor.endBlockIndex,
                endBlockHash = anchor.endBlockHash,
                blockHash = anchor.blockHash,
                docHash = anchor.docHash,
                headingPath = anchor.headingPath.ifEmpty { null },
                point = false,
            ),
            document = edited,
            blocks = TextBlockIndex.build(edited, plainText = false),
            currentDocHash = "new",
            toc = editedToc,
        )
        val heading = edited.indexOf(title)
        val body = edited.lastIndexOf(title)
        val resolved = when (result) {
            is AnchorResolveResult.Unique -> result.start
            is AnchorResolveResult.Ambiguous -> result.start
            AnchorResolveResult.Unresolved -> -1
        }
        assertEquals(heading, resolved)
        assertTrue(resolved != body)
    }

    @Test
    fun editedMarkdownBlock_staysPending() {
        val original = "前言\n\n这是**重要**的句子\n"
        val edited = "前言\n\n这是**普通**的句子\n"
        val start = original.indexOf("这是")
        val end = original.indexOf("句子") + "句子".length
        val anchor = captureTextAnchor(original, plainText = false, start, end, emptyList(), "old")
        val highlight = HighlightEntity(
            id = 11L,
            bookId = 1L,
            startPosition = start,
            endPosition = end,
            highlightedText = "这是重要的句子",
        ).withCapturedAnchor(anchor)
        val plan = planAnnotationAnchors(
            document = edited,
            plainText = false,
            docHash = "new",
            toc = emptyList(),
            highlights = listOf(highlight),
            bookmarks = emptyList(),
        )
        assertTrue(plan.paintHighlights.isEmpty())
        assertTrue(11L in plan.pendingHighlightIds)
    }

    private fun resolveMarkdown(
        document: String,
        anchor: CapturedTextAnchor,
        exact: String,
        start: Int,
        end: Int,
        currentHash: String,
    ): AnchorResolveResult {
        val query = AnchorQuery(
            exact = exact,
            docStart = start,
            docEnd = end,
            quotePrefix = anchor.quotePrefix,
            quoteSuffix = anchor.quoteSuffix,
            blockIndex = anchor.blockIndex,
            blockOffsetStart = anchor.blockOffsetStart,
            blockOffsetEnd = anchor.blockOffsetEnd,
            endBlockIndex = anchor.endBlockIndex,
            endBlockHash = anchor.endBlockHash,
            blockHash = anchor.blockHash,
            docHash = anchor.docHash,
            headingPath = anchor.headingPath.ifEmpty { null },
            point = start == end,
        )
        return AnnotationAnchorResolver.resolve(
            query = query,
            document = document,
            blocks = TextBlockIndex.build(document, plainText = false),
            currentDocHash = currentHash,
            toc = emptyList(),
        )
    }

    private fun resolve(
        document: String,
        anchor: CapturedTextAnchor,
        exact: String,
        start: Int,
        end: Int,
        currentHash: String,
    ): AnchorResolveResult {
        val query = AnchorQuery(
            exact = exact,
            docStart = start,
            docEnd = end,
            quotePrefix = anchor.quotePrefix,
            quoteSuffix = anchor.quoteSuffix,
            blockIndex = anchor.blockIndex,
            blockOffsetStart = anchor.blockOffsetStart,
            blockOffsetEnd = anchor.blockOffsetEnd,
            endBlockIndex = anchor.endBlockIndex,
            endBlockHash = anchor.endBlockHash,
            blockHash = anchor.blockHash,
            docHash = anchor.docHash,
            headingPath = anchor.headingPath.ifEmpty { null },
            point = start == end,
        )
        return AnnotationAnchorResolver.resolve(
            query = query,
            document = document,
            blocks = TextBlockIndex.build(document, plainText = true),
            currentDocHash = currentHash,
            toc = emptyList(),
        )
    }

    private fun resolveLegacy(
        document: String,
        exact: String,
        start: Int,
        end: Int,
    ): AnchorResolveResult =
        AnnotationAnchorResolver.resolve(
            query = AnchorQuery(
                exact = exact,
                docStart = start,
                docEnd = end,
                quotePrefix = null,
                quoteSuffix = null,
                blockIndex = null,
                blockOffsetStart = null,
                blockOffsetEnd = null,
                endBlockIndex = null,
                endBlockHash = null,
                blockHash = null,
                docHash = null,
                headingPath = null,
                point = false,
            ),
            document = document,
            blocks = TextBlockIndex.build(document, plainText = true),
            currentDocHash = "current",
            toc = emptyList(),
        )
}
