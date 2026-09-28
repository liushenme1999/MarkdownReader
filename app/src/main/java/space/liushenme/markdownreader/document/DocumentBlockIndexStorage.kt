package space.liushenme.markdownreader.document

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object DocumentBlockIndexStorage {
    fun write(bundleDir: File, format: DocumentFormat, content: String): DocumentChunkIndex {
        val blocks = DocumentBlockParser.parse(content, format)
        val index = DocumentBlockParser.buildChunkIndex(content.length, blocks)
        val target = File(bundleDir, DocumentArtifactManifest.BLOCK_INDEX_FILE)
        val temporary = File(bundleDir, "${DocumentArtifactManifest.BLOCK_INDEX_FILE}.tmp")
        val json = JSONObject().apply {
            put("format", format.name)
            put("contentLength", content.length)
            put("blocks", JSONArray().apply {
                blocks.forEach { block ->
                    put(JSONObject().apply {
                        put("id", block.id)
                        put("kind", blockKind(block))
                        put("start", block.range.start)
                        put("end", block.range.endExclusive)
                    })
                }
            })
            put("chunks", JSONArray().apply {
                index.chunks.forEach { chunk ->
                    put(JSONObject().apply {
                        put("index", chunk.index)
                        put("start", chunk.range.start)
                        put("end", chunk.range.endExclusive)
                        put("firstBlock", chunk.firstBlockIndex)
                        put("lastBlock", chunk.lastBlockIndex)
                    })
                }
            })
        }
        temporary.writeText(json.toString())
        if (target.exists()) target.delete()
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        return index
    }

    private fun blockKind(block: DocumentBlock): String = when (block) {
        is DocumentBlock.Heading -> "Heading"
        is DocumentBlock.Paragraph -> "Paragraph"
        is DocumentBlock.CodeBlock -> "CodeBlock"
        is DocumentBlock.Image -> "Image"
        is DocumentBlock.Formula -> "Formula"
        is DocumentBlock.Table -> "Table"
        is DocumentBlock.Diagram -> "Diagram"
        is DocumentBlock.Quote -> "Quote"
        is DocumentBlock.ListBlock -> "List"
    }
}
