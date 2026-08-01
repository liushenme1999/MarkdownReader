package space.liushenme.markdownreader.data.webdav

data class WebDavFile(
    val path: String,
    val displayName: String,
    val size: Long,
    val contentType: String,
    val resourceType: String,
    val lastModify: Long,
) {
    val isDir: Boolean
        get() = isDir(contentType, resourceType)

    companion object {
        fun isDir(contentType: String, resourceType: String): Boolean =
            contentType == "httpd/unix-directory" ||
                resourceType.lowercase().contains("collection")
    }
}
