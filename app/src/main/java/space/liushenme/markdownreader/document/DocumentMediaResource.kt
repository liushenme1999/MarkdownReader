package space.liushenme.markdownreader.document

sealed interface DocumentMediaResource {
    val stableKey: String
    val source: String

    data class NetworkImage(
        override val stableKey: String,
        override val source: String,
    ) : DocumentMediaResource

    data class LocalImage(
        override val stableKey: String,
        override val source: String,
    ) : DocumentMediaResource

    data class Diagram(
        override val stableKey: String,
        override val source: String,
    ) : DocumentMediaResource

    data class Formula(
        override val stableKey: String,
        override val source: String,
        val block: Boolean,
    ) : DocumentMediaResource

    data class PdfPage(
        override val stableKey: String,
        override val source: String,
        val pageIndex: Int,
    ) : DocumentMediaResource
}

fun DocumentSnapshot.mediaResources(): List<DocumentMediaResource> = blocks.mapNotNull { block ->
    when (block) {
        is DocumentBlock.Image -> when {
            block.pdfPageIndex != null -> DocumentMediaResource.PdfPage(
                stableKey = block.id,
                source = block.destination,
                pageIndex = block.pdfPageIndex,
            )
            block.destination.startsWith("http://", ignoreCase = true) ||
                block.destination.startsWith("https://", ignoreCase = true) ->
                DocumentMediaResource.NetworkImage(block.id, block.destination)
            else -> DocumentMediaResource.LocalImage(block.id, block.destination)
        }
        is DocumentBlock.Diagram -> DocumentMediaResource.Diagram(block.id, block.destination)
        is DocumentBlock.Formula -> DocumentMediaResource.Formula(block.id, block.expression, block.block)
        else -> null
    }
}.distinctBy(DocumentMediaResource::stableKey)
