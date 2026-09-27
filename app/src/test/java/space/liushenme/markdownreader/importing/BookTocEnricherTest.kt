package space.liushenme.markdownreader.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class BookTocEnricherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun alignToBody_reparsesAfterPrepareInsertsBlankLineBeforeTable() {
        // prepare() 会在标题与表格之间插入空行，旧 TOC 偏移会偏早。
        val raw = """
            # Intro

            ## Section Two
            | a | b |
            | - | - |
            | 1 | 2 |

            ## Section Three
            body
        """.trimIndent()
        val stale = BookTocEnricher.enrichIfEmpty(
            ImportedBookFormat.MARKDOWN,
            ExtractedBookText(body = raw),
        )
        val preparedBody = space.liushenme.markdownreader.markdown.MarkdownPreprocessor
            .ensureBlankLineBeforeTables(raw)
        assertTrue(
            "fixture must change length",
            preparedBody.length != raw.length || preparedBody != raw,
        )
        val aligned = BookTocEnricher.alignToBody(
            ImportedBookFormat.MARKDOWN,
            stale.copy(body = preparedBody),
        )
        val sec3 = aligned.toc.first { it.title == "Section Three" }
        assertEquals(
            preparedBody.indexOf("## Section Three"),
            sec3.sourceOffset,
        )
        // 若仍用 raw 上的偏移，会指到错误位置
        val staleOff = stale.toc.first { it.title == "Section Three" }.sourceOffset
        if (preparedBody.length != raw.length) {
            assertTrue(staleOff != sec3.sourceOffset)
        }
    }

    @Test
    fun readBundle_realignsTocAfterMaterializeLengthensAssetUrls() {
        val dir = tmp.newFolder("book-assets-toc")
        val assets = File(dir, ParsedBookStorage.ASSETS_DIR).also { it.mkdirs() }
        File(assets, "cover.png").writeBytes(byteArrayOf(1, 2, 3, 4))
        // 落盘正文用短占位；读盘后换成更长 file://，旧偏移会整体前移失败。
        val storedBody = """
            # Cover

            ![cover](book-asset://cover.png)

            ## Real Target
            hello
        """.trimIndent()
        val staleToc = AtxMarkdownTocParser.parse(storedBody)
        ParsedBookStorage.writeBundle(
            dir = dir,
            extracted = ExtractedBookText(body = storedBody, toc = staleToc),
            coverBytes = null,
        )
        val read = ParsedBookStorage.readBundle(dir)!!
        assertTrue(read.body.contains("file://"))
        assertTrue(read.body.length > storedBody.length)
        val target = read.toc.first { it.title == "Real Target" }
        assertEquals(read.body.indexOf("## Real Target"), target.sourceOffset)
    }
}
