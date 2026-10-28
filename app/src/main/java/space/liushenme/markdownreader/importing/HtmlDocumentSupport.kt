package space.liushenme.markdownreader.importing

object HtmlDocumentSupport {
    fun looksLikeHtmlBody(body: String): Boolean {
        val trimmed = body.trimStart().lowercase()
        return trimmed.startsWith("<!doctype html>") ||
            trimmed.startsWith("<html") ||
            (trimmed.startsWith("<head") || trimmed.startsWith("<body"))
    }
}
